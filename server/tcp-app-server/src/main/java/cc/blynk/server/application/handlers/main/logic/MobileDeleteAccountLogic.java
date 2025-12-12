package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.Holder;
import cc.blynk.server.core.dao.FileManager;
import cc.blynk.server.core.dao.ReportingDiskDao;
import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.dao.TokenManager;
import cc.blynk.server.core.dao.UserDao;
import cc.blynk.server.core.dao.UserKey;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.db.DBManager;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.illegalCommand;
import static cc.blynk.server.internal.CommonByteBufUtil.notAllowed;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * Handles account deletion requests from mobile app.
 * User must provide their password to confirm deletion.
 * 
 * The account data is moved to deleted folder (5-day backup period for admin recovery)
 * before being permanently removed.
 *
 * The Plynk Project.
 * Created for App Store compliance (Guideline 5.1.1(v)).
 */
public final class MobileDeleteAccountLogic {

    private static final Logger log = LogManager.getLogger(MobileDeleteAccountLogic.class);

    private final UserDao userDao;
    private final FileManager fileManager;
    private final DBManager dbManager;
    private final ReportingDiskDao reportingDao;
    private final TokenManager tokenManager;
    private final SessionDao sessionDao;

    public MobileDeleteAccountLogic(Holder holder) {
        this.userDao = holder.userDao;
        this.fileManager = holder.fileManager;
        this.dbManager = holder.dbManager;
        this.reportingDao = holder.reportingDiskDao;
        this.tokenManager = holder.tokenManager;
        this.sessionDao = holder.sessionDao;
    }

    /**
     * Process account deletion request.
     * Message body should contain the user's password hash for verification.
     * The password should be hashed client-side using SHA256(password + SHA256(email.toLowerCase()))
     * 
     * @param ctx Channel context
     * @param user The user requesting deletion
     * @param msg Message containing password hash confirmation
     */
    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage msg) {
        String passwordHash = msg.body;

        // Password hash is required for verification
        if (passwordHash == null || passwordHash.isEmpty()) {
            log.warn("Delete account request without password from {}", user.email);
            ctx.writeAndFlush(illegalCommand(msg.id), ctx.voidPromise());
            return;
        }

        // Verify password - compare hashes directly (client sends pre-hashed password like login)
        if (!passwordHash.equals(user.pass)) {
            log.warn("Delete account request with wrong password from {}", user.email);
            ctx.writeAndFlush(notAllowed(msg.id), ctx.voidPromise());
            return;
        }

        log.info("Processing account deletion for user: {}-{}", user.email, user.appName);

        try {
            // Remove all push notification tokens first
            for (DashBoard dash : user.profile.dashBoards) {
                Notification notification = dash.getNotificationWidget();
                if (notification != null) {
                    notification.androidTokens.clear();
                    notification.iOSTokens.clear();
                }
                
                // Delete all tokens associated with this dashboard (devices and shared)
                tokenManager.deleteDash(dash);
            }

            UserKey userKey = new UserKey(user.email, user.appName);

            // Remove from in-memory store
            userDao.delete(userKey);

            // Move user file to deleted folder (5-day backup for admin recovery)
            // FileManager.delete() moves the file to the "deleted" subdirectory
            if (!fileManager.delete(user.email, user.appName)) {
                log.error("Failed to move user file to deleted folder for {}", user.email);
            }

            // Delete reporting/historical data
            reportingDao.delete(user);

            // Delete from database if using DB
            dbManager.deleteUser(userKey);

            // Close all active sessions for this user
            Session session = sessionDao.userSession.remove(userKey);
            if (session != null) {
                session.closeAll();
            }

            log.info("Account successfully deleted for user: {}", user.email);

            // Send success response before closing connection
            ctx.writeAndFlush(ok(msg.id), ctx.voidPromise());
            
            // Close the connection
            ctx.close();

        } catch (Exception e) {
            log.error("Error deleting account for user {}: {}", user.email, e.getMessage(), e);
            ctx.writeAndFlush(illegalCommand(msg.id), ctx.voidPromise());
        }
    }
}

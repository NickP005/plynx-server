package cc.blynk.server.application.handlers.main.logic.dashboard.device;

import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NotAllowedException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.ok;
import static cc.blynk.utils.StringUtils.split2;

/**
 * Plynx linked devices: rimuove dal progetto un device alias (creato con
 * LINK_DEVICE). La scheda vera resta intatta nel progetto proprietario:
 * qui si tolgono solo l'alias e i valori dei widget che lo usavano.
 * Body: "dashId\0deviceId".
 */
public final class MobileUnlinkDeviceLogic {

    private static final Logger log = LogManager.getLogger(MobileUnlinkDeviceLogic.class);

    private MobileUnlinkDeviceLogic() {
    }

    public static void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        String[] split = split2(message.body);

        if (split.length < 2) {
            throw new IllegalCommandException("Wrong income message format.");
        }

        int dashId = Integer.parseInt(split[0]);
        int deviceId = Integer.parseInt(split[1]);

        DashBoard dash = user.profile.getDashByIdOrThrow(dashId);
        Device device = user.profile.getDeviceById(dash, deviceId);

        if (device == null) {
            throw new IllegalCommandException("Device not found.");
        }
        if (!device.isLinked()) {
            throw new NotAllowedException("Device is not a linked device.", message.id);
        }

        log.debug("Unlinking device {} from dash {}.", deviceId, dashId);

        user.profile.deleteDevice(dash, deviceId);
        user.lastModifiedTs = System.currentTimeMillis();

        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}

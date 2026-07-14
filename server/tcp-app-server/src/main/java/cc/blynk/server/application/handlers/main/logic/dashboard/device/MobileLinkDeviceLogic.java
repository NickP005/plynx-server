package cc.blynk.server.application.handlers.main.logic.dashboard.device;

import cc.blynk.server.Holder;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NotAllowedException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Command.LINK_DEVICE;
import static cc.blynk.server.internal.CommonByteBufUtil.makeUTF8StringMessage;
import static cc.blynk.utils.StringUtils.split3;

/**
 * Plynx linked devices: collega nel progetto target una scheda che vive in
 * un altro progetto. Il device creato e' un alias (stessa scheda fisica,
 * nessun token proprio): i widget del target la comandano e ne ricevono gli
 * aggiornamenti via fan-out. Body: "targetDashId\0ownerDashId\0ownerDeviceId".
 * Risponde con il JSON del device alias creato (come CREATE_DEVICE).
 */
public final class MobileLinkDeviceLogic {

    private static final Logger log = LogManager.getLogger(MobileLinkDeviceLogic.class);

    private MobileLinkDeviceLogic() {
    }

    public static void messageReceived(Holder holder, ChannelHandlerContext ctx,
                                       User user, StringMessage message) {
        String[] split = split3(message.body);

        if (split.length < 3) {
            throw new IllegalCommandException("Wrong income message format.");
        }

        int targetDashId = Integer.parseInt(split[0]);
        int ownerDashId = Integer.parseInt(split[1]);
        int ownerDeviceId = Integer.parseInt(split[2]);

        if (targetDashId == ownerDashId) {
            throw new NotAllowedException("Cannot link a device into its own project.", message.id);
        }

        DashBoard targetDash = user.profile.getDashByIdOrThrow(targetDashId);
        DashBoard ownerDash = user.profile.getDashByIdOrThrow(ownerDashId);

        Device owner = user.profile.getDeviceById(ownerDash, ownerDeviceId);
        if (owner == null) {
            throw new IllegalCommandException("Owner device not found.");
        }
        if (owner.isLinked()) {
            throw new NotAllowedException("Cannot link an alias device.", message.id);
        }

        if (targetDash.devices.length > holder.limits.deviceLimit) {
            throw new NotAllowedException("Device limit is reached.", message.id);
        }

        int newId = 0;
        for (Device device : targetDash.devices) {
            if (device.linkedToDashId == ownerDashId && device.linkedToDeviceId == ownerDeviceId) {
                throw new NotAllowedException("Device is already linked into this project.", message.id);
            }
            newId = Math.max(newId, device.id + 1);
        }

        log.debug("Linking device {}/{} into dash {}.", ownerDashId, ownerDeviceId, targetDashId);

        Device alias = new Device(newId, owner.name, owner.boardType);
        alias.connectionType = owner.connectionType;
        alias.iconName = owner.iconName;
        alias.isUserIcon = owner.isUserIcon;
        alias.linkedToDashId = ownerDashId;
        alias.linkedToDeviceId = ownerDeviceId;
        //stato mirror: il collegato nasce gia' con lo stato reale della scheda
        alias.status = owner.status;
        alias.connectTime = owner.connectTime;
        alias.disconnectTime = owner.disconnectTime;
        alias.dataReceivedAt = owner.dataReceivedAt;

        user.profile.addDevice(targetDash, alias);
        user.lastModifiedTs = System.currentTimeMillis();

        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(
                    makeUTF8StringMessage(LINK_DEVICE, message.id, alias.toString()), ctx.voidPromise());
        }
    }

}

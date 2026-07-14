package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.utils.JarUtil;
import io.netty.channel.ChannelHandlerContext;

import static cc.blynk.server.core.protocol.enums.Command.GET_SERVER_INFO;
import static cc.blynk.server.internal.CommonByteBufUtil.makeUTF8StringMessage;

/**
 * Capability handshake (Plynx): returns server version and the list of
 * optional capabilities this build supports, as a small JSON object.
 * The mobile app calls this right after login; a legacy server simply
 * never answers (no case in MobileHandler), so the app falls back to
 * "legacy" after a short timeout and degrades gracefully.
 *
 * Keep the payload additive: apps ignore unknown fields/caps.
 */
public final class MobileGetServerInfoLogic {

    /** Optional features this build advertises to the app. */
    private static final String CAPS = "\"serverInfo\",\"linkedDevices\"";

    private static String cachedBody;

    private MobileGetServerInfoLogic() {
    }

    public static void messageReceived(ChannelHandlerContext ctx, StringMessage message) {
        if (cachedBody == null) {
            String version = JarUtil.getServerVersion();
            cachedBody = "{\"version\":\"" + version + "\",\"caps\":[" + CAPS + "]}";
        }
        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(makeUTF8StringMessage(GET_SERVER_INFO, message.id, cachedBody),
                    ctx.voidPromise());
        }
    }

}

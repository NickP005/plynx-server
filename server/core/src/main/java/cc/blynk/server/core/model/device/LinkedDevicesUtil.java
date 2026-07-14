package cc.blynk.server.core.model.device;

import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.auth.Session;

/**
 * Plynx linked devices: a device owned by one project can be linked into
 * other projects as an "alias" device (same physical board, no own token).
 * The alias carries {@link Device#linkedToDashId}/{@link Device#linkedToDeviceId}
 * pointing at the owner. This util centralizes the fan-out of hardware
 * events to the projects that link a device, and the status mirroring of
 * the alias entries.
 *
 * Everything here is additive: profiles without links take the early-exit
 * paths and behave exactly like legacy.
 */
public final class LinkedDevicesUtil {

    private LinkedDevicesUtil() {
    }

    /** Quick check to keep the hot paths cheap for link-free profiles. */
    public static boolean hasLinks(Profile profile) {
        for (DashBoard dash : profile.dashBoards) {
            for (Device device : dash.devices) {
                if (device.isLinked()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sends an app-bound message to every ACTIVE project that links
     * (ownerDashId, ownerDeviceId), rewriting dash/device ids to the alias.
     */
    public static void sendToLinkedApps(Session session, Profile profile, short cmd, int msgId,
                                        int ownerDashId, int ownerDeviceId, String body) {
        for (DashBoard dash : profile.dashBoards) {
            if (dash.id == ownerDashId || !dash.isActive) {
                continue;
            }
            for (Device device : dash.devices) {
                if (device.linkedToDashId == ownerDashId && device.linkedToDeviceId == ownerDeviceId) {
                    session.sendToApps(cmd, msgId, dash.id, device.id, body);
                }
            }
        }
    }

    /** Mirrors ONLINE state (and connect time) onto every alias of the owner. */
    public static void mirrorConnected(Profile profile, int ownerDashId, Device owner) {
        forEachAlias(profile, ownerDashId, owner.id, alias -> {
            alias.status = owner.status;
            alias.connectTime = owner.connectTime;
        });
    }

    /** Mirrors OFFLINE state (and disconnect time) onto every alias of the owner. */
    public static void mirrorDisconnected(Profile profile, int ownerDashId, Device owner) {
        forEachAlias(profile, ownerDashId, owner.id, alias -> {
            alias.status = owner.status;
            alias.disconnectTime = owner.disconnectTime;
        });
    }

    /** Resolves the owner device of an alias (null if dangling or not an alias). */
    public static Device resolveOwner(Profile profile, Device alias) {
        if (!alias.isLinked()) {
            return null;
        }
        DashBoard ownerDash = profile.getDashById(alias.linkedToDashId);
        if (ownerDash == null) {
            return null;
        }
        return profile.getDeviceById(ownerDash, alias.linkedToDeviceId);
    }

    /** Removes every alias across the profile that points at (ownerDashId, ownerDeviceId). */
    public static void removeLinksTo(Profile profile, int ownerDashId, int ownerDeviceId) {
        for (DashBoard dash : profile.dashBoards) {
            Device[] devices = dash.devices;
            for (Device device : devices) {
                if (device.linkedToDashId == ownerDashId && device.linkedToDeviceId == ownerDeviceId) {
                    profile.deleteDevice(dash, device.id);
                }
            }
        }
    }

    /** Removes every alias across the profile that points into ownerDashId. */
    public static void removeLinksToDash(Profile profile, int ownerDashId) {
        for (DashBoard dash : profile.dashBoards) {
            if (dash.id == ownerDashId) {
                continue;
            }
            Device[] devices = dash.devices;
            for (Device device : devices) {
                if (device.linkedToDashId == ownerDashId) {
                    profile.deleteDevice(dash, device.id);
                }
            }
        }
    }

    private static void forEachAlias(Profile profile, int ownerDashId, int ownerDeviceId,
                                     java.util.function.Consumer<Device> consumer) {
        for (DashBoard dash : profile.dashBoards) {
            for (Device device : dash.devices) {
                if (device.linkedToDashId == ownerDashId && device.linkedToDeviceId == ownerDeviceId) {
                    consumer.accept(device);
                }
            }
        }
    }

}

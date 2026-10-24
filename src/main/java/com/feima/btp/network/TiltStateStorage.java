package com.feima.btp.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程玩家倾斜状态的存储。
 *
 * <p>刻意放在 network 包而不是 client 包——服务端也要加载本类（用于
 * {@code ClientboundTiltPacket} 的 handle 方法），不能引用任何客户端类。
 */
public final class TiltStateStorage {
    private static final Map<UUID, Boolean> REMOTE_TILT_STATES = new ConcurrentHashMap<>();

    private TiltStateStorage() {}

    public static void setTiltState(UUID playerId, boolean tilting) {
        if (tilting) {
            REMOTE_TILT_STATES.put(playerId, Boolean.TRUE);
        } else {
            REMOTE_TILT_STATES.remove(playerId);
        }
    }

    public static boolean getTiltState(UUID playerId) {
        return REMOTE_TILT_STATES.getOrDefault(playerId, Boolean.FALSE);
    }

    public static void clear() {
        REMOTE_TILT_STATES.clear();
    }
}
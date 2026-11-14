package com.feima.btp.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程玩家倾斜状态 + 散布倍率的存储。
 *
 * <p>刻意放在 network 包而不是 client 包——服务端也要加载本类（用于
 * {@code ClientboundTiltPacket} / {@code ServerboundTiltPacket} / {@code GunSpreadMixin}
 * 的 handle 方法），不能引用任何客户端类。
 *
 * <p><b>散布倍率的设计</b>：{@code BTPConfig.tiltSpreadMultiplier} 是 CLIENT 配置，
 * 专用服务器上读不到玩家设置的值。因此服务端权威射击路径
 * （{@code GunSpreadMixin}）必须读 {@link #getSpreadMultiplier(UUID)}——
 * 由客户端通过 {@code ServerboundTiltPacket} 同步过来的倍率。
 * 这样客户端准星（{@code CacheValueMixin}）与服务端命中判定使用同一个数值。
 */
public final class TiltStateStorage {
    private static final Map<UUID, Boolean> REMOTE_TILT_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> SPREAD_MULTIPLIERS = new ConcurrentHashMap<>();

    private TiltStateStorage() {}

    public static void setTiltState(UUID playerId, boolean tilting) {
        if (playerId == null) return;
        if (tilting) {
            REMOTE_TILT_STATES.put(playerId, Boolean.TRUE);
        } else {
            REMOTE_TILT_STATES.remove(playerId);
        }
    }

    public static boolean getTiltState(UUID playerId) {
        if (playerId == null) return false;
        return REMOTE_TILT_STATES.getOrDefault(playerId, Boolean.FALSE);
    }

    /**
     * 设置玩家的据枪散布倍率。1.0 表示无缩放——此时直接移除条目，
     * 避免 {@code getSpreadMultiplier} 走 Map 查找。
     */
    public static void setSpreadMultiplier(UUID playerId, float multiplier) {
        if (playerId == null) return;
        if (!Float.isFinite(multiplier) || multiplier < 0.0f) {
            SPREAD_MULTIPLIERS.remove(playerId);
            return;
        }
        if (Math.abs(multiplier - 1.0f) < 0.0001f) {
            SPREAD_MULTIPLIERS.remove(playerId);
        } else {
            SPREAD_MULTIPLIERS.put(playerId, multiplier);
        }
    }

    public static float getSpreadMultiplier(UUID playerId) {
        if (playerId == null) return 1.0f;
        return SPREAD_MULTIPLIERS.getOrDefault(playerId, 1.0f);
    }

    public static void clear() {
        REMOTE_TILT_STATES.clear();
        SPREAD_MULTIPLIERS.clear();
    }

    /** 单个玩家断开连接时调用，避免 UUID 残留。 */
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        REMOTE_TILT_STATES.remove(playerId);
        SPREAD_MULTIPLIERS.remove(playerId);
    }
}
package com.feima.btp.client;

import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TiltStateManager {
    private static final Map<UUID, Boolean> REMOTE_TILT_STATES = new ConcurrentHashMap<>();

    public static void setTiltState(UUID playerId, boolean tilting) {
        if (Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.getUUID().equals(playerId)) {
            return;
        }
        REMOTE_TILT_STATES.put(playerId, tilting);
    }

    /**
     * 统一查询接口：
     * - 本地玩家：主动据枪状态（来自 TiltToggleHandler） 或 （蹲下 && 配置允许 && 持枪）
     * - 远程玩家：缓存的主动据枪状态 或 （蹲下 && 配置允许 && 持枪）
     */
    public static boolean isTilting(Player player) {
        if (player == null) return false;

        boolean activeTilt;
        if (player == Minecraft.getInstance().player) {
            activeTilt = TiltToggleHandler.isTilting();
        } else {
            activeTilt = REMOTE_TILT_STATES.getOrDefault(player.getUUID(), false);
        }

        // 蹲下自动据枪（仅当配置允许且持枪时）
        boolean crouchTilt = false;
        if (!BTPConfig.disableVanillaCrouchTilt) {
            crouchTilt = player.isCrouching() && IGun.mainHandHoldGun(player);
        }

        return activeTilt || crouchTilt;
    }

    public static void clear() {
        REMOTE_TILT_STATES.clear();
    }
}
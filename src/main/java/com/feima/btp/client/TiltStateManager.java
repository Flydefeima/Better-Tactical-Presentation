package com.feima.btp.client;

import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.network.TiltStateStorage;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端专用的倾斜状态查询。
 *
 * <p>数据存储已迁移到 {@link TiltStateStorage}（network 包，两端通用），
 * 本类只负责「结合本地输入状态 / 蹲下状态 / 持枪状态」得出最终判定。
 */
public final class TiltStateManager {
    private TiltStateManager() {}

    /**
     * 统一查询接口：
     * - 本地玩家：主动据枪状态（来自 {@link TiltController}） 或 （蹲下 && 配置允许 && 持枪）
     * - 远程玩家：缓存的主动据枪状态 或 （蹲下 && 配置允许 && 持枪）
     */
    public static boolean isTilting(Player player) {
        if (player == null) return false;

        boolean activeTilt;
        if (player == Minecraft.getInstance().player) {
            activeTilt = TiltController.isTilting();
        } else {
            activeTilt = TiltStateStorage.getTiltState(player.getUUID());
        }

        boolean crouchTilt = false;
        if (!BTPConfig.disableVanillaCrouchTilt) {
            crouchTilt = player.isCrouching() && IGun.mainHandHoldGun(player);
        }

        return activeTilt || crouchTilt;
    }

    public static void clear() {
        TiltStateStorage.clear();
    }
}
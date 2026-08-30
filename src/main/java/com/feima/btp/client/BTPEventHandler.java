package com.feima.btp.client;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.animation.BTPAnimator;
import com.feima.btp.client.compat.TaCZLabsCompat;
import com.feima.btp.util.BTPTipsHelper;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * BTP 模组客户端统一事件处理器。
 * 负责：按键注册、玩家生命周期、启动健康检查、延迟任务等。
 */
@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BTPEventHandler {

    // ============================================================
    // 按键定义（原 ClientModEvents 内容）
    // ============================================================
    public static final KeyMapping TOGGLE_TILT_KEY = new KeyMapping(
            "key.btp.toggle_tilt",
            GLFW.GLFW_KEY_B,
            "key.categories.btp"
    );

    public static final KeyMapping HOLD_TILT_KEY = new KeyMapping(
            "key.btp.hold_tilt",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.btp"
    );

    // ============================================================
    // MOD 总线事件（按键注册）
    // ============================================================
    @Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_TILT_KEY);
            event.register(HOLD_TILT_KEY);
        }
    }

    // ============================================================
    // FORGE 总线事件（生命周期、Tick、健康检查）
    // ============================================================

    private static boolean hasChecked = false;
    private static int loginDelayTicks = 0;

    private BTPEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        hasChecked = false;
        loginDelayTicks = 20;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (hasChecked) return;
        if (loginDelayTicks > 0) {
            loginDelayTicks--;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (BTPTipsHelper.hasPendingErrors()) {
            BTPTipsHelper.sendPendingErrors(player);
        }

        performStartupChecks(player);
        hasChecked = true;
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BTPTipsHelper.clearPendingErrors();
        TiltStateManager.clear(); // ===== 新增：清空远程玩家倾斜状态缓存 =====
        hasChecked = false;
        loginDelayTicks = 0;
    }

    private static void performStartupChecks(Player player) {
        if (player == null) return;

        if (BTPMod.isPlayerAnimatorLoaded) {
            try {
                PlayerAnimatorAssetManager manager = PlayerAnimatorAssetManager.get();
                boolean rifleMissing = !manager.containsKey(BTPAnimator.RIFLE_TILT_ANIMATION_ID);
                boolean pistolMissing = !manager.containsKey(BTPAnimator.PISTOL_TILT_ANIMATION_ID);
                if (rifleMissing || pistolMissing) {
                    BTPTipsHelper.sendMissingTiltAnimationMessage(player);
                }
            } catch (Exception e) {
                BTPLog.LOGGER.warn("Failed to check tilt animations during startup", e);
            }
        }

        if (BTPMod.isTaczLabsLoaded) {
            try {
                TaCZLabsCompat.reset();
                TaCZLabsCompat.setCrosshairEnabled(!TiltToggleHandler.isTilting());
            } catch (Exception e) {
                BTPLog.LOGGER.warn("Failed to check TaczLabs compatibility during startup", e);
            }
        }
    }
}
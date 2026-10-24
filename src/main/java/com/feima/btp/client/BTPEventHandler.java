package com.feima.btp.client;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.animation.AnimationRegistry;
import com.feima.btp.client.animation.BTPAnimator;
import com.feima.btp.client.animation.TiltAnimNames;
import com.feima.btp.client.compat.TaCZLabsCompat;
import com.feima.btp.util.BTPTipsHelper;
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

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BTPEventHandler {

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

    @Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_TILT_KEY);
            event.register(HOLD_TILT_KEY);
        }
    }

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
        TiltStateManager.clear();
        // FIX: BTPAnimator 的字段/方法签名引用 dev.kosmx.playerAnim.* 类。
        // 未装 PlayerAnimator 时 JVM 解析 BTPAnimator 会抛 NoClassDefFoundError。
        // 与 BTPMod.onLoadComplete / performStartupChecks 的契约保持一致：
        // 只有在 PlayerAnimator 加载时才允许触碰 BTPAnimator。
        if (BTPMod.isPlayerAnimatorLoaded) {
            BTPAnimator.resetState();
        }
        hasChecked = false;
        loginDelayTicks = 0;
    }

    private static void performStartupChecks(Player player) {
        if (player == null) return;

        if (BTPMod.isPlayerAnimatorLoaded) {
            try {
                // AnimationRegistry.player 内部已做 containsKey 检查，
                // 直接调用即可——file 缺失和 name 缺失会各自打 warn 日志。
                boolean rifleMissing = AnimationRegistry.player(
                        TiltAnimNames.RIFLE_FILE, TiltAnimNames.RIFLE_LOOP) == null;
                boolean pistolMissing = AnimationRegistry.player(
                        TiltAnimNames.PISTOL_FILE, TiltAnimNames.PISTOL_LOOP) == null;
                if (rifleMissing || pistolMissing) {
                    BTPTipsHelper.sendMissingTiltAnimationMessage();
                }
            } catch (Exception e) {
                BTPLog.LOGGER.warn("Failed to check tilt animations during startup", e);
            }
        }

        if (BTPMod.isTaczLabsLoaded) {
            try {
                TaCZLabsCompat.reset();
            } catch (Exception e) {
                BTPLog.LOGGER.warn("Failed to check TaczLabs compatibility during startup", e);
            }
        }
    }
}
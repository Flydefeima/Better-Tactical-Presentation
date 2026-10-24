package com.feima.btp.mixin;

import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.client.tilt.ZoomStageTiltMode;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.ZoomKey;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 TaCZ 的 V 键（及手柄 V）逻辑。
 *
 * <p>三个功能：
 * <ol>
 *   <li><b>据枪档</b>：让"据枪"作为虚拟的最后一档倍率（仅 ZOOM_STAGE 模式）</li>
 *   <li><b>倍率提示</b>：ZOOM_STAGE 模式下按 V 切换倍率时显示新倍率（如 "2x"）</li>
 *   <li><b>不开镜切倍率</b>：允许玩家在非瞄准状态下按 V 切换倍率</li>
 * </ol>
 *
 * <p><b>服务端契约</b>：TaCZ 的 {@code ClientMessagePlayerZoom.handle} 会调用
 * {@code IGunOperator.zoom()}。BTP 的 aim→zoom→aim 序列依赖 Forge SimpleChannel
 * 的有序性：服务端按序收到三个包，处理 zoom 包时 aim 状态为 true，检查通过。
 */
@Mixin(value = ZoomKey.class, remap = false)
public class ZoomKeyMixin {

    // ============================================================
    // 键盘 / 鼠标入口
    // ============================================================

    @Inject(method = "doZoomLogic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void btp$interceptZoomKey(CallbackInfo ci) {
        if (btp$handleZoom()) {
            ci.cancel();
        }
    }

    // ============================================================
    // 手柄入口
    // ============================================================

    @Inject(method = "onZoomControllerPress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void btp$interceptZoomController(boolean isPress, CallbackInfoReturnable<Boolean> cir) {
        if (!isPress) return;
        if (btp$handleZoom()) {
            cir.setReturnValue(true);
        }
    }

    // ============================================================
    // 统一处理逻辑
    // ============================================================

    /**
     * 统一处理 V 键。返回 true 表示本次 V 键已完全由 BTP 接管（应 cancel TaCZ 原逻辑）；
     * 返回 false 表示继续让 TaCZ 处理（BTP 可能已显示提示）。
     *
     * <p><b>执行顺序</b>：
     * <ol>
     *   <li>据枪档开/关（{@link TiltController#tryHandleZoomAsTilt}）——
     *       返回 true 时直接接管，走"战术据枪 开/关"提示</li>
     *   <li>判断本次 V 是否会实际发包：
     *       - 已瞄准 → TaCZ 原版会发包
     *       - 非瞄准 + {@code enableZoomWithoutAim} → BTP 代理发包</li>
     *   <li>若会发包且当前是 ZOOM_STAGE 模式 → 显示"下一档倍率"提示</li>
     *   <li>若走代理发包分支 → 执行 aim→zoom→aim 序列并 cancel TaCZ 原逻辑</li>
     * </ol>
     */
    private static boolean btp$handleZoom() {
        // 1. 据枪档（已含"战术据枪 开/关"提示）
        if (TiltController.tryHandleZoomAsTilt()) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;

        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return false;
        if (!IGun.mainHandHoldGun(player)) return false;

        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean wasAim = op.isAim();
        boolean willProxySend = !wasAim && BTPConfig.enableZoomWithoutAim;
        boolean willSendZoom = wasAim || willProxySend;

        // 2. ZOOM_STAGE 模式下显示倍率提示（仅在会真正发包时）
        if (willSendZoom && TiltController.isZoomStageMode()) {
            ZoomStageTiltMode.showNextZoomHint(player);
        }

        // 3. 非瞄准状态下 BTP 代理发包
        if (willProxySend) {
            op.aim(true);
            AimKey.AIM_KEY.setDown(true);
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerZoom());
            op.aim(false);
            AimKey.AIM_KEY.setDown(false);
            return true;
        }

        // 4. 已瞄准（或有其它情况）→ 让 TaCZ 原版发包
        return false;
    }
}
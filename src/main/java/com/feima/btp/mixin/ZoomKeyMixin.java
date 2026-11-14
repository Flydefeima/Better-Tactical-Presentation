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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 TaCZ 的 V 键（及手柄 V）逻辑。
 *
 * <p><b>TaCZ 1.2.0 接口变更</b>：
 * <ul>
 *   <li>旧版（1.1.8）主入口：{@code doZoomLogic()}，返回 {@code void}</li>
 *   <li>新版（1.2.0）主入口：{@code onZoomPress()}，返回 {@code boolean}</li>
 * </ul>
 * 手柄入口 {@code onZoomControllerPress(boolean)} 签名不变，但内部改为调用
 * {@code onZoomPress()}，因此只拦截 {@code onZoomPress} 即可同时覆盖
 * 键盘与手柄两条路径。
 *
 * <p>只拦截 {@code onZoomPress}、不单独拦截 {@code onZoomControllerPress} 的原因：
 * 若两者都拦截，手柄按下时 {@code btp$handleZoom()} 会执行两次
 * （一次来自手柄拦截、一次来自 onZoomPress 拦截），导致倍率提示重复显示、
 * {@code tryHandleZoomAsTilt()} 重复切换等副作用。
 *
 * <p>三个功能：
 * <ol>
 *   <li><b>据枪档</b>：让"据枪"作为虚拟的最后一档倍率（仅 ZOOM_STAGE 模式）</li>
 *   <li><b>倍率提示</b>：ZOOM_STAGE 模式下按 V 切换倍率时显示新倍率（如 "2x"）</li>
 *   <li><b>不开镜切倍率</b>：允许玩家在非瞄准状态下按 V 切换倍率。
 *       注：TaCZ 1.2.0 已移除该能力，BTP 此功能等同于"补回官方移除的特性"</li>
 * </ol>
 *
 * <p><b>aim→zoom→aim 竞态假设</b>：BTP 在非瞄准状态下代理发 zoom 包时，
 * 采用 {@code aim(true)} → 发包 → {@code aim(false)} 的同步序列。依赖：
 * <ol>
 *   <li>TaCZ 服务端按 Forge SimpleChannel 的包顺序处理（TCP 有序）</li>
 *   <li>客户端 aim 进度在两帧之间的增量可忽略（视觉无抬枪闪烁）</li>
 * </ol>
 * 若未来 TaCZ 改为异步网络处理或客户端 aim 动画变慢，需要改为延迟发包。
 */
@Mixin(value = ZoomKey.class, remap = false)
public class ZoomKeyMixin {

    @Inject(method = "onZoomPress", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void btp$interceptZoomKey(CallbackInfoReturnable<Boolean> cir) {
        if (btp$handleZoom()) {
            cir.setReturnValue(true);
        }
    }

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
     *
     * <p><b>竞态假设</b>：见类 Javadoc 的"aim→zoom→aim 竞态假设"一节。
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
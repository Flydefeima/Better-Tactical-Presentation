package com.feima.btp.mixin;

import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接管 TaCZ 的蹲姿斜握（shouldSlide）。
 *
 * <p>原版 {@code shouldSlide()} = {@code crouching && gunData.canSlide()}。
 * BTP 在 {@code disableVanillaCrouchTilt} 开启时用据枪状态替代蹲下条件，
 * 并根据 {@link BTPConfig#forceTiltOnAllGuns} 决定是否忽略 {@code can_slide}。
 *
 * <p><b>语义矩阵</b>：
 * <ul>
 *   <li>{@code disableVanillaCrouchTilt=true} + {@code forceTiltOnAllGuns=true}：
 *       据枪时强制斜握（忽略 can_slide），蹲下未据枪不斜握</li>
 *   <li>{@code disableVanillaCrouchTilt=true} + {@code forceTiltOnAllGuns=false}：
 *       据枪时尊重 can_slide，蹲下未据枪不斜握</li>
 *   <li>{@code disableVanillaCrouchTilt=false} + {@code forceTiltOnAllGuns=true}：
 *       蹲下未据枪尊重 can_slide，据枪时强制斜握</li>
 *   <li>{@code disableVanillaCrouchTilt=false} + {@code forceTiltOnAllGuns=false}：
 *       完全原版行为，尊重 can_slide</li>
 * </ul>
 *
 * <p><b>TaCZ 1.2.0 变更</b>：官方将狙击枪的 {@code can_slide} 改为 false。
 * {@code forceTiltOnAllGuns} 默认 true，保证 BTP 的据枪体验不受此字段影响。
 *
 * <p><b>字段契约</b>：{@code GunAnimationStateContext.gunData} 在 TaCZ 1.2.0
 * 中是<em>非 final</em> 的（{@code setCurrentGunItem} 会重新赋值）。
 * <b>不要加 {@code @Shadow @Final}</b>——那会让 Mixin 抛
 * {@code InvalidMixinException}。字段名依赖 TaCZ 1.2.0，升级时需复核。
 */
@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunStateMixin {

    @Shadow
    private GunData gunData;

    @Inject(
            method = "shouldSlide",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void btp$shouldSlide(CallbackInfoReturnable<Boolean> cir) {
        boolean tilting = TiltController.isTilting();
        boolean effectiveCanSlide = BTPConfig.forceTiltOnAllGuns
                || (gunData != null && gunData.canSlide());

        if (BTPConfig.disableVanillaCrouchTilt) {
            cir.setReturnValue(tilting && effectiveCanSlide);
        } else if (!cir.getReturnValue() && tilting && effectiveCanSlide) {
            cir.setReturnValue(true);
        }
    }
}
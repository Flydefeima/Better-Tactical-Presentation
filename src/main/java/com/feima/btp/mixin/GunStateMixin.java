package com.feima.btp.mixin;

import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunStateMixin {

    @Inject(
            method = "shouldSlide",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void btp$shouldSlide(CallbackInfoReturnable<Boolean> cir) {
        boolean tilting = TiltController.isTilting();
        if (BTPConfig.disableVanillaCrouchTilt) {
            // BTP 接管：非据枪时不允许原版蹲下倾斜
            cir.setReturnValue(tilting);
        } else if (!cir.getReturnValue() && tilting) {
            // 保留原版行为，但据枪时强制开启
            cir.setReturnValue(true);
        }
    }
}
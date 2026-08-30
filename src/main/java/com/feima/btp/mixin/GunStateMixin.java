package com.feima.btp.mixin;

import com.feima.btp.client.TiltToggleHandler;
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
        if (BTPConfig.disableVanillaCrouchTilt && cir.getReturnValue()) {
            if (TiltToggleHandler.isTilting()) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            return;
        }

        if (cir.getReturnValue()) {
            return;
        }

        if (TiltToggleHandler.isTilting()) {
            cir.setReturnValue(true);
        }
    }
}
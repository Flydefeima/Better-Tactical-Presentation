package com.feima.btp.mixin;

import com.feima.btp.client.LeanToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    @Inject(
            method = "shouldSlide",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void btp$shouldSlide(CallbackInfoReturnable<Boolean> cir) {
        if (BTPConfig.disableVanillaCrouchLean && cir.getReturnValue()) {
            if (LeanToggleHandler.isLeaning()) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            return;
        }

        if (cir.getReturnValue()) {
            return;
        }

        if (LeanToggleHandler.isLeaning()) {
            cir.setReturnValue(true);
        }
    }
}
package com.feima.btp.mixin;

import com.feima.btp.client.LeanToggleHandler;
import com.feima.btp.config.BTPConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class SprintBlockMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void btp$blockSprintWhileLeaning(boolean sprinting, CallbackInfo ci) {
        if (sprinting && BTPConfig.breakSprint && LeanToggleHandler.isLeaning()) {
            ci.cancel();
        }
    }
}

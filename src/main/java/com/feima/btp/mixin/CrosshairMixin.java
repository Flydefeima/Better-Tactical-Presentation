package com.feima.btp.mixin;

import com.feima.btp.client.TiltToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderCrosshairEvent.class, remap = false)
public class CrosshairMixin {

    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/renderer/crosshair/CrosshairType;getTextureLocation(Lcom/tacz/guns/client/renderer/crosshair/CrosshairType;)Lnet/minecraft/resources/ResourceLocation;"
            ),
            remap = false
    )
    private static ResourceLocation redirectCrosshairLocation(CrosshairType originalType) {
        if (!TiltToggleHandler.isTilting()) {
            return CrosshairType.getTextureLocation(originalType);
        }

        CrosshairType overrideType = BTPConfig.getTacticalCrosshairType();
        if (overrideType != null) {
            return CrosshairType.getTextureLocation(overrideType);
        }
        return CrosshairType.getTextureLocation(originalType);
    }
}
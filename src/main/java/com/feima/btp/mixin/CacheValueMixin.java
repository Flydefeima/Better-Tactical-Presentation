package com.feima.btp.mixin;

import com.feima.btp.client.LeanToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = CacheValue.class, remap = false)
public class CacheValueMixin {

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void btp$modifySpreadOnRead(CallbackInfoReturnable<Object> cir) {
        if (Math.abs(BTPConfig.leanSpreadMultiplier - 1.0) < 0.0001) return;
        if (!LeanToggleHandler.isLeaning()) return;

        Object value = cir.getReturnValue();
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) return;

        if (!(map.keySet().iterator().next() instanceof InaccuracyType)) return;

        @SuppressWarnings("unchecked")
        Map<InaccuracyType, Float> source = (Map<InaccuracyType, Float>) map;
        Map<InaccuracyType, Float> scaled = new HashMap<>(source.size());
        float multiplier = (float) BTPConfig.leanSpreadMultiplier;
        for (Map.Entry<InaccuracyType, Float> e : source.entrySet()) {
            scaled.put(e.getKey(), e.getValue() * multiplier);
        }

        cir.setReturnValue(scaled);
    }
}
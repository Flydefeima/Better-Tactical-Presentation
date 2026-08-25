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

/**
 * Intercepts CacheValue.getValue() — the universal read point for all TACZ cached
 * properties. When BTP lean is active and the value is an inaccuracy map,
 * returns a multiplied clone without mutating the original cache.
 */
@Mixin(value = CacheValue.class, remap = false)
public class CacheValueMixin {

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void btp$modifySpreadOnRead(CallbackInfoReturnable<Object> cir) {
        // Fast-path: skip entirely when multiplier is neutral or player isn't leaning
        if (Math.abs(BTPConfig.leanSpreadMultiplier - 1.0) < 0.0001) return;
        if (!LeanToggleHandler.isLeaning()) return;

        Object value = cir.getReturnValue();
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) return;

        // Identify the inaccuracy cache by probing the first key's type
        if (!(map.keySet().iterator().next() instanceof InaccuracyType)) return;

        // Clone with multiplier applied — original cache stays intact
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

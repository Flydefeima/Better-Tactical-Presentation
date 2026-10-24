package com.feima.btp.mixin;

import com.feima.btp.client.tilt.TiltController;
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
 * properties. When BTP tilt is active and the value is an inaccuracy map,
 * returns a multiplied clone without mutating the original cache.
 *
 * <p><b>隐式契约</b>：下面的探测依赖「TaCZ 当前只有 inaccuracy 缓存使用
 * {@code Map<InaccuracyType, ?>} 这个 key 类型」。若将来 TaCZ 新增了别的
 * 以 InaccuracyType 为 key 的缓存类型，本 mixin 会误伤它。TaCZ 版本升级时
 * 需要复核 {@code CacheValue} 的所有注册点。
 */
@Mixin(value = CacheValue.class, remap = false)
public class CacheValueMixin {

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void btp$modifySpreadOnRead(CallbackInfoReturnable<Object> cir) {
        if (Math.abs(BTPConfig.tiltSpreadMultiplier - 1.0) < 0.0001) return;
        if (!TiltController.isTilting()) return;

        Object value = cir.getReturnValue();
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) return;

        if (!(map.keySet().iterator().next() instanceof InaccuracyType)) return;

        @SuppressWarnings("unchecked")
        Map<InaccuracyType, Float> source = (Map<InaccuracyType, Float>) map;
        Map<InaccuracyType, Float> scaled = new HashMap<>(source.size());
        float multiplier = (float) BTPConfig.tiltSpreadMultiplier;
        for (Map.Entry<InaccuracyType, Float> e : source.entrySet()) {
            scaled.put(e.getKey(), e.getValue() * multiplier);
        }

        cir.setReturnValue(scaled);
    }
}
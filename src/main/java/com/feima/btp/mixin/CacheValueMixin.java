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
 * <p><b>类型判别</b>：使用全量 {@code keySet()} 校验，而非只采样第一个 key。
 * 这样即使 TaCZ 未来新增了以 {@link InaccuracyType} 为 key 但与散布无关的
 * 缓存类型，也不会被误伤（前提是该 Map 不是纯 InaccuracyType → Float 结构）。
 *
 * <p>仍存在的隐式契约：TaCZ 当前只有 inaccuracy 缓存使用
 * {@code Map<InaccuracyType, Float>}。若将来新增同类结构，需要进一步
 * 通过 CacheValue 的注册点区分。
 */
@Mixin(value = CacheValue.class, remap = false)
public class CacheValueMixin {

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void btp$modifySpreadOnRead(CallbackInfoReturnable<Object> cir) {
        if (Math.abs(BTPConfig.tiltSpreadMultiplier - 1.0) < 0.0001) return;
        if (!TiltController.isTilting()) return;

        Object value = cir.getReturnValue();
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) return;

        // 全量校验：所有 key 都是 InaccuracyType 才认为是散布表。
        for (Object key : map.keySet()) {
            if (!(key instanceof InaccuracyType)) return;
        }

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
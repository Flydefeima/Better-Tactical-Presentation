package com.feima.btp.mixin.compat;

import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/**
 * 通过 {@code @Invoker} 暴露 TaCZ 包私有的 {@code getAnimations(ResourceLocation, String)}。
 * 这是 Mixin 的标准 accessor 模式，不是反射。
 */
@Mixin(value = PlayerAnimatorAssetManager.class, remap = false)
public interface PlayerAnimatorAssetAccessorMixin {

    @Invoker("getAnimations")
    Optional<KeyframeAnimation> btp$getAnimations(ResourceLocation id, String name);
}
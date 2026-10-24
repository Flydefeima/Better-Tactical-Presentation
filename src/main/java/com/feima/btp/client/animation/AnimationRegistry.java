package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.mixin.compat.PlayerAnimatorAssetAccessorMixin;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 通过 Mixin 暴露的 {@code @Invoker} 访问 TaCZ 的动画资源表，
 * 替代原先的反射方案。
 *
 * <p>关键约定：每次播放都新建 {@link KeyframeAnimationPlayer} 实例。
 * TaCZ 在资源包重载时会整体替换 {@code PlayerAnimatorAssetManager.animations}，
 * 缓存旧的 player 会指向已失效的动画数据。
 */
public final class AnimationRegistry {
    private AnimationRegistry() {}

    /** 返回 null 表示查询失败，调用方需要处理。 */
    public static KeyframeAnimationPlayer player(ResourceLocation fileId, String name) {
        PlayerAnimatorAssetManager manager = PlayerAnimatorAssetManager.get();
        if (!(manager instanceof PlayerAnimatorAssetAccessorMixin accessor)) {
            BTPLog.LOGGER.warn("PlayerAnimator accessor mixin not applied");
            return null;
        }
        if (!manager.containsKey(fileId)) {
            BTPLog.LOGGER.warn("Animation file '{}' not loaded", fileId);
            return null;
        }
        Optional<KeyframeAnimation> anim = accessor.btp$getAnimations(fileId, name);
        if (anim.isEmpty()) {
            BTPLog.LOGGER.warn("Animation '{}' not found in '{}'", name, fileId);
            return null;
        }
        return new KeyframeAnimationPlayer(anim.get());
    }
}
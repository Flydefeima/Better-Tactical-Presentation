package com.feima.btp.client.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;

/**
 * 恒等动画：永远 active，但不修改任何骨骼变换（原样返回 value0）。
 *
 * <p>作用：作为 BTP 动画层的常驻状态，让 {@code ModifierLayer.replaceAnimationWithFade}
 * 永远有可 fade 的旧动画。因为它的输出等于 value0（低优先级层，即 TaCZ 的输出），
 * 所以挂在 BTP 层时相当于「透明」——TaCZ 层的姿态原样透出。
 *
 * <p>这样进入倾斜时是从 TaCZ 姿态 fade 到倾斜姿态，退出时从倾斜姿态 fade 回 TaCZ 姿态，
 * 全程不需要任何占位 JSON 文件。
 */
public final class IdentityAnimation implements IAnimation {
    public static final IdentityAnimation INSTANCE = new IdentityAnimation();

    private IdentityAnimation() {}

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void tick() {}

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        return value0;
    }
}
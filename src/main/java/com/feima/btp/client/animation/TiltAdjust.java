package com.feima.btp.client.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class TiltAdjust extends AdjustmentModifier {
    private static final float GIMBAL_EPSILON = 1.0e-5f;

    // 模式标志：true=复杂修正（据枪），false=简单修正（占位/普通）
    private static boolean useComplexCorrection = false;

    public static void setUseComplexCorrection(boolean use) {
        useComplexCorrection = use;
    }

    private final AbstractClientPlayer player;

    private TiltAdjust(AbstractClientPlayer player) {
        super(partName -> Optional.empty());
        this.player = player;
    }

    public static AdjustmentModifier getModifier(AbstractClientPlayer player) {
        return new TiltAdjust(player);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String partName,
                                         @NotNull TransformType type,
                                         float tickDelta,
                                         @NotNull Vec3f value0) {
        Vec3f animated = super.get3DTransform(partName, type, tickDelta, value0);
        if (type != TransformType.ROTATION || !isActive() || shouldPauseViewAdjustment()) {
            return animated;
        }

        float fade = 1.0f;
        float partialTick = Minecraft.getInstance().getPartialTick();
        float pitch = Mth.wrapDegrees(Mth.lerp(partialTick, player.xRotO, player.getXRot()));
        float pitchRadians = pitch * Mth.DEG_TO_RAD * fade;

        // ---- 手臂修正 ----
        if ("rightArm".equals(partName)) {
            // 右手：始终使用复杂矩阵分解（无论据枪或占位）
            return composeParentPitch(animated, pitchRadians);
        }

        if ("leftArm".equals(partName)) {
            if (useComplexCorrection) {
                // 据枪模式 → 复杂
                return composeParentPitch(animated, pitchRadians);
            } else {
                // 占位/普通模式 → 简单附加 pitch
                return animated.add(new Vec3f(pitchRadians, 0, 0));
            }
        }

        // ---- 头部：始终简单附加（额外处理爬行 yaw） ----
        if ("head".equals(partName)) {
            if (isVisuallyCrawling()) {
                return applyCrawlingHeadYaw(animated, partialTick, fade);
            }
            return animated.add(new Vec3f(pitchRadians, 0, 0));
        }

        return animated;
    }

    private boolean shouldPauseViewAdjustment() {
        Minecraft minecraft = Minecraft.getInstance();
        return player.equals(minecraft.player) && minecraft.screen != null;
    }

    private boolean isVisuallyCrawling() {
        return !player.isSwimming() && player.getPose() == Pose.SWIMMING;
    }

    private Vec3f applyCrawlingHeadYaw(Vec3f animated, float partialTick, float fade) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float relativeYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0f, 85.0f);
        return animated.add(new Vec3f(0.0f, 0.0f, -relativeYaw * Mth.DEG_TO_RAD * fade));
    }

    /**
     * 复杂矩阵分解：Rx(pitch) * Rz(roll) * Ry(yaw) * Rx(armPitch) → Z-Y-X Euler
     */
    static Vec3f composeParentPitch(Vec3f rotation, float pitch) {
        float armPitch = rotation.getX();
        float yaw = rotation.getY();
        float roll = rotation.getZ();

        float sinX = Mth.sin(armPitch);
        float cosX = Mth.cos(armPitch);
        float sinY = Mth.sin(yaw);
        float cosY = Mth.cos(yaw);
        float sinZ = Mth.sin(roll);
        float cosZ = Mth.cos(roll);
        float sinPitch = Mth.sin(pitch);
        float cosPitch = Mth.cos(pitch);

        float r00 = cosZ * cosY;
        float r01 = cosZ * sinY * sinX - sinZ * cosX;
        float r10 = cosPitch * sinZ * cosY + sinPitch * sinY;
        float r11 = cosPitch * (sinZ * sinY * sinX + cosZ * cosX)
                - sinPitch * cosY * sinX;
        float r20 = sinPitch * sinZ * cosY - cosPitch * sinY;
        float r21 = sinPitch * (sinZ * sinY * sinX + cosZ * cosX)
                + cosPitch * cosY * sinX;
        float r22 = sinPitch * (sinZ * sinY * cosX - cosZ * sinX)
                + cosPitch * cosY * cosX;

        float composedYaw = (float) Math.asin(Mth.clamp(-r20, -1.0f, 1.0f));
        float horizontalScale = (float) Math.sqrt(Math.max(0.0f, 1.0f - r20 * r20));

        float composedArmPitch;
        float composedRoll;
        if (horizontalScale > GIMBAL_EPSILON) {
            composedArmPitch = (float) Math.atan2(r21, r22);
            composedRoll = (float) Math.atan2(r10, r00);
        } else {
            composedArmPitch = 0.0f;
            composedRoll = (float) Math.atan2(-r01, r11);
        }

        return new Vec3f(composedArmPitch, composedYaw, composedRoll);
    }
}
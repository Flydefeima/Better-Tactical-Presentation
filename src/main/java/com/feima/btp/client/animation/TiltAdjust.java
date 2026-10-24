package com.feima.btp.client.animation;

import com.feima.btp.client.TiltStateManager;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class TiltAdjust extends AdjustmentModifier {
    private static final float GIMBAL_EPSILON = 1.0e-5f;

    // ===== 本地玩家的全局状态：只有本地玩家使用 =====
    private static boolean applying = false;
    private static boolean useComplexCorrection = false;

    private static float weight = 0.0f;
    private static float weightStart = 0.0f;
    private static float weightTarget = 0.0f;
    private static long fadeStartMs = 0L;
    private static long fadeDurationMs = 1L;
    private static boolean fading = false;

    public static void setApplying(boolean v) {
        applying = v;
        if (!v) {
            fading = false;
            weight = 0.0f;
            weightStart = 0.0f;
            weightTarget = 0.0f;
        }
    }

    public static boolean isApplying() {
        return applying;
    }

    public static void setUseComplexCorrection(boolean v) {
        useComplexCorrection = v;
    }

    public static void startFadeIn(long durationMs) {
        weightStart = weight;
        weightTarget = 1.0f;
        fadeStartMs = System.currentTimeMillis();
        float delta = Math.abs(weightTarget - weightStart);
        fadeDurationMs = Math.max(1L, (long) (durationMs * delta));
        fading = true;
    }

    public static void startFadeOut(long durationMs) {
        weightStart = weight;
        weightTarget = 0.0f;
        fadeStartMs = System.currentTimeMillis();
        float delta = Math.abs(weightTarget - weightStart);
        fadeDurationMs = Math.max(1L, (long) (durationMs * delta));
        fading = true;
    }

    private static float currentWeight() {
        if (fading) {
            long elapsed = System.currentTimeMillis() - fadeStartMs;
            if (elapsed >= fadeDurationMs) {
                weight = weightTarget;
                fading = false;
            } else {
                float t = (float) elapsed / fadeDurationMs;
                weight = weightStart + (weightTarget - weightStart) * t;
            }
        }
        return weight;
    }

    // ===== 实例状态 =====

    private final AbstractClientPlayer player;
    private final boolean isLocal;

    private TiltAdjust(AbstractClientPlayer player) {
        super(partName -> Optional.empty());
        this.player = player;
        // 用 instanceof 而非 Minecraft.getInstance().player：
        // registerFactory 在玩家实例创建时调用，此刻 mc.player 可能尚未赋值
        this.isLocal = player instanceof LocalPlayer;
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

        float w;
        if (isLocal) {
            // 本地玩家：沿用原有的 fade 状态机
            if (!applying) return animated;
            w = currentWeight();
        } else {
            // 远程玩家：无 fade 状态，直接用同步状态驱动，权重恒为 1
            if (!TiltStateManager.isTilting(player)) return animated;
            w = 1.0f;
        }

        if (w <= 0.0f) return animated;

        float partialTick = Minecraft.getInstance().getPartialTick();
        float pitch = Mth.wrapDegrees(Mth.lerp(partialTick, player.xRotO, player.getXRot()));
        float pitchRadians = pitch * Mth.DEG_TO_RAD * w;

        if ("rightArm".equals(partName)) {
            return composeParentPitch(animated, pitchRadians);
        }

        if ("leftArm".equals(partName)) {
            // 远程玩家没有「据枪/普通」区分——只有同步过来才知道是不是倾斜
            // 统一用复杂修正更稳（姿态与本地一致）
            if (useComplexCorrection || !isLocal) {
                return composeParentPitch(animated, pitchRadians);
            }
            return animated.add(new Vec3f(pitchRadians, 0, 0));
        }

        if ("head".equals(partName)) {
            if (isVisuallyCrawling()) {
                return applyCrawlingHeadYaw(animated, partialTick, w);
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

    private Vec3f applyCrawlingHeadYaw(Vec3f animated, float partialTick, float w) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float relativeYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0f, 85.0f);
        return animated.add(new Vec3f(0.0f, 0.0f, -relativeYaw * Mth.DEG_TO_RAD * w));
    }

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
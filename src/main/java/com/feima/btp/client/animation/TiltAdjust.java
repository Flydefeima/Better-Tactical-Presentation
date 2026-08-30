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

/**
 * Keeps the tactical pose aligned with the player's view without destroying the
 * authored arm orientation.
 *
 * <p>A normal {@link AdjustmentModifier} adds pitch directly to the arm's X
 * Euler angle. That only works for arms with no yaw or roll. The tactical pose
 * has a large roll, so direct addition rotates around an already tilted local
 * axis and makes the arms swing behind the player at extreme view pitches.</p>
 *
 * <p>This modifier pre-multiplies the animated orientation by view pitch. In
 * other words, the complete authored arm pose is rotated around the player's
 * horizontal axis, then converted back to the Z-Y-X Euler angles used by
 * {@code ModelPart}.</p>
 */
public class TiltAdjust extends AdjustmentModifier {
    private static final float GIMBAL_EPSILON = 1.0e-5f;

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
        // The modifier is registered for the whole player lifetime. Do not
        // apply a view rotation while the tactical animation layer is idle.
        if (type != TransformType.ROTATION || !isActive() || shouldPauseViewAdjustment()) {
            return animated;
        }

        // ========== 强制 fade = 1，彻底移除淡入淡出影响 ==========
        float fade = 1.0f; // 始终全强度应用

        float partialTick = Minecraft.getInstance().getPartialTick();
        float pitch = Mth.wrapDegrees(Mth.lerp(partialTick, player.xRotO, player.getXRot()));
        float pitchRadians = pitch * Mth.DEG_TO_RAD * fade;

        return switch (partName) {
            case "rightArm", "leftArm", "head" -> {
                if (partName.equals("head") && isVisuallyCrawling()) {
                    yield applyCrawlingHeadYaw(animated, partialTick, fade);
                }
                yield composeParentPitch(animated, pitchRadians);
            }
            default -> animated;
        };
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
     * Computes {@code Rx(pitch) * Rz(roll) * Ry(yaw) * Rx(armPitch)} and
     * decomposes the result back into the ModelPart Z-Y-X Euler convention.
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

        // Only the matrix entries needed for Z-Y-X decomposition are built.
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
            // At the singularity only one combined X/Z angle is observable.
            composedArmPitch = 0.0f;
            composedRoll = (float) Math.atan2(-r01, r11);
        }

        return new Vec3f(composedArmPitch, composedYaw, composedRoll);
    }
}
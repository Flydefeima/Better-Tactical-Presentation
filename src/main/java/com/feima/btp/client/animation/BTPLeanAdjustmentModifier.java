package com.feima.btp.client.animation;

import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

import java.util.Optional;
import java.util.function.Function;

/**
 * BTP 专属视角修正器
 * 与 TaCZ 的 AdjustmentYRotModifier 不同，本修正器只对特定骨骼应用修正，
 * 避免与 BTP 动画中已有的倾斜旋转（torso 的 44° Y 轴旋转）发生冲突。
 *
 * 修正策略：
 * - body：不修正，保留动画原始倾斜
 * - head：完全跟随视角俯仰
 * - rightArm：完全跟随视角俯仰（与 TaCZ 原生行为一致）
 * - leftArm：完全跟随视角俯仰（与 TaCZ 原生行为一致）
 */
public class BTPLeanAdjustmentModifier {

    public static AdjustmentModifier getModifier(AbstractClientPlayer player) {
        return new AdjustmentModifier(new BTPLeanFunction(player));
    }

    private record BTPLeanFunction(AbstractClientPlayer player)
            implements Function<String, Optional<AdjustmentModifier.PartModifier>> {

        @Override
        public Optional<AdjustmentModifier.PartModifier> apply(String partName) {
            Minecraft mc = Minecraft.getInstance();
            if (player.equals(mc.player) && mc.screen != null) {
                return Optional.empty();
            }

            float partialTick = mc.getPartialTick();

            // 计算插值后的旋转角度
            float yBodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            float yHeadRot = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
            float xRot = Mth.lerp(partialTick, player.xRotO, player.getXRot());

            // 头部相对身体的偏航角（左右转头）
            float yaw = yHeadRot - yBodyRot;
            yaw = Mth.wrapDegrees(yaw);
            yaw = Mth.clamp(yaw, -85f, 85f);

            // 俯仰角（上下看）
            float pitch = Mth.wrapDegrees(xRot);

            // 玩家游泳/趴下时的特殊处理
            boolean isSwimmingLike = !player.isSwimming() && player.getPose() == Pose.SWIMMING;

            return switch (partName) {
                // ⭐ body（对应 TaCZ 的 torso）：不应用任何修正，完全保留动画原始值
                // 这样动画中 44° 的倾斜得以保留，不会被视角旋转覆盖
                case "body" -> Optional.empty();

                // head：完全跟随视角俯仰
                case "head" -> {
                    if (isSwimmingLike) {
                        // 趴下时头部跟随偏航
                        yield Optional.of(new AdjustmentModifier.PartModifier(
                                new Vec3f(0, 0, -yaw * Mth.DEG_TO_RAD), Vec3f.ZERO
                        ));
                    }
                    yield Optional.of(new AdjustmentModifier.PartModifier(
                            new Vec3f(pitch * Mth.DEG_TO_RAD, 0, 0), Vec3f.ZERO
                    ));
                }

                // rightArm：完全跟随视角俯仰（与 TaCZ 原生行为一致）
                case "rightArm" -> Optional.of(new AdjustmentModifier.PartModifier(
                        new Vec3f(pitch * Mth.DEG_TO_RAD, 0, 0), Vec3f.ZERO
                ));

                // leftArm：完全跟随视角俯仰（与 TaCZ 原生行为一致）
                case "leftArm" -> Optional.of(new AdjustmentModifier.PartModifier(
                        new Vec3f(pitch * Mth.DEG_TO_RAD, 0, 0), Vec3f.ZERO
                ));

                // 其他骨骼（如腿、脚等）不修正
                default -> Optional.empty();
            };
        }
    }
}
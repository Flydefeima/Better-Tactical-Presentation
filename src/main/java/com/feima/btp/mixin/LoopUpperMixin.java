package com.feima.btp.mixin;

import com.feima.btp.BTPMod;
import com.feima.btp.client.TiltToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnimationManager.class, remap = false)
public class LoopUpperMixin {

    private static final int TRANSITION_FRAMES_COUNT = 8;
    private static boolean lastFrameProne = false;
    private static int transitionFrames = 0;

    @Inject(
            method = "playLoopUpperAnimation",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void btp$interceptLoopUpperAnimation(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount, CallbackInfo ci) {
        if (!BTPMod.isPlayerAnimatorLoaded) return;
        if (player != Minecraft.getInstance().player) return;
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;

        // ===== 如果按住据枪键并开镜，则不拦截（让 TaCZ 开镜动画播放） =====
        if (TiltToggleHandler.isHoldingTiltAndAiming()) {
            return;
        }

        LocalPlayer localPlayer = (LocalPlayer) player;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);

        // ===== 修改：使用 Pose.SWIMMING 检测趴下 =====
        boolean isProne = localPlayer.getPose() == Pose.SWIMMING && !localPlayer.isSwimming();

        // 检测趴下→站立切换
        if (lastFrameProne && !isProne) {
            transitionFrames = TRANSITION_FRAMES_COUNT;
        }
        lastFrameProne = isProne;

        // 过渡窗口内不拦截
        if (transitionFrames > 0) {
            transitionFrames--;
            return;
        }

        // 趴下时完全不拦截
        if (isProne) {
            return;
        }

        // 疾跑时不拦截（breakSprint = false 时）
        if (!BTPConfig.breakSprint && localPlayer.isSprinting()) {
            return;
        }

        // 站立据枪时 BTP 接管
        if (TiltToggleHandler.isTilting()) {
            ci.cancel();
        }
    }
}
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnimationManager.class, remap = false)
public class LoopUpperMixin {

    private static final int TRANSITION_FRAMES_COUNT = 8;
    private static boolean lastFrameCrawling = false;
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

        LocalPlayer localPlayer = (LocalPlayer) player;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
        boolean isCrawling = operator.isCrawl();

        // 检测趴下→站立切换
        if (lastFrameCrawling && !isCrawling) {
            transitionFrames = TRANSITION_FRAMES_COUNT;
        }
        lastFrameCrawling = isCrawling;

        // 过渡窗口内不拦截
        if (transitionFrames > 0) {
            transitionFrames--;
            return;
        }

        // 趴下时完全不拦截
        if (isCrawling) {
            return;
        }

        // ===== 新增：疾跑时不拦截（breakSprint = false 时） =====
        // 当 breakSprint 为 false 且玩家正在疾跑时，BTP 不应接管动画
        if (!BTPConfig.breakSprint && localPlayer.isSprinting()) {
            return;
        }

        // 站立据枪时 BTP 接管
        if (TiltToggleHandler.isTilting()) {
            ci.cancel();
        }
    }
}
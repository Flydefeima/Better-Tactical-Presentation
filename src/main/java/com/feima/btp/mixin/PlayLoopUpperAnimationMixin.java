package com.feima.btp.mixin;

import com.feima.btp.BTPMod;
import com.feima.btp.client.LeanToggleHandler;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 TaCZ 的上半身循环动画调用。
 * 仅当本地玩家据枪时才取消 TaCZ 的动画播放，由独立动画处理器接管。
 * 对其他玩家的动画不做任何干预。
 */
@Mixin(value = AnimationManager.class, remap = false)
public class PlayLoopUpperAnimationMixin {

    @Inject(
            method = "playLoopUpperAnimation",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void btp$interceptLoopUpperAnimation(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount, CallbackInfo ci) {
        // 仅处理本地玩家，且 PlayerAnimator 必须加载
        if (!BTPMod.isPlayerAnimatorLoaded) return;
        if (player != Minecraft.getInstance().player) return;

        if (LeanToggleHandler.isLeaning()) {
            ci.cancel();
        }
    }
}
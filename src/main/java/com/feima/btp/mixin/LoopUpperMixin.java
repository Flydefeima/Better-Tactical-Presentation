package com.feima.btp.mixin;

import com.feima.btp.BTPMod;
import com.feima.btp.client.animation.BTPAnimator;
import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 据枪 + 开火时屏蔽 TaCZ 的上半身 loop 动画。
 *
 * <p>TaCZ 的 {@code playLoopUpperAnimation} 会每 tick 往 {@code loop_upper} 层
 * 塞持枪/移动/瞄准姿态，与 BTP 的倾斜动画在同一层（不同 layer，但骨骼覆盖
 * 权重在开火瞬间会冲突），导致开火时视觉抽搐。
 *
 * <p>仅在以下三个条件同时成立时 cancel：
 * <ul>
 *   <li>玩家处于战术据枪状态（{@link TiltController#isTilting()}）</li>
 *   <li>BTP 的倾斜动画已经进入 TILTING 状态</li>
 *   <li>BTP 的开火反馈动画正在播放</li>
 * </ul>
 *
 * <p>不拦截 once 层——BTP 的倾斜 fire 动画与 TaCZ 的 once fire 动画分层共存，
 * 由优先级决定最终输出，无需干预。
 */
@Mixin(value = AnimationManager.class, remap = false)
public class LoopUpperMixin {

    @Inject(
            method = "playLoopUpperAnimation",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void btp$interceptLoopOnTiltFire(AbstractClientPlayer player,
                                                     GunDisplayInstance display,
                                                     float limbSwingAmount,
                                                     CallbackInfo ci) {
        if (!BTPMod.isPlayerAnimatorLoaded) return;
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;
        if (player != Minecraft.getInstance().player) return;

        if (TiltController.isTilting()
                && BTPAnimator.isTiltAnimationActive()
                && BTPAnimator.isFireAnimationActive()) {
            ci.cancel();
        }
    }
}
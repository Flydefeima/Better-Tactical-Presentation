package com.feima.btp.client.animation;

import com.feima.btp.config.BTPConfig;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public final class TiltLayerController {
    private TiltLayerController() {}

    public static ModifierLayer<IAnimation> getLayer(AbstractClientPlayer player, ResourceLocation layerId) {
        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) associatedData.get(layerId);
        return layer;
    }

    public static void enter(AbstractClientPlayer player,
                             ModifierLayer<IAnimation> layer,
                             TiltAnimState state) {
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;
        if (state.isFiring()) return;
        if (state.isTilting()) return;

        if (state.isExiting()) {
            state.reset();
        }

        if (layer.getAnimation() == null || !layer.getAnimation().isActive()) {
            layer.setAnimation(IdentityAnimation.INSTANCE);
        }

        TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
        KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(kind.fileId(), kind.loop());
        if (loopPlayer == null) return;

        TiltAdjust.setApplying(true);
        TiltAdjust.setUseComplexCorrection(true);
        TiltAdjust.startFadeIn(BTPConfig.tiltTransitionTimeMs);

        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(fadeTicks(), Ease.INOUTSINE);
        layer.replaceAnimationWithFade(fade, loopPlayer);
        state.markTilting();
    }

    public static void exitOrIdle(AbstractClientPlayer player,
                                  ModifierLayer<IAnimation> layer,
                                  TiltAnimState state) {
        if (state.isFiring()) return;

        if (state.isTilting()) {
            TiltAdjust.startFadeOut(BTPConfig.tiltTransitionTimeMs);
            AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(fadeTicks(), Ease.INOUTSINE);
            layer.replaceAnimationWithFade(fade, IdentityAnimation.INSTANCE);
            state.beginExit();
            return;
        }

        if (state.isExiting()) {
            if (state.exitElapsed(BTPConfig.tiltTransitionTimeMs)) {
                TiltAdjust.setApplying(false);
                TiltAdjust.setUseComplexCorrection(false);
                layer.setAnimation(IdentityAnimation.INSTANCE);
                state.reset();
            }
            return;
        }

        if (layer.getAnimation() != IdentityAnimation.INSTANCE) {
            TiltAdjust.setApplying(false);
            TiltAdjust.setUseComplexCorrection(false);
            layer.setAnimation(IdentityAnimation.INSTANCE);
        }
    }

    public static void clearLoop(ModifierLayer<IAnimation> layer) {
        TiltAdjust.setApplying(false);
        TiltAdjust.setUseComplexCorrection(false);
        layer.setAnimation(IdentityAnimation.INSTANCE);
    }

    /** 公开给 BTPAnimator 复用，用于远程玩家 fade。 */
    public static int fadeTicks() {
        int ms = BTPConfig.tiltTransitionTimeMs;
        return Math.max(0, (int) (ms / 1000f * 20f));
    }
}
package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.LeanToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.compat.playeranimator.animation.AdjustmentYRotModifier;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Optional;

//  注意：不再使用 @Mod.EventBusSubscriber 注解！
//     改为在 BTPMod 中动态注册
public class IndependentLeanAnimationHandler {

    // 使用 BTPMod 中定义的层 ID
    private static final ResourceLocation BTP_LOOP_LAYER = BTPMod.BTP_LOOP_LAYER;
    private static final ResourceLocation BTP_ONCE_LAYER = BTPMod.BTP_ONCE_LAYER;

    // 动画资源 ID
    private static final ResourceLocation LEAN_ANIMATION_ID = new ResourceLocation("btp", "tilted_gun");

    private static boolean lastFrameLeaning = false;
    private static boolean missingAnimationWarned = false;
    private static boolean lastFrameReloading = false;

    // 进入据枪时的过渡时长（秒）
    private static final float FADE_IN_DURATION = 0.15f;

    // 反射获取 getAnimations 方法（PlayerAnimatorAssetManager 的 private 方法）
    private static final Method GET_ANIMATIONS_METHOD;
    static {
        Method method = null;
        try {
            method = PlayerAnimatorAssetManager.class.getDeclaredMethod("getAnimations", ResourceLocation.class, String.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            BTPLog.LOGGER.warn("Failed to find getAnimations method, animation will not work.");
        }
        GET_ANIMATIONS_METHOD = method;
    }

    /**
     * 由 BTPMod 在 playeranimator 加载后调用，注册动画层
     * 直接使用 playeranimator API，无需反射
     */
    public static void registerAnimationLayers() {
        // 循环层：带修正器
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_LOOP_LAYER,
                97,
                player -> new ModifierLayer<>(null, AdjustmentYRotModifier.getModifier(player))
        );
        // 一次性层（开火用）：带修正器
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_ONCE_LAYER,
                98,
                player -> new ModifierLayer<>(null, AdjustmentYRotModifier.getModifier(player))
        );
        BTPLog.LOGGER.info("BTP animation layers registered successfully.");
    }

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private static Optional<KeyframeAnimation> getAnimationsSafely(PlayerAnimatorAssetManager manager, ResourceLocation id, String name) {
        if (GET_ANIMATIONS_METHOD == null) {
            return Optional.empty();
        }
        try {
            return (Optional<KeyframeAnimation>) GET_ANIMATIONS_METHOD.invoke(manager, id, name);
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to get animation {} from {}: {}", name, id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 创建淡入修正器
     */
    private static AbstractFadeModifier fadeIn() {
        return AbstractFadeModifier.standardFadeIn((int) (FADE_IN_DURATION * 20), Ease.INOUTSINE);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.options.getCameraType().isFirstPerson()) return;

        boolean isLeaning = LeanToggleHandler.isLeaning();
        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);

        @SuppressWarnings("unchecked")
        var layer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
        if (layer == null) {
            BTPLog.LOGGER.debug("BTP loop layer not found for player");
            return;
        }

        // 检查是否正在换弹
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        boolean isReloading = operator.getSynReloadState().getStateType().isReloading();

        if (isReloading) {
            if (layer.getAnimation() != null) {
                layer.setAnimation(null);
                BTPLog.LOGGER.debug("Cleared BTP lean animation during reload");
            }
            lastFrameLeaning = false;
            lastFrameReloading = true;
            return;
        }

        if (lastFrameReloading && isLeaning) {
            lastFrameReloading = false;
        } else {
            lastFrameReloading = false;
        }

        if (isLeaning) {
            playLeanAnimationWithFadeIn(layer);
            lastFrameLeaning = true;
        } else {
            if (lastFrameLeaning) {
                layer.setAnimation(null);
                BTPLog.LOGGER.debug("Cleared BTP lean loop animation");
                lastFrameLeaning = false;
            }
        }
    }

    private static void playLeanAnimationWithFadeIn(ModifierLayer<IAnimation> layer) {
        if (!BTPConfig.enableThirdPersonLeanAnimation) return;

        var assetManager = PlayerAnimatorAssetManager.get();
        if (!assetManager.containsKey(LEAN_ANIMATION_ID)) {
            if (!missingAnimationWarned) {
                BTPLog.LOGGER.warn("Lean animation resource '{}' not loaded", LEAN_ANIMATION_ID);
                missingAnimationWarned = true;
            }
            return;
        }

        var current = layer.getAnimation();
        if (current instanceof KeyframeAnimationPlayer player && player.isActive()) {
            return;
        }

        var keyframeAnimation = getAnimationsSafely(assetManager, LEAN_ANIMATION_ID, "tilted_gun").orElse(null);
        if (keyframeAnimation == null) {
            BTPLog.LOGGER.warn("Failed to get KeyframeAnimation for 'tilted_gun'");
            return;
        }

        var newAnimation = new KeyframeAnimationPlayer(keyframeAnimation);
        layer.replaceAnimationWithFade(fadeIn(), newAnimation);
        BTPLog.LOGGER.debug("BTP lean loop animation started with fade-in");
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide().isServer()) return;
        if (!LeanToggleHandler.isLeaning()) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var layer = (ModifierLayer<IAnimation>) associatedData.get(BTP_ONCE_LAYER);
        if (layer == null) return;

        var assetManager = PlayerAnimatorAssetManager.get();
        var keyframeAnimation = getAnimationsSafely(assetManager, LEAN_ANIMATION_ID, "tilted_gun_fire").orElse(null);
        if (keyframeAnimation == null) return;

        layer.setAnimation(new KeyframeAnimationPlayer(keyframeAnimation));
        BTPLog.LOGGER.debug("BTP lean fire animation triggered");
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide().isServer()) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getEntity();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var layer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
        if (layer == null) return;

        layer.setAnimation(null);
        BTPLog.LOGGER.debug("BTP lean animation cleared on reload start");
    }
}
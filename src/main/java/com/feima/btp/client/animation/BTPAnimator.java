package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.LeanToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Optional;

public class BTPAnimator {

    private static final ResourceLocation RIFLE_LEAN_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "tilted_gun");
    private static final ResourceLocation PISTOL_LEAN_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "pistol_tilted_gun");

    private static final String LEAN_LOOP_ANIM_NAME = "tilted_gun";
    private static final String LEAN_FIRE_ANIM_NAME = "tilted_gun_fire";
    private static final String PISTOL_LOOP_ANIM_NAME = "pistol_tilted";
    private static final String PISTOL_FIRE_ANIM_NAME = "pistol_tilted_fire";

    private static final ResourceLocation BTP_LOOP_LAYER = BTPMod.BTP_LOOP_LAYER;
    private static final ResourceLocation BTP_ONCE_LAYER = BTPMod.BTP_ONCE_LAYER;

    private static boolean fireAnimationPlaying = false;
    private static long fireAnimationStartTime = 0;
    private static final long FIRE_ANIMATION_DURATION_MS = 150;
    private static boolean lastFrameLeaning = false;
    private static boolean missingAnimationWarned = false;
    private static boolean lastFrameReloading = false;

    // 窗口期相关（硬编码 8 帧）
    private static final int TRANSITION_FRAMES_COUNT = 8;
    private static boolean lastFrameCrawling = false;
    private static int transitionFrames = 0;

    private static Method getAnimationsMethod = null;

    static {
        try {
            getAnimationsMethod = PlayerAnimatorAssetManager.class.getDeclaredMethod(
                    "getAnimations", ResourceLocation.class, String.class);
            getAnimationsMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            BTPLog.LOGGER.warn("getAnimations method not found: {}", e.getMessage());
        }
    }

    private static boolean isPistol(ItemStack gunItem) {
        if (gunItem.isEmpty()) return false;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return false;
        ResourceLocation gunId = iGun.getGunId(gunItem);
        Optional<com.tacz.guns.resource.index.CommonGunIndex> indexOpt =
                TimelessAPI.getCommonGunIndex(gunId);
        if (indexOpt.isEmpty()) return false;
        String type = indexOpt.get().getType();
        return "pistol".equalsIgnoreCase(type);
    }

    private static ResourceLocation getLeanAnimationId(ItemStack gunItem) {
        if (gunItem.isEmpty()) return RIFLE_LEAN_ANIMATION_ID;
        return isPistol(gunItem) ? PISTOL_LEAN_ANIMATION_ID : RIFLE_LEAN_ANIMATION_ID;
    }

    private static String getLeanLoopAnimName(ItemStack gunItem) {
        if (gunItem.isEmpty()) return LEAN_LOOP_ANIM_NAME;
        return isPistol(gunItem) ? PISTOL_LOOP_ANIM_NAME : LEAN_LOOP_ANIM_NAME;
    }

    private static String getLeanFireAnimName(ItemStack gunItem) {
        if (gunItem.isEmpty()) return LEAN_FIRE_ANIM_NAME;
        return isPistol(gunItem) ? PISTOL_FIRE_ANIM_NAME : LEAN_FIRE_ANIM_NAME;
    }

    private static Optional<KeyframeAnimation> getAnimationsSafely(PlayerAnimatorAssetManager manager,
                                                                   ResourceLocation id, String name) {
        if (getAnimationsMethod == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Optional<KeyframeAnimation> result =
                    (Optional<KeyframeAnimation>) getAnimationsMethod.invoke(manager, id, name);
            return result;
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to invoke getAnimations: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public static void registerAnimationLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_LOOP_LAYER,
                97,
                player -> new ModifierLayer<>(null, BTPLeanAdjustmentModifier.getModifier(player))
        );
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_ONCE_LAYER,
                98,
                player -> new ModifierLayer<>(null, BTPLeanAdjustmentModifier.getModifier(player))
        );
    }

    private static void clearFireAnimation(LocalPlayer player) {
        if (player == null) return;
        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var onceLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_ONCE_LAYER);
        if (onceLayer != null && onceLayer.getAnimation() != null) {
            onceLayer.setAnimation(null);
        }
        fireAnimationPlaying = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.options.getCameraType().isFirstPerson()) return;

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);

        // 窗口期检测：趴下→站立切换，重置窗口帧数
        boolean isCrawling = operator.isCrawl();
        if (lastFrameCrawling && !isCrawling) {
            transitionFrames = TRANSITION_FRAMES_COUNT;
        }
        lastFrameCrawling = isCrawling;

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var loopLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
        if (loopLayer == null) return;

        // 过渡窗口内：清空 BTP 循环动画和射击动画，并直接返回，阻止据枪动画播放
        if (transitionFrames > 0) {
            transitionFrames--;
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            return;
        }

        // 趴下时清空 BTP 层
        if (isCrawling) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameLeaning = false;
            lastFrameReloading = false;
            return;
        }

        // ===== 疾跑时清空 BTP 动画层（breakSprint = false 时） =====
        if (!BTPConfig.breakSprint && player.isSprinting()) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameLeaning = false;
            lastFrameReloading = false;
            return;
        }

        // 以下为据枪逻辑
        boolean isLeaning = LeanToggleHandler.isLeaning();

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        boolean isReloading = gunOperator.getSynReloadState().getStateType().isReloading();

        if (isReloading) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameLeaning = false;
            lastFrameReloading = true;
            return;
        }

        if (lastFrameReloading && isLeaning) {
            lastFrameReloading = false;
        } else {
            lastFrameReloading = false;
        }

        if (fireAnimationPlaying) {
            long elapsed = System.currentTimeMillis() - fireAnimationStartTime;
            @SuppressWarnings("unchecked")
            var onceLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_ONCE_LAYER);
            if (onceLayer != null) {
                IAnimation anim = onceLayer.getAnimation();
                if (anim == null || !anim.isActive() || elapsed > FIRE_ANIMATION_DURATION_MS + 50) {
                    onceLayer.setAnimation(null);
                    fireAnimationPlaying = false;
                }
            } else {
                fireAnimationPlaying = false;
            }
        }

        if (isLeaning) {
            ItemStack gunItem = player.getMainHandItem();
            playLeanAnimation(loopLayer, gunItem);
            lastFrameLeaning = true;
        } else {
            if (lastFrameLeaning) {
                loopLayer.setAnimation(null);
                clearFireAnimation(player);
                lastFrameLeaning = false;
            }
        }
    }

    private static void playLeanAnimation(ModifierLayer<IAnimation> layer, ItemStack gunItem) {
        if (!BTPConfig.enableThirdPersonLeanAnimation) return;

        ResourceLocation animId = getLeanAnimationId(gunItem);
        String animName = getLeanLoopAnimName(gunItem);

        var assetManager = PlayerAnimatorAssetManager.get();
        if (!assetManager.containsKey(animId)) {
            if (!missingAnimationWarned) {
                BTPLog.LOGGER.warn("Lean animation resource '{}' not loaded", animId);
                missingAnimationWarned = true;
            }
            return;
        }

        var current = layer.getAnimation();
        if (current instanceof KeyframeAnimationPlayer player && player.isActive()) {
            return;
        }

        var keyframeAnimation = getAnimationsSafely(assetManager, animId, animName).orElse(null);
        if (keyframeAnimation == null) {
            BTPLog.LOGGER.warn("Failed to get KeyframeAnimation for '{}' from '{}'", animName, animId);
            return;
        }

        layer.setAnimation(new KeyframeAnimationPlayer(keyframeAnimation));
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

        // 如果 breakSprint = false 且正在疾跑，不播放射击动画
        if (!BTPConfig.breakSprint && player.isSprinting()) return;

        ItemStack gunItem = player.getMainHandItem();
        ResourceLocation animId = getLeanAnimationId(gunItem);
        String animName = getLeanFireAnimName(gunItem);

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var layer = (ModifierLayer<IAnimation>) associatedData.get(BTP_ONCE_LAYER);
        if (layer == null) return;

        if (layer.getAnimation() != null) {
            layer.setAnimation(null);
        }

        var assetManager = PlayerAnimatorAssetManager.get();
        var keyframeAnimation = getAnimationsSafely(assetManager, animId, animName).orElse(null);
        if (keyframeAnimation == null) {
            BTPLog.LOGGER.warn("Failed to get fire animation '{}' from '{}'", animName, animId);
            return;
        }

        var newAnimation = new KeyframeAnimationPlayer(keyframeAnimation);
        layer.setAnimation(newAnimation);

        fireAnimationPlaying = true;
        fireAnimationStartTime = System.currentTimeMillis();
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
        clearFireAnimation(player);
    }
}
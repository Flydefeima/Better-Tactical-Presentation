package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.TiltToggleHandler;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Optional;

public class BTPAnimator {

    // 改为 public static final，供 BTPEventHandler 启动检查使用
    public static final ResourceLocation RIFLE_TILT_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "rifle_tilted_anim");
    public static final ResourceLocation PISTOL_TILT_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "pistol_tilted_anim");

    private static final String TILT_LOOP_ANIM_NAME = "rifle_tilted_gun";
    private static final String TILT_FIRE_ANIM_NAME = "rifle_tilted_gun_fire";
    private static final String PISTOL_LOOP_ANIM_NAME = "pistol_tilted";
    private static final String PISTOL_FIRE_ANIM_NAME = "pistol_tilted_fire";

    private static final ResourceLocation BTP_LOOP_LAYER = BTPMod.BTP_LOOP_LAYER;
    private static final ResourceLocation BTP_ONCE_LAYER = BTPMod.BTP_ONCE_LAYER;

    private static boolean fireAnimationPlaying = false;
    private static long fireAnimationStartTime = 0;
    private static final long FIRE_ANIMATION_DURATION_MS = 150;
    private static boolean lastFrameShouldPlay = false;
    private static boolean lastFrameReloading = false;

    // 窗口期相关（硬编码 8 帧）
    private static final int TRANSITION_FRAMES_COUNT = 8;
    private static boolean lastFrameCrawling = false;
    private static int transitionFrames = 0;

    // 物品切换追踪
    private static ItemStack lastGunItem = ItemStack.EMPTY;

    // ===== 新增：蹲下开镜状态追踪 =====
    private static boolean crouchTiltClearedByAim = false;

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

    private static ResourceLocation getTiltAnimationId(ItemStack gunItem) {
        if (gunItem.isEmpty()) return RIFLE_TILT_ANIMATION_ID;
        return isPistol(gunItem) ? PISTOL_TILT_ANIMATION_ID : RIFLE_TILT_ANIMATION_ID;
    }

    private static String getTiltLoopAnimName(ItemStack gunItem) {
        if (gunItem.isEmpty()) return TILT_LOOP_ANIM_NAME;
        return isPistol(gunItem) ? PISTOL_LOOP_ANIM_NAME : TILT_LOOP_ANIM_NAME;
    }

    private static String getTiltFireAnimName(ItemStack gunItem) {
        if (gunItem.isEmpty()) return TILT_FIRE_ANIM_NAME;
        return isPistol(gunItem) ? PISTOL_FIRE_ANIM_NAME : TILT_FIRE_ANIM_NAME;
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
                player -> new ModifierLayer<>(null, TiltAdjust.getModifier(player))
        );
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_ONCE_LAYER,
                98,
                player -> new ModifierLayer<>(null, TiltAdjust.getModifier(player))
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

        // 窗口期检测：趴下→站立切换
        boolean isCrawling = operator.isCrawl();
        if (lastFrameCrawling && !isCrawling) {
            transitionFrames = TRANSITION_FRAMES_COUNT;
        }
        lastFrameCrawling = isCrawling;

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var loopLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
        if (loopLayer == null) return;

        // 过渡窗口内：清空动画并返回
        if (transitionFrames > 0) {
            transitionFrames--;
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            crouchTiltClearedByAim = false;
            return;
        }

        // 趴下时清空
        if (isCrawling) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = false;
            crouchTiltClearedByAim = false;
            return;
        }

        // 疾跑时清空（breakSprint = false 时）
        if (!BTPConfig.breakSprint && player.isSprinting()) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = false;
            crouchTiltClearedByAim = false;
            return;
        }

        // ========== 物品切换检测 ==========
        ItemStack currentGun = player.getMainHandItem();
        boolean gunChanged = !ItemStack.matches(currentGun, lastGunItem);
        if (gunChanged) {
            lastGunItem = currentGun.copy();
            if (TiltToggleHandler.isTilting() || shouldPlayCrouchTilt(player)) {
                if (loopLayer.getAnimation() != null) {
                    loopLayer.setAnimation(null);
                }
                clearFireAnimation(player);
                crouchTiltClearedByAim = false;
                BTPLog.LOGGER.debug("Gun switched, BTP animation layers cleared.");
            }
        }

        // 换弹状态
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        boolean isReloading = gunOperator.getSynReloadState().getStateType().isReloading();
        if (isReloading) {
            if (loopLayer.getAnimation() != null) {
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = true;
            crouchTiltClearedByAim = false;
            return;
        }
        if (lastFrameReloading) {
            lastFrameReloading = false;
        }

        // 射击动画过期清理
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

        // ========== 判定是否应该播放 BTP 动画 ==========
        boolean isTilting = TiltToggleHandler.isTilting();
        boolean isCrouchTilt = shouldPlayCrouchTilt(player);

        // ===== 新增：蹲下倾斜 + 右键检测逻辑 =====
        if (isCrouchTilt && !isTilting) {
            // 检测玩家是否正在开镜（右键按住）
            boolean isAiming = operator.isAim() || AimKey.AIM_KEY.isDown();

            if (isAiming) {
                // 按了右键 → 清空 BTP 动画，让 TaCZ 开镜
                if (loopLayer.getAnimation() != null) {
                    loopLayer.setAnimation(null);
                }
                crouchTiltClearedByAim = true;
                lastFrameShouldPlay = false;
            } else if (crouchTiltClearedByAim) {
                // 右键松开 → 恢复 BTP 动画
                crouchTiltClearedByAim = false;
                ItemStack gunItem = player.getMainHandItem();
                playTiltAnimation(loopLayer, gunItem);
                lastFrameShouldPlay = true;
            } else {
                // 正常蹲下倾斜，但确保动画在播放
                ItemStack gunItem = player.getMainHandItem();
                playTiltAnimation(loopLayer, gunItem);
                lastFrameShouldPlay = true;
            }
        } else if (isTilting) {
            // 站立据枪模式（由 TiltToggleHandler 控制）
            if (crouchTiltClearedByAim) {
                crouchTiltClearedByAim = false;
            }
            ItemStack gunItem = player.getMainHandItem();
            playTiltAnimation(loopLayer, gunItem);
            lastFrameShouldPlay = true;
        } else {
            // 没有倾斜 → 清空动画
            if (lastFrameShouldPlay) {
                loopLayer.setAnimation(null);
                clearFireAnimation(player);
                lastFrameShouldPlay = false;
                crouchTiltClearedByAim = false;
            }
        }
    }

    /**
     * 判断蹲下时是否应该播放 BTP 动画
     * 条件：disableVanillaCrouchTilt == false 且玩家蹲下且持枪
     */
    private static boolean shouldPlayCrouchTilt(LocalPlayer player) {
        return !BTPConfig.disableVanillaCrouchTilt
                && player.isCrouching()
                && IGun.mainHandHoldGun(player);
    }

    private static void playTiltAnimation(ModifierLayer<IAnimation> layer, ItemStack gunItem) {
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;

        ResourceLocation animId = getTiltAnimationId(gunItem);
        String animName = getTiltLoopAnimName(gunItem);

        var assetManager = PlayerAnimatorAssetManager.get();
        if (!assetManager.containsKey(animId)) {
            BTPLog.LOGGER.warn("Tilt animation resource '{}' not loaded", animId);
            Minecraft.getInstance().execute(() -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    BTPTipsHelper.sendMissingTiltAnimationMessage(player);
                }
            });
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
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        boolean shouldPlayShoot = TiltToggleHandler.isTilting() || shouldPlayCrouchTilt(player);
        if (!shouldPlayShoot) return;

        if (!BTPConfig.breakSprint && player.isSprinting()) return;

        ItemStack gunItem = player.getMainHandItem();
        ResourceLocation animId = getTiltAnimationId(gunItem);
        String animName = getTiltFireAnimName(gunItem);

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
        lastFrameShouldPlay = false;
        crouchTiltClearedByAim = false;
    }
}
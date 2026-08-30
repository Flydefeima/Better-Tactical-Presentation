package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.TiltToggleHandler;
import com.feima.btp.config.BTPConfig;
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
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Optional;

public class BTPAnimator {

    // ===== 倾斜动画资源 ID =====
    public static final ResourceLocation RIFLE_TILT_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "rifle_tilted_anim");
    public static final ResourceLocation PISTOL_TILT_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "pistol_tilted_anim");

    // ===== 占位动画资源 ID（指向 placeholder.json） =====
    public static final ResourceLocation PLACEHOLDER_ANIMATION_ID = new ResourceLocation(BTPMod.MOD_ID, "placeholder");

    private static final String TILT_LOOP_ANIM_NAME = "rifle_tilted_gun";
    private static final String TILT_FIRE_ANIM_NAME = "rifle_tilted_gun_fire";
    private static final String PISTOL_LOOP_ANIM_NAME = "pistol_tilted";
    private static final String PISTOL_FIRE_ANIM_NAME = "pistol_tilted_fire";

    // 占位动画名称（从 placeholder.json 中读取）
    private static final String PLACEHOLDER_RIFLE_HOLD = "rifle_hold_placeholder";
    private static final String PLACEHOLDER_RIFLE_AIM = "rifle_aim_placeholder";
    private static final String PLACEHOLDER_PISTOL_HOLD = "pistol_hold_placeholder";
    private static final String PLACEHOLDER_PISTOL_AIM = "pistol_aim_placeholder";

    private static final ResourceLocation BTP_LOOP_LAYER = BTPMod.BTP_LOOP_LAYER;
    private static final ResourceLocation BTP_ONCE_LAYER = BTPMod.BTP_ONCE_LAYER;

    private static boolean fireAnimationPlaying = false;
    private static long fireAnimationStartTime = 0;
    private static final long FIRE_ANIMATION_DURATION_MS = 150;
    private static boolean lastFrameShouldPlay = false;
    private static boolean lastFrameReloading = false;

    // 窗口期相关（硬编码 8 帧）
    private static final int TRANSITION_FRAMES_COUNT = 8;
    private static boolean lastFrameProne = false;
    private static int transitionFrames = 0;

    // 物品切换追踪
    private static ItemStack lastGunItem = ItemStack.EMPTY;

    private static boolean crouchTiltClearedByAim = false;

    // ===== 状态机 =====
    private enum State { IDLE, TILTING, EXITING }
    private static State currentState = State.IDLE;
    private static long exitStartTime = 0;
    private static boolean isProneRecovery = false;

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

    // ===== 工具方法 =====
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

    /**
     * 获取占位动画名称，根据枪型和是否瞄准选择
     */
    private static String getPlaceholderAnimName(ItemStack gunItem, boolean isAiming) {
        boolean pistol = isPistol(gunItem);
        if (pistol) {
            return isAiming ? PLACEHOLDER_PISTOL_AIM : PLACEHOLDER_PISTOL_HOLD;
        } else {
            return isAiming ? PLACEHOLDER_RIFLE_AIM : PLACEHOLDER_RIFLE_HOLD;
        }
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

    // ===== 注册动画层 =====
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

    // ===== 获取 KeyframeAnimationPlayer =====
    private static KeyframeAnimationPlayer getPlaceholderPlayer(AbstractClientPlayer player, boolean isAiming) {
        ItemStack gunItem = player.getMainHandItem();
        String name = getPlaceholderAnimName(gunItem, isAiming);
        var manager = PlayerAnimatorAssetManager.get();
        var anim = getAnimationsSafely(manager, PLACEHOLDER_ANIMATION_ID, name).orElse(null);
        if (anim == null) {
            BTPLog.LOGGER.warn("Placeholder animation '{}' from '{}' not found", name, PLACEHOLDER_ANIMATION_ID);
            return null;
        }
        return new KeyframeAnimationPlayer(anim);
    }

    private static KeyframeAnimationPlayer getTiltLoopPlayer(AbstractClientPlayer player) {
        ItemStack gunItem = player.getMainHandItem();
        ResourceLocation id = getTiltAnimationId(gunItem);
        String name = getTiltLoopAnimName(gunItem);
        var manager = PlayerAnimatorAssetManager.get();
        var anim = getAnimationsSafely(manager, id, name).orElse(null);
        if (anim == null) {
            BTPLog.LOGGER.warn("Tilt loop animation '{}' from '{}' not found", name, id);
            return null;
        }
        return new KeyframeAnimationPlayer(anim);
    }

    private static void resetState() {
        currentState = State.IDLE;
        exitStartTime = 0;
        isProneRecovery = false;
        lastFrameShouldPlay = false;
        crouchTiltClearedByAim = false;
        TiltAdjust.setUseComplexCorrection(false);
    }

    /**
     * 检测玩家是否处于趴下状态（Pose.SWIMMING 且不在游泳）
     */
    private static boolean isProne(LocalPlayer player) {
        if (player == null) return false;
        return player.getPose() == Pose.SWIMMING && !player.isSwimming();
    }

    // ===== 核心 Tick 事件 =====
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.options.getCameraType().isFirstPerson()) return;

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);

        // ===== 如果按住据枪键并开镜，则清除动画并返回（让 TaCZ 原生动画播放） =====
        if (TiltToggleHandler.isHoldingTiltAndAiming()) {
            var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
            @SuppressWarnings("unchecked")
            var loopLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
            if (loopLayer != null && loopLayer.getAnimation() != null) {
                TiltAdjust.setUseComplexCorrection(false);
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            crouchTiltClearedByAim = false;
            resetState();
            return;
        }

        // ===== 使用 Pose.SWIMMING 检测趴下 =====
        boolean isProne = isProne(player);
        if (lastFrameProne && !isProne) {
            transitionFrames = TRANSITION_FRAMES_COUNT;
            isProneRecovery = true;
        }
        lastFrameProne = isProne;

        var associatedData = PlayerAnimationAccess.getPlayerAssociatedData(player);
        @SuppressWarnings("unchecked")
        var loopLayer = (ModifierLayer<IAnimation>) associatedData.get(BTP_LOOP_LAYER);
        if (loopLayer == null) return;

        // 过渡窗口内：清空动画并返回
        if (transitionFrames > 0) {
            transitionFrames--;
            if (loopLayer.getAnimation() != null) {
                TiltAdjust.setUseComplexCorrection(false);
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            crouchTiltClearedByAim = false;
            currentState = State.IDLE;
            return;
        }

        // 趴下时清空
        if (isProne) {
            if (loopLayer.getAnimation() != null) {
                TiltAdjust.setUseComplexCorrection(false);
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = false;
            crouchTiltClearedByAim = false;
            resetState();
            return;
        }

        // 疾跑时清空（breakSprint = false 时）
        if (!BTPConfig.breakSprint && player.isSprinting()) {
            if (loopLayer.getAnimation() != null) {
                TiltAdjust.setUseComplexCorrection(false);
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = false;
            crouchTiltClearedByAim = false;
            resetState();
            return;
        }

        // 物品切换检测
        ItemStack currentGun = player.getMainHandItem();
        boolean gunChanged = !ItemStack.matches(currentGun, lastGunItem);
        if (gunChanged) {
            lastGunItem = currentGun.copy();
            if (TiltToggleHandler.isTilting() || shouldPlayCrouchTilt(player)) {
                if (loopLayer.getAnimation() != null) {
                    TiltAdjust.setUseComplexCorrection(false);
                    loopLayer.setAnimation(null);
                }
                clearFireAnimation(player);
                crouchTiltClearedByAim = false;
                resetState();
                BTPLog.LOGGER.debug("Gun switched, BTP animation layers cleared.");
            }
        }

        // 换弹状态
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        boolean isReloading = gunOperator.getSynReloadState().getStateType().isReloading();
        if (isReloading) {
            if (loopLayer.getAnimation() != null) {
                TiltAdjust.setUseComplexCorrection(false);
                loopLayer.setAnimation(null);
            }
            clearFireAnimation(player);
            lastFrameShouldPlay = false;
            lastFrameReloading = true;
            crouchTiltClearedByAim = false;
            resetState();
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
        boolean shouldPlay = isTilting || isCrouchTilt;

        // 蹲下倾斜 + 右键检测逻辑
        if (isCrouchTilt && !isTilting) {
            boolean isAiming = operator.isAim() || AimKey.AIM_KEY.isDown();
            if (isAiming) {
                if (loopLayer.getAnimation() != null) {
                    TiltAdjust.setUseComplexCorrection(false);
                    loopLayer.setAnimation(null);
                }
                crouchTiltClearedByAim = true;
                if (currentState != State.IDLE) {
                    resetState();
                }
                lastFrameShouldPlay = false;
            } else if (crouchTiltClearedByAim) {
                crouchTiltClearedByAim = false;
                playTiltWithTransition(loopLayer, player, isProneRecovery);
                lastFrameShouldPlay = true;
            } else {
                playTiltWithTransition(loopLayer, player, isProneRecovery);
                lastFrameShouldPlay = true;
            }
        } else if (isTilting) {
            if (crouchTiltClearedByAim) {
                crouchTiltClearedByAim = false;
            }
            playTiltWithTransition(loopLayer, player, isProneRecovery);
            lastFrameShouldPlay = true;
        } else {
            // ===== 没有倾斜 → 清空动画（带退出过渡） =====
            if (currentState == State.TILTING) {
                // 退出据枪，播放占位动画
                boolean willBeAiming = operator.isAim();
                KeyframeAnimationPlayer placeholder = getPlaceholderPlayer(player, willBeAiming);
                if (placeholder != null) {
                    // 先切换修正器为简单模式，再淡入占位动画
                    TiltAdjust.setUseComplexCorrection(false);
                    float transition = BTPConfig.tiltTransitionTimeMs / 1000f;
                    AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn((int)(transition * 20), Ease.INOUTSINE);
                    loopLayer.replaceAnimationWithFade(fade, placeholder);
                }
                currentState = State.EXITING;
                exitStartTime = System.currentTimeMillis();
                lastFrameShouldPlay = false;
            } else if (currentState == State.EXITING) {
                if (System.currentTimeMillis() - exitStartTime > BTPConfig.tiltTransitionTimeMs + 50) {
                    if (loopLayer.getAnimation() != null) {
                        TiltAdjust.setUseComplexCorrection(false);
                        loopLayer.setAnimation(null);
                    }
                    currentState = State.IDLE;
                    lastFrameShouldPlay = false;
                }
            } else {
                if (loopLayer.getAnimation() != null) {
                    TiltAdjust.setUseComplexCorrection(false);
                    loopLayer.setAnimation(null);
                }
                lastFrameShouldPlay = false;
            }
            isProneRecovery = false;
        }
    }

    /**
     * 播放倾斜动画（带过渡逻辑）
     * @param layer 动画层
     * @param player 玩家
     * @param proneRecovery 是否刚从趴下恢复（直接设置，无过渡）
     */
    private static void playTiltWithTransition(ModifierLayer<IAnimation> layer, AbstractClientPlayer player, boolean proneRecovery) {
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;

        // 如果正在退出，取消退出
        if (currentState == State.EXITING) {
            layer.setAnimation(null);
            resetState();
        }

        // 已经在倾斜状态，无需重复播放
        if (currentState == State.TILTING) {
            return;
        }

        KeyframeAnimationPlayer tiltPlayer = getTiltLoopPlayer(player);
        if (tiltPlayer == null) {
            BTPLog.LOGGER.warn("Tilt loop animation missing, cannot play.");
            return;
        }

        // 趴下恢复：直接设置，无过渡
        if (proneRecovery) {
            TiltAdjust.setUseComplexCorrection(true);
            layer.setAnimation(tiltPlayer);
            currentState = State.TILTING;
            isProneRecovery = false;
            return;
        }

        // 进入据枪前播放占位动画（作为过渡起点）
        boolean wasAiming = TiltToggleHandler.getWasAimingBeforeTilt();
        KeyframeAnimationPlayer placeholder = getPlaceholderPlayer(player, wasAiming);
        if (placeholder != null) {
            TiltAdjust.setUseComplexCorrection(false);
            layer.setAnimation(placeholder);
        }

        float transition = BTPConfig.tiltTransitionTimeMs / 1000f;
        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn((int)(transition * 20), Ease.INOUTSINE);
        layer.replaceAnimationWithFade(fade, tiltPlayer);
        TiltAdjust.setUseComplexCorrection(true);
        currentState = State.TILTING;
        isProneRecovery = false;
    }

    private static boolean shouldPlayCrouchTilt(LocalPlayer player) {
        return !BTPConfig.disableVanillaCrouchTilt
                && player.isCrouching()
                && IGun.mainHandHoldGun(player);
    }

    // ===== 射击事件 =====
    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide().isServer()) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);

        // ===== 如果正在开镜，则直接返回，让 TaCZ 原生开火动画播放 =====
        if (operator.isAim()) {
            return;
        }

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

        TiltAdjust.setUseComplexCorrection(false);
        layer.setAnimation(null);
        clearFireAnimation(player);
        lastFrameShouldPlay = false;
        crouchTiltClearedByAim = false;
        resetState();
    }
}
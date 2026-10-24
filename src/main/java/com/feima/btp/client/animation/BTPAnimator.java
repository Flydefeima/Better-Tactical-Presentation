package com.feima.btp.client.animation;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.TiltStateManager;
import com.feima.btp.client.tilt.TiltController;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BTPAnimator {

    private static final int BTP_LAYER_PRIORITY = 110;
    private static final ResourceLocation BTP_LAYER = BTPMod.BTP_LOOP_LAYER;

    private static final long FIRE_ANIMATION_DURATION_MS = 350;
    private static final long FIRE_EARLY_CUT_MS = 50;

    private static final TiltAnimState STATE = new TiltAnimState();

    /**
     * 本地玩家当前手持"枪的类型"。
     *
     * <p>这是判断是否需要重新加载据枪动画的关键——TaCZ 的所有枪械物品都是同一个
     * Java 类（{@code ModernKineticGunItem}），仅凭 {@code ItemStack.getItem()}
     * 无法区分"手枪"和"步枪"。必须通过 {@link TiltAnimNames#isPistol} 判断。
     */
    private enum GunKindState { NONE, PISTOL, RIFLE }

    private static GunKindState lastGunKindState = GunKindState.NONE;
    private static boolean lastFrameProne = false;
    private static long fireStartMs = 0L;

    public static boolean isTiltAnimationActive() {
        return STATE.isAnimActive();
    }

    public static boolean isFireAnimationActive() {
        return STATE.isFiring();
    }

    public static void registerAnimationLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                BTP_LAYER,
                BTP_LAYER_PRIORITY,
                player -> {
                    ModifierLayer<IAnimation> layer =
                            new ModifierLayer<>(null, TiltAdjust.getModifier(player));
                    layer.setAnimation(IdentityAnimation.INSTANCE);
                    return layer;
                }
        );
    }

    public static void resetState() {
        STATE.reset();
        lastFrameProne = false;
        lastGunKindState = GunKindState.NONE;
        fireStartMs = 0L;
        TiltAdjust.setApplying(false);
        TiltAdjust.setUseComplexCorrection(false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;

        if (local != null && !mc.options.getCameraType().isFirstPerson()) {
            tickLocal(local, mc);
        }

        if (mc.level != null) {
            for (Player other : mc.level.players()) {
                if (other == local) continue;
                if (other instanceof AbstractClientPlayer acp) {
                    tickRemote(acp);
                }
            }
        }
    }

    private static void tickLocal(LocalPlayer player, Minecraft mc) {
        ModifierLayer<IAnimation> layer = TiltLayerController.getLayer(player, BTP_LAYER);
        if (layer == null) return;

        if (TiltController.isHoldingTiltAndAiming()) {
            hardClear(layer);
            return;
        }

        boolean isProne = isProne(player);
        STATE.onProneChanged(lastFrameProne, isProne);
        lastFrameProne = isProne;

        if (STATE.tickProneTransition() || isProne) {
            hardClear(layer);
            return;
        }

        if (!BTPConfig.breakSprint && player.isSprinting()) {
            hardClear(layer);
            return;
        }

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        if (gunOperator.getSynReloadState().getStateType().isReloading()) {
            hardClear(layer);
            return;
        }

        // 手枪 / 步枪 切换检测
        GunKindState currentKind = getGunKindState(player.getMainHandItem());
        if (currentKind != lastGunKindState) {
            GunKindState prevKind = lastGunKindState;
            lastGunKindState = currentKind;

            boolean wasGun = prevKind != GunKindState.NONE;
            boolean isGun = currentKind != GunKindState.NONE;

            if (wasGun && isGun) {
                // 枪 ↔ 枪：可能是手枪↔步枪，也可能是同类枪切换。
                // 手枪↔步枪时动画文件名不同，必须重新加载，用 fade 平滑过渡。
                if (TiltController.isTilting() || shouldPlayCrouchTilt(player)) {
                    reloadTiltAnimation(player, layer);
                }
            } else if (isGun || wasGun) {
                // 枪 ↔ 非枪
                if (TiltController.isTilting() || shouldPlayCrouchTilt(player)) {
                    hardClear(layer);
                    BTPLog.LOGGER.debug("Gun state changed, BTP animation layer cleared.");
                    return;
                }
            }
        }

        tickFireLifecycle(player, layer);

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean isTilting = TiltController.isTilting();
        boolean isCrouchTilt = shouldPlayCrouchTilt(player);

        if (isCrouchTilt && !isTilting) {
            boolean isAiming = operator.isAim() || AimKey.AIM_KEY.isDown();
            if (isAiming) {
                TiltAdjust.setApplying(false);
                TiltAdjust.setUseComplexCorrection(false);
                layer.setAnimation(IdentityAnimation.INSTANCE);
                STATE.reset();
            } else {
                TiltLayerController.enter(player, layer, STATE);
            }
        } else if (isTilting) {
            TiltLayerController.enter(player, layer, STATE);
        } else {
            TiltLayerController.exitOrIdle(player, layer, STATE);
        }
    }

    /**
     * 把当前层的动画平滑过渡到新枪的 loop，保持 TiltAdjust 状态和据枪状态不变。
     *
     * <p>用于手枪↔步枪切换的动画热替换，采用与进入据枪相同的 fade 时长和缓动曲线，
     * 视觉上与正常的据枪进入/退出过渡一致。
     *
     * <p><b>FIRE 相位下不执行</b>：让 {@link #tickFireLifecycle} 自然走完 fire 动画，
     * 再由它切到新枪的 loop——避免 fade 与 fire 生命周期互相打断。
     *
     * <p><b>第三人称动画关闭时不执行</b>：与 {@link TiltLayerController#enter} 保持
     * 一致的开关语义，否则会绕过配置项强推动画。
     */
    private static void reloadTiltAnimation(LocalPlayer player, ModifierLayer<IAnimation> layer) {
        if (STATE.isFiring()) return;
        if (!BTPConfig.enableThirdPersonTiltAnimation) return;

        TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
        KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(kind.fileId(), kind.loop());
        if (loopPlayer == null) return;

        TiltAdjust.setApplying(true);
        TiltAdjust.setUseComplexCorrection(true);

        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(
                TiltLayerController.fadeTicks(), Ease.INOUTSINE);
        layer.replaceAnimationWithFade(fade, loopPlayer);
        STATE.markTilting();
        BTPLog.LOGGER.debug("Gun kind changed, faded to new tilt animation: {}", kind.fileId());
    }

    private static GunKindState getGunKindState(ItemStack stack) {
        if (stack.isEmpty()) return GunKindState.NONE;
        if (IGun.getIGunOrNull(stack) == null) return GunKindState.NONE;
        return TiltAnimNames.isPistol(stack) ? GunKindState.PISTOL : GunKindState.RIFLE;
    }

    /**
     * 远程玩家：只做「同步状态 → loop/Identity」的切换。
     * 不处理 fire、prone、切枪、换弹等本地瞬态动作。
     */
    private static void tickRemote(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = TiltLayerController.getLayer(player, BTP_LAYER);
        if (layer == null) return;

        boolean shouldTilt = TiltStateManager.isTilting(player);
        IAnimation current = layer.getAnimation();

        if (shouldTilt) {
            if (current == null || current == IdentityAnimation.INSTANCE || !current.isActive()) {
                TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
                KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(kind.fileId(), kind.loop());
                if (loopPlayer != null) {
                    AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(
                            TiltLayerController.fadeTicks(), Ease.INOUTSINE);
                    layer.replaceAnimationWithFade(fade, loopPlayer);
                }
            }
        } else {
            if (current != null && current != IdentityAnimation.INSTANCE) {
                AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(
                        TiltLayerController.fadeTicks(), Ease.INOUTSINE);
                layer.replaceAnimationWithFade(fade, IdentityAnimation.INSTANCE);
            }
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide().isServer()) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator.isAim()) return;

        boolean shouldPlayShoot = TiltController.isTilting() || shouldPlayCrouchTilt(player);
        if (!shouldPlayShoot) return;
        if (!BTPConfig.breakSprint && player.isSprinting()) return;

        playFire(player);
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide().isServer()) return;
        if (!BTPMod.isPlayerAnimatorLoaded) return;

        LivingEntity shooter = event.getEntity();
        if (!(shooter instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;

        ModifierLayer<IAnimation> layer = TiltLayerController.getLayer(player, BTP_LAYER);
        if (layer == null) return;
        hardClear(layer);
    }

    private static void hardClear(ModifierLayer<IAnimation> layer) {
        TiltAdjust.setApplying(false);
        TiltAdjust.setUseComplexCorrection(false);
        layer.setAnimation(IdentityAnimation.INSTANCE);
        STATE.reset();
        fireStartMs = 0L;
    }

    private static void playFire(LocalPlayer player) {
        ModifierLayer<IAnimation> layer = TiltLayerController.getLayer(player, BTP_LAYER);
        if (layer == null) return;
        if (!STATE.isAnimActive()) return;

        TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
        KeyframeAnimationPlayer firePlayer = AnimationRegistry.player(kind.fileId(), kind.fire());
        if (firePlayer == null) return;

        TiltAdjust.setApplying(true);
        TiltAdjust.setUseComplexCorrection(true);
        layer.setAnimation(firePlayer);
        STATE.markFiring();
        fireStartMs = System.currentTimeMillis();
    }

    private static void tickFireLifecycle(LocalPlayer player, ModifierLayer<IAnimation> layer) {
        if (!STATE.isFiring()) return;

        long elapsed = System.currentTimeMillis() - fireStartMs;
        if (elapsed < FIRE_ANIMATION_DURATION_MS - FIRE_EARLY_CUT_MS) return;

        TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
        KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(kind.fileId(), kind.loop());
        if (loopPlayer != null) {
            layer.setAnimation(loopPlayer);
        }
        STATE.markTilting();
    }

    private static boolean shouldPlayCrouchTilt(LocalPlayer player) {
        return !BTPConfig.disableVanillaCrouchTilt
                && player.isCrouching()
                && IGun.mainHandHoldGun(player);
    }

    private static boolean isProne(LocalPlayer player) {
        if (player == null) return false;
        return player.getPose() == Pose.SWIMMING && !player.isSwimming();
    }
}
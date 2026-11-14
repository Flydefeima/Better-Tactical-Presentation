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
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BTPAnimator {

    private static final int BTP_LAYER_PRIORITY = 110;
    private static final ResourceLocation BTP_LAYER = BTPMod.BTP_LOOP_LAYER;

    private static final long FIRE_ANIMATION_DURATION_MS = 350;
    private static final long FIRE_EARLY_CUT_MS = 50;

    private static final TiltAnimState STATE = new TiltAnimState();

    /**
     * 远程玩家当前播放的枪种类缓存。
     *
     * <p>远程玩家的 loop 动画是循环的（永远 active），无法通过 {@code isActive()}
     * 判断"是否需要切枪刷新"。用 kind 缓存做增量对比，切枪后才重新加载。
     * 在 {@link #resetState()} 与玩家退出时清空。
     */
    private static final Map<UUID, TiltAnimNames.GunKind> REMOTE_KINDS = new HashMap<>();

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
        REMOTE_KINDS.clear();
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

        // 与 TaCZ 的 PlayerAnimatorCompat.canPlayAnimation 对齐：
        // 游泳 / 爬梯 / 睡觉 / 滑翔时不播放任何 BTP 第三人称动画。
        // 缺少此检查时，TaCZ 已停掉所有动画，但 BTP 层（priority 110）会继续
        // 播放 tilt loop 覆盖 TaCZ 的空状态，导致视觉错乱。
        if (!PlayerAnimatorCompat.canPlayAnimation(player)) {
            hardClear(layer);
            return;
        }

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

        GunKindState currentKind = getGunKindState(player.getMainHandItem());
        if (currentKind != lastGunKindState) {
            GunKindState prevKind = lastGunKindState;
            lastGunKindState = currentKind;

            boolean wasGun = prevKind != GunKindState.NONE;
            boolean isGun = currentKind != GunKindState.NONE;

            if (wasGun && isGun) {
                if (TiltController.isTilting() || shouldPlayCrouchTilt(player)) {
                    reloadTiltAnimation(player, layer);
                }
            } else if (isGun || wasGun) {
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
     *
     * <p>切枪检测用 {@link #REMOTE_KINDS} 缓存当前播放的 kind，与最新
     * {@link TiltAnimNames#forGun(ItemStack)} 比对。循环动画永远 active，
     * 不能只靠 {@code isActive()} 判断是否需要重建 player。
     */
    private static void tickRemote(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = TiltLayerController.getLayer(player, BTP_LAYER);
        if (layer == null) return;

        // 与 TaCZ 的 PlayerAnimatorCompat.canPlayAnimation 对齐：
        // 游泳 / 爬梯 / 睡觉 / 滑翔时淡出到 Identity，避免 BTP 层覆盖 TaCZ 的空状态。
        if (!PlayerAnimatorCompat.canPlayAnimation(player)) {
            REMOTE_KINDS.remove(player.getUUID());
            IAnimation current = layer.getAnimation();
            if (current != null && current != IdentityAnimation.INSTANCE) {
                AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(
                        TiltLayerController.fadeTicks(), Ease.INOUTSINE);
                layer.replaceAnimationWithFade(fade, IdentityAnimation.INSTANCE);
            }
            return;
        }

        boolean shouldTilt = TiltStateManager.isTilting(player);
        IAnimation current = layer.getAnimation();

        if (shouldTilt) {
            TiltAnimNames.GunKind want = TiltAnimNames.forGun(player.getMainHandItem());
            TiltAnimNames.GunKind have = REMOTE_KINDS.get(player.getUUID());

            boolean needReload = current == null
                    || current == IdentityAnimation.INSTANCE
                    || !current.isActive()
                    || want != have;

            if (needReload) {
                KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(want.fileId(), want.loop());
                if (loopPlayer != null) {
                    AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(
                            TiltLayerController.fadeTicks(), Ease.INOUTSINE);
                    layer.replaceAnimationWithFade(fade, loopPlayer);
                    REMOTE_KINDS.put(player.getUUID(), want);
                }
            }
        } else {
            REMOTE_KINDS.remove(player.getUUID());
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

    /**
     * 从 FIRE 切回 TILTING。
     *
     * <p><b>契约</b>：{@code markTilting()} 必须在 loop player 成功设置之后才执行。
     * 如果 loop 资源缺失 / reload 中途（{@code AnimationRegistry.player} 返回 null），
     * 层里仍持有失效的 fire 动画，而 STATE 已切到 TILTING——下一 tick
     * {@code enter/exitOrIdle} 会因 {@code state.isTilting()} 跳过重新加载，
     * 同时 {@code LoopUpperMixin} 因 {@code isFireAnimationActive()} 变 false 而
     * 不再 cancel TaCZ 的 loop，出现视觉错乱且无法自愈。此时必须 {@link #hardClear}，
     * 让下一 tick 从完整流程重新走。
     */
    private static void tickFireLifecycle(LocalPlayer player, ModifierLayer<IAnimation> layer) {
        if (!STATE.isFiring()) return;

        long elapsed = System.currentTimeMillis() - fireStartMs;
        if (elapsed < FIRE_ANIMATION_DURATION_MS - FIRE_EARLY_CUT_MS) return;

        TiltAnimNames.GunKind kind = TiltAnimNames.forGun(player.getMainHandItem());
        KeyframeAnimationPlayer loopPlayer = AnimationRegistry.player(kind.fileId(), kind.loop());
        if (loopPlayer != null) {
            layer.setAnimation(loopPlayer);
            STATE.markTilting();
        } else {
            hardClear(layer);
        }
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
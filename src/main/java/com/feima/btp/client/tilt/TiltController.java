package com.feima.btp.client.tilt;

import com.feima.btp.BTPMod;
import com.feima.btp.client.compat.TaCZLabsCompat;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.config.TiltTriggerMode;
import com.feima.btp.network.NetworkHandler;
import com.feima.btp.network.ServerboundTiltPacket;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.feima.btp.client.BTPEventHandler.HOLD_TILT_KEY;

/**
 * 据枪状态机的中央控制器。
 *
 * <p><b>职责边界</b>：
 * <ul>
 *   <li>持有唯一的"是否据枪"状态 {@code tilting}</li>
 *   <li>负责网络广播（ServerboundTiltPacket）</li>
 *   <li>负责 TaCZ:Labs 准星抑制 / 恢复</li>
 *   <li>负责进入据枪时的瞄准取消、疾跑打断</li>
 *   <li>处理 Hold 键（按住据枪键），它是跨模式的基础能力</li>
 *   <li>分发其他输入事件到当前激活的 {@link TiltMode}</li>
 * </ul>
 *
 * <p><b>模式选择</b>由 {@code BTPConfig.tiltTriggerMode} 枚举决定，
 * 可选值见 {@link TiltTriggerMode}。Forge 在配置加载时已校验该值，
 * 本类只需通过 {@link #modeFor(TiltTriggerMode)} 映射到 Mode 实例。
 * 若为 {@link TiltTriggerMode#NONE}，{@code activeMode} 为 {@code null}，
 * BTP 不接管任何输入（Hold 键仍生效）。
 */
@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TiltController {

    public static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BTP-TiltTimer");
                t.setDaemon(true);
                return t;
            });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!SCHEDULER.isShutdown()) {
                SCHEDULER.shutdownNow();
            }
        }));
    }

    // ============================================================
    // 全局状态
    // ============================================================

    private static boolean tilting = false;
    private static boolean wasAimingBeforeTilt = false;
    private static TiltMode activeMode = null;
    private static boolean holdActive = false;

    private TiltController() {}

    // ============================================================
    // 查询 API
    // ============================================================

    public static boolean isTilting() {
        if (!tilting) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return IGun.mainHandHoldGun(player);
    }

    public static boolean getWasAimingBeforeTilt() {
        return wasAimingBeforeTilt;
    }

    public static boolean isHoldActive() {
        return holdActive;
    }

    public static boolean isHoldingTiltAndAiming() {
        if (!holdActive) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        return operator.isAim() || AimKey.AIM_KEY.isDown();
    }

    /**
     * 当前是否处于 ZOOM_STAGE 模式。
     *
     * <p>{@code ZoomKeyMixin} 用它决定是否在 V 键切换倍率时显示倍率提示。
     * 其它模式（DEFAULT / LONG_PRESS / NONE）返回 false。
     */
    public static boolean isZoomStageMode() {
        return ensureMode() == ZoomStageTiltMode.INSTANCE;
    }

    // ============================================================
    // 状态转换 API（供 Mode 调用）
    // ============================================================

    public static void enterTilt(boolean recordAim) {
        if (tilting) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (recordAim) {
            wasAimingBeforeTilt = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        }

        if (BTPConfig.breakSprint) {
            player.setSprinting(false);
        }
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        tilting = true;
        TaCZLabsCompat.suppressCrosshair();
        sendTiltStateToServer(true);
    }

    public static void exitTilt() {
        if (!tilting) return;
        tilting = false;
        TaCZLabsCompat.restoreCrosshair();
        sendTiltStateToServer(false);
    }

    public static void forceReset() {
        if (tilting) {
            sendTiltStateToServer(false);
        }
        tilting = false;
        wasAimingBeforeTilt = false;
        TaCZLabsCompat.restoreCrosshair();
    }

    // ============================================================
    // 辅助方法（供 Mode 复用）
    // ============================================================

    public static void restoreAim(LocalPlayer player) {
        if (player == null) return;
        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (!op.isAim()) {
            op.aim(true);
            AimKey.AIM_KEY.setDown(true);
        }
    }

    public static void cancelAim(LocalPlayer player) {
        if (player == null) return;
        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (op.isAim()) {
            op.aim(false);
            AimKey.AIM_KEY.setDown(false);
        }
    }

    public static boolean isHoldingGun(LocalPlayer player) {
        if (player == null) return false;
        return IGun.mainHandHoldGun(player);
    }

    public static void releaseUseInput() {
        Minecraft.getInstance().options.keyUse.setDown(false);
    }

    private static void sendTiltStateToServer(boolean state) {
        if (Minecraft.getInstance().player == null) return;
        if (!NetworkHandler.isServerPresent()) return;
        NetworkHandler.CHANNEL.sendToServer(new ServerboundTiltPacket(state));
    }

    // ============================================================
    // 模式映射与分发
    // ============================================================

    /**
     * 枚举 → Mode 单例。{@link TiltTriggerMode#NONE} 返回 {@code null}。
     *
     * <p>这个映射放在客户端类里而不是枚举里——{@link TiltTriggerMode} 在
     * {@code config} 包，不能引用任何客户端类（它被 BTPConfig 引用，
     * 而 BTPConfig 在服务端也会加载）。
     */
    private static TiltMode modeFor(TiltTriggerMode trigger) {
        return switch (trigger) {
            case DEFAULT -> DefaultTiltMode.INSTANCE;
            case LONG_PRESS -> LongPressTiltMode.INSTANCE;
            case ZOOM_STAGE -> ZoomStageTiltMode.INSTANCE;
            case NONE -> null;
        };
    }

    /** Forge 保证该值始终有效，直接映射即可，无需缓存或容错。 */
    private static TiltMode determineMode() {
        return modeFor(BTPConfig.tiltTriggerMode);
    }

    private static TiltMode ensureMode() {
        if (activeMode == null) {
            activeMode = determineMode();
        }
        return activeMode;
    }

    /**
     * V 键虚拟档位入口。由 {@code ZoomKeyMixin} 调用。
     *
     * <p>仅当 BTP 当前处于 {@link TiltTriggerMode#ZOOM_STAGE} 时才拦截 V 键。
     *
     * @return true 表示本次 V 键已被 BTP 消费，TaCZ 不应再处理。
     */
    public static boolean tryHandleZoomAsTilt() {
        if (ensureMode() != ZoomStageTiltMode.INSTANCE) return false;
        return ZoomStageTiltMode.INSTANCE.tryHandleZoomAsTilt();
    }

    // ============================================================
    // Hold 键处理（跨模式基础能力）
    // ============================================================

    /**
     * 处理 Hold 键按下/释放。
     *
     * <p>GUI 打开时：
     * <ul>
     *   <li>PRESS 不进入据枪（避免意外动作），但事件被消费，防止按键穿透到 GUI</li>
     *   <li>RELEASE 始终处理——即便 GUI 打开，若此前已进入据枪则必须退出，
     *       避免状态残留（例如玩家按住 Hold 打开背包后再松手）</li>
     * </ul>
     *
     * @return true 表示本次事件属于 Hold 键
     */
    private static boolean handleHoldKey(Minecraft mc, LocalPlayer player, int keyOrButton, int action) {
        if (keyOrButton != HOLD_TILT_KEY.getKey().getValue()) return false;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return false;

        if (!isHoldingGun(player)) {
            if (holdActive) {
                holdActive = false;
                exitTilt();
            }
            HOLD_TILT_KEY.consumeClick();
            return true;
        }

        if (action == GLFW.GLFW_PRESS) {
            // GUI 中按 HOLD 键不进入据枪
            if (mc.screen != null) {
                HOLD_TILT_KEY.consumeClick();
                return true;
            }
            if (!holdActive) {
                holdActive = true;
                enterTilt(true);
            }
        } else {
            // 释放始终处理——即便此时 GUI 已打开，也要清理状态并通知 Mode 重新同步
            if (holdActive) {
                holdActive = false;
                exitTilt();
                TiltMode mode = ensureMode();
                if (mode != null) {
                    mode.onHoldKeyReleased(player);
                }
            }
        }
        HOLD_TILT_KEY.consumeClick();
        return true;
    }

    // ============================================================
    // 事件订阅
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TiltMode mode = ensureMode();
        if (mode != null) {
            mode.reset();
        }
        holdActive = false;
        forceReset();
        activeMode = null;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Hold 键始终处理（内部会区分 GUI）
        if (handleHoldKey(mc, player, event.getKey(), event.getAction())) return;

        if (mc.screen != null) return;

        TiltMode mode = ensureMode();
        if (mode == null) return;
        mode.onKeyInput(mc, player, event.getKey(), event.getAction());
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Hold 键可绑定到鼠标；GUI 处理逻辑在 handleHoldKey 内部
        if (handleHoldKey(mc, player, event.getButton(), event.getAction())) {
            event.setCanceled(true);
            return;
        }

        if (mc.screen != null) return;

        TiltMode mode = ensureMode();
        if (mode == null) return;
        if (mode.onMouseInput(mc, player, event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        TiltMode desiredMode = determineMode();

        // 运行时配置切换：清空旧 mode、强制重置据枪
        if (activeMode != desiredMode) {
            if (activeMode != null) {
                activeMode.reset();
            }
            holdActive = false;
            forceReset();
            activeMode = desiredMode;
        }

        if (activeMode == null) return;
        activeMode.onClientTick(mc, player);

        // 兜底：据枪状态下切到非枪物品时清理残留状态。
        // 场景——resetToAimOnItemSwitch = false 时，某些 Mode（如 DefaultTiltMode
        // 用 B 键 toggle 进入据枪但 rightPressed = false）不会主动 exitTilt，
        // 导致 tilting 字段和 TaCZ:Labs 准星抑制状态残留。
        if (tilting && !isHoldingGun(player)) {
            forceReset();
        }
    }
}
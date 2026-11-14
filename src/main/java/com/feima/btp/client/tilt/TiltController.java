package com.feima.btp.client.tilt;

import com.feima.btp.BTPMod;
import com.feima.btp.client.compat.ClientNetworkHelper;
import com.feima.btp.client.compat.TaCZLabsCompat;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.config.TiltTriggerMode;
import com.feima.btp.network.NetworkHandler;
import com.feima.btp.network.ServerboundTiltPacket;
import com.feima.btp.network.TiltStateStorage;
import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.concurrent.TimeUnit;

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
 * <p><b>Hold 键</b>：由 {@link BTPConfig#enableHoldTilt} 控制是否启用。键位绑定
 * 沿用控制设置里的 {@code HOLD_TILT_KEY}——KeyMapping 本来就支持键盘或鼠标，
 * 玩家自行绑定即可。本配置只决定"是否响应这个绑定"，不改变绑定本身。
 * 关闭时 Hold 键事件被完全忽略，{@link TiltTriggerMode} 等其他触发逻辑不受影响。
 *
 * <p><b>Hold 键绑鼠标时的 cancel 契约</b>：{@link #onMouseInput} 在
 * {@link #handleHoldKey} 返回 true 时会 cancel 鼠标事件。因此
 * {@code handleHoldKey} 的返回值被严格定义为「<b>本次事件是否真正改变了
 * hold 状态</b>」——只有进入或退出 hold 时才返回 true。在"键匹配但不该触发"
 * 的场景（空手 / GUI 打开 / 重复 PRESS / 已退出后的 RELEASE）必须返回 false，
 * 让鼠标事件走 vanilla 逻辑（吃东西、交互、GUI 点击）。否则玩家绑定鼠标右键后
 * 空手右键吃东西、在 GUI 里右键点击全部会失效。
 *
 * <p><b>本地玩家状态写入 TiltStateStorage</b>：{@code ServerboundTiltPacket.handle}
 * 只把包转发给追踪该玩家的<em>其他</em>客户端，发送者自己不会收到
 * {@code ClientboundTiltPacket}。为了让 {@code SprintBlockMixin} 能双端
 * 统一用 {@link TiltStateStorage#getTiltState} 查询，本类在
 * {@link #enterTilt} / {@link #exitTilt} / {@link #forceReset} 里主动写入
 * 本地玩家的状态。
 *
 * <p><b>散布倍率同步</b>：{@code BTPConfig.tiltSpreadMultiplier} 是 CLIENT 配置，
 * 服务端 {@code GunSpreadMixin} 读不到。因此本类在发包时把当前倍率一起发到
 * 服务端（见 {@link ServerboundTiltPacket}）。同步时机是进入/退出据枪——
 * 玩家在据枪中修改配置需下次据枪才生效，这是有意的简化。
 *
 * <p><b>AimKey 压制策略</b>：
 * <ul>
 *   <li>{@link #enterTilt} 瞬时清 {@code AimKey}，让 TaCZ 立刻停止 aim</li>
 *   <li>{@link #onClientTick} 在 {@code tilting == true} 时每 tick 调
 *       {@link #suppressAimIfTilting} 兜底——防止 {@code HOLD_TO_AIM = true}
 *       场景下 TaCZ 的 {@code onAimHoldingPreInput} 重新 aim 抵消据枪</li>
 *   <li>{@link #releaseUseInput} <b>不</b>碰 {@code AimKey}——
 *       {@link #restoreAim} 设置它后需要保持，否则"退出据枪后自动开镜"会失效
 *       （这是 BTP 的<em>设计选择</em>，不是 bug）</li>
 * </ul>
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
            if (SCHEDULER.isShutdown()) return;
            SCHEDULER.shutdown();
            try {
                if (!SCHEDULER.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    SCHEDULER.shutdownNow();
                }
            } catch (InterruptedException e) {
                SCHEDULER.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "BTP-TiltTimer-Shutdown"));
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
        TiltStateStorage.setTiltState(player.getUUID(), true);
        TaCZLabsCompat.suppressCrosshair();
        sendTiltStateToServer(true);
    }

    public static void exitTilt() {
        if (!tilting) return;
        tilting = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            TiltStateStorage.setTiltState(player.getUUID(), false);
        }
        TaCZLabsCompat.restoreCrosshair();
        sendTiltStateToServer(false);
    }

    public static void forceReset() {
        boolean wasTilting = tilting;
        tilting = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            TiltStateStorage.setTiltState(player.getUUID(), false);
        }
        if (wasTilting) {
            sendTiltStateToServer(false);
        }
        wasAimingBeforeTilt = false;
        TaCZLabsCompat.restoreCrosshair();
    }

    // ============================================================
    // 辅助方法（供 Mode 复用）
    // ============================================================

    /**
     * 恢复瞄准状态。设置 {@code AimKey.setDown(true)} 是为了让 TaCZ 的
     * {@code onAimHoldingPreInput}（{@code HOLD_TO_AIM = true} 时每 tick 检查
     * {@code AimKey.isDown()}）保持 aim。
     *
     * <p><b>不要在任何后续路径调用 {@link #releaseUseInput} 清掉 {@code AimKey}</b>——
     * 否则本方法的效果会被下个 tick 的 TaCZ 逻辑抵消。
     *
     * <p>「退出据枪后自动开镜」是 BTP 的<em>设计选择</em>，并非 bug。
     */
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

    /**
     * 释放鼠标右键输入。**只清 vanilla {@code keyUse}**。
     *
     * <p><b>不要在这里清 TaCZ 的 {@code AimKey}</b>：{@link #restoreAim} 依赖
     * {@code AimKey.setDown(true)} 让 TaCZ 的 {@code onAimHoldingPreInput}
     * 保持瞄准。若本方法清掉它，{@link #restoreAim} 会被下个 tick 的 TaCZ
     * 逻辑抵消，导致"退出据枪后自动开镜"失效。
     */
    public static void releaseUseInput() {
        Minecraft.getInstance().options.keyUse.setDown(false);
    }

    private static void suppressAimIfTilting(LocalPlayer player) {
        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (op.isAim()) {
            op.aim(false);
        }
        if (AimKey.AIM_KEY.isDown()) {
            AimKey.AIM_KEY.setDown(false);
        }
    }

    private static void sendTiltStateToServer(boolean state) {
        if (Minecraft.getInstance().player == null) return;
        if (!ClientNetworkHelper.isServerPresent()) return;
        NetworkHandler.CHANNEL.sendToServer(
                new ServerboundTiltPacket(state, (float) BTPConfig.tiltSpreadMultiplier)
        );
    }

    // ============================================================
    // 模式映射与分发
    // ============================================================

    private static TiltMode modeFor(TiltTriggerMode trigger) {
        return switch (trigger) {
            case DEFAULT -> DefaultTiltMode.INSTANCE;
            case LONG_PRESS -> LongPressTiltMode.INSTANCE;
            case ZOOM_STAGE -> ZoomStageTiltMode.INSTANCE;
            case NONE -> null;
        };
    }

    private static TiltMode determineMode() {
        return modeFor(BTPConfig.tiltTriggerMode);
    }

    private static TiltMode ensureMode() {
        if (activeMode == null) {
            activeMode = determineMode();
        }
        return activeMode;
    }

    public static boolean tryHandleZoomAsTilt() {
        if (ensureMode() != ZoomStageTiltMode.INSTANCE) return false;
        return ZoomStageTiltMode.INSTANCE.tryHandleZoomAsTilt();
    }

    // ============================================================
    // Hold 键处理（跨模式基础能力）
    // ============================================================

    /**
     * 处理 Hold 键输入。
     *
     * <p><b>类型契约</b>：{@code HOLD_TILT_KEY} 可以是键盘（{@code KEYSYM}）或鼠标
     * （{@code MOUSE}）绑定。本方法用 {@code isMouseEvent} 参数区分事件来源，
     * 并校验绑定类型与事件类型一致——避免"键盘 keyCode 恰好等于鼠标 button 号"
     * 这类隐式碰撞。虽然当前两个码空间不重叠（键盘 32~348 / 鼠标 0~7），
     * 但类型校验让语义从"碰巧对"变成"设计对"，防止未来引入新输入类型时回归。
     *
     * <p><b>返回值语义</b>：<b>只有真正进入或退出 hold 状态时才返回 true</b>。
     * 调用方（{@link #onMouseInput}）在返回 true 时会 cancel 事件。若在"匹配键
     * 但不触发 hold"的场景（空手 / GUI 打开 / 重复 PRESS / 已退出后的 RELEASE）
     * 也返回 true，会误吃鼠标事件，导致绑鼠标右键时右键的正常功能
     * （吃东西、交互、GUI 点击）全部失效。
     *
     * <p><b>click 队列清理</b>：键匹配后立即 {@code while (consumeClick())} 清空
     * {@code HOLD_TILT_KEY} 的 click 计数。这些 click 对 BTP 无意义（hold 状态用
     * {@code holdActive} 跟踪，不依赖 KeyMapping 的 clickCount），清空避免堆积到
     * 后续 tick 被 vanilla {@code handleKeybinds} 意外处理。
     *
     * @param isMouseEvent 当前事件是否来自 {@code InputEvent.MouseButton}
     * @return true 表示本次事件已进入/退出 hold 状态，调用方应 cancel 事件
     */
    private static boolean handleHoldKey(Minecraft mc, LocalPlayer player,
                                         int keyOrButton, int action, boolean isMouseEvent) {
        // 由 BTPConfig.enableHoldTilt 控制是否响应 HOLD_TILT_KEY 绑定。
        // 绑定本身不变——KeyMapping 支持键盘和鼠标，玩家在控制设置里自行绑定。
        if (!BTPConfig.enableHoldTilt) return false;

        // 类型校验：绑定类型必须与事件来源一致。
        InputConstants.Key bound = HOLD_TILT_KEY.getKey();
        boolean boundIsMouse = bound.getType() == InputConstants.Type.MOUSE;
        if (boundIsMouse != isMouseEvent) return false;

        // 键码校验。
        if (keyOrButton != bound.getValue()) return false;

        // 只响应 PRESS / RELEASE（REPEAT 是 OS 按键重复，对 hold 状态无意义）。
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return false;

        // 键匹配。清空 click 队列，避免累积到后续 tick。
        while (HOLD_TILT_KEY.consumeClick()) {}

        // 空手：不进入新 hold；若已在 hold 中（物品被切走），退出 hold 并消费本次事件。
        if (!isHoldingGun(player)) {
            if (holdActive) {
                holdActive = false;
                exitTilt();
                return true;
            }
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            // GUI 打开时不进入 hold，也不消费——让 GUI 正常处理鼠标点击。
            if (mc.screen != null) return false;
            if (!holdActive) {
                holdActive = true;
                enterTilt(true);
                return true;
            }
            // 已在 hold 中收到重复 PRESS（keyboard auto-repeat / 鼠标抖动），不消费。
            return false;
        }

        // RELEASE
        if (holdActive) {
            holdActive = false;
            exitTilt();
            TiltMode mode = ensureMode();
            if (mode != null) {
                mode.onHoldKeyReleased(player);
            }
            return true;
        }
        // 从未进入 hold（比如进入前就松开了），不消费。
        return false;
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

        if (handleHoldKey(mc, player, event.getKey(), event.getAction(), false)) return;

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

        if (handleHoldKey(mc, player, event.getButton(), event.getAction(), true)) {
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

        // 防御性清理：玩家在 hold 按住期间把 enableHoldTilt 改成 false，
        // 后续不会再有匹配的 RELEASE 事件到达 handleHoldKey，
        // holdActive 会卡住。这里主动结束 hold 状态。
        if (!BTPConfig.enableHoldTilt && holdActive) {
            holdActive = false;
            exitTilt();
            TiltMode mode = ensureMode();
            if (mode != null) {
                mode.onHoldKeyReleased(player);
            }
        }

        TiltMode desiredMode = determineMode();

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

        if (tilting) {
            suppressAimIfTilting(player);
        }

        if (tilting && !isHoldingGun(player)) {
            forceReset();
        }
    }
}
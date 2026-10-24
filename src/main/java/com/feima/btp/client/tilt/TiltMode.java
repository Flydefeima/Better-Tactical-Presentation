package com.feima.btp.client.tilt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 一种据枪触发策略。
 *
 * <p>实现类只负责"何时请求进入/退出据枪"，通过 {@link TiltController} 的
 * {@code enterTilt} / {@code exitTilt} 来改变全局状态。
 *
 * <p>每个 Mode 独立持有自己的输入状态（按键、定时器等），Mode 之间互斥。
 * 由配置 {@code BTPConfig.tiltTriggerMode} 决定当前激活哪个 Mode。
 *
 * <p><b>Hold 键</b>由 {@link TiltController} 直接管理，不属于任何 Mode。
 * Mode 通过 {@link TiltController#isHoldActive()} 判断当前是否被 Hold 键接管；
 * Hold 键释放时 {@link #onHoldKeyReleased(LocalPlayer)} 会被调用，让 Mode
 * 重新同步输入状态。
 */
public interface TiltMode {

    /** 模式的唯一标识（用于调试 / 日志）。 */
    String id();

    /**
     * 处理键盘事件。
     *
     * <p>调用前提：不在 GUI 中，且玩家已存在。
     * 键盘事件无法被 cancel，实现类通过 {@code KeyMapping.consumeClick()} 清理队列。
     */
    void onKeyInput(Minecraft mc, LocalPlayer player, int keyCode, int action);

    /**
     * 处理鼠标按键事件。
     *
     * <p>调用前提：不在 GUI 中，且玩家已存在。
     *
     * @return true 表示事件已被消费，{@code TiltController} 会 cancel 该事件
     */
    boolean onMouseInput(Minecraft mc, LocalPlayer player, int button, int action);

    /**
     * 每 tick 的状态维护：GUI 打开、物品切换、按键队列清理等。
     *
     * <p>调用前提：玩家已存在（可能在 GUI 中）。
     */
    void onClientTick(Minecraft mc, LocalPlayer player);

    /**
     * 清空所有内部状态，不改变 {@link TiltController} 的据枪状态。
     * 用于模式切换、玩家断开等场景。
     */
    void reset();

    /**
     * Hold 键释放后调用。
     *
     * <p>此时 {@link TiltController#isHoldActive()} 已经是 false，
     * 据枪状态也已经 {@code exitTilt}。Mode 应检查右键是否仍按着，
     * 并据此重新进入据枪或保持中立。
     */
    default void onHoldKeyReleased(LocalPlayer player) {}
}
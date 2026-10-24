package com.feima.btp.client.tilt;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;
import com.feima.btp.util.InputHelper;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.feima.btp.client.BTPEventHandler.HOLD_TILT_KEY;
import static com.feima.btp.client.BTPEventHandler.TOGGLE_TILT_KEY;

/**
 * 右键长按触发模式。
 *
 * <p>语义：
 * <ul>
 *   <li>右键短按 → 切换瞄准开关</li>
 *   <li>右键长按超过 {@code longPressThreshold} 毫秒 → 进入据枪；松开退出</li>
 *   <li>B 键禁用</li>
 * </ul>
 *
 * <p>Hold 键由 {@link TiltController} 独立处理，本类不参与。
 */
public final class LongPressTiltMode implements TiltMode {

    public static final LongPressTiltMode INSTANCE = new LongPressTiltMode();

    private boolean rightPressed = false;
    private boolean rightLongPressTiltTriggered = false;
    private boolean wasAimBeforePress = false;

    private ItemStack lastMainHand = ItemStack.EMPTY;
    private int lastSelectedSlot = -1;
    private boolean initialized = false;

    // volatile：SCHEDULER 线程会在阈值极短时于 schedule() 返回前观察到该字段
    private volatile ScheduledFuture<?> tiltTimerTask = null;

    private LongPressTiltMode() {}

    @Override
    public String id() {
        return "long_press";
    }

    // ============================================================
    // 事件处理
    // ============================================================

    @Override
    public void onKeyInput(Minecraft mc, LocalPlayer player, int keyCode, int action) {
        // B 键在长按模式下禁用
    }

    @Override
    public boolean onMouseInput(Minecraft mc, LocalPlayer player, int button, int action) {
        // Hold 键激活期间，右键不接管
        if (TiltController.isHoldActive()) return false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        if (!TiltController.isHoldingGun(player)) {
            if (rightPressed) {
                forceStopAllActions(player);
            }
            rightPressed = false;
            cancelTiltTimer();
            rightLongPressTiltTriggered = false;
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            wasAimBeforePress = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
            rightPressed = true;
            rightLongPressTiltTriggered = false;
            TiltController.exitTilt();

            cancelTiltTimer();

            int threshold = BTPConfig.longPressThreshold;
            tiltTimerTask = TiltController.SCHEDULER.schedule(() -> {
                Minecraft.getInstance().execute(() -> {
                    ScheduledFuture<?> task = tiltTimerTask;
                    if (task == null || task.isCancelled()
                            || !rightPressed || rightLongPressTiltTriggered) {
                        return;
                    }
                    LocalPlayer currentPlayer = Minecraft.getInstance().player;
                    if (TiltController.isHoldingGun(currentPlayer)) {
                        TiltController.cancelAim(currentPlayer);
                        TiltController.enterTilt(true);
                        rightLongPressTiltTriggered = true;

                        if (BTPConfig.showLongPressTiltMessages) {
                            BTPTipsHelper.sendTiltOnMessage(currentPlayer);
                        }
                    }
                });
            }, threshold, TimeUnit.MILLISECONDS);

            return true;
        }

        if (action == GLFW.GLFW_RELEASE) {
            rightPressed = false;
            cancelTiltTimer();

            if (rightLongPressTiltTriggered) {
                TiltController.exitTilt();
                rightLongPressTiltTriggered = false;
            } else {
                boolean targetAim = !wasAimBeforePress;
                if (targetAim) {
                    TiltController.restoreAim(player);
                } else {
                    TiltController.cancelAim(player);
                }
                if (BTPConfig.showLongPressTiltMessages && targetAim) {
                    BTPTipsHelper.sendAimOnMessage(player);
                }
            }

            TiltController.releaseUseInput();
            return true;
        }

        return false;
    }

    @Override
    public void onClientTick(Minecraft mc, LocalPlayer player) {
        // Hold 键激活期间跳过（但要保留定时器清理）
        if (TiltController.isHoldActive()) {
            cancelTiltTimer();
            while (TOGGLE_TILT_KEY.consumeClick()) {}
            while (HOLD_TILT_KEY.consumeClick()) {}
            return;
        }

        if (!initialized) {
            lastMainHand = player.getMainHandItem().copy();
            lastSelectedSlot = player.getInventory().selected;
            initialized = true;
        }

        if (mc.screen != null) {
            if (rightPressed || TiltController.isTilting()) {
                cancelTiltTimer();
                rightPressed = false;
                rightLongPressTiltTriggered = false;
                TiltController.exitTilt();
                TiltController.releaseUseInput();
            }
            while (TOGGLE_TILT_KEY.consumeClick()) {}
            while (HOLD_TILT_KEY.consumeClick()) {}
            return;
        }

        // 物理松开检测
        if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
            rightPressed = false;
            cancelTiltTimer();
            if (rightLongPressTiltTriggered) {
                TiltController.exitTilt();
                rightLongPressTiltTriggered = false;
            }
        }

        ItemStack currentMainHand = player.getMainHandItem();
        if (!ItemStack.matches(currentMainHand, lastMainHand)) {
            if (BTPConfig.resetToAimOnItemSwitch) {
                if (isRealItemSwitch(player, currentMainHand)) {
                    forceStopAllActions(player);
                }
            } else if (!TiltController.isHoldingGun(player) && rightPressed) {
                forceStopAllActions(player);
            }
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_TILT_KEY.consumeClick()) {}
        while (HOLD_TILT_KEY.consumeClick()) {}
    }

    @Override
    public void reset() {
        cancelTiltTimer();
        rightPressed = false;
        rightLongPressTiltTriggered = false;
        wasAimBeforePress = false;
        lastMainHand = ItemStack.EMPTY;
        lastSelectedSlot = -1;
        initialized = false;
    }

    @Override
    public void onHoldKeyReleased(LocalPlayer player) {
        rightPressed = InputHelper.isPhysicalRightPressed();
        cancelTiltTimer();
        if (rightPressed) {
            // 右键仍按着——视为"长按据枪"已生效，保持据枪
            rightLongPressTiltTriggered = true;
            if (TiltController.isHoldingGun(player)) {
                TiltController.enterTilt(false);
            }
        } else {
            rightLongPressTiltTriggered = false;
        }
    }

    // ============================================================
    // 内部逻辑
    // ============================================================

    private void cancelTiltTimer() {
        ScheduledFuture<?> task = tiltTimerTask;
        if (task != null && !task.isDone()) {
            task.cancel(false);
            tiltTimerTask = null;
        }
    }

    private boolean isRealItemSwitch(LocalPlayer player, ItemStack currentMainHand) {
        int selected = player.getInventory().selected;
        if (selected != lastSelectedSlot) {
            lastSelectedSlot = selected;
            return true;
        }
        return currentMainHand.getItem() != lastMainHand.getItem();
    }

    private void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        TiltController.cancelAim(player);
        rightPressed = false;
        cancelTiltTimer();
        rightLongPressTiltTriggered = false;
        wasAimBeforePress = false;
        TiltController.releaseUseInput();
        TiltController.forceReset();
    }
}
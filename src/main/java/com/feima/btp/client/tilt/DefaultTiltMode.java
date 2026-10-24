package com.feima.btp.client.tilt;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;
import com.feima.btp.util.InputHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import static com.feima.btp.client.BTPEventHandler.HOLD_TILT_KEY;
import static com.feima.btp.client.BTPEventHandler.TOGGLE_TILT_KEY;

/**
 * 默认触发模式：B 键 toggle + 右键控制。
 *
 * <p>状态说明：
 * <ul>
 *   <li>{@code mode = 0}：右键 = 瞄准（原版 TaCZ 行为）</li>
 *   <li>{@code mode = 1}：右键 = 据枪</li>
 * </ul>
 *
 * <p>Hold 键由 {@link TiltController} 独立处理，本类不参与。
 */
public final class DefaultTiltMode implements TiltMode {

    public static final DefaultTiltMode INSTANCE = new DefaultTiltMode();

    private int mode = 0;
    private boolean rightPressed = false;
    private boolean suppressRightClick = false;

    private ItemStack lastMainHand = ItemStack.EMPTY;
    private int lastSelectedSlot = -1;
    private boolean initialized = false;

    private DefaultTiltMode() {}

    @Override
    public String id() {
        return "default";
    }

    // ============================================================
    // 事件处理
    // ============================================================

    @Override
    public void onKeyInput(Minecraft mc, LocalPlayer player, int keyCode, int action) {
        // B 键：仅 release 时切换
        if (keyCode != TOGGLE_TILT_KEY.getKey().getValue()) return;
        if (action != GLFW.GLFW_RELEASE) return;
        handleToggleKey(player);
    }

    @Override
    public boolean onMouseInput(Minecraft mc, LocalPlayer player, int button, int action) {
        // Hold 键激活期间，右键不接管（交给 TaCZ 处理普通瞄准）
        if (TiltController.isHoldActive()) return false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        // 抑制右键：吞掉事件直到物理释放
        if (suppressRightClick) {
            if (action == GLFW.GLFW_RELEASE) {
                suppressRightClick = false;
                return false;
            }
            TiltController.releaseUseInput();
            return true;
        }

        // 非持枪：清理残留
        if (!TiltController.isHoldingGun(player)) {
            if (rightPressed) {
                forceStopAllActions(player);
                return true;
            }
            rightPressed = false;
            return false;
        }

        // mode = 0：右键完全交给 TaCZ
        if (mode == 0) {
            rightPressed = action == GLFW.GLFW_PRESS;
            return false;
        }

        // mode = 1：右键控制据枪
        rightPressed = action == GLFW.GLFW_PRESS;
        if (rightPressed) {
            TiltController.enterTilt(true);
        } else {
            TiltController.exitTilt();
        }
        return true;
    }

    @Override
    public void onClientTick(Minecraft mc, LocalPlayer player) {
        // Hold 键激活期间跳过
        if (TiltController.isHoldActive()) {
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
                rightPressed = false;
                TiltController.exitTilt();
                suppressRightClick = false;
                TiltController.releaseUseInput();
            }
            while (TOGGLE_TILT_KEY.consumeClick()) {}
            while (HOLD_TILT_KEY.consumeClick()) {}
            return;
        }

        if (suppressRightClick) {
            TiltController.releaseUseInput();
            if (!InputHelper.isPhysicalRightPressed()) {
                suppressRightClick = false;
            }
        }

        ItemStack currentMainHand = player.getMainHandItem();
        if (!ItemStack.matches(currentMainHand, lastMainHand)) {
            if (BTPConfig.resetToAimOnItemSwitch) {
                if (isRealItemSwitch(player, currentMainHand)) {
                    TiltController.exitTilt();
                    mode = 0;
                    if (rightPressed) {
                        if (TiltController.isHoldingGun(player)) {
                            TiltController.restoreAim(player);
                        } else {
                            forceStopAllActions(player);
                        }
                    }
                }
            } else if (!TiltController.isHoldingGun(player) && rightPressed) {
                forceStopAllActions(player);
            }
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_TILT_KEY.consumeClick()) {}
        while (HOLD_TILT_KEY.consumeClick()) {}

        if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
            TiltController.exitTilt();
            rightPressed = false;
            TiltController.releaseUseInput();
            suppressRightClick = false;
        }
    }

    @Override
    public void reset() {
        mode = 0;
        rightPressed = false;
        suppressRightClick = false;
        lastMainHand = ItemStack.EMPTY;
        lastSelectedSlot = -1;
        initialized = false;
    }

    @Override
    public void onHoldKeyReleased(LocalPlayer player) {
        rightPressed = InputHelper.isPhysicalRightPressed();
        if (rightPressed && mode == 1 && TiltController.isHoldingGun(player)) {
            // 右键仍按着，mode = 1 → 保持据枪
            TiltController.enterTilt(false);
        }
        // mode = 0：让 TaCZ 处理
    }

    // ============================================================
    // 内部逻辑
    // ============================================================

    private void handleToggleKey(LocalPlayer player) {
        if (!TiltController.isHoldingGun(player)) return;

        int newMode = 1 - mode;

        if (BTPConfig.interruptOnToggle) {
            forceStopAllActions(player);
            mode = newMode;
            BTPTipsHelper.sendModeSwitchMessage(player, newMode == 1);
            return;
        }

        if (rightPressed) {
            if (mode == 0 && newMode == 1) {
                // 右键仍物理按着，从「瞄准模式」切到「据枪模式」：
                // 进入据枪，同时设置 suppressRightClick。
                // 否则下一 tick TaCZ 检测到物理右键按着会重新开启瞄准，
                // 与 BTP 的据枪状态叠加。
                TiltController.enterTilt(true);
                suppressRightClick = true;
            } else if (mode == 1 && newMode == 0) {
                TiltController.exitTilt();
                TiltController.restoreAim(player);
            }
        }

        mode = newMode;
        TiltController.releaseUseInput();
        BTPTipsHelper.sendModeSwitchMessage(player, newMode == 1);
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
        TiltController.releaseUseInput();
        suppressRightClick = true;
        TiltController.exitTilt();
    }
}
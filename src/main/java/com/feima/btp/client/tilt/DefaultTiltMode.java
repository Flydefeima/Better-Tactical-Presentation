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
 *
 * <p><b>关于 {@code forceStopAllActions}</b>：调用 {@link TiltController#forceReset()}
 * 而非 {@link TiltController#exitTilt()}。后者在 {@code tilting==false} 时直接 return，
 * 如果 TaCZ:Labs 快照存在但据枪状态已异常（比如 suppress 成功但 enterTilt 中途失败），
 * 会留下准星永久消失。forceReset 无条件恢复。
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
        if (keyCode != TOGGLE_TILT_KEY.getKey().getValue()) return;
        if (action != GLFW.GLFW_RELEASE) return;
        handleToggleKey(player);
    }

    @Override
    public boolean onMouseInput(Minecraft mc, LocalPlayer player, int button, int action) {
        if (TiltController.isHoldActive()) return false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        if (suppressRightClick) {
            if (action == GLFW.GLFW_RELEASE) {
                suppressRightClick = false;
                return false;
            }
            TiltController.releaseUseInput();
            return true;
        }

        if (!TiltController.isHoldingGun(player)) {
            if (rightPressed) {
                forceStopAllActions(player);
                return true;
            }
            rightPressed = false;
            return false;
        }

        if (mode == 0) {
            rightPressed = action == GLFW.GLFW_PRESS;
            return false;
        }

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

    /**
     * Hold 键释放后，根据右键物理状态和当前 mode 恢复据枪/瞄准。
     *
     * <p><b>为什么必须显式 {@link TiltController#restoreAim}</b>：
     * {@code enterTilt} 在进入据枪时清了 {@code AimKey.AIM_KEY}。玩家一直按住
     * 右键时没有新的 PRESS 事件，vanilla 的 {@code KeyMapping} 不会自动把
     * {@code AimKey} 重新设为 {@code true}，TaCZ 的 {@code onAimHoldingPreInput}
     * 无法自动开镜。必须显式调用 {@code restoreAim} 恢复 aim 状态。
     *
     * <p>「退出据枪后自动开镜」是 BTP 的<em>设计选择</em>，非 bug。
     */
    @Override
    public void onHoldKeyReleased(LocalPlayer player) {
        rightPressed = InputHelper.isPhysicalRightPressed();
        if (!rightPressed) return;
        if (!TiltController.isHoldingGun(player)) return;

        if (mode == 1) {
            TiltController.enterTilt(false);
        } else {
            TiltController.restoreAim(player);
        }
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
        TiltController.forceReset();
    }
}
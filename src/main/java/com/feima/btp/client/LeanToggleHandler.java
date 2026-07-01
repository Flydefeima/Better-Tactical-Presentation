package com.feima.btp.client;

import com.feima.btp.BTPMod;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.InputHelper;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.feima.btp.client.ClientModEvents.TOGGLE_LEAN_KEY;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LeanToggleHandler {

    private static int mode = 0;
    private static boolean rightPressed = false;
    private static ItemStack lastMainHand = ItemStack.EMPTY;
    private static boolean suppressRightClick = false;
    private static boolean isLeaning = false;

    // ---------- 切换键长按冷却 ----------
    private static long keyPressTime = 0L;
    private static long cooldownUntil = 0L;
    private static boolean hasShownCooldownMessage = false;
    private static boolean longPressTriggered = false;

    // ---------- 右键长按据枪模式 ----------
    private static long rightPressTime = 0L;
    private static boolean rightLongPressTriggered = false;
    private static boolean wasAimBeforePress = false;

    // ---------- 辅助方法 ----------
    private static boolean isCoolingDown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    public static boolean isLeaning() {
        LocalPlayer player = Minecraft.getInstance().player;
        return isLeaning && rightPressed && isHoldingGun(player);
    }

    private static boolean isHoldingGun(LocalPlayer player) {
        if (player == null) return false;
        return IGun.mainHandHoldGun(player);
    }

    // ---------- 核心动作控制 ----------
    private static void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        isLeaning = false;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        rightPressed = false;
        rightLongPressTriggered = false;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        suppressRightClick = true;
    }

    private static void applyAimMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isHoldingGun(player)) return;

        isLeaning = false;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);

        if (enable) {
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
            AimKey.AIM_KEY.setDown(true);
        }
    }

    private static void applyLeanMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isHoldingGun(player)) return;

        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);

        isLeaning = enable;
    }

    private static void toggleMode(LocalPlayer player) {
        if (!isHoldingGun(player)) return;

        applyAimMode(false);
        applyLeanMode(false);

        mode = 1 - mode;

        if (!BTPConfig.interruptOnToggle && rightPressed) {
            if (mode == 0) {
                applyAimMode(true);
            } else {
                applyLeanMode(true);
            }
        }

        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);

        Component message = Component.translatable(
                mode == 0 ? "message.btp.mode.aim" : "message.btp.mode.lean"
        );
        player.displayClientMessage(message, true);
    }

    // ---------- 切换键按键事件 ----------
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (BTPConfig.enableLongPressLean) {
            return;
        }

        if (event.getKey() != TOGGLE_LEAN_KEY.getKey().getValue()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            keyPressTime = System.currentTimeMillis();
            longPressTriggered = false;
            hasShownCooldownMessage = false;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (longPressTriggered) {
                longPressTriggered = false;
                return;
            }

            if (isCoolingDown()) {
                if (!hasShownCooldownMessage) {
                    long remaining = (cooldownUntil - System.currentTimeMillis()) / 1000 + 1;
                    Component cooldownMsg = Component.translatable("message.btp.cooldown", remaining);
                    player.displayClientMessage(cooldownMsg, true);
                    hasShownCooldownMessage = true;
                }
                return;
            }

            toggleMode(player);
        }
    }

    // ---------- 鼠标右键事件 ----------
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (BTPConfig.enableLongPressLean) {
            if (!isHoldingGun(player)) {
                if (rightPressed) {
                    forceStopAllActions(player);
                }
                rightPressed = false;
                rightLongPressTriggered = false;
                return;
            }

            event.setCanceled(true);

            if (event.getAction() == GLFW.GLFW_PRESS) {
                wasAimBeforePress = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
                rightPressTime = System.currentTimeMillis();
                rightLongPressTriggered = false;
                rightPressed = true;
                applyLeanMode(false);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                rightPressed = false;

                if (rightLongPressTriggered) {
                    applyLeanMode(false);
                    rightLongPressTriggered = false;
                } else {
                    applyAimMode(!wasAimBeforePress);
                    if (BTPConfig.showLongPressLeanMessages) {
                        Component msg = Component.translatable(
                                wasAimBeforePress ? "message.btp.aim_off" : "message.btp.aim_on"
                        );
                        player.displayClientMessage(msg, true);
                    }
                }

                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
            }
            return;
        }

        // ---------- 原有逻辑（enableLongPressLean = false） ----------
        if (suppressRightClick) {
            if (event.getAction() == GLFW.GLFW_RELEASE) {
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
                return;
            } else {
                event.setCanceled(true);
                mc.options.keyUse.setDown(false);
                return;
            }
        }

        if (!isHoldingGun(player)) {
            if (rightPressed) {
                event.setCanceled(true);
                forceStopAllActions(player);
                return;
            } else {
                rightPressed = false;
                return;
            }
        }

        if (mode == 0) {
            rightPressed = event.getAction() == GLFW.GLFW_PRESS;
            return;
        }

        event.setCanceled(true);
        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        rightPressed = pressed;
        applyLeanMode(pressed);
    }

    // ---------- 客户端 Tick ----------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 打断疾跑：根据配置决定是否打断
        if (BTPConfig.breakSprint && isLeaning && rightPressed && isHoldingGun(player)) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }

        // ----- 切换键模式的长按检测 -----
        if (!BTPConfig.enableLongPressLean) {
            if (TOGGLE_LEAN_KEY.isDown() && keyPressTime > 0 && !longPressTriggered && !isCoolingDown()) {
                long pressDuration = System.currentTimeMillis() - keyPressTime;
                if (pressDuration >= BTPConfig.longPressThreshold) {
                    long cooldown = BTPConfig.cooldownDuration;
                    if (cooldown > 0) {
                        if (BTPConfig.longPressTriggersToggle) {
                            toggleMode(player);
                        }
                        cooldownUntil = System.currentTimeMillis() + cooldown;
                        longPressTriggered = true;
                        Component cooldownStartMsg = Component.translatable("message.btp.cooldown_start", cooldown / 1000);
                        player.displayClientMessage(cooldownStartMsg, true);
                    }
                }
            }
        }

        // ----- 右键长按据枪模式 -----
        if (BTPConfig.enableLongPressLean) {
            if (rightPressed && !rightLongPressTriggered && isHoldingGun(player)) {
                long pressDuration = System.currentTimeMillis() - rightPressTime;
                if (pressDuration >= BTPConfig.longPressThreshold) {
                    applyAimMode(false);
                    applyLeanMode(true);
                    rightLongPressTriggered = true;
                    if (BTPConfig.showLongPressLeanMessages) {
                        Component msg = Component.translatable("message.btp.lean_on");
                        player.displayClientMessage(msg, true);
                    }
                }
            }

            ItemStack currentMainHand = player.getMainHandItem();
            boolean itemChanged = !ItemStack.matches(currentMainHand, lastMainHand);
            if (itemChanged) {
                if (!isHoldingGun(player) && rightPressed) {
                    forceStopAllActions(player);
                }
                lastMainHand = currentMainHand.copy();
            }

            while (TOGGLE_LEAN_KEY.consumeClick()) {
                // 禁用切换键
            }
            return;
        }

        // ----- 原有 Tick 逻辑（enableLongPressLean = false） -----
        if (suppressRightClick) {
            mc.options.keyUse.setDown(false);
            if (!InputHelper.isPhysicalRightPressed()) {
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
            }
        }

        ItemStack currentMainHand = player.getMainHandItem();
        boolean itemChanged = !ItemStack.matches(currentMainHand, lastMainHand);
        if (itemChanged) {
            if (!isHoldingGun(player) && rightPressed) {
                forceStopAllActions(player);
            }
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_LEAN_KEY.consumeClick()) {
            // 切换由 Key 事件驱动
        }
    }
}
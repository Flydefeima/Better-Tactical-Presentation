package com.feima.btp.client;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.client.compat.TaczLabsCompatHelper;
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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.feima.btp.client.ClientModEvents.TOGGLE_LEAN_KEY;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LeanToggleHandler {

    // ---------- 状态枚举 ----------
    public enum LeanMode { AIM, LEAN }
    public enum LeanState { IDLE, AIMING, LEANING, LONG_PRESS_WAITING }

    // ---------- 状态变量 ----------
    private static LeanMode currentMode = LeanMode.AIM;
    private static LeanState currentState = LeanState.IDLE;
    private static volatile boolean rightPressed = false;
    private static volatile boolean suppressRightClick = false;
    private static volatile boolean rightLongPressTriggered = false;
    private static volatile boolean wasAimBeforePress = false;

    private static ItemStack lastMainHand = ItemStack.EMPTY;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BTP-LeanTimer");
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

    private static ScheduledFuture<?> leanTimerTask = null;

    // ---------- 对外接口 ----------
    public static boolean isLeaning() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isHoldingGun(player)) return false;
        if (BTPConfig.enableLongPressLean) {
            return currentState == LeanState.LEANING && rightPressed;
        }
        return currentState == LeanState.LEANING && rightPressed && currentMode == LeanMode.LEAN;
    }

    public static LeanState getCurrentState() { return currentState; }
    public static LeanMode getCurrentMode() { return currentMode; }

    private static boolean isHoldingGun(LocalPlayer player) {
        return player != null && IGun.mainHandHoldGun(player);
    }

    private static void cancelLeanTimer() {
        if (leanTimerTask != null && !leanTimerTask.isDone()) {
            leanTimerTask.cancel(false);
            leanTimerTask = null;
        }
    }

    // ---------- 核心状态转换 ----------
    private static void resetAllStates() {
        cancelLeanTimer();
        currentMode = LeanMode.AIM;
        currentState = LeanState.IDLE;
        rightPressed = false;
        suppressRightClick = false;
        rightLongPressTriggered = false;
        lastMainHand = ItemStack.EMPTY;
        TaczLabsCompatHelper.setCrosshairEnabled(true);
        BTPLog.LOGGER.debug("All states reset.");
    }

    private static void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        cancelLeanTimer();
        rightPressed = false;
        rightLongPressTriggered = false;
        suppressRightClick = true;
        currentState = LeanState.IDLE;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        TaczLabsCompatHelper.setCrosshairEnabled(true);
        BTPLog.LOGGER.debug("Force stopped all actions.");
    }

    private static void applyAimMode(boolean enable, LocalPlayer player) {
        if (!isHoldingGun(player)) return;
        boolean currentAim = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        if (enable && !currentAim) {
            currentState = LeanState.AIMING;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
            AimKey.AIM_KEY.setDown(true);
            TaczLabsCompatHelper.setCrosshairEnabled(true);
        } else if (!enable && currentAim) {
            currentState = LeanState.IDLE;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
            AimKey.AIM_KEY.setDown(false);
            TaczLabsCompatHelper.setCrosshairEnabled(true);
        }
    }

    private static void applyLeanMode(boolean enable, LocalPlayer player) {
        if (!isHoldingGun(player)) return;
        if (BTPConfig.breakSprint) {
            player.setSprinting(false);
        }
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        currentState = enable ? LeanState.LEANING : LeanState.IDLE;
        TaczLabsCompatHelper.setCrosshairEnabled(!enable);
    }

    private static void toggleMode(LocalPlayer player) {
        if (!isHoldingGun(player)) return;
        LeanMode newMode = (currentMode == LeanMode.AIM) ? LeanMode.LEAN : LeanMode.AIM;

        if (BTPConfig.interruptOnToggle) {
            forceStopAllActions(player);
            currentMode = newMode;
            player.displayClientMessage(
                    Component.translatable(newMode == LeanMode.AIM ? "message.btp.mode.aim" : "message.btp.mode.lean"),
                    true
            );
            return;
        }

        if (rightPressed && isHoldingGun(player)) {
            if (currentMode == LeanMode.AIM && newMode == LeanMode.LEAN) {
                applyAimMode(false, player);
                applyLeanMode(true, player);
            } else if (currentMode == LeanMode.LEAN && newMode == LeanMode.AIM) {
                applyLeanMode(false, player);
                applyAimMode(true, player);
            }
        }
        currentMode = newMode;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        player.displayClientMessage(
                Component.translatable(newMode == LeanMode.AIM ? "message.btp.mode.aim" : "message.btp.mode.lean"),
                true
        );
    }

    // ---------- 事件处理 ----------
    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAllStates();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
    
        if (BTPConfig.enableLongPressLean) {
            return;
        }
        if (event.getKey() != TOGGLE_LEAN_KEY.getKey().getValue()) return;
        if (event.getAction() != GLFW.GLFW_RELEASE) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        toggleMode(player);
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (BTPConfig.enableLongPressLean) {
            handleLongPressLean(event, player);
            return;
        }

        // 标准模式
        handleStandardLean(event, player);
    }

    private static void handleStandardLean(InputEvent.MouseButton.Pre event, LocalPlayer player) {
        int action = event.getAction();
        Minecraft mc = Minecraft.getInstance();

        if (suppressRightClick) {
            if (action == GLFW.GLFW_RELEASE) {
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
            }
            return;
        }

        if (currentMode == LeanMode.AIM) {
            rightPressed = (action == GLFW.GLFW_PRESS);
            return;
        }

        // LeanMode.LEAN
        event.setCanceled(true);
        rightPressed = (action == GLFW.GLFW_PRESS);
        applyLeanMode(rightPressed, player);
    }

    private static void handleLongPressLean(InputEvent.MouseButton.Pre event, LocalPlayer player) {
        int action = event.getAction();
        Minecraft mc = Minecraft.getInstance();

        if (!isHoldingGun(player)) {
            if (rightPressed) forceStopAllActions(player);
            rightPressed = false;
            cancelLeanTimer();
            rightLongPressTriggered = false;
            return;
        }

        event.setCanceled(true);

        if (action == GLFW.GLFW_PRESS) {
            wasAimBeforePress = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
            rightPressed = true;
            rightLongPressTriggered = false;
            applyLeanMode(false, player);
            currentState = LeanState.LONG_PRESS_WAITING;

            // 长按开始时打断疾跑
            if (BTPConfig.breakSprint) {
                player.setSprinting(false);
            }

            cancelLeanTimer();
            int threshold = BTPConfig.longPressThreshold;
            leanTimerTask = SCHEDULER.schedule(() -> {
                Minecraft.getInstance().execute(() -> {
                    Minecraft mc2 = Minecraft.getInstance();
                    if (mc2.player == null) return;
                    LocalPlayer currentPlayer = mc2.player;
                    if (rightPressed && !rightLongPressTriggered && isHoldingGun(currentPlayer)) {
                        applyAimMode(false, currentPlayer);
                        applyLeanMode(true, currentPlayer);
                        rightLongPressTriggered = true;
                        if (BTPConfig.showLongPressLeanMessages) {
                            currentPlayer.displayClientMessage(
                                    Component.translatable("message.btp.lean_on"),
                                    true
                            );
                        }
                    }
                });
            }, threshold, TimeUnit.MILLISECONDS);

        } else if (action == GLFW.GLFW_RELEASE) {
            rightPressed = false;
            cancelLeanTimer();

            if (rightLongPressTriggered) {
                applyLeanMode(false, player);
                rightLongPressTriggered = false;
            } else {
                applyAimMode(!wasAimBeforePress, player);
                if (BTPConfig.showLongPressLeanMessages && !wasAimBeforePress) {
                    player.displayClientMessage(Component.translatable("message.btp.aim_on"), true);
                }
            }

            InputHelper.clearRightMouseButton();
            mc.options.keyUse.setDown(false);
        }
    }

    // ---------- 统一状态清理 Tick ----------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // GUI 打开时退出据枪
        if (mc.screen != null) {
            boolean wasLeaning = currentState == LeanState.LEANING;
            if (wasLeaning || rightPressed) {
                cancelLeanTimer();
                rightPressed = false;
                rightLongPressTriggered = false;
                suppressRightClick = false;
                if (currentState == LeanState.LEANING || wasLeaning) {
                    applyLeanMode(false, player);
                }
                if (currentState == LeanState.AIMING) {
                    applyAimMode(true, player);
                }
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
                TaczLabsCompatHelper.setCrosshairEnabled(true);
                BTPLog.LOGGER.debug("GUI opened, state cleaned.");
                return;
            }
        }

        // 长按模式下的状态修正
        if (BTPConfig.enableLongPressLean) {
            // 打断疾跑（长按等待期间也生效）
            if (rightPressed && BTPConfig.breakSprint && isHoldingGun(player)) {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
                if (mc.options.keySprint.isDown()) {
                    mc.options.keySprint.setDown(false);
                }
            }

            if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
                if (currentState == LeanState.LEANING) {
                    applyLeanMode(false, player);
                }
                rightPressed = false;
                rightLongPressTriggered = false;
                cancelLeanTimer();
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
                suppressRightClick = false;
                TaczLabsCompatHelper.setCrosshairEnabled(true);
            }

            // 物品切换检测
            ItemStack currentMainHand = player.getMainHandItem();
            if (!ItemStack.matches(currentMainHand, lastMainHand)) {
                if (!isHoldingGun(player) && rightPressed) {
                    forceStopAllActions(player);
                }
                if (BTPConfig.resetToAimOnItemSwitch && isHoldingGun(player)) {
                    applyLeanMode(false, player);
                    currentMode = LeanMode.AIM;
                }
                lastMainHand = currentMainHand.copy();
            }

            while (TOGGLE_LEAN_KEY.consumeClick()) { /* consume */ }
            return;
        }

        // 标准模式
        if (suppressRightClick) {
            mc.options.keyUse.setDown(false);
            if (!InputHelper.isPhysicalRightPressed()) {
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
            }
        }

        if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
            if (currentState == LeanState.LEANING) {
                applyLeanMode(false, player);
            }
            rightPressed = false;
            rightLongPressTriggered = false;
            cancelLeanTimer();
            InputHelper.clearRightMouseButton();
            mc.options.keyUse.setDown(false);
            suppressRightClick = false;
            TaczLabsCompatHelper.setCrosshairEnabled(true);
        }

        // 打断疾跑
        if (BTPConfig.breakSprint && isLeaning() && isHoldingGun(player)) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            if (mc.options.keySprint.isDown()) {
                mc.options.keySprint.setDown(false);
            }
        }

        // 物品切换检测
        ItemStack currentMainHand = player.getMainHandItem();
        if (!ItemStack.matches(currentMainHand, lastMainHand)) {
            if (!isHoldingGun(player) && rightPressed) {
                forceStopAllActions(player);
            }
            if (BTPConfig.resetToAimOnItemSwitch && isHoldingGun(player)) {
                applyLeanMode(false, player);
                currentMode = LeanMode.AIM;
            }
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_LEAN_KEY.consumeClick()) { /* consume */ }
    }
}
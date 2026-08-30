package com.feima.btp.client;

import com.feima.btp.BTPMod;
import com.feima.btp.client.compat.TaCZLabsCompat;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.network.NetworkHandler;
import com.feima.btp.network.ServerboundTiltPacket;
import com.feima.btp.util.BTPTipsHelper;
import com.feima.btp.util.InputHelper;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

import static com.feima.btp.client.BTPEventHandler.HOLD_TILT_KEY;
import static com.feima.btp.client.BTPEventHandler.TOGGLE_TILT_KEY;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TiltToggleHandler {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
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

    private static volatile int mode = 0;
    private static volatile boolean rightPressed = false;
    private static volatile boolean isTilting = false;
    private static volatile boolean suppressRightClick = false;
    private static volatile boolean rightLongPressTiltTriggered = false;
    private static volatile boolean wasAimBeforePress = false;
    private static volatile boolean holdTiltActive = false; // 保持不变

    // ===== 暴露 holdTiltActive 的 getter =====
    public static boolean isHoldTiltActive() {
        return holdTiltActive;
    }

    // ===== 新增：判断是否按住据枪键且开镜中 =====
    public static boolean isHoldingTiltAndAiming() {
        if (!holdTiltActive) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        return operator.isAim() || AimKey.AIM_KEY.isDown();
    }

    private static volatile boolean wasAimingBeforeTilt = false;

    public static boolean getWasAimingBeforeTilt() {
        return wasAimingBeforeTilt;
    }

    public static void resetWasAimingBeforeTilt() {
        wasAimingBeforeTilt = false;
    }

    public static boolean isTilting() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isHoldingGun(player)) return false;

        if (holdTiltActive) {
            return true;
        }

        if (BTPConfig.enableLongPressTilt) {
            return isTilting && rightPressed;
        }

        return isTilting && rightPressed && mode == 1;
    }

    private static boolean isHoldingGun(LocalPlayer player) {
        if (player == null) return false;
        return IGun.mainHandHoldGun(player);
    }

    private static boolean isRealItemSwitch(LocalPlayer player, ItemStack currentMainHand) {
        int selected = player.getInventory().selected;
        if (selected != lastSelectedSlot) {
            lastSelectedSlot = selected;
            return true;
        }
        return currentMainHand.getItem() != lastMainHand.getItem();
    }

    private static void cancelTiltTimer() {
        if (tiltTimerTask != null && !tiltTimerTask.isDone()) {
            tiltTimerTask.cancel(false);
            tiltTimerTask = null;
        }
    }

    private static void resetAllStates() {
        cancelTiltTimer();
        mode = 0;
        rightPressed = false;
        isTilting = false;
        suppressRightClick = false;
        rightLongPressTiltTriggered = false;
        holdTiltActive = false;
        lastMainHand = ItemStack.EMPTY;
        lastSelectedSlot = -1;
        TaCZLabsCompat.setCrosshairEnabled(true);
        wasAimingBeforeTilt = false;
    }

    private static void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        isTilting = false;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        rightPressed = false;
        cancelTiltTimer();
        rightLongPressTiltTriggered = false;
        holdTiltActive = false;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        suppressRightClick = true;
        TaCZLabsCompat.setCrosshairEnabled(true);
        sendTiltStateToServer(false);
        wasAimingBeforeTilt = false;
    }

    private static void applyAimMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!isHoldingGun(player)) return;

        boolean currentAim = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();

        if (enable && !currentAim) {
            isTilting = false;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
            AimKey.AIM_KEY.setDown(true);
            TaCZLabsCompat.setCrosshairEnabled(true);
            sendTiltStateToServer(false);
        } else if (!enable && currentAim) {
            isTilting = false;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
            AimKey.AIM_KEY.setDown(false);
            TaCZLabsCompat.setCrosshairEnabled(true);
            sendTiltStateToServer(false);
        }
    }

    private static void applyTiltMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!isHoldingGun(player)) return;

        if (enable) {
            wasAimingBeforeTilt = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();

            if (BTPConfig.breakSprint) {
                player.setSprinting(false);
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
            AimKey.AIM_KEY.setDown(false);
            isTilting = true;
            TaCZLabsCompat.setCrosshairEnabled(false);
            sendTiltStateToServer(true);
        } else {
            isTilting = false;
            TaCZLabsCompat.setCrosshairEnabled(true);
            sendTiltStateToServer(false);
        }
    }

    private static void sendTiltStateToServer(boolean tilting) {
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.CHANNEL.sendToServer(new ServerboundTiltPacket(tilting));
        }
    }

    private static void toggleMode(LocalPlayer player) {
        if (!isHoldingGun(player)) return;

        int newMode = 1 - mode;

        if (BTPConfig.interruptOnToggle) {
            forceStopAllActions(player);
            mode = newMode;
            BTPTipsHelper.sendModeSwitchMessage(player, newMode == 1);
            return;
        }

        if (rightPressed && isHoldingGun(player)) {
            if (mode == 0 && newMode == 1) {
                wasAimingBeforeTilt = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
                
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
                AimKey.AIM_KEY.setDown(false);
                if (BTPConfig.breakSprint) {
                    player.setSprinting(false);
                }
                isTilting = true;
                TaCZLabsCompat.setCrosshairEnabled(false);
                sendTiltStateToServer(true);
            } else if (mode == 1 && newMode == 0) {
                isTilting = false;
                TaCZLabsCompat.setCrosshairEnabled(true);
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
                AimKey.AIM_KEY.setDown(true);
                sendTiltStateToServer(false);
            }
        }

        mode = newMode;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        BTPTipsHelper.sendModeSwitchMessage(player, newMode == 1);
    }

    private static void handleHoldTiltKey(int action, LocalPlayer player) {
        if (!isHoldingGun(player)) {
            if (holdTiltActive) {
                holdTiltActive = false;
                if (!rightPressed) {
                    applyTiltMode(false);
                }
            }
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            holdTiltActive = true;
            applyTiltMode(true);
        } else if (action == GLFW.GLFW_RELEASE) {
            holdTiltActive = false;

            if (rightPressed) {
                if (mode == 0 && !(BTPConfig.enableLongPressTilt && rightLongPressTiltTriggered)) {
                    applyAimMode(true);
                }
            } else {
                if (isTilting) {
                    applyTiltMode(false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAllStates();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.screen != null) return;

        int keyCode = event.getKey();
        int action = event.getAction();

        if (keyCode == HOLD_TILT_KEY.getKey().getValue()) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                handleHoldTiltKey(action, player);
                HOLD_TILT_KEY.consumeClick();
            }
            return;
        }

        if (BTPConfig.enableLongPressTilt) return;
        if (keyCode != TOGGLE_TILT_KEY.getKey().getValue()) return;
        if (action != GLFW.GLFW_RELEASE) return;
        toggleMode(player);
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        int button = event.getButton();
        int action = event.getAction();

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (button == HOLD_TILT_KEY.getKey().getValue()) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                handleHoldTiltKey(action, player);
                event.setCanceled(true);
                HOLD_TILT_KEY.consumeClick();
            }
            return;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (BTPConfig.enableLongPressTilt) {
            if (!isHoldingGun(player)) {
                if (rightPressed) forceStopAllActions(player);
                rightPressed = false;
                cancelTiltTimer();
                rightLongPressTiltTriggered = false;
                return;
            }

            event.setCanceled(true);

            if (event.getAction() == GLFW.GLFW_PRESS) {
                wasAimBeforePress = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
                rightPressed = true;
                rightLongPressTiltTriggered = false;
                applyTiltMode(false);

                cancelTiltTimer();

                int threshold = BTPConfig.longPressThreshold;
                tiltTimerTask = SCHEDULER.schedule(() -> {
                    Minecraft.getInstance().execute(() -> {
                        if (tiltTimerTask == null || tiltTimerTask.isCancelled() || !rightPressed || rightLongPressTiltTriggered) {
                            return;
                        }
                        LocalPlayer currentPlayer = Minecraft.getInstance().player;
                        if (isHoldingGun(currentPlayer)) {
                            applyAimMode(false);
                            applyTiltMode(true);
                            rightLongPressTiltTriggered = true;

                            if (BTPConfig.showLongPressTiltMessages) {
                                BTPTipsHelper.sendTiltOnMessage(currentPlayer);
                            }
                        }
                    });
                }, threshold, TimeUnit.MILLISECONDS);

            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                rightPressed = false;
                cancelTiltTimer();

                if (rightLongPressTiltTriggered) {
                    applyTiltMode(false);
                    rightLongPressTiltTriggered = false;
                } else {
                    applyAimMode(!wasAimBeforePress);
                    if (BTPConfig.showLongPressTiltMessages && !wasAimBeforePress) {
                        BTPTipsHelper.sendAimOnMessage(player);
                    }
                }

                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
            }
            return;
        }

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
        rightPressed = event.getAction() == GLFW.GLFW_PRESS;
        applyTiltMode(rightPressed);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.screen != null) {
            if (holdTiltActive || isTilting || rightPressed) {
                cancelTiltTimer();
                rightPressed = false;
                rightLongPressTiltTriggered = false;
                holdTiltActive = false;
                applyTiltMode(false);
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
            }
        }

        if (BTPConfig.enableLongPressTilt) {
            ItemStack currentMainHand = player.getMainHandItem();
            if (!ItemStack.matches(currentMainHand, lastMainHand)) {
                if (BTPConfig.resetToAimOnItemSwitch) {
                    if (isRealItemSwitch(player, currentMainHand)) {
                        forceStopAllActions(player);
                    }
                } else if (!isHoldingGun(player) && rightPressed) {
                    forceStopAllActions(player);
                }
                lastMainHand = currentMainHand.copy();
            }

            while (TOGGLE_TILT_KEY.consumeClick()) {}
            while (HOLD_TILT_KEY.consumeClick()) {}
            return;
        }

        if (suppressRightClick) {
            mc.options.keyUse.setDown(false);
            if (!InputHelper.isPhysicalRightPressed()) {
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
            }
        }

        ItemStack currentMainHand = player.getMainHandItem();
        if (!ItemStack.matches(currentMainHand, lastMainHand)) {
            if (BTPConfig.resetToAimOnItemSwitch) {
                if (isRealItemSwitch(player, currentMainHand)) {
                    applyTiltMode(false);
                    mode = 0;
                    if (rightPressed) {
                        if (isHoldingGun(player)) {
                            applyAimMode(true);
                        } else {
                            forceStopAllActions(player);
                        }
                    }
                }
            } else if (!isHoldingGun(player) && rightPressed) {
                forceStopAllActions(player);
            }
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_TILT_KEY.consumeClick()) {}
        while (HOLD_TILT_KEY.consumeClick()) {}

        if (mc.screen == null) {
            if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
                if (isTilting) {
                    applyTiltMode(false);
                }
                rightPressed = false;
                rightLongPressTiltTriggered = false;
                cancelTiltTimer();
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
                suppressRightClick = false;
                TaCZLabsCompat.setCrosshairEnabled(true);
            }
        }
    }

    // ========== 原有私有变量 ==========
    private static ItemStack lastMainHand = ItemStack.EMPTY;
    private static int lastSelectedSlot = -1;
    private static ScheduledFuture<?> tiltTimerTask = null;
}
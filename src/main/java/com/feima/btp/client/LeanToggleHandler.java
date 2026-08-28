package com.feima.btp.client;

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

import static com.feima.btp.client.ClientModEvents.HOLD_LEAN_KEY;
import static com.feima.btp.client.ClientModEvents.TOGGLE_LEAN_KEY;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LeanToggleHandler {

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

    private static volatile int mode = 0;
    private static volatile boolean rightPressed = false;
    private static volatile boolean isLeaning = false;
    private static volatile boolean suppressRightClick = false;
    private static volatile boolean rightLongPressTriggered = false;
    private static volatile boolean wasAimBeforePress = false;
    private static volatile boolean holdLeanActive = false;

    private static ItemStack lastMainHand = ItemStack.EMPTY;
    private static int lastSelectedSlot = -1;
    private static ScheduledFuture<?> leanTimerTask = null;

    public static boolean isLeaning() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isHoldingGun(player)) return false;

        if (holdLeanActive) {
            return true;
        }

        if (BTPConfig.enableLongPressLean) {
            return isLeaning && rightPressed;
        }

        return isLeaning && rightPressed && mode == 1;
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

    private static void cancelLeanTimer() {
        if (leanTimerTask != null && !leanTimerTask.isDone()) {
            leanTimerTask.cancel(false);
            leanTimerTask = null;
        }
    }

    private static void resetAllStates() {
        cancelLeanTimer();
        mode = 0;
        rightPressed = false;
        isLeaning = false;
        suppressRightClick = false;
        rightLongPressTriggered = false;
        holdLeanActive = false;
        lastMainHand = ItemStack.EMPTY;
        lastSelectedSlot = -1;
        TaczLabsCompatHelper.setCrosshairEnabled(true);
    }

    private static void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        isLeaning = false;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        rightPressed = false;
        cancelLeanTimer();
        rightLongPressTriggered = false;
        holdLeanActive = false;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        suppressRightClick = true;
        TaczLabsCompatHelper.setCrosshairEnabled(true);
    }

    private static void applyAimMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!isHoldingGun(player)) return;

        boolean currentAim = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();

        if (enable && !currentAim) {
            isLeaning = false;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
            AimKey.AIM_KEY.setDown(true);
            TaczLabsCompatHelper.setCrosshairEnabled(true);
        } else if (!enable && currentAim) {
            isLeaning = false;
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
            AimKey.AIM_KEY.setDown(false);
            TaczLabsCompatHelper.setCrosshairEnabled(true);
        }
    }

    private static void applyLeanMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!isHoldingGun(player)) return;

        if (enable && BTPConfig.breakSprint) {
            player.setSprinting(false);
        }
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        isLeaning = enable;
        TaczLabsCompatHelper.setCrosshairEnabled(!enable);
    }

    private static void toggleMode(LocalPlayer player) {
        if (!isHoldingGun(player)) return;

        int newMode = 1 - mode;

        if (BTPConfig.interruptOnToggle) {
            forceStopAllActions(player);
            mode = newMode;
            player.displayClientMessage(
                    Component.translatable(newMode == 0 ? "message.btp.mode.aim" : "message.btp.mode.lean"),
                    true
            );
            return;
        }

        if (rightPressed && isHoldingGun(player)) {
            if (mode == 0 && newMode == 1) {
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
                AimKey.AIM_KEY.setDown(false);
                if (BTPConfig.breakSprint) {
                    player.setSprinting(false);
                }
                isLeaning = true;
                TaczLabsCompatHelper.setCrosshairEnabled(false);
            } else if (mode == 1 && newMode == 0) {
                isLeaning = false;
                TaczLabsCompatHelper.setCrosshairEnabled(true);
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(true);
                AimKey.AIM_KEY.setDown(true);
            }
        }

        mode = newMode;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        player.displayClientMessage(
                Component.translatable(newMode == 0 ? "message.btp.mode.aim" : "message.btp.mode.lean"),
                true
        );
    }

    private static void handleHoldLeanKey(int action, LocalPlayer player) {
        if (!isHoldingGun(player)) {
            if (holdLeanActive) {
                holdLeanActive = false;
                if (!rightPressed) {
                    applyLeanMode(false);
                }
            }
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            holdLeanActive = true;
            applyLeanMode(true);
        } else if (action == GLFW.GLFW_RELEASE) {
            holdLeanActive = false;

            if (rightPressed) {
                if (mode == 0 && !(BTPConfig.enableLongPressLean && rightLongPressTriggered)) {
                    applyAimMode(true);
                }
            } else {
                if (isLeaning) {
                    applyLeanMode(false);
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

        if (keyCode == HOLD_LEAN_KEY.getKey().getValue()) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                handleHoldLeanKey(action, player);
                HOLD_LEAN_KEY.consumeClick();
            }
            return;
        }

        if (BTPConfig.enableLongPressLean) return;
        if (keyCode != TOGGLE_LEAN_KEY.getKey().getValue()) return;
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

        if (button == HOLD_LEAN_KEY.getKey().getValue()) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                handleHoldLeanKey(action, player);
                event.setCanceled(true);
                HOLD_LEAN_KEY.consumeClick();
            }
            return;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (BTPConfig.enableLongPressLean) {
            if (!isHoldingGun(player)) {
                if (rightPressed) forceStopAllActions(player);
                rightPressed = false;
                cancelLeanTimer();
                rightLongPressTriggered = false;
                return;
            }

            event.setCanceled(true);

            if (event.getAction() == GLFW.GLFW_PRESS) {
                wasAimBeforePress = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
                rightPressed = true;
                rightLongPressTriggered = false;
                applyLeanMode(false);

                cancelLeanTimer();

                int threshold = BTPConfig.longPressThreshold;
                leanTimerTask = SCHEDULER.schedule(() -> {
                    Minecraft.getInstance().execute(() -> {
                        if (leanTimerTask == null || leanTimerTask.isCancelled() || !rightPressed || rightLongPressTriggered) {
                            return;
                        }
                        LocalPlayer currentPlayer = Minecraft.getInstance().player;
                        if (isHoldingGun(currentPlayer)) {
                            applyAimMode(false);
                            applyLeanMode(true);
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

            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                rightPressed = false;
                cancelLeanTimer();

                if (rightLongPressTriggered) {
                    applyLeanMode(false);
                    rightLongPressTriggered = false;
                } else {
                    applyAimMode(!wasAimBeforePress);
                    if (BTPConfig.showLongPressLeanMessages) {
                        if (!wasAimBeforePress) {
                            player.displayClientMessage(
                                    Component.translatable("message.btp.aim_on"),
                                    true
                            );
                        }
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
        applyLeanMode(rightPressed);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.screen != null) {
            if (holdLeanActive || isLeaning || rightPressed) {
                cancelLeanTimer();
                rightPressed = false;
                rightLongPressTriggered = false;
                holdLeanActive = false;
                applyLeanMode(false);
                suppressRightClick = false;
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
            }
        }

        if (BTPConfig.enableLongPressLean) {
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

            while (TOGGLE_LEAN_KEY.consumeClick()) {}
            while (HOLD_LEAN_KEY.consumeClick()) {}
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
                    applyLeanMode(false);
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

        while (TOGGLE_LEAN_KEY.consumeClick()) {}
        while (HOLD_LEAN_KEY.consumeClick()) {}

        if (mc.screen == null) {
            if (rightPressed && !InputHelper.isPhysicalRightPressed()) {
                if (isLeaning) {
                    applyLeanMode(false);
                }
                rightPressed = false;
                rightLongPressTriggered = false;
                cancelLeanTimer();
                InputHelper.clearRightMouseButton();
                mc.options.keyUse.setDown(false);
                suppressRightClick = false;
                TaczLabsCompatHelper.setCrosshairEnabled(true);
            }
        }
    }
}
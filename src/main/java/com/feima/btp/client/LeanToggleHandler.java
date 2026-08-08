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

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BTP-LeanTimer");
        t.setDaemon(true);
        return t;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (SCHEDULER != null && !SCHEDULER.isShutdown()) {
                SCHEDULER.shutdownNow();
            }
        }));
    }

    private static int mode = 0;
    private static boolean rightPressed = false;
    private static ItemStack lastMainHand = ItemStack.EMPTY;
    private static boolean suppressRightClick = false;
    private static boolean isLeaning = false;

    private static long rightPressTime = 0L;
    private static boolean rightLongPressTriggered = false;
    private static boolean wasAimBeforePress = false;
    private static ScheduledFuture<?> leanTimerTask = null;

    public static boolean isLeaning() {
        LocalPlayer player = Minecraft.getInstance().player;
        return isLeaning && rightPressed && isHoldingGun(player);
    }

    private static boolean isHoldingGun(LocalPlayer player) {
        if (player == null) return false;
        return IGun.mainHandHoldGun(player);
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
        lastMainHand = ItemStack.EMPTY;
        TaczLabsCompatHelper.setCrosshairEnabled(true);
        BTPLog.LOGGER.info("All states reset.");
    }

    private static void forceStopAllActions(LocalPlayer player) {
        if (player == null) return;
        isLeaning = false;
        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        rightPressed = false;
        cancelLeanTimer();
        rightLongPressTriggered = false;
        InputHelper.clearRightMouseButton();
        Minecraft.getInstance().options.keyUse.setDown(false);
        suppressRightClick = true;
        TaczLabsCompatHelper.setCrosshairEnabled(true);
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
        TaczLabsCompatHelper.setCrosshairEnabled(true);
    }

    private static void applyLeanMode(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isHoldingGun(player)) return;

        IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        AimKey.AIM_KEY.setDown(false);
        isLeaning = enable;
        TaczLabsCompatHelper.setCrosshairEnabled(!enable);
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

        player.displayClientMessage(
                Component.translatable(mode == 0 ? "message.btp.mode.aim" : "message.btp.mode.lean"),
                true
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAllStates();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (BTPConfig.enableLongPressLean) return;
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
                rightPressTime = System.currentTimeMillis();
                rightPressed = true;
                rightLongPressTriggered = false;
                applyLeanMode(false);

                cancelLeanTimer();

                int threshold = BTPConfig.longPressThreshold;
                leanTimerTask = SCHEDULER.schedule(() -> {
                    Minecraft.getInstance().execute(() -> {
                        LocalPlayer currentPlayer = Minecraft.getInstance().player;
                        if (rightPressed && !rightLongPressTriggered && isHoldingGun(currentPlayer)) {
                            applyAimMode(false);
                            applyLeanMode(true);
                            rightLongPressTriggered = true;
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
                        player.displayClientMessage(
                                Component.translatable(wasAimBeforePress ? "message.btp.aim_off" : "message.btp.aim_on"),
                                true
                        );
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

        if (BTPConfig.breakSprint && isLeaning() && rightPressed && isHoldingGun(player)) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            if (mc.options.keySprint.isDown()) {
                mc.options.keySprint.setDown(false);
            }
        }

        if (BTPConfig.enableLongPressLean) {
            ItemStack currentMainHand = player.getMainHandItem();
            if (!ItemStack.matches(currentMainHand, lastMainHand)) {
                if (!isHoldingGun(player) && rightPressed) {
                    forceStopAllActions(player);
                }
                lastMainHand = currentMainHand.copy();
            }

            while (TOGGLE_LEAN_KEY.consumeClick()) {}
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
            if (!isHoldingGun(player) && rightPressed) forceStopAllActions(player);
            lastMainHand = currentMainHand.copy();
        }

        while (TOGGLE_LEAN_KEY.consumeClick()) {}
    }
}
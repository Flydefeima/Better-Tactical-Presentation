package com.feima.btp.client.compat;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.config.BTPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TaczLabsCompatHelper {

    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;
    private static boolean messageSent = false;  // BTP FIX: 只发送一次提示
    private static Object enableCrosshairFieldValue;
    private static Method setMethod;

    private static void initReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            Class<?> hudConfigClass = Class.forName("com.txttext.taczlabs.config.fileconfig.HudConfig");
            Field field = hudConfigClass.getDeclaredField("ENABLE_TL_CROSSHAIR");
            field.setAccessible(true);
            enableCrosshairFieldValue = field.get(null);
            setMethod = enableCrosshairFieldValue.getClass().getMethod("set", Object.class);
            reflectionReady = true;
            BTPLog.LOGGER.info("TaCZ:Labs reflection initialized.");
        } catch (Exception e) {
            reflectionFailed = true;
            BTPLog.LOGGER.warn("TaCZ:Labs reflection permanently disabled: {}", e.getMessage());
            // BTP FIX: 发送一次聊天栏提示
            if (!messageSent) {
                Minecraft.getInstance().execute(() -> {
                    Player player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(
                            Component.translatable("message.btp.taczlabs_reflection_failed"),
                            false
                        );
                        messageSent = true;
                    }
                });
            }
        }
    }

    public static void setCrosshairEnabled(boolean enabled) {
        if (!BTPMod.isTaczLabsLoaded || !BTPConfig.compatTaczLabsCrosshair) {
            return;
        }
        initReflection();
        if (!reflectionReady) return;
        try {
            setMethod.invoke(enableCrosshairFieldValue, enabled);
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to set crosshair: {}", e.getMessage());
        }
    }
}
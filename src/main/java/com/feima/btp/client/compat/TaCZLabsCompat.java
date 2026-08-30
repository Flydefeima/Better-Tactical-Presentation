package com.feima.btp.client.compat;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TaCZLabsCompat {

    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;
    // ===== 移除：private static boolean messageSent = false; =====
    private static Object enableCrosshairFieldValue;
    private static Method setMethod;

    // 重置反射状态，用于每次进入世界时重新尝试
    public static void reset() {
        reflectionReady = false;
        reflectionFailed = false;
        // ===== 移除：messageSent = false; =====
        enableCrosshairFieldValue = null;
        setMethod = null;
    }

    private static void initReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            Class<?> hudConfigClass = Class.forName("com.txttext.taczlabs.config.fileconfig.HudConfig");
            Field field = hudConfigClass.getDeclaredField("ENABLE_TL_CROSSHAIR");
            field.setAccessible(true);
            enableCrosshairFieldValue = field.get(null);
            setMethod = enableCrosshairFieldValue.getClass().getMethod("set", Object.class);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            BTPLog.LOGGER.warn("TaCZ:Labs reflection failed: {}", e.getMessage());
            // ===== 直接调用 BTPTipsHelper，由它统一去重 =====
            Minecraft.getInstance().execute(() -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    BTPTipsHelper.sendTaczLabsReflectionFailedMessage(player);
                }
            });
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
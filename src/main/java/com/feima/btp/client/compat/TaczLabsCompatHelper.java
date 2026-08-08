package com.feima.btp.client.compat;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.config.BTPConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TaczLabsCompatHelper {

    private static boolean reflectionReady = false;
    private static Object enableCrosshairFieldValue;
    private static Method setMethod;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            Class<?> hudConfigClass = Class.forName("com.txttext.taczlabs.config.fileconfig.HudConfig");
            Field field = hudConfigClass.getDeclaredField("ENABLE_TL_CROSSHAIR");
            field.setAccessible(true);
            enableCrosshairFieldValue = field.get(null);
            setMethod = enableCrosshairFieldValue.getClass().getMethod("set", Object.class);
            reflectionReady = true;
            BTPLog.LOGGER.info("TaCZ:Labs reflection initialized.");
        } catch (Exception e) {
            BTPLog.LOGGER.warn("TaCZ:Labs reflection failed: {}", e.getMessage());
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
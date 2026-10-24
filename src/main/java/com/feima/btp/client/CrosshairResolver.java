package com.feima.btp.client;

import com.feima.btp.BTPLog;
import com.feima.btp.config.BTPConfig;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;

import java.util.Locale;

/**
 * 把 {@link BTPConfig#tacticalCrosshair} 的字符串解析成 {@link CrosshairType}。
 *
 * <p>刻意从 BTPConfig 里拆出来——BTPConfig 会被服务端加载，
 * 而 CrosshairType 会间接引用 TaCZ 的客户端类（{@code GunMod}）。
 */
public final class CrosshairResolver {
    private CrosshairResolver() {}

    private static CrosshairType cachedType = null;
    private static String cachedConfig = null;

    public static CrosshairType getTacticalCrosshairType() {
        String current = BTPConfig.tacticalCrosshair;
        if (current == null || current.trim().isEmpty()) {
            return null;
        }
        if (!current.equals(cachedConfig)) {
            try {
                cachedType = CrosshairType.valueOf(current.trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException e) {
                BTPLog.LOGGER.warn("Invalid crosshair type '{}', falling back to no override.", current);
                cachedType = null;
            }
            cachedConfig = current;
        }
        return cachedType;
    }

    public static void clearCache() {
        cachedConfig = null;
        cachedType = null;
    }
}
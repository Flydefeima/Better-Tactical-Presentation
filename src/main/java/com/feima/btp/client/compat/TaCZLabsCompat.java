package com.feima.btp.client.compat;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * TaCZ:Labs 兼容。
 *
 * <p>TaCZ:Labs 没有提供公开 API 或事件，BTP 无法在渲染阶段独立决定准星显隐，
 * 只能通过反射读写它的 {@code HudConfig.ENABLE_TL_CROSSHAIR} 配置项。
 *
 * <p>由于 Forge 的 {@code ForgeConfigSpec.BooleanValue.set()} 会立即写盘，
 * 必须遵循「进入据枪时快照原值 → 关闭 → 退出据枪时恢复原值」的原则，
 * 避免永久覆盖玩家的配置。
 */
public class TaCZLabsCompat {

    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;
    private static Object enableCrosshairFieldValue;
    private static Method getMethod;
    private static Method setMethod;

    // ===== 快照：记录玩家进入据枪前的原始配置值，退出时恢复 =====
    private static boolean hasSnapshot = false;
    private static boolean snapshotValue = false;

    /** 进入世界时重置所有状态（反射缓存 + 快照）。 */
    public static void reset() {
        reflectionReady = false;
        reflectionFailed = false;
        enableCrosshairFieldValue = null;
        getMethod = null;
        setMethod = null;
        hasSnapshot = false;
        snapshotValue = false;
    }

    private static void initReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            Class<?> hudConfigClass = Class.forName("com.txttext.taczlabs.config.fileconfig.HudConfig");
            Field field = hudConfigClass.getDeclaredField("ENABLE_TL_CROSSHAIR");
            field.setAccessible(true);
            enableCrosshairFieldValue = field.get(null);
            Class<?> valueClass = enableCrosshairFieldValue.getClass();
            getMethod = valueClass.getMethod("get");
            setMethod = valueClass.getMethod("set", Object.class);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            BTPLog.LOGGER.warn("TaCZ:Labs reflection failed: {}", e.getMessage());
            BTPTipsHelper.sendTaczLabsReflectionFailedMessage();
        }
    }

    /**
     * 进入据枪：记录玩家当前配置值，然后关闭准星。
     * 若已有快照（重复调用），只关闭，不覆盖快照。
     */
    public static void suppressCrosshair() {
        if (!BTPMod.isTaczLabsLoaded || !BTPConfig.compatTaczLabsCrosshair) return;
        initReflection();
        if (!reflectionReady) return;
        try {
            if (!hasSnapshot) {
                snapshotValue = (Boolean) getMethod.invoke(enableCrosshairFieldValue);
                hasSnapshot = true;
            }
            setMethod.invoke(enableCrosshairFieldValue, false);
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to suppress TaCZ:Labs crosshair: {}", e.getMessage());
        }
    }

    /**
     * 退出据枪：把配置恢复成玩家原始值，并清除快照。
     * 没有快照时什么都不做——避免误改玩家配置。
     */
    public static void restoreCrosshair() {
        if (!BTPMod.isTaczLabsLoaded || !BTPConfig.compatTaczLabsCrosshair) return;
        if (!hasSnapshot) return;
        initReflection();
        if (!reflectionReady) {
            hasSnapshot = false;
            return;
        }
        try {
            setMethod.invoke(enableCrosshairFieldValue, snapshotValue);
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to restore TaCZ:Labs crosshair: {}", e.getMessage());
        } finally {
            hasSnapshot = false;
        }
    }
}
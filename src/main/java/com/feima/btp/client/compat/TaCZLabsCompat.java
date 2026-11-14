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
 * <p><b>快照-恢复策略</b>：进入据枪时快照原值 → 关闭 → 退出据枪时恢复原值。
 * 即使 Forge 1.20.1 的 {@code ConfigValue.set()} 只更新内存不写盘，快照-恢复
 * 仍然必要——否则玩家在据枪中触发 {@code ModConfigEvent.Reloading}（手动编辑
 * 配置文件 / Cloth Config 保存）时，{@code reloadConfig()} 会把
 * {@code compatTaczLabsCrosshair} 读成被 set() 改过的值，导致永久覆盖。
 *
 * <p><b>restore 不受 {@code compatTaczLabsCrosshair} 控制</b>：该配置只决定
 * 「进入时是否 suppress」，不应该影响「退出时是否 restore」——否则
 * 玩家在据枪中把配置 toggle 掉，快照永远无法恢复。
 */
public class TaCZLabsCompat {

    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;
    private static Object enableCrosshairFieldValue;
    private static Method getMethod;
    private static Method setMethod;

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
     *
     * <p><b>不检查 {@code compatTaczLabsCrosshair}</b>：该配置只控制 suppress，
     * 不控制 restore。否则玩家在据枪中 toggle 掉配置，快照永远恢复不了。
     */
    public static void restoreCrosshair() {
        if (!BTPMod.isTaczLabsLoaded) return;
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
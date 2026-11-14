package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一管理 BTP 模组的聊天栏反馈信息。
 * - 普通操作提示（模式切换、据枪开关、倍率切换）：无前缀，显示在快捷栏上方
 * - 错误/警告信息（加载失败、反射失败、缺失动画）：带 §6[BTP] §r 前缀，显示在聊天栏
 *
 * <p><b>线程契约</b>：{@link #reportErrorOnce} 可能从网络线程 / SCHEDULER 线程调用。
 * 集合操作在 {@code synchronized(LOCK)} 内完成；{@code Player.displayClientMessage}
 * 必须通过 {@code Minecraft.execute} 派发到主线程——这是 Minecraft 客户端对
 * "修改世界状态" API 的基本要求。
 */
public final class BTPTipsHelper {

    public static final String MSG_MODE_AIM = "message.btp.mode.aim";
    public static final String MSG_MODE_TILT = "message.btp.mode.tilt";
    public static final String MSG_TILT_ON = "message.btp.tilt_on";
    public static final String MSG_AIM_ON = "message.btp.aim_on";
    public static final String MSG_ZOOM_HINT = "message.btp.zoom_hint";

    public static final String MSG_ANIM_LOAD_FAILED = "message.btp.independent_anim_load_failed";
    public static final String MSG_TACZLABS_REFLECTION_FAILED = "message.btp.taczlabs_reflection_failed";
    public static final String MSG_MISSING_TILT_ANIMATION = "message.btp.missing_tilt_animation";
    public static final String MSG_TACZ_VERSION_MISMATCH = "message.btp.tacz_version_mismatch";

    private static final String ERROR_PREFIX = "§6[BTP] §r";

    private static final List<String> pendingErrors = new ArrayList<>();
    private static final Set<String> reportedErrors = new HashSet<>();
    private static final Object LOCK = new Object();

    private BTPTipsHelper() {}

    // ============================================================
    // 普通消息（无前缀，快捷栏上方）
    // ============================================================

    public static void sendModeSwitchMessage(Player player, boolean isTiltMode) {
        if (player == null) return;
        String key = isTiltMode ? MSG_MODE_TILT : MSG_MODE_AIM;
        player.displayClientMessage(Component.translatable(key), true);
    }

    public static void sendTiltOnMessage(Player player) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable(MSG_TILT_ON), true);
    }

    public static void sendAimOnMessage(Player player) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable(MSG_AIM_ON), true);
    }

    public static void sendZoomHintMessage(Player player, float zoomValue) {
        if (player == null) return;
        String text = formatZoomValue(zoomValue);
        player.displayClientMessage(
                Component.translatable(MSG_ZOOM_HINT, text),
                true
        );
    }

    private static String formatZoomValue(float zoom) {
        if (Math.abs(zoom - Math.round(zoom)) < 0.01f) {
            return String.valueOf(Math.round(zoom));
        }
        return String.format(Locale.US, "%.1f", zoom);
    }

    // ============================================================
    // 错误消息（带前缀，聊天栏）
    // ============================================================

    /**
     * 上报一条错误（每个 key 只显示一次）。可从任意线程调用。
     *
     * <p>如果当前有玩家（主线程），立即派发到主线程显示；否则放入
     * pendingErrors 队列，由 {@link #sendPendingErrors(Player)} 在
     * 玩家进入世界后补发。
     */
    private static void reportErrorOnce(String messageKey) {
        if (messageKey == null || messageKey.isEmpty()) return;

        synchronized (LOCK) {
            if (!reportedErrors.add(messageKey)) return;
            pendingErrors.add(messageKey);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            Player player = mc.player;
            if (player == null) return;
            synchronized (LOCK) {
                if (!pendingErrors.remove(messageKey)) return;
            }
            player.displayClientMessage(
                    Component.literal(ERROR_PREFIX).append(Component.translatable(messageKey)),
                    false
            );
        });
    }

    public static void sendAnimLoadFailedMessage() {
        reportErrorOnce(MSG_ANIM_LOAD_FAILED);
    }

    public static void sendTaczLabsReflectionFailedMessage() {
        reportErrorOnce(MSG_TACZLABS_REFLECTION_FAILED);
    }

    public static void sendMissingTiltAnimationMessage() {
        reportErrorOnce(MSG_MISSING_TILT_ANIMATION);
    }

    public static void sendTaczVersionMismatchMessage() {
        reportErrorOnce(MSG_TACZ_VERSION_MISMATCH);
    }

    public static void sendPendingErrors(Player player) {
        if (player == null) return;
        synchronized (LOCK) {
            if (pendingErrors.isEmpty()) return;
            for (String key : pendingErrors) {
                player.displayClientMessage(
                        Component.literal(ERROR_PREFIX).append(Component.translatable(key)),
                        false
                );
            }
            pendingErrors.clear();
        }
    }

    public static void clearPendingErrors() {
        synchronized (LOCK) {
            pendingErrors.clear();
            reportedErrors.clear();
        }
    }

    public static boolean hasPendingErrors() {
        synchronized (LOCK) {
            return !pendingErrors.isEmpty();
        }
    }
}
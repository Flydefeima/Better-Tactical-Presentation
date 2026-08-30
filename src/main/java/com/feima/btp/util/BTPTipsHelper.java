package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一管理 BTP 模组的聊天栏反馈信息。
 * - 普通操作提示（模式切换、据枪开关）：无前缀，显示在快捷栏上方
 * - 错误/警告信息（加载失败、反射失败、缺失动画）：带 §6[BTP] §r 前缀，显示在聊天栏
 */
public final class BTPTipsHelper {

    // ========== 普通消息键（无前缀，显示在快捷栏上方） ==========
    public static final String MSG_MODE_AIM = "message.btp.mode.aim";
    public static final String MSG_MODE_TILT = "message.btp.mode.tilt";
    public static final String MSG_TILT_ON = "message.btp.tilt_on";
    public static final String MSG_AIM_ON = "message.btp.aim_on";

    // ========== 错误消息键（带前缀，显示在聊天栏） ==========
    public static final String MSG_ANIM_LOAD_FAILED = "message.btp.independent_anim_load_failed";
    public static final String MSG_TACZLABS_REFLECTION_FAILED = "message.btp.taczlabs_reflection_failed";
    public static final String MSG_MISSING_TILT_ANIMATION = "message.btp.missing_tilt_animation";

    private static final String ERROR_PREFIX = "§6[BTP] §r";

    // 错误消息收集器（用于登录延迟发送）
    private static final List<String> pendingErrors = new ArrayList<>();

    // ===== 新增：当前会话已报告的错误，防止重复 =====
    private static final Set<String> reportedErrors = new HashSet<>();

    private BTPTipsHelper() {}

    // ============================================================
    // 普通消息（无前缀，显示在快捷栏上方）
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

    // ============================================================
    // 错误消息（带前缀，显示在聊天栏）
    // ============================================================

    /**
     * 发送错误消息，同一错误在同一个游戏会话中只报告一次。
     * - 若玩家已登录，立即发送到聊天栏
     * - 若玩家未登录，暂存队列，等待登录时统一发送
     */
    private static void reportErrorOnce(String messageKey) {
        if (messageKey == null || messageKey.isEmpty()) return;

        synchronized (reportedErrors) {
            // 同一会话已经报告过，直接跳过
            if (reportedErrors.contains(messageKey)) {
                return;
            }
            // 标记为已报告
            reportedErrors.add(messageKey);
        }

        synchronized (pendingErrors) {
            // 已有相同错误则跳过
            if (pendingErrors.contains(messageKey)) {
                return;
            }
            pendingErrors.add(messageKey);

            // 若玩家已在世界中，立即发送此条错误到聊天栏
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                        Component.literal(ERROR_PREFIX).append(Component.translatable(messageKey)),
                        false
                );
                pendingErrors.remove(messageKey);
            }
        }
    }

    public static void sendAnimLoadFailedMessage(Player player) {
        reportErrorOnce(MSG_ANIM_LOAD_FAILED);
    }

    public static void sendTaczLabsReflectionFailedMessage(Player player) {
        reportErrorOnce(MSG_TACZLABS_REFLECTION_FAILED);
    }

    public static void sendMissingTiltAnimationMessage(Player player) {
        reportErrorOnce(MSG_MISSING_TILT_ANIMATION);
    }

    /**
     * 在玩家进入世界时调用，将所有累积的错误消息一次性输出到聊天栏。
     * 发送后清空列表。
     */
    public static void sendPendingErrors(Player player) {
        if (player == null) return;
        synchronized (pendingErrors) {
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

    /**
     * 清空所有待发送的错误消息（不发送）。
     * 通常在玩家登出时调用，防止跨世界残留。
     */
    public static void clearPendingErrors() {
        synchronized (pendingErrors) {
            pendingErrors.clear();
        }
        // ===== 新增：清空已报告标记，允许下次进世界重新报告 =====
        synchronized (reportedErrors) {
            reportedErrors.clear();
        }
    }

    /**
     * 检查是否有待发送的错误消息。
     */
    public static boolean hasPendingErrors() {
        synchronized (pendingErrors) {
            return !pendingErrors.isEmpty();
        }
    }

    // ===== 新增：重置已报告标记（仅用于测试或特殊场景） =====
    public static void resetReportedErrors() {
        synchronized (reportedErrors) {
            reportedErrors.clear();
        }
    }
}
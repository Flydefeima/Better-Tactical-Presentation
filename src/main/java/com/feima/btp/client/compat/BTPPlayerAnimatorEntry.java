package com.feima.btp.client.compat;

import com.feima.btp.client.animation.BTPAnimator;
import net.minecraftforge.common.MinecraftForge;

/**
 * PlayerAnimator 相关功能的延迟入口。
 *
 * <p>这个类直接引用 {@link BTPAnimator}，而 BTPAnimator 又引用
 * {@code dev.kosmx.playerAnim.*}——所以本类必须在确认 PlayerAnimator
 * 已安装之后才允许被加载。
 *
 * <p>{@code BTPMod.onLoadComplete} 在 {@code isPlayerAnimatorLoaded} 分支里
 * 才调用 {@link #register()}，JVM 只在这一刻才加载本类，达成延迟加载，
 * 不需要 {@code Class.forName} 反射。
 */
public final class BTPPlayerAnimatorEntry {
    private BTPPlayerAnimatorEntry() {}

    public static void register() {
        BTPAnimator.registerAnimationLayers();
        MinecraftForge.EVENT_BUS.register(BTPAnimator.class);
    }
}
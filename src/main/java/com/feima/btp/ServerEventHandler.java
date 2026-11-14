package com.feima.btp;

import com.feima.btp.network.TiltStateStorage;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端 / 单人游戏内置服务器的事件订阅。
 *
 * <p><b>职责</b>：玩家登出时清理 {@link TiltStateStorage} 中该 UUID 的残留条目。
 * {@code REMOTE_TILT_STATES} / {@code SPREAD_MULTIPLIERS} 是进程级 ConcurrentHashMap，
 * 玩家离开后不清理会持续累积——大型多人服上这是真实的资源泄漏。
 *
 * <p><b>与客户端清理的关系</b>：
 * <ul>
 *   <li>本类监听 {@link PlayerEvent.PlayerLoggedOutEvent}——服务端 / 单人游戏
 *       内置服务器在玩家断开时触发，清理该玩家在服务端进程内的残留</li>
 *   <li>客户端 {@code BTPEventHandler.onPlayerLogout} 监听
 *       {@code ClientPlayerNetworkEvent.LoggingOut}，调用
 *       {@link com.feima.btp.client.TiltStateManager#clear()} 清空客户端进程的
 *       全部条目</li>
 * </ul>
 * 两者互补，互不重叠。没有本类时，服务端的 {@code REMOTE_TILT_STATES}
 * 会随玩家进出无限增长。
 *
 * <p><b>双端安全</b>：本类只引用 {@code TiltStateStorage}（network 包双端安全类），
 * 不引用任何 {@code net.minecraft.client.*}。注册在 FORGE 总线上，专用服务器
 * 和客户端都会加载，但 {@code PlayerLoggedOutEvent} 只在逻辑服务端触发。
 */
@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEventHandler {

    private ServerEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() == null) return;
        TiltStateStorage.clearPlayer(event.getEntity().getUUID());
    }
}
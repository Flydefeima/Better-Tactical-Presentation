package com.feima.btp.client.compat;

import com.feima.btp.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端专用的网络查询辅助。
 *
 * <p>刻意从 {@link NetworkHandler} 拆出——{@code NetworkHandler} 在服务端也会加载，
 * 不能引用任何 {@code net.minecraft.client.*} 类。虽然 HotSpot 的方法验证是 lazy 的，
 * 服务端不调用本类就不会崩，但把客户端逻辑放在客户端包更符合类加载契约。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    /**
     * 检查当前连接的服务端是否也装了 BTP。
     *
     * @return 服务端存在 {@code btp:main} 通道时返回 true；未连接或服务端无 BTP 返回 false。
     */
    public static boolean isServerPresent() {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;
        Connection connection = listener.getConnection();
        if (connection == null) return false;
        return NetworkHandler.CHANNEL.isRemotePresent(connection);
    }
}
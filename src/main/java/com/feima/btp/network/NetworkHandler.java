package com.feima.btp.network;

import com.feima.btp.BTPMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 双端通用的网络通道注册。
 *
 * <p>本类不引用任何 {@code net.minecraft.client.*} 类——服务端会加载本类。
 * 客户端专用的通道存在性检查请使用
 * {@code com.feima.btp.client.compat.ClientNetworkHelper#isServerPresent()}。
 *
 * <p><b>版本兼容策略</b>：{@code mods.toml} 声明了
 * {@code displayTest="IGNORE_ALL_VERSION"}——不同 BTP 版本可以连接。
 * 为了避免"mod 列表显示可连接但网络握手失败"，此处放宽版本校验到
 * 主版本号一致（{@code "1."} 前缀）。仅当协议格式发生不兼容变更时
 * 才应提高 {@code PROTOCOL_VERSION} 的主版本号。
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1.0";
    private static final String PROTOCOL_MAJOR_PREFIX = "1.";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(BTPMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(NetworkHandler::isVersionCompatible)
            .serverAcceptedVersions(NetworkHandler::isVersionCompatible)
            .simpleChannel();

    private static int id = 0;
    private static boolean registered = false;

    private static boolean isVersionCompatible(String remote) {
        return remote != null && remote.startsWith(PROTOCOL_MAJOR_PREFIX);
    }

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(id++, ServerboundTiltPacket.class,
                ServerboundTiltPacket::encode, ServerboundTiltPacket::decode, ServerboundTiltPacket::handle);
        CHANNEL.registerMessage(id++, ClientboundTiltPacket.class,
                ClientboundTiltPacket::encode, ClientboundTiltPacket::decode, ClientboundTiltPacket::handle);
    }
}
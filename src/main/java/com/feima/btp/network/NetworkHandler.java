package com.feima.btp.network;

import com.feima.btp.BTPMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(BTPMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, ServerboundTiltPacket.class,
                ServerboundTiltPacket::encode, ServerboundTiltPacket::decode, ServerboundTiltPacket::handle);
        CHANNEL.registerMessage(id++, ClientboundTiltPacket.class,
                ClientboundTiltPacket::encode, ClientboundTiltPacket::decode, ClientboundTiltPacket::handle);
    }

    /**
     * 检查当前连接的服务端是否也装了 BTP。
     *
     * <p>BTP 是纯客户端模组，服务端可能没有安装。此时客户端的通道虽然注册了，
     * 但服务端不会注册对应的 {@code btp:main} 通道，发送前先调用此方法判断，
     * 避免无谓的发送与日志噪音。
     *
     * @return 服务端存在 BTP 通道时返回 true；未连接或服务端无 BTP 返回 false。
     */
    public static boolean isServerPresent() {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;
        Connection connection = listener.getConnection();
        if (connection == null) return false;
        return CHANNEL.isRemotePresent(connection);
    }
}
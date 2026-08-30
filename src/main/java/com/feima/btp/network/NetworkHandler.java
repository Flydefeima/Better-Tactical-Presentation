package com.feima.btp.network;

import com.feima.btp.BTPMod;
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
}
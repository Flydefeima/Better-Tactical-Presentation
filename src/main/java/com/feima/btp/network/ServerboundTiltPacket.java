package com.feima.btp.network;

import com.feima.btp.BTPLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundTiltPacket {
    private final boolean tilting;

    public ServerboundTiltPacket(boolean tilting) {
        this.tilting = tilting;
    }

    public static void encode(ServerboundTiltPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.tilting);
    }

    public static ServerboundTiltPacket decode(FriendlyByteBuf buf) {
        return new ServerboundTiltPacket(buf.readBoolean());
    }

    public static void handle(ServerboundTiltPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                BTPLog.LOGGER.warn("Received tilt packet from null sender");
                return;
            }
            // 广播给所有追踪该玩家的客户端（包括发送者自己，但客户端会忽略自身）
            NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sender),
                new ClientboundTiltPacket(sender.getUUID(), msg.tilting)
            );
        });
        context.setPacketHandled(true);
    }
}
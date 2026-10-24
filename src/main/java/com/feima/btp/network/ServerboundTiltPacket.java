package com.feima.btp.network;

import com.feima.btp.BTPLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

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
            // 只广播给追踪该玩家的其他客户端；发送者自身的 tilt 状态
            // 由 TiltController 直接维护，无需回传。
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> sender),
                    new ClientboundTiltPacket(sender.getUUID(), msg.tilting)
            );
        });
        context.setPacketHandled(true);
    }
}
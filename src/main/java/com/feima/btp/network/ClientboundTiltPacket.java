package com.feima.btp.network;

import com.feima.btp.client.TiltStateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundTiltPacket {
    private final UUID playerId;
    private final boolean tilting;

    public ClientboundTiltPacket(UUID playerId, boolean tilting) {
        this.playerId = playerId;
        this.tilting = tilting;
    }

    public static void encode(ClientboundTiltPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.tilting);
    }

    public static ClientboundTiltPacket decode(FriendlyByteBuf buf) {
        return new ClientboundTiltPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ClientboundTiltPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // 更新远程玩家的倾斜状态
            TiltStateManager.setTiltState(msg.playerId, msg.tilting);
        });
        context.setPacketHandled(true);
    }
}
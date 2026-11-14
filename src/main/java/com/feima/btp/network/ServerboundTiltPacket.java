package com.feima.btp.network;

import com.feima.btp.BTPLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：同步玩家据枪状态 + 当前据枪散布倍率。
 *
 * <p><b>为什么倍率要一起发</b>：{@code BTPConfig.tiltSpreadMultiplier} 是 CLIENT 配置，
 * 专用服务器读不到。服务端 {@code GunSpreadMixin} 必须用玩家真实设置值，
 * 才能与客户端准星视觉（{@code CacheValueMixin}）保持一致。
 *
 * <p>倍率只在进入/退出据枪时发送（{@code TiltController.enterTilt/exitTilt} 触发）。
 * 玩家在据枪中改配置需要下一次据枪才生效——这是有意的简化，避免每 tick 发包。
 */
public class ServerboundTiltPacket {
    private final boolean tilting;
    private final float spreadMultiplier;

    public ServerboundTiltPacket(boolean tilting, float spreadMultiplier) {
        this.tilting = tilting;
        this.spreadMultiplier = spreadMultiplier;
    }

    public static void encode(ServerboundTiltPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.tilting);
        buf.writeFloat(msg.spreadMultiplier);
    }

    public static ServerboundTiltPacket decode(FriendlyByteBuf buf) {
        return new ServerboundTiltPacket(buf.readBoolean(), buf.readFloat());
    }

    public static void handle(ServerboundTiltPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                BTPLog.LOGGER.warn("Received tilt packet from null sender");
                return;
            }
            TiltStateStorage.setTiltState(sender.getUUID(), msg.tilting);
            TiltStateStorage.setSpreadMultiplier(sender.getUUID(), msg.spreadMultiplier);

            // 只广播给追踪该玩家的其他客户端；发送者自身的 tilt 状态
            // 由客户端 TiltController 直接维护（并写入本地 TiltStateStorage）。
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> sender),
                    new ClientboundTiltPacket(sender.getUUID(), msg.tilting)
            );
        });
        context.setPacketHandled(true);
    }
}
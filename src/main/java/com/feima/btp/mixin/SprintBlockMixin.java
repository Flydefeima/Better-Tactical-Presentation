package com.feima.btp.mixin;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.network.TiltStateStorage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 据枪时阻断疾跑。
 *
 * <p><b>类加载契约</b>：本 mixin 混入双端类 {@link LivingEntity}。为避免
 * 专用服务器在 {@code setSprinting} 调用时触发 {@code TiltController} 类加载
 * （该类引用 {@code Minecraft} 等客户端类），本 mixin 改用双端安全的
 * {@link TiltStateStorage}，并通过 {@link FMLEnvironment#dist} 判断是否客户端
 * ——{@code BTPConfig.breakSprint} 是 CLIENT 配置，专用服务器不读玩家设置。
 *
 * <p><b>为什么服务端不做阻断</b>：客户端阻断 sprint 后不会向服务端发
 * {@code START_SPRINTING} 包，服务端因此也不会进入 sprint。两端状态天然一致。
 * 如果服务端也读默认 true 的 {@code breakSprint} 并阻断，反而会出现
 * "客户端关掉 breakSprint → 客户端 sprint → 服务端拦截" 的撕裂。
 *
 * <p><b>TiltStateStorage 能查到本地玩家</b>：{@code TiltController.enterTilt}
 * / {@code exitTilt} 会主动把本地玩家状态写入 {@link TiltStateStorage}，
 * 因此本 mixin 无需区分本地/远程玩家。
 */
@Mixin(LivingEntity.class)
public class SprintBlockMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void btp$blockSprintWhileTilting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;
        if (!FMLEnvironment.dist.isClient()) return;
        if (!BTPConfig.breakSprint) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (TiltStateStorage.getTiltState(self.getUUID())) {
            ci.cancel();
        }
    }
}
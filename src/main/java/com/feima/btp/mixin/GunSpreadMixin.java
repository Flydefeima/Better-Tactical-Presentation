package com.feima.btp.mixin;

import com.feima.btp.network.TiltStateStorage;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在服务端权威射击路径上应用据枪散布倍率。
 *
 * <p><b>背景</b>：TaCZ 的射击由服务端执行（{@code ClientMessagePlayerShoot}
 * → 服务端 {@code IGunOperator.shoot} → {@code gunItem.shoot} →
 * {@code gunItem.doBulletSpread}），而 {@code CacheValue} 无玩家上下文，
 * 服务端无法按玩家应用倍率。
 *
 * <p><b>为什么 {@code @Mixin} 需要包含两个类</b>：
 * <ul>
 *   <li>{@link AbstractGunItem} 声明了默认的 {@code doBulletSpread}，
 *       默认实现会调用 {@code projectile.shootFromRotation(..., inaccuracy)}</li>
 *   <li>{@link ModernKineticGunItem} <b>覆写</b>了 {@code doBulletSpread}——
 *       引入 Lua 脚本支持（{@code calcSpread}），最终仍调用
 *       {@code bullet.shootFromRotation(..., inaccuracy)}</li>
 * </ul>
 *
 * <p>由于 {@code IGunOperator.shoot} 走<b>虚方法分派</b>，运行时执行的
 * 是子类 {@link ModernKineticGunItem} 的覆写版本。仅 mixin
 * {@link AbstractGunItem} 对实际枪械<b>完全不生效</b>。必须同时 mixin 子类。
 *
 * <p><b>方法签名契约</b>（TaCZ 1.2.0 两个类的签名完全一致）：
 * {@code doBulletSpread(ShooterDataHolder, ItemStack, LivingEntity, Projectile,
 * int, float processedSpeed, float inaccuracy, float pitch, float yaw)}。
 * {@code @ModifyVariable} 的 {@code ordinal = 1, argsOnly = true} 精确指向
 * 参数中的第 2 个 float（{@code inaccuracy}）。
 *
 * <p><b>已知边界</b>：
 * <ul>
 *   <li>若枪包在 {@code shoot} Lua 脚本里提供 {@code calcSpread} 函数，
 *       它会返回自定义散布向量覆盖 {@code inaccuracy}——此时本 mixin
 *       修改的 {@code inaccuracy} 参数<b>不生效</b>。</li>
 *   <li>若 TaCZ 未来新增其他 {@link AbstractGunItem} 子类并覆写
 *       {@code doBulletSpread}，需同步加入本 {@code @Mixin} 的
 *       {@code value} 数组。</li>
 * </ul>
 *
 * <p><b>生效条件</b>：服务端必须安装 BTP。单人 / 局域网 / 装了 BTP 的服务器
 * 均满足。服务端未安装 BTP 时本 mixin 不会应用，服务端散布保持原版值。
 */
@Mixin(value = { AbstractGunItem.class, ModernKineticGunItem.class }, remap = false)
public class GunSpreadMixin {

    @ModifyVariable(
            method = "doBulletSpread",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false
    )
    private float btp$scaleInaccuracy(
            float originalInaccuracy,
            ShooterDataHolder dataHolder,
            ItemStack gunItem,
            LivingEntity shooter,
            Projectile projectile,
            int bulletCnt,
            float processedSpeed,
            float pitch,
            float yaw) {
        if (shooter == null) {
            return originalInaccuracy;
        }
        if (!TiltStateStorage.getTiltState(shooter.getUUID())) {
            return originalInaccuracy;
        }
        float multiplier = TiltStateStorage.getSpreadMultiplier(shooter.getUUID());
        if (Math.abs(multiplier - 1.0f) < 0.0001f) {
            return originalInaccuracy;
        }
        return originalInaccuracy * multiplier;
    }
}
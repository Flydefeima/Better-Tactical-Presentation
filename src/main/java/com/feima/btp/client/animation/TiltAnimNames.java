package com.feima.btp.client.animation;

import com.feima.btp.BTPMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 集中管理 BTP 动画资源 ID 与动画名。
 * 动画名必须与 JSON 里的 {@code extraData.name} 严格一致——
 * TaCZ 的 {@code PlayerAnimatorAssetManager} 读取 extraData.name 来注册动画。
 */
public final class TiltAnimNames {
    private TiltAnimNames() {}

    public static final ResourceLocation RIFLE_FILE  = new ResourceLocation(BTPMod.MOD_ID, "rifle_tilted_anim");
    public static final ResourceLocation PISTOL_FILE = new ResourceLocation(BTPMod.MOD_ID, "pistol_tilted_anim");

    public static final String RIFLE_LOOP  = "rifle_tilted_gun";
    public static final String RIFLE_FIRE  = "rifle_tilted_gun_fire";
    public static final String PISTOL_LOOP = "pistol_tilted";
    public static final String PISTOL_FIRE = "pistol_tilted_fire";

    public record GunKind(ResourceLocation fileId, String loop, String fire) {}

    public static GunKind forGun(ItemStack gunItem) {
        return isPistol(gunItem)
                ? new GunKind(PISTOL_FILE, PISTOL_LOOP, PISTOL_FIRE)
                : new GunKind(RIFLE_FILE, RIFLE_LOOP, RIFLE_FIRE);
    }

    public static boolean isPistol(ItemStack gunItem) {
        if (gunItem.isEmpty()) return false;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return false;
        ResourceLocation gunId = iGun.getGunId(gunItem);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> "pistol".equalsIgnoreCase(index.getType()))
                .orElse(false);
    }
}
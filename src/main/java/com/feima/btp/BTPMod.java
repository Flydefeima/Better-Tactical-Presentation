package com.feima.btp;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(BTPMod.MOD_ID)
public class BTPMod {
    public static final String MOD_ID = "btp";
    public static boolean isTaczLabsLoaded = false;
    public static boolean isPlayerAnimatorLoaded = false;

    public static final ResourceLocation BTP_LOOP_LAYER = new ResourceLocation(MOD_ID, "tilt_upper_loop");
    public static final ResourceLocation BTP_ONCE_LAYER = new ResourceLocation(MOD_ID, "tilt_upper_once");

    public BTPMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);

        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BTPConfig.SPEC, "btp-client.toml");

            // 与 TaCZ 一致：构造期用 ModLoadingContext.get().registerExtensionPoint 注册。
            // 未装 Cloth Config 也安全——lambda 体里通过 isLoaded 决定是否引用
            // BTPMenuIntegration.getConfigScreen（首次执行 lambda 时才触发 Cloth Config 类加载）。
            try {
                com.feima.btp.client.compat.cloth.BTPMenuIntegration.registerModsPage();
            } catch (Throwable e) {
                BTPLog.LOGGER.warn("Failed to register config screen factory: {}", e.getMessage());
            }
        }

        NetworkHandler.register();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        isTaczLabsLoaded = ModList.get().isLoaded("taczlabs");
        isPlayerAnimatorLoaded = ModList.get().isLoaded("playeranimator");

        if (FMLEnvironment.dist.isClient() && isPlayerAnimatorLoaded) {
            try {
                com.feima.btp.client.compat.BTPPlayerAnimatorEntry.register();
            } catch (Throwable e) {
                BTPLog.LOGGER.warn("Failed to register playeranimator features: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
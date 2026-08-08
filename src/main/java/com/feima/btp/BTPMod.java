package com.feima.btp;

import com.feima.btp.client.ClientModEvents;
import com.feima.btp.config.BTPConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BTPMod.MOD_ID)
public class BTPMod {
    public static final String MOD_ID = "btp";
    public static boolean isTaczLabsLoaded = false;

    public BTPMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientModEvents::onRegisterKeys)
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BTPConfig.SPEC, "btp-client.toml");
        BTPLog.LOGGER.info("Config registered.");
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        isTaczLabsLoaded = ModList.get().isLoaded("taczlabs");
        if (isTaczLabsLoaded) {
            BTPLog.LOGGER.info("TaCZ:Labs detected, crosshair compatibility enabled.");
        }
    }
}
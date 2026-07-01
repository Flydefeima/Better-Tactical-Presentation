package com.feima.btp;

import com.feima.btp.client.ClientModEvents;
import com.feima.btp.config.BTPConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BTPMod.MOD_ID)
public class BTPMod {
    public static final String MOD_ID = "btp";

    public BTPMod() {
        // 注册按键事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientModEvents::onRegisterKeys);

        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BTPConfig.SPEC, "btp-common.toml");
        System.out.println("[BTP] Config registered.");
    }
}
package com.feima.btp;

import com.feima.btp.client.BTPEventHandler;
import com.feima.btp.config.BTPConfig;
import com.feima.btp.network.NetworkHandler;
import com.feima.btp.util.BTPTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.Method;

@Mod(BTPMod.MOD_ID)
public class BTPMod {
    public static final String MOD_ID = "btp";
    public static boolean isTaczLabsLoaded = false;
    public static boolean isPlayerAnimatorLoaded = false;

    public static final ResourceLocation BTP_LOOP_LAYER = new ResourceLocation(MOD_ID, "tilt_upper_loop");
    public static final ResourceLocation BTP_ONCE_LAYER = new ResourceLocation(MOD_ID, "tilt_upper_once");

    public BTPMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                FMLJavaModLoadingContext.get().getModEventBus().addListener(BTPEventHandler.ModBusEvents::onRegisterKeys)
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BTPConfig.SPEC, "btp-client.toml");

        // ===== 新增：注册网络包（必须在 Mod 总线事件之前，但构造器中调用是安全的） =====
        NetworkHandler.register();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        isTaczLabsLoaded = ModList.get().isLoaded("taczlabs");
        isPlayerAnimatorLoaded = ModList.get().isLoaded("playeranimator");

        if (isPlayerAnimatorLoaded) {
            try {
                Class<?> animatorClass = Class.forName("com.feima.btp.client.animation.BTPAnimator");
                Method registerMethod = animatorClass.getDeclaredMethod("registerAnimationLayers");
                registerMethod.invoke(null);
                MinecraftForge.EVENT_BUS.register(animatorClass);
            } catch (Throwable e) {
                BTPLog.LOGGER.warn("Failed to register playeranimator features: {}", e.getMessage());
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> {
                    Player player = Minecraft.getInstance().player;
                    if (player != null) {
                        BTPTipsHelper.sendAnimLoadFailedMessage(player);
                    }
                });
            }
        }
    }
}
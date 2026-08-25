package com.feima.btp;

import com.feima.btp.client.ClientModEvents;
import com.feima.btp.config.BTPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    // 动画层 ID（供其他类使用）
    public static final ResourceLocation BTP_LOOP_LAYER = new ResourceLocation(MOD_ID, "lean_upper_loop");
    public static final ResourceLocation BTP_ONCE_LAYER = new ResourceLocation(MOD_ID, "lean_upper_once");

    public BTPMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientModEvents::onRegisterKeys)
        );
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BTPConfig.SPEC, "btp-client.toml");
        BTPLog.LOGGER.info("BTPMod initialized.");
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        isTaczLabsLoaded = ModList.get().isLoaded("taczlabs");
        isPlayerAnimatorLoaded = ModList.get().isLoaded("playeranimator");

        if (isTaczLabsLoaded) {
            BTPLog.LOGGER.info("TaCZ:Labs detected, crosshair compatibility enabled.");
        }

        if (isPlayerAnimatorLoaded) {
            BTPLog.LOGGER.info("PlayerAnimator detected, registering animation features.");
            try {
                // 1. 通过反射加载 IndependentLeanAnimationHandler（不会触发类加载失败）
                //    因为此时 playeranimator 已确认存在
                Class<?> handlerClass = Class.forName("com.feima.btp.client.animation.IndependentLeanAnimationHandler");
                
                // 2. 调用静态方法 registerAnimationLayers()
                Method registerMethod = handlerClass.getDeclaredMethod("registerAnimationLayers");
                registerMethod.invoke(null);
                
                // 3. 注册事件处理器（@SubscribeEvent 方法会被 Forge 识别）
                MinecraftForge.EVENT_BUS.register(handlerClass);
                
                BTPLog.LOGGER.info("IndependentLeanAnimationHandler registered and animation layers set up.");
            } catch (Throwable e) {
                BTPLog.LOGGER.warn("Failed to register playeranimator features: {}", e.getMessage());
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> {
                    Player player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(
                                Component.translatable("message.btp.independent_anim_load_failed"),
                                false
                        );
                    }
                });
            }
        } else {
            BTPLog.LOGGER.info("PlayerAnimator not detected, third-person lean animation disabled.");
        }
    }
}
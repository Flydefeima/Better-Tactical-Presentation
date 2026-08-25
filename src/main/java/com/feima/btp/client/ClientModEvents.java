package com.feima.btp.client;

import com.feima.btp.BTPMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    public static final KeyMapping TOGGLE_LEAN_KEY = new KeyMapping(
            "key.btp.toggle_lean",
            GLFW.GLFW_KEY_B,
            "key.categories.btp"
    );

    // 1.4.3 新增：独立据枪按键，默认不绑定
    public static final KeyMapping HOLD_LEAN_KEY = new KeyMapping(
            "key.btp.hold_lean",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.btp"
    );

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_LEAN_KEY);
        event.register(HOLD_LEAN_KEY);
    }
}
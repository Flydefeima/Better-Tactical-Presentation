package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class InputHelper {

    public static void clearRightMouseButton() {
        // 右键状态由 GLFW 物理键位直接读取，无需手动维护
    }

    public static boolean isPhysicalRightPressed() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
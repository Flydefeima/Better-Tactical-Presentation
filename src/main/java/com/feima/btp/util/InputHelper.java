package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class InputHelper {

    private InputHelper() {}

    /**
     * 查询物理鼠标右键是否按下。
     *
     * <p>{@code Window.getWindow()} 在窗口未初始化、最小化或关闭流程中
     * 可能返回 {@code 0L}。此时 {@code glfwGetMouseButton(0, ...)} 会返回
     * {@code GLFW_RELEASE}，调用方会误判为"已松开"。加此守卫避免误判。
     */
    public static boolean isPhysicalRightPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return false;
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
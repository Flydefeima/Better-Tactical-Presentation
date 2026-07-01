package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class InputHelper {
    private static final Field BUTTONS_FIELD;

    static {
        Field field = null;
        try {
            field = MouseHandler.class.getDeclaredField("buttons");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                field = MouseHandler.class.getDeclaredField("field_91512_");
                field.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            }
        }
        BUTTONS_FIELD = field;
    }

    public static void clearRightMouseButton() {
        if (BUTTONS_FIELD == null) return;
        Minecraft mc = Minecraft.getInstance();
        MouseHandler handler = mc.mouseHandler;
        if (handler == null) return;
        try {
            byte[] buttons = (byte[]) BUTTONS_FIELD.get(handler);
            if (buttons != null && buttons.length > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                buttons[GLFW.GLFW_MOUSE_BUTTON_RIGHT] = 0;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static boolean isPhysicalRightPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
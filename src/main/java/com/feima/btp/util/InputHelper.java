package com.feima.btp.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class InputHelper {
    private static final Field RIGHT_PRESSED_FIELD;

    static {
        Field field = null;
        // Mojang 1.20.1: isRightPressed (boolean)
        try {
            field = MouseHandler.class.getDeclaredField("isRightPressed");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // SRG fallback
            try {
                field = MouseHandler.class.getDeclaredField("field_91512_");
                field.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            }
        }
        RIGHT_PRESSED_FIELD = field;
    }

    public static void clearRightMouseButton() {
        if (RIGHT_PRESSED_FIELD == null) return;
        Minecraft mc = Minecraft.getInstance();
        MouseHandler handler = mc.mouseHandler;
        if (handler == null) return;
        try {
            Object value = RIGHT_PRESSED_FIELD.get(handler);
            if (value instanceof Boolean) {
                RIGHT_PRESSED_FIELD.setBoolean(handler, false);
            } else if (value instanceof byte[]) {
                byte[] buttons = (byte[]) value;
                if (buttons.length > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    buttons[GLFW.GLFW_MOUSE_BUTTON_RIGHT] = 0;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isPhysicalRightPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}

package com.feima.btp.util;

import com.feima.btp.BTPLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class InputHelper {

    private static Field rightPressedField = null;
    private static boolean useByteArray = false;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            rightPressedField = MouseHandler.class.getDeclaredField("isRightPressed");
            rightPressedField.setAccessible(true);
            return;
        } catch (NoSuchFieldException ignored) {}

        try {
            rightPressedField = MouseHandler.class.getDeclaredField("field_91512_");
            rightPressedField.setAccessible(true);
            return;
        } catch (NoSuchFieldException ignored) {}

        for (Field f : MouseHandler.class.getDeclaredFields()) {
            f.setAccessible(true);
            String name = f.getName().toLowerCase();
            if (f.getType() == boolean.class && (name.contains("right") || name.contains("press"))) {
                rightPressedField = f;
                return;
            }
            if (f.getType() == byte[].class && (name.contains("button") || name.contains("press"))) {
                rightPressedField = f;
                useByteArray = true;
                return;
            }
        }

        BTPLog.LOGGER.warn("Could not find MouseHandler field for right click clearing.");
    }

    public static void clearRightMouseButton() {
        init();
        if (rightPressedField == null) return;

        Minecraft mc = Minecraft.getInstance();
        MouseHandler handler = mc.mouseHandler;
        if (handler == null) return;

        try {
            if (useByteArray) {
                byte[] buttons = (byte[]) rightPressedField.get(handler);
                if (buttons != null && buttons.length > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    buttons[GLFW.GLFW_MOUSE_BUTTON_RIGHT] = 0;
                }
            } else {
                rightPressedField.setBoolean(handler, false);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isPhysicalRightPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
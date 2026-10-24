package com.feima.btp.client.compat.cloth;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 未安装 Cloth Config 时展示的提示界面。
 *
 * <p>仅在玩家点击 Mods 菜单中 BTP 的 Config 按钮、且 {@code cloth_config}
 * 未加载时出现。只引用 Minecraft / Forge 类，不引用任何 Cloth Config API。
 */
public final class BTPMissingClothConfigScreen extends Screen {

    private final Screen parent;

    public BTPMissingClothConfigScreen(Screen parent) {
        super(Component.translatable("config.btp.missing_cloth_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_BACK,
                b -> onClose()
        ).bounds(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, delta);

        int centerX = width / 2;
        int y = height / 2 - 30;

        // 1.20.1 正确方法名：drawCenteredString（美式拼写）
        gfx.drawCenteredString(font, title, centerX, y, 0xFFAA00);
        gfx.drawCenteredString(
                font,
                Component.translatable("config.btp.missing_cloth_config.message"),
                centerX, y + 20, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
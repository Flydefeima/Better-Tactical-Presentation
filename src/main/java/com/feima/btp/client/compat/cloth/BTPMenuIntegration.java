package com.feima.btp.client.compat.cloth;

import com.feima.btp.config.BTPConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

import javax.annotation.Nullable;

/**
 * 配置界面主入口，结构对齐 TaCZ 的 {@code MenuIntegration}。
 *
 * <p><b>类加载契约</b>：
 * <ul>
 *   <li>本类被 {@code BTPMod} 在客户端构造期加载。所有公开方法（{@code registerModsPage}
 *       / {@code getConfigScreen}）的签名只引用 Minecraft / Forge 类型，
 *       不引用 Cloth Config 类型</li>
 *   <li>{@code getConfigBuilder()} 的签名引用了 {@code ConfigBuilder}——类加载时
 *       JVM 不会强制解析方法返回类型，只有该方法第一次执行时才加载 Cloth Config。
 *       而该方法只会在 {@code cloth_config} 已加载的 lambda 分支里被调用</li>
 * </ul>
 *
 * <p><b>保存语义</b>：{@code setSavingRunnable} 同时执行
 * {@code SPEC.save()} 和 {@code BTPConfig.reload()}。前者写盘，后者立即刷新
 * 静态快照——不依赖 Forge 的异步文件监视器，消除"保存后短暂读到旧值"的窗口。
 */
public class BTPMenuIntegration {

    public static ConfigBuilder getConfigBuilder() {
        ConfigBuilder root = ConfigBuilder.create()
                .setTitle(Component.translatable("config.btp.title"))
                .setSavingRunnable(() -> {
                    BTPConfig.SPEC.save();
                    BTPConfig.reload();
                });
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        ConfigEntryBuilder entryBuilder = root.entryBuilder();

        BTPGeneralClothConfig.init(root, entryBuilder);
        BTPTiltModeClothConfig.init(root, entryBuilder);

        return root;
    }

    public static void registerModsPage() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> {
                    if (ModList.get().isLoaded("cloth_config")) {
                        return getConfigScreen(parent);
                    }
                    return new BTPMissingClothConfigScreen(parent);
                })
        );
    }

    public static Screen getConfigScreen(@Nullable Screen parent) {
        return BTPMenuIntegration.getConfigBuilder().setParentScreen(parent).build();
    }
}
package com.feima.btp.config;

import com.feima.btp.BTPMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BTPConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue INTERRUPT_ON_TOGGLE;
    public static final ForgeConfigSpec.IntValue LONG_PRESS_THRESHOLD;
    public static final ForgeConfigSpec.IntValue COOLDOWN_DURATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LONG_PRESS_LEAN;
    public static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_CROUCH_LEAN;
    public static final ForgeConfigSpec.BooleanValue SHOW_LONG_PRESS_LEAN_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue LONG_PRESS_TRIGGERS_TOGGLE;
    public static final ForgeConfigSpec.BooleanValue BREAK_SPRINT;
    public static final ForgeConfigSpec.DoubleValue LEAN_SPREAD_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Better Tactical Presentation 设置").push("general");

        INTERRUPT_ON_TOGGLE = builder
                .comment("如果开启，切换模式时会立即停止当前动作（瞄准或倾斜），而不会在右键按住时自动进入新模式。关闭则无缝衔接。")
                .translation("config.btp.interruptOnToggle")
                .define("interruptOnToggle", false);

        LONG_PRESS_THRESHOLD = builder
                .comment("长按判定时间（毫秒），用于切换键长按和右键长按据枪。")
                .translation("config.btp.longPressThreshold")
                .defineInRange("longPressThreshold", 450, 100, 2000);

        COOLDOWN_DURATION = builder
                .comment("长按切换键后触发的冷却时长（毫秒）。设为 0 可禁用冷却。")
                .translation("config.btp.cooldownDuration")
                .defineInRange("cooldownDuration", 3000, 0, 10000);

        ENABLE_LONG_PRESS_LEAN = builder
                .comment("开启后，切换键被禁用，右键短按切换开镜，长按进入战术据枪（倾斜）。")
                .translation("config.btp.enableLongPressLean")
                .define("enableLongPressLean", false);

        DISABLE_VANILLA_CROUCH_LEAN = builder
                .comment("开启后，玩家蹲下时枪械不会自动倾斜（禁用 TACZ 原版的蹲下战术据枪）。")
                .translation("config.btp.disableVanillaCrouchLean")
                .define("disableVanillaCrouchLean", true);

        SHOW_LONG_PRESS_LEAN_MESSAGES = builder
                .comment("开启后，在右键长按据枪模式下显示开镜/关镜/据枪提示消息。")
                .translation("config.btp.showLongPressLeanMessages")
                .define("showLongPressLeanMessages", true);

        LONG_PRESS_TRIGGERS_TOGGLE = builder
                .comment("开启后，切换键长按达到阈值会触发一次模式切换并进入冷却；关闭则长按只触发冷却，不切换。")
                .translation("config.btp.longPressTriggersToggle")
                .define("longPressTriggersToggle", false);

        BREAK_SPRINT = builder
                .comment("开启后，战术据枪状态下会强制打断疾跑并禁止触发。")
                .translation("config.btp.breakSprint")
                .define("breakSprint", true);

        LEAN_SPREAD_MULTIPLIER = builder
                .comment("战术据枪时子弹散布倍率（0.0~2.0，默认1.0不变）。小于1.0缩小散布提高精度，大于1.0增大散布。")
                .translation("config.btp.leanSpreadMultiplier")
                .defineInRange("leanSpreadMultiplier", 0.5, 0.0, 2.0);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean interruptOnToggle = false;
    public static int longPressThreshold = 450;
    public static int cooldownDuration = 3000;
    public static boolean enableLongPressLean = false;
    public static boolean disableVanillaCrouchLean = true;
    public static boolean showLongPressLeanMessages = true;
    public static boolean longPressTriggersToggle = false;
    public static boolean breakSprint = true;
    public static double leanSpreadMultiplier = 1.0;

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            reloadConfig();
            ensureConfigFileExists(event.getConfig().getFullPath());
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            reloadConfig();
        }
    }

    private static void reloadConfig() {
        interruptOnToggle = INTERRUPT_ON_TOGGLE.get();
        longPressThreshold = LONG_PRESS_THRESHOLD.get();
        cooldownDuration = COOLDOWN_DURATION.get();
        enableLongPressLean = ENABLE_LONG_PRESS_LEAN.get();
        disableVanillaCrouchLean = DISABLE_VANILLA_CROUCH_LEAN.get();
        showLongPressLeanMessages = SHOW_LONG_PRESS_LEAN_MESSAGES.get();
        longPressTriggersToggle = LONG_PRESS_TRIGGERS_TOGGLE.get();
        breakSprint = BREAK_SPRINT.get();
        leanSpreadMultiplier = LEAN_SPREAD_MULTIPLIER.get();
        System.out.println("[BTP] 配置加载: interruptOnToggle=" + interruptOnToggle +
                ", longPressThreshold=" + longPressThreshold +
                ", cooldownDuration=" + cooldownDuration +
                ", enableLongPressLean=" + enableLongPressLean +
                ", disableVanillaCrouchLean=" + disableVanillaCrouchLean +
                ", showLongPressLeanMessages=" + showLongPressLeanMessages +
                ", longPressTriggersToggle=" + longPressTriggersToggle +
                ", breakSprint=" + breakSprint +
                ", leanSpreadMultiplier=" + leanSpreadMultiplier);
    }

    private static void ensureConfigFileExists(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                String defaultConfig = """
                        #Better Tactical Presentation 设置

                        [general]
                        \t#如果开启，切换模式时会立即停止当前动作（瞄准或倾斜），而不会在右键按住时自动进入新模式。关闭则无缝衔接。
                        \tinterruptOnToggle = false
                        \t#长按判定时间（毫秒），用于切换键长按和右键长按据枪。
                        \tlongPressThreshold = 450
                        \t#长按切换键后触发的冷却时长（毫秒）。设为 0 可禁用冷却。
                        \tcooldownDuration = 3000
                        \t#开启后，切换键被禁用，右键短按切换开镜，长按进入战术据枪（倾斜）。
                        \tenableLongPressLean = false
                        \t#开启后，玩家蹲下时枪械不会自动倾斜（禁用 TACZ 原版的蹲下战术据枪）。
                        \tdisableVanillaCrouchLean = true
                        \t#开启后，在右键长按据枪模式下显示开镜/关镜/据枪提示消息。
                        \tshowLongPressLeanMessages = true
                        \t#开启后，切换键长按达到阈值会触发一次模式切换并进入冷却；关闭则长按只触发冷却，不切换。
                        \tlongPressTriggersToggle = false
                        \t#开启后，战术据枪状态下会强制打断疾跑并禁止触发。
                        \tbreakSprint = true
                        \t#战术据枪时子弹散布倍率（0.0~2.0，默认1.0不变）。小于1.0缩小散布提高精度，大于1.0增大散布。
                        \tleanSpreadMultiplier = 1.0
                        """;
                Files.writeString(configPath, defaultConfig);
                System.out.println("[BTP] 已创建默认配置文件: " + configPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
package com.feima.btp.config;

import com.feima.btp.BTPLog;
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
    public static final ForgeConfigSpec.BooleanValue ENABLE_LONG_PRESS_LEAN;
    public static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_CROUCH_LEAN;
    public static final ForgeConfigSpec.BooleanValue SHOW_LONG_PRESS_LEAN_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue BREAK_SPRINT;
    public static final ForgeConfigSpec.DoubleValue LEAN_SPREAD_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TACZLABS_CROSSHAIR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Better Tactical Presentation Settings").push("general");

        INTERRUPT_ON_TOGGLE = builder
                .comment("If enabled, toggling mode will instantly stop current action (aiming or leaning) instead of auto-entering new mode while right-click is held. Disabled for seamless transition.")
                .translation("config.btp.interruptOnToggle")
                .define("interruptOnToggle", false);

        LONG_PRESS_THRESHOLD = builder
                .comment("Right-click long press threshold in milliseconds for lean detection. Only effective when enableLongPressLean is enabled.")
                .translation("config.btp.longPressThreshold")
                .defineInRange("longPressThreshold", 200, 50, 2000);

        ENABLE_LONG_PRESS_LEAN = builder
                .comment("If enabled, toggle key is disabled. Right-click short press toggles aim, long press enters tactical lean.")
                .translation("config.btp.enableLongPressLean")
                .define("enableLongPressLean", false);

        DISABLE_VANILLA_CROUCH_LEAN = builder
                .comment("If enabled, gun will not auto-lean when crouching (disables TACZ vanilla crouch lean).")
                .translation("config.btp.disableVanillaCrouchLean")
                .define("disableVanillaCrouchLean", true);

        SHOW_LONG_PRESS_LEAN_MESSAGES = builder
                .comment("If enabled, shows aim on/off and lean on messages in right-click long press lean mode.")
                .translation("config.btp.showLongPressLeanMessages")
                .define("showLongPressLeanMessages", true);

        BREAK_SPRINT = builder
                .comment("If enabled, sprint is forcefully disabled while in tactical lean state.")
                .translation("config.btp.breakSprint")
                .define("breakSprint", true);

        LEAN_SPREAD_MULTIPLIER = builder
                .comment("Spread multiplier while leaning (0.0~2.0, default 1.0). Below 1.0 improves accuracy, above 1.0 increases spread.")
                .translation("config.btp.leanSpreadMultiplier")
                .defineInRange("leanSpreadMultiplier", 0.3, 0.0, 2.0);

        COMPAT_TACZLABS_CROSSHAIR = builder
                .comment("If enabled, TaCZ:Labs crosshair will auto-hide when leaning.")
                .translation("config.btp.compatTaczLabsCrosshair")
                .define("compatTaczLabsCrosshair", true);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean interruptOnToggle = false;
    public static int longPressThreshold = 200;
    public static boolean enableLongPressLean = false;
    public static boolean disableVanillaCrouchLean = true;
    public static boolean showLongPressLeanMessages = true;
    public static boolean breakSprint = true;
    public static double leanSpreadMultiplier = 0.3;
    public static boolean compatTaczLabsCrosshair = true;

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
        enableLongPressLean = ENABLE_LONG_PRESS_LEAN.get();
        disableVanillaCrouchLean = DISABLE_VANILLA_CROUCH_LEAN.get();
        showLongPressLeanMessages = SHOW_LONG_PRESS_LEAN_MESSAGES.get();
        breakSprint = BREAK_SPRINT.get();
        leanSpreadMultiplier = LEAN_SPREAD_MULTIPLIER.get();
        compatTaczLabsCrosshair = COMPAT_TACZLABS_CROSSHAIR.get();
        BTPLog.LOGGER.info("Config loaded: interruptOnToggle={}, longPressThreshold={}, " +
                        "enableLongPressLean={}, disableVanillaCrouchLean={}, showLongPressLeanMessages={}, " +
                        "breakSprint={}, leanSpreadMultiplier={}, compatTaczLabsCrosshair={}",
                interruptOnToggle, longPressThreshold, enableLongPressLean,
                disableVanillaCrouchLean, showLongPressLeanMessages,
                breakSprint, leanSpreadMultiplier, compatTaczLabsCrosshair);
    }

    private static void ensureConfigFileExists(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                String defaultConfig = """
                        #Better Tactical Presentation Settings

                        [general]
                        \t#If enabled, toggling mode will instantly stop current action (aiming or leaning) instead of auto-entering new mode while right-click is held. Disabled for seamless transition.
                        \tinterruptOnToggle = false
                        \t#Right-click long press threshold in milliseconds for lean detection. Only effective when enableLongPressLean is enabled.
                        \tlongPressThreshold = 200
                        \t#If enabled, toggle key is disabled. Right-click short press toggles aim, long press enters tactical lean.
                        \tenableLongPressLean = false
                        \t#If enabled, gun will not auto-lean when crouching (disables TACZ vanilla crouch lean).
                        \tdisableVanillaCrouchLean = true
                        \t#If enabled, shows aim on/off and lean on messages in right-click long press lean mode.
                        \tshowLongPressLeanMessages = true
                        \t#If enabled, sprint is forcefully disabled while in tactical lean state.
                        \tbreakSprint = true
                        \t#Spread multiplier while leaning (0.0~2.0, default 1.0). Below 1.0 improves accuracy, above 1.0 increases spread.
                        \tleanSpreadMultiplier = 1.0
                        \t#If enabled, TaCZ:Labs crosshair will auto-hide when leaning.
                        \tcompatTaczLabsCrosshair = true
                        """;
                Files.writeString(configPath, defaultConfig);
                BTPLog.LOGGER.info("Default config file created: {}", configPath);
            }
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to create default config file: {}", e.getMessage());
        }
    }
}
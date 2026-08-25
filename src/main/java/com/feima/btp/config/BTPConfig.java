package com.feima.btp.config;

import com.feima.btp.BTPLog;
import com.feima.btp.BTPMod;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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
    public static final ForgeConfigSpec.BooleanValue RESET_TO_AIM_ON_ITEM_SWITCH;
    public static final ForgeConfigSpec.BooleanValue ENABLE_THIRD_PERSON_LEAN_ANIMATION;
    public static final ForgeConfigSpec.ConfigValue<String> TACTICAL_CROSSHAIR;

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
                .comment("Spread multiplier while leaning (0.0~2.0, default 0.3). Below 1.0 improves accuracy, above 1.0 increases spread.")
                .translation("config.btp.leanSpreadMultiplier")
                .defineInRange("leanSpreadMultiplier", 0.3, 0.0, 2.0);

        COMPAT_TACZLABS_CROSSHAIR = builder
                .comment("If enabled, TaCZ:Labs crosshair will auto-hide when leaning.")
                .translation("config.btp.compatTaczLabsCrosshair")
                .define("compatTaczLabsCrosshair", true);

        RESET_TO_AIM_ON_ITEM_SWITCH = builder
                .comment("If enabled, switching the main hand item resets to normal aim mode (cancels tactical lean and returns to aim mode).")
                .translation("config.btp.resetToAimOnItemSwitch")
                .define("resetToAimOnItemSwitch", true);

        ENABLE_THIRD_PERSON_LEAN_ANIMATION = builder
                .comment("If enabled, third-person lean animation will play when in tactical lean. Disable this if you prefer to keep the default TACZ third-person pose.")
                .translation("config.btp.enableThirdPersonLeanAnimation")
                .define("enableThirdPersonLeanAnimation", true);

        TACTICAL_CROSSHAIR = builder
                .comment("Crosshair type to use while in tactical lean. Valid values: EMPTY, DOT_1, CIRCLE_1, CIRCLE_2, CIRCLE_3, CROSS_1, CROSS_2, CROSS_3, CROSS_4, CROSS_5, CROSS_6, LINE_1, LINE_2, LINE_3, SQUARE_1, SQUARE_2, SQUARE_3, SQUARE_4, SQUARE_5, SQUARE_6, TRIDENT_1, TRIDENT_2. Leave empty to disable override.")
                .translation("config.btp.tacticalCrosshair")
                .define("tacticalCrosshair", "DOT_1");

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
    public static boolean resetToAimOnItemSwitch = true;
    public static boolean enableThirdPersonLeanAnimation = true;
    public static String tacticalCrosshair = "DOT_1";

    private static CrosshairType cachedCrosshairType = null;
    private static String cachedCrosshairConfig = null;

    public static CrosshairType getTacticalCrosshairType() {
        String current = tacticalCrosshair;
        if (current == null || current.trim().isEmpty()) {
            return null;
        }
        if (!current.equals(cachedCrosshairConfig)) {
            try {
                cachedCrosshairType = CrosshairType.valueOf(current.trim().toUpperCase(Locale.US));
                cachedCrosshairConfig = current;
            } catch (IllegalArgumentException e) {
                BTPLog.LOGGER.warn("Invalid crosshair type '{}', falling back to no override.", current);
                cachedCrosshairType = null;
                cachedCrosshairConfig = current;
            }
        }
        return cachedCrosshairType;
    }

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
        resetToAimOnItemSwitch = RESET_TO_AIM_ON_ITEM_SWITCH.get();
        enableThirdPersonLeanAnimation = ENABLE_THIRD_PERSON_LEAN_ANIMATION.get();
        tacticalCrosshair = TACTICAL_CROSSHAIR.get();
        cachedCrosshairConfig = null;
        cachedCrosshairType = null;
        BTPLog.LOGGER.info("Config loaded.");
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
                        \t#Spread multiplier while leaning (0.0~2.0, default 0.3). Below 1.0 improves accuracy, above 1.0 increases spread.
                        \tleanSpreadMultiplier = 0.3
                        \t#If enabled, TaCZ:Labs crosshair will auto-hide when leaning.
                        \tcompatTaczLabsCrosshair = true
                        \t#If enabled, switching the main hand item resets to normal aim mode (cancels tactical lean and returns to aim mode).
                        \tresetToAimOnItemSwitch = true
                        \t#If enabled, third-person lean animation will play when in tactical lean. Disable this if you prefer to keep the default TACZ third-person pose.
                        \tenableThirdPersonLeanAnimation = true
                        \t#Crosshair type to use while in tactical lean. Valid values: EMPTY, DOT_1, CIRCLE_1, CIRCLE_2, CIRCLE_3, CROSS_1, CROSS_2, CROSS_3, CROSS_4, CROSS_5, CROSS_6, LINE_1, LINE_2, LINE_3, SQUARE_1, SQUARE_2, SQUARE_3, SQUARE_4, SQUARE_5, SQUARE_6, TRIDENT_1, TRIDENT_2. Leave empty to disable override.
                        \ttacticalCrosshair = "DOT_1"
                        """;
                Files.writeString(configPath, defaultConfig);
                BTPLog.LOGGER.info("Default config file created: {}", configPath);
            }
        } catch (Exception e) {
            BTPLog.LOGGER.warn("Failed to create default config file: {}", e.getMessage());
        }
    }
}
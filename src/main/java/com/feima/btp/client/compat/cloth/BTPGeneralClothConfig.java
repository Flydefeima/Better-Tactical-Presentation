package com.feima.btp.client.compat.cloth;

import com.feima.btp.config.BTPConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public class BTPGeneralClothConfig {
    public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory general = root.getOrCreateCategory(Component.translatable("config.btp.category.general"));

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.disableVanillaCrouchTilt"), BTPConfig.DISABLE_VANILLA_CROUCH_TILT.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.disableVanillaCrouchTilt.desc"))
                .setSaveConsumer(BTPConfig.DISABLE_VANILLA_CROUCH_TILT::set).build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.breakSprint"), BTPConfig.BREAK_SPRINT.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.breakSprint.desc"))
                .setSaveConsumer(BTPConfig.BREAK_SPRINT::set).build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("config.btp.tiltSpreadMultiplier"), BTPConfig.TILT_SPREAD_MULTIPLIER.get())
                .setMin(0.0).setMax(2.0).setDefaultValue(0.3).setTooltip(Component.translatable("config.btp.tiltSpreadMultiplier.desc"))
                .setSaveConsumer(BTPConfig.TILT_SPREAD_MULTIPLIER::set).build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.compatTaczLabsCrosshair"), BTPConfig.COMPAT_TACZLABS_CROSSHAIR.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.compatTaczLabsCrosshair.desc"))
                .setSaveConsumer(BTPConfig.COMPAT_TACZLABS_CROSSHAIR::set).build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.resetToAimOnItemSwitch"), BTPConfig.RESET_TO_AIM_ON_ITEM_SWITCH.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.resetToAimOnItemSwitch.desc"))
                .setSaveConsumer(BTPConfig.RESET_TO_AIM_ON_ITEM_SWITCH::set).build());

        general.addEntry(entryBuilder.startStrField(Component.translatable("config.btp.tacticalCrosshair"), BTPConfig.TACTICAL_CROSSHAIR.get())
                .setDefaultValue("DOT_1").setTooltip(Component.translatable("config.btp.tacticalCrosshair.desc"))
                .setSaveConsumer(BTPConfig.TACTICAL_CROSSHAIR::set).build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.enableThirdPersonTiltAnimation"), BTPConfig.ENABLE_THIRD_PERSON_TILT_ANIMATION.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.enableThirdPersonTiltAnimation.desc"))
                .setSaveConsumer(BTPConfig.ENABLE_THIRD_PERSON_TILT_ANIMATION::set).build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("config.btp.tiltTransitionTimeMs"), BTPConfig.TILT_TRANSITION_TIME_MS.get())
                .setMin(0).setMax(1000).setDefaultValue(250).setTooltip(Component.translatable("config.btp.tiltTransitionTimeMs.desc"))
                .setSaveConsumer(BTPConfig.TILT_TRANSITION_TIME_MS::set).build());
    }
}
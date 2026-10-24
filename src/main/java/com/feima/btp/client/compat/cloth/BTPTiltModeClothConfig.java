package com.feima.btp.client.compat.cloth;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.config.TiltTriggerMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public class BTPTiltModeClothConfig {
    public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory tiltMode = root.getOrCreateCategory(Component.translatable("config.btp.category.tilt_mode"));

        tiltMode.addEntry(entryBuilder.startEnumSelector(Component.translatable("config.btp.tiltTriggerMode"), TiltTriggerMode.class, BTPConfig.TILT_TRIGGER_MODE.get())
                .setDefaultValue(TiltTriggerMode.DEFAULT)
                .setEnumNameProvider(e -> Component.translatable("config.btp.tiltTriggerMode." + e.name()))
                .setTooltip(Component.translatable("config.btp.tiltTriggerMode.desc"))
                .setSaveConsumer(BTPConfig.TILT_TRIGGER_MODE::set).build());

        tiltMode.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.interruptOnToggle"), BTPConfig.INTERRUPT_ON_TOGGLE.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.btp.interruptOnToggle.desc"))
                .setSaveConsumer(BTPConfig.INTERRUPT_ON_TOGGLE::set).build());

        tiltMode.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.btp.showLongPressTiltMessages"), BTPConfig.SHOW_LONG_PRESS_TILT_MESSAGES.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.btp.showLongPressTiltMessages.desc"))
                .setSaveConsumer(BTPConfig.SHOW_LONG_PRESS_TILT_MESSAGES::set).build());

        tiltMode.addEntry(entryBuilder.startIntField(Component.translatable("config.btp.longPressThreshold"), BTPConfig.LONG_PRESS_THRESHOLD.get())
                .setMin(50).setMax(2000).setDefaultValue(200).setTooltip(Component.translatable("config.btp.longPressThreshold.desc"))
                .setSaveConsumer(BTPConfig.LONG_PRESS_THRESHOLD::set).build());
    }
}
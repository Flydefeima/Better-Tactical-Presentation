package com.feima.btp.config;

import com.feima.btp.BTPMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 只包含客户端配置。
 *
 * <p><b>不要在这里引用任何 {@code net.minecraft.client.*} 或 TaCZ 客户端类</b>
 * ——服务端也会加载本类（用于 {@code FMLJavaModLoadingContext} 的事件订阅），
 * 引用客户端类会导致 NoClassDefFoundError。
 *
 * <p><b>专用服务器注意</b>：{@code ModConfig.Type.CLIENT} 在专用服务器上
 * <em>不会</em>读取玩家配置文件，静态快照字段会停留在默认值。
 * {@code tiltSpreadMultiplier} 因此不能直接被服务端 mixin 使用——
 * 它通过 {@code ServerboundTiltPacket} 同步到服务端
 * （见 {@code TiltStateStorage} 与 {@code GunSpreadMixin}）。
 */
@Mod.EventBusSubscriber(modid = BTPMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BTPConfig {
    public static final ForgeConfigSpec SPEC;

    // ===== general =====
    public static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_CROUCH_TILT;
    public static final ForgeConfigSpec.BooleanValue FORCE_TILT_ON_ALL_GUNS;
    public static final ForgeConfigSpec.BooleanValue BREAK_SPRINT;
    public static final ForgeConfigSpec.DoubleValue TILT_SPREAD_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TACZLABS_CROSSHAIR;
    public static final ForgeConfigSpec.BooleanValue RESET_TO_AIM_ON_ITEM_SWITCH;
    public static final ForgeConfigSpec.ConfigValue<String> TACTICAL_CROSSHAIR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_THIRD_PERSON_TILT_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ZOOM_WITHOUT_AIM;
    public static final ForgeConfigSpec.IntValue TILT_TRANSITION_TIME_MS;

    // ===== tilt mode =====
    public static final ForgeConfigSpec.EnumValue<TiltTriggerMode> TILT_TRIGGER_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HOLD_TILT;
    public static final ForgeConfigSpec.BooleanValue INTERRUPT_ON_TOGGLE;
    public static final ForgeConfigSpec.BooleanValue SHOW_LONG_PRESS_TILT_MESSAGES;
    public static final ForgeConfigSpec.IntValue LONG_PRESS_THRESHOLD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ============================================================
        // general
        // ============================================================
        builder.comment("Better Tactical Presentation Settings",
                        "更好的战术据枪表现设置").push("general");

        DISABLE_VANILLA_CROUCH_TILT = builder
                .comment("Disable TACZ vanilla crouch tilt.",
                         "禁用 TaCZ 原版蹲下据枪。")
                .translation("config.btp.disableVanillaCrouchTilt")
                .define("disableVanillaCrouchTilt", true);

        FORCE_TILT_ON_ALL_GUNS = builder
                .comment("Ignore the gun's can_slide flag and always tilt while using BTP tilt.",
                         "(TaCZ 1.2.0 sniper rifles set can_slide=false.)",
                         "忽略枪械的 can_slide 属性，据枪时强制斜握。",
                         "（TaCZ 1.2.0 狙击枪的 can_slide 为 false。）")
                .translation("config.btp.forceTiltOnAllGuns")
                .define("forceTiltOnAllGuns", true);

        BREAK_SPRINT = builder
                .comment("Force-disable sprint while tilting (client-side only).",
                         "据枪时强制禁止疾跑（仅客户端阻断）。")
                .translation("config.btp.breakSprint")
                .define("breakSprint", true);

        TILT_SPREAD_MULTIPLIER = builder
                .comment("Spread multiplier while tilting (0.0~2.0, default 0.3).",
                         "This value is sent to the server via BTP's network packet, so the",
                         "server-side hit detection matches the client-side crosshair even on",
                         "dedicated servers.",
                         "If BTP is not installed on the server, the server's spread is unchanged.",
                         "据枪时的散布倍率（0.0~2.0，默认 0.3）。",
                         "该值会通过 BTP 网络包同步到服务端，因此专用服务器上服务端命中",
                         "判定与客户端准星视觉保持一致。",
                         "若服务端未安装 BTP，服务端散布保持不变。")
                .translation("config.btp.tiltSpreadMultiplier")
                .defineInRange("tiltSpreadMultiplier", 0.3, 0.0, 2.0);

        COMPAT_TACZLABS_CROSSHAIR = builder
                .comment("Auto-hide TaCZ:Labs crosshair while tilting.",
                         "据枪时自动隐藏 TaCZ:Labs 准星。")
                .translation("config.btp.compatTaczLabsCrosshair")
                .define("compatTaczLabsCrosshair", true);

        RESET_TO_AIM_ON_ITEM_SWITCH = builder
                .comment("Reset to aim mode when switching main hand item.",
                         "切换主手物品时重置为瞄准模式。")
                .translation("config.btp.resetToAimOnItemSwitch")
                .define("resetToAimOnItemSwitch", true);

        TACTICAL_CROSSHAIR = builder
                .comment("Crosshair type while tilting. Options: EMPTY, DOT_1, CIRCLE_1~3, CROSS_1~6, LINE_1~3, SQUARE_1~6, TRIDENT_1~2. Empty to disable.",
                         "据枪时的准星类型。可选：EMPTY、DOT_1、CIRCLE_1~3、CROSS_1~6、LINE_1~3、SQUARE_1~6、TRIDENT_1~2。留空禁用。")
                .translation("config.btp.tacticalCrosshair")
                .define("tacticalCrosshair", "DOT_1");

        ENABLE_THIRD_PERSON_TILT_ANIMATION = builder
                .comment("Play third-person tilt animation.",
                         "播放第三人称据枪动画。")
                .translation("config.btp.enableThirdPersonTiltAnimation")
                .define("enableThirdPersonTiltAnimation", true);

        ENABLE_ZOOM_WITHOUT_AIM = builder
                .comment("Allow TaCZ zoom switch (V key) while not aiming.",
                         "允许不开镜时切换倍率（V 键）。")
                .translation("config.btp.enableZoomWithoutAim")
                .define("enableZoomWithoutAim", true);

        TILT_TRANSITION_TIME_MS = builder
                .comment("Tilt fade-in/out time in ms. (Default: 250)",
                         "据枪淡入/淡出时长（毫秒）。（默认：250）")
                .translation("config.btp.tiltTransitionTimeMs")
                .defineInRange("tiltTransitionTimeMs", 250, 0, 1000);

        builder.pop();

        // ============================================================
        // tilt mode
        // ============================================================
        builder.comment("Tilt trigger mode settings.",
                        "据枪触发模式设置。")
                .push("tilt_mode");

        TILT_TRIGGER_MODE = builder
                .comment("Tilt trigger mode. Case-insensitive.",
                         "Valid values: DEFAULT, LONG_PRESS, ZOOM_STAGE, NONE",
                         "",
                         "据枪触发模式。大小写不敏感。",
                         "可选值：DEFAULT、LONG_PRESS、ZOOM_STAGE、NONE")
                .translation("config.btp.tiltTriggerMode")
                .defineEnum("tiltTriggerMode", TiltTriggerMode.DEFAULT);

        ENABLE_HOLD_TILT = builder
                .comment("Enable the hold-to-tilt key (HOLD_TILT_KEY in Controls settings).",
                         "The key can be bound to either a keyboard key or a mouse button.",
                         "Disable this to turn off the hold key entirely; other tilt trigger",
                         "modes remain unaffected.",
                         "启用按住据枪键（控制设置中的 HOLD_TILT_KEY）。",
                         "该键可绑定到键盘按键或鼠标按钮。",
                         "关闭后将完全禁用按住据枪键，其他据枪触发模式不受影响。")
                .translation("config.btp.enableHoldTilt")
                .define("enableHoldTilt", true);

        INTERRUPT_ON_TOGGLE = builder
                .comment("(DEFAULT) Toggling instantly stops current action instead of auto-entering new mode.",
                         "（DEFAULT）切换时立即停止当前动作，而非自动进入新模式。")
                .translation("config.btp.interruptOnToggle")
                .define("interruptOnToggle", false);

        SHOW_LONG_PRESS_TILT_MESSAGES = builder
                .comment("(LONG_PRESS) Show aim on/off and tilt on messages.",
                         "（LONG_PRESS）显示瞄准开/关和据枪开启的消息。")
                .translation("config.btp.showLongPressTiltMessages")
                .define("showLongPressTiltMessages", true);

        LONG_PRESS_THRESHOLD = builder
                .comment("(LONG_PRESS) Right-click long press threshold in ms.",
                         "（LONG_PRESS）右键长按判定阈值（毫秒）。")
                .translation("config.btp.longPressThreshold")
                .defineInRange("longPressThreshold", 200, 50, 2000);

        builder.pop();

        SPEC = builder.build();
    }

    // ===== public 快照 =====
    public static boolean disableVanillaCrouchTilt = true;
    public static boolean forceTiltOnAllGuns = true;
    public static boolean breakSprint = true;
    public static double tiltSpreadMultiplier = 0.3;
    public static boolean compatTaczLabsCrosshair = true;
    public static boolean resetToAimOnItemSwitch = true;
    public static String tacticalCrosshair = "DOT_1";
    public static boolean enableThirdPersonTiltAnimation = true;
    public static boolean enableZoomWithoutAim = true;
    public static int tiltTransitionTimeMs = 250;

    public static TiltTriggerMode tiltTriggerMode = TiltTriggerMode.DEFAULT;
    public static boolean enableHoldTilt = true;
    public static boolean interruptOnToggle = false;
    public static boolean showLongPressTiltMessages = true;
    public static int longPressThreshold = 200;

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            reload();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            reload();
        }
    }

    /**
     * 从 {@link ForgeConfigSpec} 重新拉取所有值到静态快照字段。
     *
     * <p><b>为什么是 public</b>：{@code BTPMenuIntegration} 在 Cloth Config 的
     * 保存回调里会主动调用一次。{@code ForgeConfigSpec.save()} 只写盘，不会
     * 同步触发 {@code ModConfigEvent.Reloading}——后者由 {@code ConfigTracker}
     * 的文件监视器异步检测（几十 tick 后）。这段窗口里若玩家立刻据枪，读到的
     * 会是旧值。主动调用本方法消除该窗口，让"保存 → 生效"变成同步语义。
     */
    public static void reload() {
        disableVanillaCrouchTilt = DISABLE_VANILLA_CROUCH_TILT.get();
        forceTiltOnAllGuns = FORCE_TILT_ON_ALL_GUNS.get();
        breakSprint = BREAK_SPRINT.get();
        tiltSpreadMultiplier = TILT_SPREAD_MULTIPLIER.get();
        compatTaczLabsCrosshair = COMPAT_TACZLABS_CROSSHAIR.get();
        resetToAimOnItemSwitch = RESET_TO_AIM_ON_ITEM_SWITCH.get();
        tacticalCrosshair = TACTICAL_CROSSHAIR.get();
        enableThirdPersonTiltAnimation = ENABLE_THIRD_PERSON_TILT_ANIMATION.get();
        enableZoomWithoutAim = ENABLE_ZOOM_WITHOUT_AIM.get();
        tiltTransitionTimeMs = TILT_TRANSITION_TIME_MS.get();

        tiltTriggerMode = TILT_TRIGGER_MODE.get();
        enableHoldTilt = ENABLE_HOLD_TILT.get();
        interruptOnToggle = INTERRUPT_ON_TOGGLE.get();
        showLongPressTiltMessages = SHOW_LONG_PRESS_TILT_MESSAGES.get();
        longPressThreshold = LONG_PRESS_THRESHOLD.get();
    }
}
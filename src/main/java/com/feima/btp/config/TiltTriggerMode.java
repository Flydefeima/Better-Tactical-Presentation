package com.feima.btp.config;

/**
 * 据枪触发模式。
 *
 * <p>TOML 配置中直接填写枚举名（大小写不敏感）：
 * <pre>
 *   DEFAULT      - B 键 toggle + 右键控制
 *   LONG_PRESS   - 右键短按切瞄准，长按进入据枪
 *   ZOOM_STAGE   - V 键虚拟档位
 *   NONE         - BTP 不接管任何输入（Hold 键仍生效）
 * </pre>
 *
 * <p>Forge 的 {@code defineEnum} 会自动校验该值；非法值回退到默认值
 * 并输出配置纠正日志，无需手动解析。
 *
 * <p><b>本枚举必须保持无客户端依赖</b>——它被 {@link BTPConfig} 引用，
 * 而 BTPConfig 在服务端也会加载。枚举到 {@code TiltMode} 实例的映射
 * 请放在 {@code TiltController} 中。
 */
public enum TiltTriggerMode {
    DEFAULT,
    LONG_PRESS,
    ZOOM_STAGE,
    NONE
}
# Better Tactical Presentation

> **Notice**: This document was created with the assistance of a translator. Please excuse any inaccuracies.
>
> **提示**：本文档使用了翻译器辅助生成，可能阅读时会带来困难。如有不准确之处，敬请谅解。

---

## Overview / 概述

**Better Tactical Presentation** is a client-side enhancement mod for [TACZ (Timeless and Classics Zero)](https://github.com/MCModderAnchor/TACZ). It improves the tactical leaning experience by intercepting mouse events and using Mixins to provide two flexible operation modes.

**Better Tactical Presentation** 是一个为 [TACZ（Timeless and Classics Zero）](https://github.com/MCModderAnchor/TACZ) 开发的客户端增强模组，通过接管鼠标事件和 Mixin 注入，优化战术据枪（枪械倾斜）的操作体验。

---

## Features / 功能特点

- **Dual-mode Toggle**: Press `B` (default) to switch between "Aim Mode" and "Lean Mode".
- **Aim Mode**: Right-click to aim down sights (ADS), release to stop.
- **Lean Mode**: Right-click to enter tactical lean, release to recover.
- **Long Press Cooldown**: Long-press `B` to trigger a cooldown (configurable whether it also toggles mode).
- **Right-click Long Press Lean (optional)**: When enabled, `B` is disabled; short right-click toggles ADS, long right-click triggers lean.
- **Disable Vanilla Crouch Lean**: Option to disable TACZ's default "crouch to lean" behavior.
- **Break Sprint**: Automatically disables sprinting while leaning.
- **Customizable Messages**: Screen messages for mode switching, cooldown, and lean status (configurable).

- **双模式切换**：按下 `B` 键（默认）在“瞄准模式”与“倾斜模式”间切换。
- **瞄准模式**：右键按下开镜，松开即关镜。
- **倾斜模式**：右键按下进入据枪（枪械倾斜），松开恢复。
- **长按切换键触发冷却**：长按 `B` 键可触发冷却，防止误触（可配置是否同时切换模式）。
- **右键长按据枪（可选）**：启用后 `B` 键失效，右键短按切换开镜，长按进入据枪。
- **禁用原版蹲下倾斜**：可关闭 TACZ 原版的“蹲下自动倾斜”功能。
- **打断疾跑**：据枪状态下自动禁用疾跑。
- **消息提示**：切换模式、冷却、据枪状态均有屏幕消息反馈（可配置）。

> All keys and parameters are adjustable via the config file. / 所有按键和参数均可通过配置文件调整。

---

## Configuration / 配置说明

Config file: `.minecraft/config/btp-common.toml`

配置文件位于 `.minecraft/config/btp-common.toml`

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `interruptOnToggle` | boolean | `false` | Instantly interrupt current action when toggling modes |
| `longPressThreshold` | integer | `450` | Long press detection time (milliseconds) |
| `cooldownDuration` | integer | `3000` | Cooldown duration after long-pressing `B` (`0` to disable) |
| `enableLongPressLean` | boolean | `false` | Enable right-click long press lean (disables `B` toggle) |
| `disableVanillaCrouchLean` | boolean | `true` | Disable TACZ's default crouch-to-lean behavior |
| `showLongPressLeanMessages` | boolean | `true` | Show screen messages for right-click long press lean |
| `longPressTriggersToggle` | boolean | `false` | Whether long-pressing `B` also toggles mode |
| `breakSprint` | boolean | `true` | Force disable sprinting while leaning |

## Dependencies / 依赖要求

- **Minecraft**: 1.20.1
- **Forge**: 47.x (recommended 47.3.0 or higher)
- **TACZ**: 1.1.5 or higher (for 1.20.1 only)

> This mod is **client-side only**. It is not required on the server.
>
> 本模组 **仅客户端** 需要安装，服务端非必需。

---

## License / 开源许可

GNU General Public License v3.0

---

## Credits / 致谢

- TACZ Team for the excellent gun framework.
- Sweda666 for providing code support for the lean spread feature.
- All testers and users who provided feedback.

- TACZ 团队提供了优秀的枪械框架。
- Sweda666 为据枪散布功能提供了代码支持。
- 所有测试人员与反馈用户。

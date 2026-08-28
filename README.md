# Better Tactical Presentation

> **Notice**: This document was created with the assistance of a translator. Please excuse any inaccuracies.

---

## Overview / 概述

**Better Tactical Presentation** is a client-side enhancement mod for [TACZ](https://github.com/MCModderAnchor/TACZ). It improves the tactical leaning experience by intercepting mouse events and using Mixins to provide two flexible operation modes.

**Better Tactical Presentation** 是一个为 [TACZ](https://github.com/MCModderAnchor/TACZ) 开发的客户端增强模组，通过接管鼠标事件和 Mixin 注入，优化战术据枪（枪械倾斜）的操作体验。

---

## Features / 功能特点

- **Dual-mode Toggle**: Press B (default) to switch between "Aim Mode" and "Lean Mode".
- **Lean Mode**: Right-click to enter tactical lean, release to recover.
- **Right-click Long Press Lean (optional)**: When enabled, B is disabled; short right-click toggles ADS, long right-click triggers lean.
- **Disable Vanilla Crouch Lean**: Option to disable TACZ's default "crouch to lean" behavior.
- **Break Sprint**: Automatically disables sprinting while leaning (also interrupts sprint immediately on long press start).
- **Third-Person Lean Animation**: Optional third-person lean animation (requires [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator).
- **TaCZ-Labs Compatibility**: Crosshair automatically hides when leaning.
- **Auto Exit on GUI Open**: Automatically exits lean when any GUI (inventory, chat, etc.) is opened.
- **双模式切换**：按下 B 键（默认）在“瞄准模式”与“倾斜模式”间切换。
- **倾斜模式**：右键按下进入据枪（枪械倾斜），松开恢复。
- **右键长按据枪（可选）**：启用后 B 键失效，右键短按切换开镜，长按进入据枪。
- **禁用原版蹲下倾斜**：可关闭 TACZ 原版的“蹲下自动倾斜”功能。
- **打断疾跑**：据枪状态下自动禁用疾跑（长按开始时立即打断）。
- **第三人称据枪动画**：可选的第三人称据枪动画，（需要 [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator)）。
- **[TaCZ-Labs](https://github.com/Txt-Text/TaCZ-Labs/) 兼容**：据枪时自动隐藏准星。
- **GUI 自动退出**：打开任何界面（背包、聊天等）时自动退出据枪。

> All keys and parameters are adjustable via the config file. / 所有按键和参数均可通过配置文件调整。

---

## Configuration / 配置说明

Config file: `.minecraft/config/btp-client.toml`

配置文件位于 `.minecraft/config/btp-client.toml`

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `interruptOnToggle` | boolean | `false` | Instantly interrupt current action when toggling modes |
| `longPressThreshold` | integer | `200` | Long press detection time (milliseconds) |
| `enableLongPressLean` | boolean | `false` | Enable right-click long press lean (disables `B` toggle) |
| `disableVanillaCrouchLean` | boolean | `true` | Disable TACZ's default crouch-to-lean behavior |
| `showLongPressLeanMessages` | boolean | `true` | Show screen messages for right-click long press lean |
| `breakSprint` | boolean | `true` | Force disable sprinting while leaning |
| `compatTaczLabsCrosshair` | boolean | `true` | If enabled, TaCZ:Labs crosshair will auto-hide when leaning. |
| `leanSpreadMultiplier` | float | `0.3` | Spread multiplier while leaning (0.0~2.0) |
| `resetToAimOnItemSwitch` | boolean | `true` | Reset to aim mode when switching items. |
| `enableThirdPersonLeanAnimation` | boolean | `true` | Enable third-person lean animation (requires playeranimator) |
| `tacticalCrosshair` | string | `"DOT_1"` | Crosshair type while leaning (leave empty to disable override) |
---

Dependencies / 依赖要求

- **Minecraft**: 1.20.1
- **Forge**: 47.x (recommended 47.3.0 or higher)
- For BTP versions below 1.2.0: Requires [TaCZ](https://github.com/MCModderAnchor/TACZ) 1.1.8+ and [TaCZ Tweaks](https://github.com/MUKSC/TaCZTweaks) 3.0.0-alpha.6+.
- For BTP versions 1.2.0 and above: Requires [TaCZ](https://github.com/MCModderAnchor/TACZ) 1.1.5+.
- [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator) (optional): Required for third-person lean animation

> This mod is **client-side only**. It is not required on the server.
>
> 本模组 **仅客户端** 需要安装，服务端非必需。

---

## License / 开源许可

GNU General Public License v3.0

---

## Credits / 致谢

- [TaCZ](https://github.com/MCModderAnchor/TACZ) Team for the excellent gun framework.
- [Sweda](https://github.com/Sweda666) for providing technical assistance.
- All testers and users who provided feedback.
- [TaCZ](https://github.com/MCModderAnchor/TACZ) 团队提供了优秀的枪械框架。
- [Sweda](https://github.com/Sweda666) 提供的技术支持。
- 所有测试人员与反馈用户。

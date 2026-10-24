# Better Tactical Presentation

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange.svg)](https://files.minecraftforge.net/)
[![Environment](https://img.shields.io/badge/Environment-Client--side-blue.svg)]()
[![License](https://img.shields.io/badge/License-GPL--3.0-lightgrey.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![TaCZ](https://img.shields.io/badge/TaCZ-1.1.5%2B-red.svg)](https://github.com/MCModderAnchor/TACZ)

> **Notice**: This document was created with the assistance of a translator. Please excuse any inaccuracies.

---

## Overview / 概述

**Better Tactical Presentation** is a client-side enhancement mod for [TACZ](https://github.com/MCModderAnchor/TACZ). It improves the tactical tilting experience by intercepting mouse events and using Mixins to provide several flexible trigger modes.

**Better Tactical Presentation** 是一个为 [TACZ](https://github.com/MCModderAnchor/TACZ) 开发的客户端增强模组，通过接管鼠标事件和 Mixin 注入，优化战术据枪（枪械倾斜）的操作体验。

---

## Features / 功能特点

- **Four Tilt Trigger Modes** (configurable): Default, Long Press, Zoom Stage, None.
  - **Default**: Press B (default) to toggle between "Aim Mode" and "Tilt Mode". Right-click to enter tilt, release to recover.
  - **Long Press**: B is disabled; short right-click toggles ADS, long right-click triggers tilt.
  - **Zoom Stage**: Treats "Tactical Tilt" as the final zoom stage; pick it with the zoom key, then right-click to enter tilt.
  - **None**: BTP does not take over any input (Hold key still works).
- **Hold Tilt Key**: An extra trigger available in every mode.
- **Zoom Without Aiming**: Allows TaCZ scope zoom switching (V key) while not aiming, including gamepad support.
- **Zoom Level Display**: In Zoom Stage mode, pressing V shows the current zoom number; entering tilt shows "Tactical Tilt".
- **Disable Vanilla Crouch Tilt**: Option to disable TACZ's default "crouch to tilt" behavior.
- **Break Sprint**: Automatically disables sprinting while tilting.
- **Third-Person Tilt Animation**: Optional third-person tilt animation with smooth fade-in/out (requires [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator)).
- **TaCZ:Labs Compatibility**: Crosshair automatically hides when tilting.
- **Auto Exit on GUI Open**: Automatically exits tilt when any GUI (inventory, chat, etc.) is opened.
- **Remote Player Sync**: Other players' tilt animations are visible in multiplayer.
- **In-Game Config Screen**: Powered by [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config); shows a hint page if it is missing.

- **四种据枪触发模式**（可配置）：默认、长按、虚拟倍率档、禁用。
  - **默认**：按下 B 键（默认）在“瞄准模式”与“倾斜模式”间切换；右键按下进入据枪，松开恢复。
  - **长按**：B 键失效；右键短按切换开镜，长按进入据枪。
  - **虚拟倍率档**：将“战术据枪”作为最后一个倍率档位；缩放键选中，右键进入据枪。
  - **禁用**：BTP 不接管任何输入（Hold 键仍可用）。
- **Hold Tilt 键**：所有模式都可用的额外触发方式。
- **不开镜切换倍率**：允许在未开镜时按 V 键切换 TaCZ 瞄准镜倍率，含手柄支持。
- **倍率显示**：虚拟倍率档模式下，按 V 会显示当前倍率数字；进入据枪档显示“战术据枪”。
- **禁用原版蹲下倾斜**：可关闭 TACZ 原版的“蹲下自动倾斜”功能。
- **打断疾跑**：据枪状态下自动禁用疾跑。
- **第三人称据枪动画**：可选的第三人称据枪动画，支持平滑淡入淡出（需要 [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator)）。
- **[TaCZ:Labs](https://github.com/Txt-Text/TaCZ-Labs/) 兼容**：据枪时自动隐藏准星。
- **GUI 自动退出**：打开任何界面（背包、聊天等）时自动退出据枪。
- **远程玩家同步**：多人游戏中可以看到其他玩家的据枪动画。
- **游戏内配置界面**：由 [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) 提供；未安装时会显示提示页。

> All keys and parameters are adjustable via the config file or the in-game config screen. / 所有按键和参数均可通过配置文件或游戏内配置界面调整。

---

## Configuration / 配置说明

Config file: `.minecraft/config/btp-client.toml`

配置文件位于 `.minecraft/config/btp-client.toml`

### General / 通用

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `disableVanillaCrouchTilt` | boolean | `true` | Disable TACZ's default crouch-to-tilt behavior |
| `breakSprint` | boolean | `true` | Force disable sprinting while tilting |
| `tiltSpreadMultiplier` | float | `0.3` | Spread multiplier while tilting (0.0~2.0) |
| `compatTaczLabsCrosshair` | boolean | `true` | If enabled, TaCZ:Labs crosshair will auto-hide when tilting |
| `resetToAimOnItemSwitch` | boolean | `true` | Reset to aim mode when switching items |
| `tacticalCrosshair` | string | `"DOT_1"` | Crosshair type while tilting (leave empty to disable override) |
| `enableThirdPersonTiltAnimation` | boolean | `true` | Enable third-person tilt animation (requires PlayerAnimator) |
| `enableZoomWithoutAim` | boolean | `true` | Allow TaCZ zoom switching (V key) while not aiming |
| `tiltTransitionTimeMs` | integer | `250` | Fade duration for tilt entry/exit (0 = instant, range 0~1000) |

### Tilt Mode / 据枪模式

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `tiltTriggerMode` | enum | `DEFAULT` | Trigger mode: `DEFAULT`, `LONG_PRESS`, `ZOOM_STAGE`, `NONE` |
| `interruptOnToggle` | boolean | `false` | (DEFAULT) Instantly interrupt current action when toggling modes |
| `showLongPressTiltMessages` | boolean | `true` | (LONG_PRESS) Show screen messages for right-click long press tilt |
| `longPressThreshold` | integer | `200` | (LONG_PRESS) Long press detection time (range 50~2000 ms) |

---

## Dependencies / 依赖要求

- **Minecraft**: 1.20.1
- **Forge**: 47.x (recommended 47.3.0 or higher)
- **[TaCZ](https://github.com/MCModderAnchor/TACZ)**: 1.1.5+
- **[PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator)** (optional): Required for third-person tilt animation
- **[TaCZ:Labs](https://github.com/Txt-Text/TaCZ-Labs/)** (optional): Crosshair compatibility
- **[Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config)** (optional): In-game config screen

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
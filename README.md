# Better Tactical Presentation

A Minecraft Forge 1.20.1 mod that adds a toggle key (B) and right-click long-press for [TACZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)'s tilt/lean gun mechanic, with configurable spread reduction while leaning.

一个为 [TACZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) 添加战术据枪切换（B 键）和右键长按据枪的 Minecraft Forge 1.20.1 模组，据枪时可缩窄子弹散布。

---

## ✨ Lean Spread Reduction · 据枪散布缩窄

Press **B** to enter tactical lean mode. While leaning and firing, bullet spread is reduced by a configurable multiplier.

按 **B 键**进入战术据枪模式，据枪时开枪子弹散布缩小，倍率可配置。

### Config · 配置项 `leanSpreadMultiplier`

**Path** `.minecraft/config/btp-common.toml`：

```toml
[general]
# Lean spread multiplier (0.0 ~ 2.0, default 0.5 = no change)
# < 1.0 narrows spread, > 1.0 widens spread
# 据枪时子弹散布倍率，< 1.0 缩窄散布，> 1.0 增大散布
leanSpreadMultiplier = 1.0
```

### How to Use · 使用方式

1. Hold a gun, press **B** to switch to Lean Mode (message: `Lean Mode`)
2. Hold **right-click** to enter lean stance
3. Fire — spread is automatically reduced
4. Press **B** again to switch back to Aim Mode

---

1. 手持枪械，按 **B** 切换至「倾斜模式」
2. 按住**右键**进入据枪姿态
3. 开枪 — 散布自动缩窄
4. 再按 **B** 切回「瞄准模式」

> Also works in right-click long-press mode (`enableLongPressLean = true`).
> 长按右键据枪模式下同样生效。

---

## ⚙️ All Config Options · 全部配置项

| Config · 配置 | Default · 默认 | Description · 说明 |
|---|---|---|
| `interruptOnToggle` | false | Interrupt current action on toggle · 切换时打断动作 |
| `longPressThreshold` | 450 | Long press threshold (ms) · 长按判定时间 |
| `cooldownDuration` | 3000 | Cooldown duration (ms) · 冷却时长 |
| `enableLongPressLean` | false | Right-click long press lean · 右键长按据枪 |
| `disableVanillaCrouchLean` | true | Disable TACZ vanilla crouch lean · 禁用原版蹲下据枪 |
| `showLongPressLeanMessages` | true | Show lean messages · 显示提示消息 |
| `longPressTriggersToggle` | false | Long press B toggles mode · 长按切换模式 |
| `breakSprint` | true | Break sprint while leaning · 据枪时打断疾跑 |
| **`leanSpreadMultiplier`** | **1.0** | **Lean spread multiplier (0.0~2.0) · 据枪散布倍率** |

## 📦 Install · 安装

1. Minecraft Forge 1.20.1 (47.3.0+)
2. [TACZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) 1.1.5+
3. Drop `BetterTacticalPresentation-forge-1.3.1.jar` into `.minecraft/mods/`

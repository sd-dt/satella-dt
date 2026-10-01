<div align="center">

<img src="icon.png" width="160" alt="satella-dt">

# satella-dt

**A modified build based on `satella-mc26.2-1.0.0+mc26.2`**

Auto trading · Auto crafting / stonecutting · Item protection · Better crossbow · Three trident-interaction modes · Offhand food · Enchantment glint · `/st` locator

Current version: **1.0.0+mc26.2-dt261001b** | Minecraft: **26.2** + Fabric | License: **CC0-1.0** | Updated: 2026-10-01

[中文说明](README.md) ｜ **English**

Repository: `https://github.com/sd-dt/satella-dt`

</div>

---

## What is this

**Satella** is a client-side Fabric utility mod (its internal mod id is still `satella`) that bundles automatic villager trading together with a set of automation helpers.

This repository is a **modified build based on `satella-mc26.2-1.0.0+mc26.2`**. The source tree targets Minecraft **26.2** (since 26.x, official names *are* the runtime names — no mappings needed) and is built with Fabric Loom + JDK 25.

> Everything is implemented client-side — **nothing has to be installed on the server**. Please follow the rules of the server you play on.

## Features

### Auto trading

| Feature | Description |
|---|---|
| **Background auto trading** | Configure the items you want and the mod buys from villagers without you keeping the trade screen open; supports one-shot and automatic polling modes |
| **Target tracking** | Trade targets are tracked by villager UUID, so highlighting and state survive a relog |
| **Timed trade-screen refresh** | Closes the container every N game ticks so the server refreshes its offers (0 = never) |
| **Trade GUI toggle** | Show the real merchant screen, or hide it and trade in the background |
| **Output handling** | Trade results can be dropped automatically |

### Automation (crafting / stonecutting)

| Feature | Description |
|---|---|
| **Fully automatic crafting** | Scans the inventory → builds a material table → fills the gaps → takes results out in a loop; materials are accounted per stack, and a half stack is only taken when it does not exceed the gap |
| **Fully automatic stonecutting** | Cuts the configured input item into the configured output and drops it |
| **Residual crafting** | Folds "crafting leftovers" into the automation cycle: stacks below the configured threshold are skipped |
| **GUI toggle** | The crafting table / stonecutter screen can be shown or hidden (hidden = runs in the background) |

### Item protection

| Feature | Description |
|---|---|
| **Block dropping of chosen items** | Whitelist semantics: listed items can only be dropped by "pick them up and click outside the screen"; hand Q / Ctrl+Q, inventory and container Q / Ctrl+Q, and creative-inventory drops are all blocked |

### Combat helpers

| Feature | Description |
|---|---|
| **Better crossbow** | Holding right-click with a crossbow keeps using it and re-clicks on a configurable interval; firing pauses automatically while a container is open or while litematica-printer is restocking shulkers |
| **Three trident-interaction modes** | Tool first / Interactable block first / Place block first (see below) |
| **Offhand food** | With a riptide trident in the main hand and food in the offhand, "Eat first" suppresses riptide and lets vanilla eat; you will not shoot forward right after finishing a meal |

### Interaction and glint

| Feature | Description |
|---|---|
| **Trident interaction** | **Tool first** — always use the tool (riptide trident / charged crossbow / bow with arrows) in front of any block; **Interactable block first** (default) — interactable blocks are used normally, only non-interactable blocks trigger riptide; **Place block first** — no riptide, block interaction is left to vanilla and the offhand block is placed as usual |
| **Enchantment glint colour** | 19 presets (including rainbow, vanilla-soft and off) |
| **Glint shape** | **Cool** — the glint is kept on the bright grid lines only (patterned), which removes the flicker that custom models such as face textures show around the eyes; **Default** — the full glint film |

### `/st` locator

A client-side biome and structure locator (a port of the Datapack Map algorithm): `/st anystructure` and `/st anybiome` for nearby multi-structure / multi-biome queries, multi-ring rules (seed + datapack subset), a visual rule editor (`/st rules`), and Xaero waypoint strings in the output that can be imported with a click.

### Compatibility

| Feature | Description |
|---|---|
| **Server quick-shulker compatibility** | Makes Item Scroller / Inventory Profiles Next / Tweakeroo ignore selected shulker components (default `minecraft:custom_data`); the list lives in `config/satella/ignored-components.txt` |
| **Printer restock pause** | Detects when litematica-printer is doing a "quick shulker auto-restock" and pauses crossbow firing meanwhile |
| **Chinese / English** | Ships both `zh_cn` and `en_us` language files |

## Changes compared to `satella-mc26.2-1.0.0+mc26.2`

| Change | Description |
|---|---|
| **Trident interaction: two modes → three modes** | The old "tool interaction priority" (tool first / interact first) became "trident interaction": Tool first / **Interactable block first** (default, closest to vanilla) / **Place block first** (no riptide, offhand block wins); existing config values are migrated automatically |
| **New "offhand food" option** | "Riptide first / Eat first", sitting right below the trident option; eating is handled entirely by vanilla, so you no longer get an eating animation that never fills you up (or a riptide when you stop) |
| **On-land riptide compensation** | With a riptide enchantment but out of water, vanilla `use()` returns FAIL early and the release packet never goes out; the mod now enters the using state so servers that allow on-land riptide work properly |
| **Armor glint fix** | Removed the override that applied the **item** glint texture to **armor**; armor glint is fully vanilla again (item colours and shapes are unaffected) |
| **Localisation** | Language files completed; the new trident modes and offhand-food option have both Chinese and English keys |

> The mod itself, its textures and most features come from the original Satella author; this repository only carries the changes listed above.

## Installation

1. Minecraft **26.2** + Fabric Loader (≥ 0.19.3).
2. Required: [Fabric API](https://modrinth.com/mod/fabric-api), [MaLiLib](https://modrinth.com/mod/malilib) (`>= 0.29.2- < 0.30.0-`, provides the config screen and hotkey framework).
3. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) (config entry point), [Item Scroller](https://modrinth.com/mod/item-scroller) / [Tweakeroo](https://modrinth.com/mod/tweakeroo) / Inventory Profiles Next (only affect the server quick-shulker compatibility option).
4. Drop the newest **`satella-dt-*.jar`** from the Releases page (or `build\libs\satella-mc26.2-*.jar` if you built it yourself — not the `-sources` one) into `.minecraft/mods/`. **Only one satella build may be enabled at a time.**

## Building from source

This repository is a standard Fabric Loom project (no mappings needed for 26.2):

```powershell
# JDK 25 is required (26.2 uses class file version 69)
$env:JAVA_HOME = '<path to your JDK 25>'
.\gradlew.bat build --no-daemon --console=plain
# output: build\libs\satella-mc26.2-<version>.jar
```

Dependency versions live in `gradle.properties`: `minecraft_version=26.2`, `loader_version=0.19.3`, `fabric_api_version=0.161.0+26.2`, `malilib_version=0.29.6`, `modmenu_version=20.0.1`.
The Gradle wrapper points at the Tencent mirror (`mirrors.cloud.tencent.com`); the first build downloads Gradle 9.6 automatically.

## Configuration

In game: **MaLiLib config screen → satella-dt** (or the Mod Menu config button). It has three pages: Trading / Automation / Misc.
Config file: `.minecraft/config/satella/Satella.json`. Frequently used entries:

| Key | Description |
|---|---|
| `启用自动交易` / `交易模式` / `交易间隔` | Auto-trading master switch, one-shot vs. polling, polling interval |
| `输入物品1/2` / `输出物品` | Trade filter: display name, item id, or id without the `minecraft:` prefix |
| `全自动合成` group | Automation switch, mode (crafting / stonecutting), cycle, residual threshold |
| `拦截目标物品丢弃` | Item-protection whitelist |
| `更NB的弩` / `更NB的弩周期` | Better crossbow and its interval |
| **`三叉戟交互`** | Tool first / Interactable block first (default) / Place block first |
| **`副手食物`** | Riptide first (default) / Eat first |
| `附魔显示颜色` / `光效形态` | Glint colour and shape (items only) |
| `服务器快捷潜影盒兼容` | Works together with `config/satella/ignored-components.txt` |
| `多环定位规则` | The `/st rules` visual editor |

*(In-game option labels are currently Chinese only; the new trident / offhand-food values have English names as well.)*

> Want to trace the trident decision chain in game? Create an empty `satella-diag.txt` in your game directory — every tool takeover appends one line of detail.

## Credits and license

* Original mod **Satella** by **PetraSM** (this repository is based on `satella-mc26.2-1.0.0+mc26.2`)
* This modified build is maintained by **sd_dt** and **deepseekfl4.1**
* License: **CC0-1.0** (Creative Commons Zero v1.0 Universal, public-domain dedication). You may use, modify and redistribute it freely and without permission; attribution is not required, but crediting the original author is appreciated.

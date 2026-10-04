# Oritech Addons One

**🌐 Language:** [English](README.md) | [简体中文](README.zh-CN.md)

> An Oritech addon that adds a few useful addons and features.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%2026.1.2-brightgreen.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-Required-orange.svg)]()

## 📖 About

**Oritech Addons One** is an addon for [Oritech](https://github.com/Rearth/Oritech) that adds:

- **Three tiers of extension addons**: they hold Oritech's addons and forward all of their effects to the machine they are connected to;
- **Wireless versions** of all three tiers: they do not have to touch the machine and provide their addon effects from any distance;
- **Five brand-new addons**: warehouse, tank, chunk loading and item transfer.

## 📦 What it adds

| Block | What it does |
| --- | --- |
| **Extension Addon Type I / II / III** | Hold Oritech's addons and forward their effects to the machine they are connected to |
| **Wireless Extension Addon Type I / II / III** | The same three tiers, but linked to a machine with the Target Designator instead of attached to it |
| **Warehouse Addon** | +16 slots of capacity for every item slot of the machine |
| **Tank Addon** | +8000 mB of capacity for every tank of the machine |
| **Chunk Anchor Addon** | Force-loads the chunk the machine it is connected to stands in |
| **Transfer Extension Addon** | Makes the six faces of the block it sits in (an extension addon or a wireless one) feed and empty the machine |
| **Transfer Addon** | The same transfer, but the faces are picked and configured on a 3D model of the machine |

And a few extra features:

- install addons on your **Refinery** through a wireless extension addon;
- show the processing speed of the **Atomic Forge**;
- let the **Atomic Forge** process in parallel by installing an **Auxiliary Processing Chamber Addon** on the **Enderic Lasers** that charge it.

## ⚙️ Configuration

| Option | Default | Range | Meaning |
| --- | --- | --- | --- |
| `type1Slots` | 5 | 1 – 72 | Usable addon slots of Extension Addon Type I |
| `type2Slots` | 5 | as above | Usable addon slots of Extension Addon Type II |
| `type3SlotCapacity` | 256 | ≥ 1 | How many addons one slot of Extension Addon Type III holds |
| `transferItemsPerTick` | 64 | 1 – 6400 | Items one configured face moves per tick |
| `showWirelessDocksInAddonPage` | true | – | List the machine's wireless addons in Oritech's own addon page (client-side display option) |

## 🎮 Game versions & dependencies

| Branch | Minecraft | NeoForge | Oritech | JDK |
| --- | --- | --- | --- | --- |
| **`26.1.2`** (default) | 26.1.2 | 26.1.2.107 | 2.0.0-exp6 | 25 |
| **`1.21.1`** | 1.21.1 | 21.1.200 or newer | 1.2.12 | 21 |

### Required

- [Oritech](https://github.com/Rearth/Oritech)

### Optional / Integrations

- [Oritech Things](https://github.com/Rearth/Oritech-Things) — its tiered addons fit into Extension Addon Type I and III.

## 🚀 Installation

1. Install [NeoForge](https://neoforged.net/) for your Minecraft version.
2. Download the correct version of **Oritech** and place it in your `mods` folder.
3. Download **Oritech Addons One** and place it in your `mods` folder.
4. Launch the game and enjoy!

## 🤝 Contributing

Contributions of all kinds are welcome! Feel free to:

- Submit an **Issue** to report bugs or suggest features.
- Open a **Pull Request** to contribute code or improvements.

Please make sure your changes are well-tested before submitting.

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- **Rearth** — for creating the excellent [Oritech](https://github.com/Rearth/Oritech) mod.
- **DeepSeek-V41-Flash** — for providing code assistance during development.

---

*Made with ❤️ for the Oritech community.*

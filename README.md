# Oritech Addons One

**🌐 Language:** [English](README.md) | [简体中文](README.zh-CN.md)

> An Oritech addon that packs many addons into one compact extension addon, makes them wireless, and adds five addons of its own — including two that feed and empty a machine through its own faces.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%2026.1.2-brightgreen.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-Required-orange.svg)]()

## 📖 About

**Oritech Addons One** is an addon for [Oritech](https://github.com/Rearth/Oritech) built around one problem: **a machine only has a few addon slots**. It adds

- **three tiers of extension addons** that hold Oritech's own addons and forward everything they do to the machine they are attached to,
- **wireless versions** of all three tiers, which serve a machine from anywhere in the world,
- and **five addons of its own** for the things Oritech has no addon for: storage, tanks, force loading and item transfer.

## 📦 What it adds

| Block | What it does |
| --- | --- |
| **Extension Addon Type I / II / III** | Hold Oritech's addons and forward them to the machine they are attached to |
| **Wireless Extension Addon Type I / II / III** | The same three tiers, linked to a machine instead of attached to it |
| **Warehouse Addon** | +16 item slots per slot of the machine |
| **Tank Addon** | +8,000 mB per tank of the machine |
| **Chunk Anchor Addon** | Force-loads the chunk of the machine it is attached or linked to |
| **Transfer Extension Addon** | Feeds and empties the machine through the six faces of the block it is put in |
| **Transfer Addon** | The same transfer, with the faces picked on a 3D model of the machine |

### 🧩 Extension addons

- **Type I** takes Oritech's *stat* addons (speed, efficiency, ultimate, processing chamber, capacitor, acceptor) and adds their numbers up. It also takes this mod's warehouse and tank addons.
- **Type II** takes *every other* Oritech addon, including the inventory proxy, and forwards their special, block-type behaviour to the machine as if the addons were attached to it directly.
- **Type III** takes the same stat addons as Type I, but gives every *(category, tier)* pair a **dedicated slot**, with a configurable capacity per slot (256 by default).
- Types I and III work on **addon categories and tiers** instead of block identity, so tiered addons from other addon mods (such as [Oritech Things](https://github.com/Rearth/Oritech-Things)) fit as well.
- The number of slots of Types I and II is configurable (5 by default — see the Configuration section below).
- With a machine acceptor addon inside, an extension addon becomes an energy input of its machine.

### 📡 Wireless extension addons

- Link one with Oritech's **target designator**: shift + right click the dock to save its position, then shift + right click the machine — an addon or a multiblock core of it works too.
- The dock forwards the addons inside it to that machine from any distance, and its screen shows the machine it is bound to, the coordinates it stands at, and whether its chunk is force loaded.
- **The machine's chunk has to be loaded for the link to work** — otherwise nothing is forwarded. Put a **Chunk Anchor Addon** in the dock's reserved slot to keep it loaded.
- Every kind of addon works in a dock exactly as it does in a wired addon, the two transfer addons included.

### 🖥️ The screen

Extension addons and wireless docks share one screen; tabs appear only while the addon they belong to is inside the block.

| Tab | Shown | What it does |
| --- | --- | --- |
| **Plugins** | always | The addon slots of the block |
| **Wireless** | always | The connected machine, its coordinates, its chunk state, and the reserved slot (for a Chunk Anchor Addon) |
| **Item Proxy** | an Oritech *inventory proxy* addon is inside | Makes one face proxy one slot of the machine's inventory |
| **Transfer Extension Addon** | a Transfer Extension Addon is inside | The six faces of the block |
| **Transfer Addon** | a Transfer Addon is inside | The machine as a rotatable 3D model |

A transfer addon **placed in the world** is not a container: it shows only its own page.

### 🔁 Transfer

Both transfer addons move items between a machine and the containers around it, one direction per face:

- a face is **Input** (pulls into the machine), **Output** (pushes out of it), **I+O** (both) or unset;
- a configured face can be switched to **Automation**, which moves items by itself every tick; with it off, the face simply offers its inventory to pipes and hoppers;
- **slot roles are respected** — an Input face only fills the machine's input slots and an Output face only empties its output slots, so automation can neither stuff a product slot nor drain a machine's ingredients;
- the rate is configurable (`transferItemsPerTick`, 64 items per slot and direction per tick by default);
- **Transfer Extension Addon** — put it in an extension addon and the machine becomes reachable through that block's faces; hang it on Oritech's **machine extender** and it configures the extender's six faces. Its page is the cube net of the block.
- **Transfer Addon** — put it in an extension addon, place it on a machine, or hang it on a machine extender. Its page draws the machine it serves as a rotatable **3D model**, and every face of every cell of the structure is configurable on that model.

### 🎯 Item filter

Every configured face has a **Filter** button that opens a filter page modelled on Oritech's own item filter:

- **two filters per face** — one per direction, because what may enter a machine and what may leave it are two different questions;
- **12 slots** per filter, **whitelist or blacklist**;
- optional **NBT** and **data component** matching, so two otherwise identical items can be told apart;
- an empty filter lets everything through, and a face nobody filtered behaves exactly as it did before the filter page existed.

## ⚙️ Configuration

`config/oritechaddonsone-common.toml`:

| Option | Default | Range | Meaning |
| --- | --- | --- | --- |
| `type1Slots` | 5 | 1 – 96 (1.21.1), 1 – 72 (26.1.2) | Usable addon slots of Extension Addon Type I |
| `type2Slots` | 5 | as above | Usable addon slots of Extension Addon Type II |
| `type3SlotCapacity` | 256 | 1 and up | How many addons fit into one slot of Extension Addon Type III |
| `transferItemsPerTick` | 64 | 1 – 6400 | Items one machine slot moves per tick, per direction, per configured face |
| `showWirelessDocksInAddonPage` | true | – | List a machine's wireless docks in Oritech's own addon page (client side display option) |

## 📋 Recipes

### Extension addons

`A` = steel ingot, `B` = machine extender, `D` = ender pearl. All three tiers share one shape — only the middle
ingredient differs.

```text
wired     wireless
ABA       ABA
ACA       ACA
          ADA
```

| Tier | `C` — the middle ingredient |
| --- | --- |
| Type I | processor |
| Type II | flux gate |
| Type III | super AI chip, or an unholy intelligence instead |

### The other addons

| Result | Pattern | Key |
| --- | --- | --- |
| Chunk Anchor Addon | `ABA` / `ACA` / `DED` | A: plastic sheet · B: platinum ingot · C: ender eye · D: processor · E: plating |
| Warehouse Addon | `AAA` / `ABA` / `CDC` | A: plastic sheet · B: chest · C: planks · D: plating |
| Tank Addon | `AAA` / `ABA` / `CDC` | A: plastic sheet · B: portable tank · C: silicon · D: plating |
| Transfer Addon | `" A "` / `BCB` / `BDB` | A: piston · B: plastic sheet · C: processor · D: plating |
| Transfer Extension Addon | `" A "` / `BCB` / `BDB` | A: piston · B: plastic sheet · C: processor · D: machine extender |

- **plating** is any of Oritech's platings (copper, iron, nickel, carbon — the `oritech:plating` tag).
- **planks** is the vanilla planks tag, so any wood works.
- Every recipe also unlocks itself in the recipe book.

## 🎮 Game versions & dependencies

| Branch | Minecraft | NeoForge | Oritech | JDK |
| --- | --- | --- | --- | --- |
| **`26.1.2`** (default) | 26.1.2 | 26.1.2.107 | 2.0.0-exp6 | 25 |
| **`1.21.1`** | 1.21.1 | 21.1.200 or newer | 1.2.12 | 21 |

### Required

- [Oritech](https://github.com/Rearth/Oritech)

### Optional / Integrations

- [Oritech Things](https://github.com/Rearth/Oritech-Things) — its tiered addons fit into Extension Addon Types I and III.

## 🚀 Installation

1. Install [NeoForge](https://neoforged.net/) for your Minecraft version.
2. Download the correct version of **Oritech** and place it in your `mods` folder.
3. Download **Oritech Addons One** and place it in your `mods` folder.
4. Launch the game and enjoy!

## 🧷 Notes

- The extension addons cannot be used by Oritech's addon splicer.
- A wireless dock only works while its machine's chunk is loaded — that is what the Chunk Anchor Addon is for.
- Both refineries get the addon stats panel this mod patches into Oritech's screen, and a machine's wireless docks can be listed in Oritech's own addon page.

## 🤝 Contributing

Contributions of all kinds are welcome! Feel free to:

- Submit an **Issue** to report bugs or suggest features.
- Open a **Pull Request** to contribute code or improvements.

Please make sure your changes are well-tested before submitting; the branch model, the build commands and the
porting rules are described in [CONTRIBUTING.md](CONTRIBUTING.md).

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- **Rearth** — for creating the excellent [Oritech](https://github.com/Rearth/Oritech) mod.
- **DeepSeek-V41-Flash** — for providing code assistance during development.

---

*Made with ❤️ for the Oritech community.*

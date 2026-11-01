# Oritech Addons One（Oritech：插件合一）

**🌐 语言：** [English](README.md) | [简体中文](README.zh-CN.md)

> 一个 Oritech 附属模组：添加实用的机器插件、无线扩展插件和传输工具。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/badge/Release-1.2.2-blue.svg)](https://github.com/Xiao232ming/Oritech-Addons-One/releases/tag/v1.2.2)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%2026.1.2-brightgreen.svg)]()
[![Loader](https://img.shields.io/badge/Loader-NeoForge%20%7C%20Fabric-orange.svg)]()

## 📖 简介

**Oritech Addons One** 是 [Oritech](https://github.com/Rearth/Oritech) 的附属模组，新增三种型号的扩展插件、三个型号的无线版本，以及五个用于存储、区块加载和传输的插件。

1.2.2 还新增了扩展插件Ⅱ型的红石控制页、流体传输、按方块独立的扩展传输设置，以及完整的游戏内配置界面。

## 📦 新增内容

| 方块 | 作用 |
| --- | --- |
| **扩展插件Ⅰ / Ⅱ / Ⅲ型** | 装入 Oritech 插件，并把全部效果转发给所连接的机器 |
| **无线扩展坞Ⅰ / Ⅱ / Ⅲ型** | 同样三个型号，但通过目标标识器连接到机器，不必贴着机器 |
| **仓库插件** | 为机器每个物品槽增加可配置的容量（默认 `+16`，范围 `1–64`） |
| **储罐插件** | 为机器每个流体罐增加可配置的容量（默认 `+8000 mB`，范围 `1000–32000 mB`） |
| **锚点插件** | 强制加载它所在的区块，或它所寄宿的扩展插件 / 扩展坞所在的区块 |
| **扩展传输插件** | 让它所在方块的六个面为机器输入输出；每个方块独立保存六面设置，并同时处理物品与流体 |
| **传输插件** | 在机器的 3D 模型上配置输入输出面；支持物品、流体和储能扩展器 |

额外功能：

- **扩展插件Ⅱ型的红石控制页**：五种红石模式、监控槽位和比较器输出；
- 通过无线扩展坞为你的**精炼厂**安装插件；
- 显示**原子锻造器**的加工速度；
- 在给原子锻造器供能的**末影激光器**上安装**辅助加工室插件**，让其并行加工；
- 目标标识器右键空气时清空存储坐标；
- 在游戏内通过 NeoForge 配置界面修改通用配置，Fabric 侧则通过 **Mod Menu**。

## ⚙️ 配置

通用配置文件为 `config/oritechaddonsone-common.toml`。

| 配置项 | 默认值 | 范围 | 含义 |
| --- | --- | --- | --- |
| `type1Slots` | 5 | 1 – 72 | 扩展插件Ⅰ型可用的插件槽数 |
| `type2Slots` | 5 | 1 – 72 | 扩展插件Ⅱ型可用的插件槽数 |
| `type3SlotCapacity` | 1024 | ≥ 1 | 扩展插件Ⅲ型单个槽位能容纳的插件数量 |
| `warehouseAddonSlots` | 16 | 1 – 64 | 每个仓库插件为机器每个物品槽增加的格数 |
| `tankAddonCapacity` | 8000 | 1000 – 32000 | 每个储罐插件为机器每个流体罐增加的容量（mB） |
| `transferItemsPerTick` | 64 | 1 – 6400 | 每个已配置面每 tick 搬运的物品数 |
| `transferFluidPerTick` | 1000 | 1 – 1000000 | 每个已配置面每 tick 搬运的流体量（mB） |
| `showWirelessDocksInAddonPage` | true | – | 在 Oritech 自带的插件页面中列出该机器的无线扩展坞 |

## 🎮 游戏版本与前置

| 发行版 | Minecraft | 加载器 | Oritech | JDK |
| --- | --- | --- | --- | --- |
| **26.1.2**（默认分支） | 26.1.2 | NeoForge 26.1.2.109+ | 2.0.0-exp11+ | 25 |
| **1.21.1 NeoForge** | 1.21.1 | NeoForge 21.1.200+ | 1.2.12+ | 21 |
| **1.21.1 Fabric** | 1.21.1 | Fabric Loader 0.15.11+ | 1.2.12+ | 21 |

### 必需

- [Oritech](https://github.com/Rearth/Oritech)，以及对应发行版的加载器。
- **26.1.2：** 客户端需要 Athena（Oritech 的前置）。
- **1.21.1（NeoForge 与 Fabric）：** Architectury API 13.0.2+、客户端 Athena 4.x、GeckoLib 4.x。
- **1.21.1 Fabric：** Fabric API、Forge Config API Port 21.1.x。

Oritech 的 Fabric 版本已内置 Team Reborn Energy，无需单独安装。

### 可选 / 联动

- [Oritech Things](https://github.com/Rearth/Oritech-Things) —— 它的分级插件可以放进扩展插件Ⅰ型与Ⅲ型。
- Fabric 侧的 **Mod Menu** —— 提供游戏内配置界面。

## 🚀 安装方法

1. 为你的 Minecraft 版本安装 **NeoForge** 或 **Fabric**。
2. 下载与发行版对应的 jar：
   - `oritechaddonsone-1.2.2+mc26.1.2.jar` —— Minecraft 26.1.2 / NeoForge；
   - `oritechaddonsone-neoforge-1.2.2+mc1.21.1.jar` —— Minecraft 1.21.1 / NeoForge；
   - `oritechaddonsone-fabric-1.2.2+mc1.21.1.jar` —— Minecraft 1.21.1 / Fabric。
3. 安装 **Oritech** 及上面列出的必需前置。
4. 把下载的 jar 放入 `mods` 文件夹并启动游戏。
5. Fabric 侧如需在游戏内修改配置，请安装 **Mod Menu**。

## 🤝 贡献

欢迎任何人以任何形式贡献！

- 提交 **Issue** 反馈 Bug 或建议新功能。
- 提交 **Pull Request** 贡献代码或改进。

提交前请确保你的改动已经过充分测试。

## 📄 许可证

本项目采用 **MIT 许可证** — 详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- **Rearth** — 创造了 [Oritech](https://github.com/Rearth/Oritech) 这个优秀的模组。
- **DeepSeek-V41-Flash** — 为项目开发提供代码帮助。

---

*用 ❤️ 为 Oritech 社区制作。*

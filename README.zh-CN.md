# Oritech Addons One（Oritech：插件合一）

**🌐 语言：** [English](README.md) | [简体中文](README.zh-CN.md)

> 一个 Oritech 附属模组：添加了一些有用的插件和功能。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%2026.1.2-brightgreen.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-Required-orange.svg)]()

## 📖 简介

**Oritech Addons One** 是 [Oritech](https://github.com/Rearth/Oritech) 的附属模组，它新增了：

- **三种型号的扩展插件**：把 Oritech 的插件装进去，并将其全部效果转发给它所连接的机器；
- **无线版本**的三个型号：不必贴着机器，隔着多远都能为那台机器提供插件效果；
- **五个全新插件**：仓库、储罐、区块加载与物品传输。

## 📦 新增内容

| 方块 | 作用 |
| --- | --- |
| **扩展插件Ⅰ / Ⅱ / Ⅲ型** | 装入 Oritech 插件，并把效果转发给它所连接的机器 |
| **无线扩展坞Ⅰ / Ⅱ / Ⅲ型** | 同样三个型号，但不需贴着机器，而是通过目标标识器“连接”到机器 |
| **仓库插件** | 机器每个物品槽 +16 格容量 |
| **储罐插件** | 机器每个储罐 +8000 mB 容量 |
| **锚点插件** | 强制加载它所连接的那台机器所在的区块 |
| **扩展传输插件** | 让它所在方块（扩展插件/扩展坞）的六个面为机器输入输出物品 |
| **传输插件** | 同样的传输，但面是在机器的 3D 模型上选择面进行配置 |

以及额外功能：
- 通过无线扩展坞来为你的精炼厂安装插件；
- 显示原子锻造器的加工速度；
- 通过在末影激光器上安装辅助加工室插件来使原子锻造器能够进行并行加工。

## ⚙️ 配置

| 配置项 | 默认值 | 范围 | 含义 |
| --- | --- | --- | --- |
| `type1Slots` | 5 | 1 – 72 | 扩展插件Ⅰ型可用的插件槽数 |
| `type2Slots` | 5 | 同上 | 扩展插件Ⅱ型可用的插件槽数 |
| `type3SlotCapacity` | 256 | ≥ 1 | 扩展插件Ⅲ型单个槽位能容纳的插件数量 |
| `transferItemsPerTick` | 64 | 1 – 6400 | 每个已配置面每 tick搬运的物品数 |
| `showWirelessDocksInAddonPage` | true | – | 在 Oritech 自带的插件页面里列出该机器的无线扩展坞（客户端显示选项） |

## 🎮 游戏版本与前置

| 分支 | Minecraft | NeoForge | Oritech | JDK |
| --- | --- | --- | --- | --- |
| **`26.1.2`**（默认分支） | 26.1.2 | 26.1.2.107 | 2.0.0-exp6 | 25 |
| **`1.21.1`** | 1.21.1 | 21.1.200 或更高 | 1.2.12 | 21 |

### 必需

- [Oritech](https://github.com/Rearth/Oritech)

### 可选 / 联动

- [Oritech Things](https://github.com/Rearth/Oritech-Things) —— 它的分级插件可以放进扩展插件Ⅰ型与Ⅲ型。

## 🚀 安装方法

1. 为你的 Minecraft 版本安装 [NeoForge](https://neoforged.net/)。
2. 下载对应版本的 **Oritech**，放入 `mods` 文件夹。
3. 下载 **Oritech Addons One**，放入 `mods` 文件夹。
4. 启动游戏即可享用！

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

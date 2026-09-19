# Oritech Addons One（MC 1.21.1 版）

[![Build](https://github.com/Xiao232ming/oritech-addons-one/actions/workflows/build.yml/badge.svg?branch=1.21.1)](https://github.com/Xiao232ming/oritech-addons-one/actions/workflows/build.yml?query=branch%3A1.21.1)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.250-orange)
![Oritech](https://img.shields.io/badge/Oritech-1.2.12-9cf)
![License](https://img.shields.io/badge/license-MIT-green)

**Oritech Addons One** 是一个 Minecraft **1.21.1** + NeoForge **21.1.250** 的附属模组，为
**Oritech 1.2.12** 添加三个插件方块：**扩展插件Ⅰ型**、**扩展插件Ⅱ型**与**扩展插件Ⅲ型**。它们本身就是
Oritech 机器插件，内部带多个插件槽，放入其中的插件效果会叠加并作用于该方块所连接的机器。

> **仓库 / Repository**：<https://github.com/Xiao232ming/oritech-addons-one>。**一个仓库、每个 MC 版本一条分支**：
> 本文件在 **`1.21.1`** 分支（MC 1.21.1 + NeoForge 21.1.250 + Oritech 1.2.12，JDK 21），
> 默认分支是 **`26.1.2`**（MC 26.1.2 + NeoForge 26.1.2.107 + Oritech 2.0.0-exp6，JDK 25）：
>
> ```powershell
> git clone https://github.com/Xiao232ming/oritech-addons-one.git
> git switch 1.21.1        # 默认停在 26.1.2，切过来才是本版本
> ```
>
> 两个版本的注册 API / 渲染管线 / 数据格式差异很大，**源码各自维护、不共用**；
> 分支模型、跨版本移植与发布流程见 `CONTRIBUTING.md`。
>
> **模组显示名**与创造模式标签页叫 **Oritech Addons One**（游戏里看到的名字）；
> **产物文件名**则用全小写无空格的模组 ID：`oritechaddonsone-1.0.0+mc1.21.1.jar`
> （`版本+mc游戏版本`，与**模组 ID / 资源命名空间 / Java 包名**一样都是 `oritechaddonsone`，
> 方便命令行、脚本与自动分发处理）。许可证 **MIT**（见 `LICENSE`）。

## 三种型号 / The three types

| | Ⅰ型 / Type I | Ⅱ型 / Type II | Ⅲ型 / Type III |
|---|---|---|---|
| 方块 ID | `oritechaddonsone:extension_plugin_1` | `oritechaddonsone:extension_plugin_2` | `oritechaddonsone:extension_plugin_3` |
| 中文名 / 英文名 | 扩展插件Ⅰ型 / Extension Plugin Type I | 扩展插件Ⅱ型 / Extension Plugin Type II | 扩展插件Ⅲ型 / Extension Plugin Type III |
| 插件槽 | 5（配置文件可改，1–72） | 5（配置文件可改，1–72） | **按「类别 × 等级」自动生成网格**：**6 列**（速度、效率、终极、加工、储能、适配器，列内同类）× **N 行**（等级由已安装的插件决定：Oritech 自带为等级 1，Oritech Things 为 2–9，故当前实例是 6×9 = **54 格**），最多 12 行（72 格） |
| 单格上限 | 64 | 64 | **可配置，默认 256**（`type3SlotCapacity`，1 ~ 2147483647） |
| 可放入 | **按类别**接受 6 类数值型插件：速度、效率、终极（速度+效率）、加工、储能、适配器。Oritech 自带的 6 种，以及**其它附属模组的同类别分级插件**（如 Oritech Things 的等级 2–9，实测 48 种） | 其余全部 Oritech 插件（实测 12 种：作物过滤器、脉冲超频、复合插件、机器扩展坞、储能扩展坞、流体处理、生物校位、红石控制、精准采集、产量升级、矿场、蒸汽锅炉），以及其它附属模组的特殊插件（含 Oritech Things 的 `cross_dimensional`） | **每格只接受「该类别 + 该等级」的插件**：速度列第 5 行只收等级 5 的速度插件，Oritech 自带的进第 1 行 |
| 格位提示 | — | — | 每个空格里显示**该格对应的那个插件**（类别+等级）的**暗色贴图**作为提示，所以 6×9 网格里每格图标都不同 |
| 红石控制 | — | 放入**控制单元插件**（`oritech:machine_redstone_addon`）后可被红石信号控制：**通电停机器／断电恢复**，机器界面会出现红石面板 | — |
| 生效方式 | 数值合并（速度/效率/储能/输入/舱位/超频刻数，按数量叠加） | 数值合并 **+ 特殊功能转发**（矿场、精准采集、流体、作物过滤、蒸汽锅炉、生物校位等按原版方式生效） | 与Ⅰ型相同，但**单种插件可堆到 256 个** |
| 适配器供能 | 有适配器插件时可作为机器能量输入面（**任意等级**的接收器插件都行） | — | 有适配器插件时可作为机器能量输入面（**任意等级**的接收器插件都行） |
| 侧边贴图 | 无插件口的金属面板 | 面板上**亮蓝色能量条纹**，未连接时变**红色** | 机壳／面板为**创造模式紫色系**，插件口保留 Oritech 原配色（已连接蓝／未连接红） |
| 配方 | `IPI` / `IMI`（4 铁锭 + 处理器 + 机器扩展坞） | `IPI` / `EME`（2 铁锭 + 处理器 + 2 高级计算引擎 + 机器扩展坞） | `IPI` / `EME` / `III`（5 铁锭 + 处理器 + 2 高级计算引擎 + 机器扩展坞） |

三者共有：

| | |
|---|---|
| 外形 | 半砖，**可竖放也可横放**：点方块侧面→竖半砖（随视角旋转），点顶/底面→横半砖（上/下半砖） |
| 物品栏图标 | **横放（台阶）外观**：物品模型挂在横半砖模型上 + `display.gui` 等距变换与 `gui_light: front` |
| 已连接时 | `addon_used=true` 时切换到扩展坞点亮贴图（Ⅱ型同时把能量条纹由红变蓝） |
| 叠加 | 同一格中的**整组插件按数量叠加**：一叠 2 个矿场插件 = 2 份效果 |
| 插件绞接器 | **无法用于插件绞接器**（1.21.1 里中文名仍是“插件绞接器”，代码类是 `ShrinkerBlockEntity`）：绞接器不会消耗这三个方块、也不会读取它们的插件；见下方 mixin 说明 |
| 界面 | 右键打开，插件槽 + 玩家背包；三个型号共用同一套程序化绘制界面（Ⅲ型在空格里画暗色插件提示） |
| 物品提示 | 与 Oritech 自己的插件一致：**按住 Ctrl** 才展开详细说明，平时只显示 Oritech 的「按住Ctrl查看更多信息」 |
| 创造模式 | 自带标签页，同时注入 Oritech 的机械标签页（`oritech:machine_group`） |

## 工作原理 / How it works

1. 把它放在机器的**插件槽**位置（和放普通插件方块一样）。放置时 Oritech 会自动执行一次插件扫描
   （`MachineAddonController#initAddons`）。
2. 右键打开界面放入插件（仅限该型号允许的种类，物品栏与 Shift 点击都会校验）。同一格中的整组插件按数量生效。
   Ⅰ/Ⅲ型放入**机器适配器插件**后还会成为该机器的能量输入面。
3. 每次机器重算插件数据时，本方块会在机器算完自己的插件数据之后（`setControllerPos` 时机）：
   - **数值合并**：把内部插件的速度/效率/储能/输入速率/舱位/超频刻数合并追加到机器的 addon 数据上，
     与「这些插件各自直接装在机器上」等价（同时兼容 Oritech 的 `additiveAddons` 加法模式与乘法模式）；
   - **特殊功能转发**（Ⅱ型）：为每个插件合成一个 `AddonBlock` 并回调机器的
     `getAdditionalStatFromAddon`，因为 Oritech 的机器是通过「方块类型」识别这类插件的。
4. **插件绞接器**：`ShrinkerBlockEntityMixin` 在绞接器 `gatherAddonStats` 的头部把这三个方块从插件列表里剔除。
   1.21.1 的 `MachineAddonController#initAddons` 会把这个列表**同时**用于重建 `getConnectedAddons()`，
   而绞接器 `doShrink()` 正是遍历该字段把插件方块清成空气——所以在列表里剔除它们，就等于绞接器
   既不会消耗它们、也不会把它们的插件算进机器核心，而同一个绞接器上的其他插件照常工作。
5. **能量输入**：方块实体实现 Oritech 的 `EnergyApi.BlockProvider`，并在构造时用
   `EnergyApi.BLOCK.registerBlockEntity(...)` 注册自己的方块实体类型；Oritech 的 NeoForge 桥
   （`NeoforgeEnergyApiImpl`）会自动把已注册类型暴露成 NeoForge 的 `IEnergyStorage` 能力，
   因此 Oritech 自己的电缆与其它模组的能量管道都能给这个方块输入能量。
6. **红石控制**（Ⅱ型 + **控制单元插件**，即 `oritech:machine_redstone_addon`）：把控制单元插件放进Ⅱ型后，
   **给方块通电就停机器、断电恢复**。
   - **信号读取**：由服务端 ticker 每刻轮询 `Level#hasNeighborSignal`，位置取「本方块」**或**「它附着的机器」——
     因为本方块是半砖，拉杆很容易贴在机器上而不是本方块上，只靠 `neighborChanged` 会漏掉那种情况
     （`neighborChanged` 仍保留，用于即时响应）。
   - **转发**：通过机器的 `RedstoneControllable#onRedstoneEvent` 设置其「被红石禁用」状态（与原版控制单元一致）。
   - **机器界面**：原版判定是「机器插件槽位置上的方块 == 控制单元方块」，本方块自然不匹配，所以用
     `UpgradableOritechScreenHandlerMixin` 扩展该判定（`UpgradableOritechScreenHandler#showRedstoneAddon`），
     依据是同步过去的方块状态 `control_unit`（`ExtensionPluginBlock.HAS_CONTROL_UNIT`）。之所以用方块状态而不是读
     方块实体：**机器 GUI 的判定在客户端执行，而方块实体的物品栏不会同步给客户端**。
   - **模式**：控制单元的模式（输入控制 / 比较器输出）存在它自己的方块实体里，而作为物品放进本方块时并没有
     方块实体，所以经本方块生效时等同默认的**输入控制**模式（机器界面上的红石面板是只读显示：火把亮灭、
     信号强度与效果文本）。
   - **取出即释放**：把控制单元插件取出来后会自动解除红石禁用（不会把机器卡在停机状态）；如果机器上
     **直接装着**一个控制单元，则交回给它管，不会被本方块覆盖。

## 与其它附属模组的兼容（分级插件）/ Tiered plugins from other addons

Ⅰ型和Ⅲ型**不按方块身份、而按类别**接受插件，所以别的附属模组加的「同类别、不同等级」插件也能放进来。
分类的解析顺序（`ExtensionPluginType#categoryOf`）：

1. **Oritech 自带的 6 种数值型插件**：按方块精确匹配；
2. **Oritech Things 的类别标签**：查物品标签 `oritechthings:tiered_addon_speed` /`_efficiency`
   /`_efficient_speed` /`_processing` /`_capacitor` /`_acceptor`——这是数据驱动的，
   所以它以后新增等级也会自动被识别；**没有装 Oritech Things 时标签为空，什么都不影响**（不是硬依赖）；
3. **注册名关键字兜底**（供其它附属模组使用）：`efficient_speed`/`ultimate`/`synergy` → 终极；
   `speed` → 速度；`efficiency` → 效率；`processing`/`chamber`/`auxiliary` → 加工；
   `capacitor`/`capacity` → 储能；`acceptor` → 适配器。名字里带 `extender`（如 Oritech 的机器扩展坞 /
   储能扩展坞）的会被排除——它们增加的是插件槽位，无法从本方块内部转发，仍归Ⅱ型。

实测（1.21.1 + Oritech 1.2.12 + Oritech Things 0.0.46）：启动日志会打印分类结果，共 **54** 个方块被归类，
其中 Oritech Things 的 48 个分级插件全部正确归入 6 个类别，`addon_block_cross_dimensional` 与
`oritech:capacitor_addon_extender` 正确保持未归类（仍属Ⅱ型）。Ⅱ型的判定逻辑未变。

### Ⅲ型的「类别 × 等级」网格 / The type III category-and-tier grid

Ⅲ型不再固定 6 格，而是**为每个等级单独开格**，并**按分类整齐排列**（`ExtensionPluginType#type3Slots`）：

- **列 = 类别**（速度 / 效率 / 终极 / 加工 / 储能 / 适配器），**行 = 等级**，等级自上而下递增；
- 等级由已安装的插件决定：Oritech 自带的 6 种没有等级属性 → **等级 1**；Oritech Things 用方块状态属性
  `tier`（2–9）标注等级 → 于是当前是 **6 列 × 9 行 = 54 格**，最多 12 行（72 格，与内部存储上限一致）；
- 每个格子只接受**该类别该等级**的插件，空格显示**那一格对应插件**的暗色图标；
- 界面因此改为**列优先**布局（`ExtensionPluginLayout#ofColumnMajor`：第 i 格在 `column = i / rows`、
  `row = i % rows`），面板高 `114 + 行数 × 18`——9 行时为 **176×276**；
- 未安装某些等级时，网格仍按「所有出现过的等级」整齐排满，缺插件的格子留空且不接受任何物品。

### 接收器（适配器）插件 / Acceptor plugins

判定改为**按类别**：只要方块属于适配器类别（`StatCategory.ACCEPTOR`），**任意等级**都让扩展插件成为
机器的能量输入端点——与放入 Oritech 自带的机器适配器完全等效（能量由本方块直接送入所连机器的储能）。
Oritech Things 的 `addon_block_acceptor_tier_2..9` 因此在Ⅰ/Ⅱ/Ⅲ型里都有同样的供能效果。

## 配置 / Config

配置文件在 `config/oritechaddonsone-common.toml`（首次启动自动生成）：

```toml
#Number of plugin slots of the Extension Plugin Type I.
type1Slots = 5
#Number of plugin slots of the Extension Plugin Type II.
type2Slots = 5
#Capacity of every plugin slot of the Extension Plugin Type III.
type3SlotCapacity = 256
```

- Ⅰ/Ⅱ型分别配置格数，范围 **1–72**；Ⅲ型格数**由已安装插件自动决定**（6 列 × 等级行数），配置的是
  **每格堆叠上限** `type3SlotCapacity`（默认 256，范围 1 ~ 2147483647）。
- **也支持游戏内编辑**：`模组列表 → Oritech Addons One → 配置`（NeoForge 标准配置界面）。
- 每个方块内部**始终保留 72 格存储**，配置只决定"可见可用"的格数，所以把格数调小**不会销毁**里面的插件；
  挖掉方块时会把全部 72 格里的东西都掉出来。Ⅲ型超过 99 个的堆叠用「以数量 1 保存物品 + 平行 int 数组记录真实数量」
  的方式存读档（因为 `ItemStack.CODEC` 的 count 上限是 99）。

## 已知限制 / Limitations

- 控制单元插件经Ⅱ型生效时只能用作**输入控制**（通电停机器）：它的模式与比较器输出依赖它自己的方块实体，
  而作为物品存放时没有方块实体。需要其它模式时请把控制单元直接装在机器上。

- Ⅱ型的特殊功能转发依赖机器实现了 `getAdditionalStatFromAddon`；个别机器对产量类插件有上限，转发时不会二次夹取。
- 物品栏代理插件（`machine_inventory_proxy_addon`）三种型号都放不进去（它自带物品栏能力，无法转发），请直接装在机器上。
- Ⅲ型网格**最多 12 行**（72 格，与内部存储一致）；某个整合包若加入超过 12 个等级，高于 12 的等级不会有专属格
  （低等级优先保留）。另外 9 行时面板高 276 px，窗口很小 / GUI 缩放很大时可能超出屏幕，必要时调低 GUI 缩放。
- Ⅲ型每格上限只在方块自己的格子里生效；鼠标**拖动均分**时原版逻辑按物品自身 64 上限分配，
  超过 64 的部分请用 Shift 点击或分次放置。
- 绞接器 mixin 依赖 Oritech 1.2.12 的 `ShrinkerBlockEntity#gatherAddonStats`；已设为 `required: true` +
  `defaultRequire: 1`，若未来 Oritech 改名/改结构会明确报错而不是静默失效。
- 1.21.1 的 Oritech **没有**「机器之心」插件（26.1.2 版本里有），所以这里不需要排除它。

## 开发 / Development

```powershell
git switch 1.21.1          # 本分支（26.1.2 版源码在 26.1.2 分支）
.\gradlew build            # 产物 build\libs\oritechaddonsone-1.0.0+mc1.21.1.jar
.\gradlew runClient        # 开发客户端（自动带上 Oritech / Architectury / Athena / GeckoLib）
```

- **依赖不用手动准备**：`oritech`、`athena`（Athena CTM）、`geckolib` 与可选的 `oritech-things`
  从 **Modrinth Maven** 解析，`architectury` 从**官方 Architectury Maven** 解析
  （它没有发布到 Modrinth）；版本号与版本 ID 都在 `gradle.properties`，
  换版本与完全离线的做法见 `libs/README.md`；它们**不会**被打进本模组的 jar。
- **JDK 21**（26.1.2 分支用 JDK 25），由 `build.gradle` 的 toolchain 指定。
- 本机两个版本同时放在磁盘上用 **git worktree**（共享同一个 `.git`）：
  `D:\Games\MC\MOD\26.1.2-NeoForge\Oritech Addons One`（26.1.2 分支）与
  `D:\Games\MC\MOD\1.21.1-NeoForge\Oritech Addons One`（1.21.1 分支）；工作区总览见 `D:\Games\MC\MOD\README.md`。
- 1.21.1 的 `neoforge.mods.toml` **仍需要** `modLoader="javafml"` 与 `loaderVersion`（更新的版本已移除这两个字段）。
- 物品模型放在 `assets\oritechaddonsone\models\item\*.json`（1.21.1 没有 `assets\<ns>\items\` 那套物品模型定义）。
- 侧边贴图脚本：`tools\generate-side-texture.ps1`、`tools\generate-plugin3-textures.ps1`（从 Oritech jar 读取贴图重着色）。

### 与 26.1.2 版的移植对照 / Porting notes

| 方面 | 26.1.2 | 1.21.1 |
|---|---|---|
| 资源 ID | `Identifier.fromNamespaceAndPath` | `ResourceLocation.fromNamespaceAndPath` |
| 方块交互 | `useItemOn` 返回 `InteractionResult` | 返回 `ItemInteractionResult`；`useWithoutItem` 返回 `InteractionResult` |
| 物品提示 | 方块实现 `TooltipProvider#addToTooltip` | `Item#appendHoverText(ItemStack, TooltipContext, List, TooltipFlag)`；Ctrl 检测用 `Screen.hasControlDown()` |
| 界面渲染 | 渲染状态化 `GuiGraphicsExtractor#extractBackground` | `AbstractContainerScreen#renderBg(GuiGraphics, float, int, int)` + `GuiGraphics#renderItem` |
| 界面绘制顺序 | 渲染状态 API 保留调用顺序，`fill` 可覆盖已画的物品图标 | `GuiGraphics` **按渲染类型分批**提交，`fill` 与 `renderItem` 分属不同批次、按类型刷新，所以「先画图标再填阴影」的阴影会落在图标**下面**；Ⅲ型提示需要先画图标 → `graphics.flush()` → 再填阴影 |
| 存储读写 | `ValueInput` / `ValueOutput` | `CompoundTag` + `HolderLookup.Provider`（`ContainerHelper` + 平行 int 数组保数量） |
| 能量 API | `neoforge.transfer.energy.EnergyHandler` + `rearth.oritech.api.transfer.energy.*` | `rearth.oritech.api.energy.EnergyApi`（长整型 `EnergyStorage`）+ Oritech 的 NeoForge 桥 |
| 创造标签注入 | `oritech:machine_group` | 同名 `oritech:machine_group`（`ItemGroups.MACHINE_GROUP`） |
| 绞接器类 | `AddonSplicerBlockEntity#gatherAddonStats` | `ShrinkerBlockEntity#gatherAddonStats`（中文名仍是“插件绞接器”） |
| 数值型插件命名 | 协同矩阵 / 辅助加工室 | **终极插件（`MACHINE_ULTIMATE_ADDON`）/ 机器加工插件（`MACHINE_PROCESSING_ADDON`）** |
| 配方语法 | 材料可写裸字符串 `key: "minecraft:iron_ingot"` | 材料需对象形式 `key: {"item": "..."}`；**两版的 `result` 都用物品栈键名 `id`**（`{"id": …, "count": n}`——写成 `item` 会让 `RecipeManager` 报 `No key id in MapLike[...]` 并导致整条配方加载失败） |

## 目录 / Layout

```
src/main/java/com/example/oritechaddonsone/
├── OritechAddonsOne.java                        # 注册方块/方块实体/菜单/标签页/配置 + Oritech 能量注册
├── Config.java                                  # type1Slots / type2Slots / type3SlotCapacity
├── ModEvents.java                               # 注入 Oritech 机械标签页
├── block/ExtensionPluginType.java               # 型号定义、可接受插件集合、Ⅲ型固定格位顺序
├── block/ExtensionPluginBlock.java              # 竖/横半砖外形、右键开界面、破坏掉落、Ctrl 提示明细
├── block/entity/ExtensionPluginBlockEntity.java # 容器 + 白名单 + 数值合并 + 特殊功能转发 + 能量输入
├── item/ExtensionPluginItem.java                # BlockItem：Ctrl 门控的物品提示
├── menu/ExtensionPluginMenu.java / Layout.java  # 容器菜单与界面几何（9 格一行、面板高度计算）
├── mixin/ShrinkerBlockEntityMixin.java          # 让插件绞接器无法使用这三个方块
└── client/                                      # 程序化界面与界面注册（仅客户端）
```

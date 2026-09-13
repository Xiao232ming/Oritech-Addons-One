# Oritech Addons One（MC 1.21.1 版）

**Oritech Addons One** 是一个 Minecraft **1.21.1** + NeoForge **21.1.250** 的附属模组，为
**Oritech 1.2.12** 添加三个插件方块：**扩展插件Ⅰ型**、**扩展插件Ⅱ型**与**扩展插件Ⅲ型**。它们本身就是
Oritech 机器插件，内部带多个插件槽，放入其中的插件效果会叠加并作用于该方块所连接的机器。

> 与名字相关的一切都叫 **Oritech Addons One**：构建产物（`Oritech Addons One-1.0.0.jar`）、
> 模组显示名与创造模式标签页。只有**模组 ID / 资源命名空间 / Java 包名**是全小写无空格的 `oritechaddonsone`。
>
> 本仓库是 1.21.1 版本；26.1.2 版本在 `..\..\26.1.2-NeoForge\Oritech Addons One\`，两者是**互相独立**的仓库，
> 源码各自维护（两个版本的 Forge/Oritech API 差异很大，无法共用）。

## 三种型号 / The three types

| | Ⅰ型 / Type I | Ⅱ型 / Type II | Ⅲ型 / Type III |
|---|---|---|---|
| 方块 ID | `oritechaddonsone:extension_plugin_1` | `oritechaddonsone:extension_plugin_2` | `oritechaddonsone:extension_plugin_3` |
| 中文名 / 英文名 | 扩展插件Ⅰ型 / Extension Plugin Type I | 扩展插件Ⅱ型 / Extension Plugin Type II | 扩展插件Ⅲ型 / Extension Plugin Type III |
| 插件槽 | 5（配置文件可改，1–36） | 5（配置文件可改，1–36） | **固定 6 格：每种插件各占一格**（速度、效率、终极、加工、储能、适配器），格位顺序固定 |
| 单格上限 | 64 | 64 | **可配置，默认 256**（`type3SlotCapacity`，1 ~ 2147483647） |
| 可放入 | 6 种数值型插件：速度升级、效率升级、终极插件、机器加工插件、机器储能插件、机器适配器 | 其余全部 Oritech 插件（实测 12 种：作物过滤器、脉冲超频、复合插件、机器扩展坞、储能扩展坞、流体处理、生物校位、红石控制、精准采集、产量升级、矿场、蒸汽锅炉） | 与Ⅰ型相同的 6 种，且**每种只能放进属于自己的那一格** |
| 格位提示 | — | — | 每个空格里显示该格对应插件的**暗色贴图**作为提示 |
| 生效方式 | 数值合并（速度/效率/储能/输入/舱位/超频刻数，按数量叠加） | 数值合并 **+ 特殊功能转发**（矿场、精准采集、流体、作物过滤、蒸汽锅炉、生物校位等按原版方式生效） | 与Ⅰ型相同，但**单种插件可堆到 256 个** |
| 适配器供能 | 有适配器插件时可作为机器能量输入面 | — | 有适配器插件时可作为机器能量输入面 |
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

- Ⅰ/Ⅱ型分别配置格数，范围 **1–36**；Ⅲ型固定 6 格，配置的是**每格堆叠上限**（默认 256，范围 1 ~ 2147483647）。
- **也支持游戏内编辑**：`模组列表 → Oritech Addons One → 配置`（NeoForge 标准配置界面）。
- 每个方块内部**始终保留 36 格存储**，配置只决定"可见可用"的格数，所以把格数调小**不会销毁**里面的插件；
  挖掉方块时会把全部 36 格里的东西都掉出来。Ⅲ型超过 99 个的堆叠用「以数量 1 保存物品 + 平行 int 数组记录真实数量」
  的方式存读档（因为 `ItemStack.CODEC` 的 count 上限是 99）。

## 已知限制 / Limitations

- Ⅱ型的特殊功能转发依赖机器实现了 `getAdditionalStatFromAddon`；个别机器对产量类插件有上限，转发时不会二次夹取。
- 物品栏代理插件（`machine_inventory_proxy_addon`）三种型号都放不进去（它自带物品栏能力，无法转发），请直接装在机器上。
- Ⅲ型的单格上限只在方块自己的 6 个格子里生效；鼠标**拖动均分**时原版逻辑按物品自身 64 上限分配，
  超过 64 的部分请用 Shift 点击或分次放置。
- 绞接器 mixin 依赖 Oritech 1.2.12 的 `ShrinkerBlockEntity#gatherAddonStats`；已设为 `required: true` +
  `defaultRequire: 1`，若未来 Oritech 改名/改结构会明确报错而不是静默失效。
- 1.21.1 的 Oritech **没有**「机器之心」插件（26.1.2 版本里有），所以这里不需要排除它。

## 开发 / Development

```powershell
cd "D:\Games\MC\MOD\1.21.1-NeoForge\Oritech Addons One"
.\gradlew build        # 产物 build\libs\Oritech Addons One-1.0.0.jar
.\gradlew runClient    # 开发客户端（自动加载 libs\ 里的 oritech/architectury/athena/geckolib）
```

- **JDK 21**（26.1.2 版用 JDK 25）。已在 `~\.gradle\gradle.properties` 登记，无需额外配置。
- `libs\` 需要四个第三方 jar（**不入库**，见 `libs\README.md`）：
  `oritech-neoforge-1.21.1-1.2.12.jar`（`compileOnly` + `localRuntime`）、
  `architectury-13.0.11-neoforge.jar`、`athena-neoforge-1.21.1-4.0.6.jar`、`geckolib-neoforge-1.21.1-4.9.2.jar`（都是 `localRuntime`）。
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
| 存储读写 | `ValueInput` / `ValueOutput` | `CompoundTag` + `HolderLookup.Provider`（`ContainerHelper` + 平行 int 数组保数量） |
| 能量 API | `neoforge.transfer.energy.EnergyHandler` + `rearth.oritech.api.transfer.energy.*` | `rearth.oritech.api.energy.EnergyApi`（长整型 `EnergyStorage`）+ Oritech 的 NeoForge 桥 |
| 创造标签注入 | `oritech:machine_group` | 同名 `oritech:machine_group`（`ItemGroups.MACHINE_GROUP`） |
| 绞接器类 | `AddonSplicerBlockEntity#gatherAddonStats` | `ShrinkerBlockEntity#gatherAddonStats`（中文名仍是“插件绞接器”） |
| 数值型插件命名 | 协同矩阵 / 辅助加工室 | **终极插件（`MACHINE_ULTIMATE_ADDON`）/ 机器加工插件（`MACHINE_PROCESSING_ADDON`）** |
| 配方语法 | `key: "minecraft:iron_ingot"`、`result.id` | `key: {"item": "..."}`、`result.item` |

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

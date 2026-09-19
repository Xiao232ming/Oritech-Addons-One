# Oritech Addons One / 扩展插件Ⅰ型·Ⅱ型·Ⅲ型

[![Build](https://github.com/Xiao232ming/oritech-addons-one/actions/workflows/build.yml/badge.svg?branch=26.1.2)](https://github.com/Xiao232ming/oritech-addons-one/actions/workflows/build.yml?query=branch%3A26.1.2)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue)
![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.107-orange)
![Oritech](https://img.shields.io/badge/Oritech-2.0.0--exp6-9cf)
![License](https://img.shields.io/badge/license-MIT-green)

**Oritech Addons One** 是一个 Minecraft **26.1.2** + NeoForge **26.1.2.107** 的附属模组，为
**Oritech 2.0.0-exp6** 添加三个插件方块：**扩展插件Ⅰ型**、**扩展插件Ⅱ型**与**扩展插件Ⅲ型**。它们本身就是
Oritech 机器插件，内部带多个插件槽，放入其中的插件效果会叠加并作用于该方块所连接的机器。

**Oritech Addons One** is an addon for Minecraft 26.1.2 / NeoForge 26.1.2.107 adding the
**Extension Plugin Type I**, **Type II** and **Type III** blocks for Oritech 2.0.0-exp6: machine
plugins that hold several other plugins and apply their combined effects to the machine they are
attached to.

> **仓库 / Repository**：<https://github.com/Xiao232ming/oritech-addons-one>。**一个仓库、每个 MC 版本一条分支**：
> 本文件在 **`26.1.2`** 分支（默认分支，MC 26.1.2 + NeoForge 26.1.2.107 + Oritech 2.0.0-exp6，JDK 25），
> 另一版本在 **`1.21.1`** 分支（MC 1.21.1 + NeoForge 21.1.250 + Oritech 1.2.12，JDK 21）：
>
> ```powershell
> git clone https://github.com/Xiao232ming/oritech-addons-one.git
> git switch 1.21.1        # 切到 1.21.1 版源码（默认在 26.1.2）
> ```
>
> 两个版本的注册 API / 渲染管线 / 数据格式差异很大，**源码各自维护、不共用**；
> 分支模型、跨版本移植与发布流程见 `CONTRIBUTING.md`。
>
> **模组显示名**与创造模式标签页是 **Oritech Addons One**（游戏里看到的名字）；
> **产物文件名**用全小写无空格的模组 ID：`oritechaddonsone-1.0.0+mc26.1.2.jar`
> （`版本+mc游戏版本`）——与**模组 ID / 资源命名空间** `oritechaddonsone` 一致（Java 包名是 `io.github.xiao232ming.oritechaddonsone`），
> 方便命令行、脚本与自动分发处理。许可证 **MIT**（见 `LICENSE`）。
>
> ⚠️ 方块 ID 变更史：`oritechaddons:extension_addon` → `oritechaddonsone:extension_plugin_1`
> → 现在为 `oritechaddonsone:extension_plugin_1`（Ⅰ型）、`oritechaddonsone:extension_plugin_2`（Ⅱ型）
> 与 `oritechaddonsone:extension_plugin_3`（Ⅲ型）。
> 升级时请删掉旧 jar，并把存档里旧 ID 的方块先挖掉（否则会变成未知方块；挖掉时里面的插件会掉落）。

## 三种型号 / The three types

| | Ⅰ型 / Type I | Ⅱ型 / Type II | Ⅲ型 / Type III |
|---|---|---|---|
| 方块 ID / Block id | `oritechaddonsone:extension_plugin_1` | `oritechaddonsone:extension_plugin_2` | `oritechaddonsone:extension_plugin_3` |
| 中文名 / 英文名 | 扩展插件Ⅰ型 / Extension Plugin Type I | 扩展插件Ⅱ型 / Extension Plugin Type II | 扩展插件Ⅲ型 / Extension Plugin Type III |
| 插件槽 / Slots | 5（可在配置文件里改，1–72） | 5（可在配置文件里改，1–72） | **固定 6 格：每种插件各占一格**（速度、效率、协同矩阵、辅助加工室、机器容量、机器适配器），格位顺序固定 |
| 单格上限 / Stack limit | 64 | 64 | **可配置，默认 256**（`type3SlotCapacity`，1 ~ 2147483647） |
| 可放入 / Accepts | 速度升级、效率升级、协同矩阵、辅助加工室、机器容量、机器适配器 | **除上述 6 种、机器之心、物品栏代理以外的所有 Oritech 插件**（实测 11 种：控制单元、作物过滤器、脉冲超频、机器扩展坞、流体处理、生物校位、精准采集、产量升级、储能扩展坞、矿场、蒸汽锅炉） | 与Ⅰ型相同（上述 6 种数值型插件），且**每种只能放进属于自己的那一格** |
| 格位提示 / Slot hint | — | — | 每个空格里显示该格对应插件的**暗色贴图**作为提示 |
| 红石控制 / Redstone | — | 放入**控制单元插件**后可被红石信号控制：**通电停机器／断电恢复**，机器界面会出现红石面板 | — |
| 生效方式 / Effect | 数值合并（速度/效率/储能/输入/舱位/超频刻数，按数量叠加） | 数值合并 **+ 特殊功能转发**（矿场、精准采集、流体、作物过滤、蒸汽锅炉、生物校位等按原版方式生效） | 与Ⅰ型相同的数值合并，但**单种插件可堆到 256 个**（相当于把一种插件堆满一格） |
| 适配器供能 / Acceptor | 有适配器插件时可作为机器能量输入面 | —（Ⅱ型不放适配器） | 有适配器插件时可作为机器能量输入面 |
| 侧边贴图 / Side texture | 无插件口的金属面板 | 面板上**亮蓝色能量条纹**，未连接时变为**红色**（配色取自 Oritech 扩展坞贴图） | 机壳／面板改为**创造模式紫色系**（参考 Oritech 创造流体罐／创造能量仓的配色），而**插件口保留 Oritech 原本配色**：已连接时是**蓝色**（`#3597AD`/`#3EC9D5`/`#9CFFF5`），未连接时是**红色**（`#FFA7A4`/`#F48686`/`#EC736F`） |
| 配方 / Recipe | `IPI` / `IMI`（4 铁锭 + 处理单元 + 机器扩展坞） | `IPI` / `EME`（2 铁锭 + 处理单元 + 2 高级计算引擎 + 机器扩展坞） | `IPI` / `EME` / `III`（5 铁锭 + 处理单元 + 2 高级计算引擎 + 机器扩展坞） |

三者共有：

| | |
|---|---|
| 外形 / Shape | 半砖，**可竖放也可横放**：点方块侧面→竖半砖（随视角旋转），点顶/底面→横半砖（上/下半砖） |
| 物品栏图标 / Item icon | **横放（台阶）外观**：物品模型挂在横半砖模型上 + `display.gui` 等距变换（40/-45、缩放 0.6）与 `gui_light: front` |
| 已连接时 / When connected | `addon_used=true` 时切换到扩展坞点亮贴图（Ⅱ型同时把能量条纹由红变蓝） |
| 叠加 / Stacking | 同一格中的**整组插件按数量叠加**：一叠 2 个矿场插件 = 2 份效果（Ⅲ型单格可堆到 256，见配置） |
| 插件绞接器 / Addon splicer | **无法用于插件绞接器**：绞接器不会消耗这三个方块、也不会读取它们的插件 |
| 界面 / GUI | 右键打开，插件槽 + 玩家背包；三个型号共用同一套程序化绘制界面（Ⅲ型在空格里画暗色插件提示） |
| 物品提示 / Tooltip | 精简为 3 行，与 Oritech 自己的插件一致：**按住 Ctrl** 才展开详细说明（Ⅰ型「允许将数值型插件放入物品栏」、Ⅱ型「允许将功能型插件放入物品栏」、Ⅲ型「每种数值型插件各占一格，单格上限由配置决定」），平时只显示 Oritech 的「按住Ctrl查看更多信息」。注意：方块实现的 `TooltipProvider` **不会**自动出现在物品提示里，所以三个方块用自定义的 `ExtensionPluginItem` 桥接（与 Oritech `BlockContent` 的做法相同） |
| 创造模式 / Creative | 自带标签页，同时注入 Oritech 的「Oritech 机械」标签页 |

## 工作原理 / How it works

1. 把它放在机器的**插件槽**位置（和放普通插件方块一样）。放置时 Oritech 会自动执行一次插件扫描
   （`MachineAddonController#initAddons`）。
2. 右键打开界面放入插件（仅限该型号允许的种类，物品栏与 Shift 点击都会校验）。
   同一格中的整组插件按数量生效。Ⅰ/Ⅲ型放入**机器适配器插件**后还会成为该机器的能量输入面
   （等同原版适配器插件，能量直接进入机器储能）。Ⅲ型还会**按格位校验**：每种插件只能放进
   属于自己的那一格（格子里有暗色贴图提示），并且每格上限由 `type3SlotCapacity` 决定。
3. 每次机器重算插件数据时，本方块会在机器算完自己的插件数据之后：
   - **数值合并**：把内部插件的速度/效率/储能/输入速率/舱位/超频刻数合并追加到机器的 addon 数据上，
     与「这些插件各自直接装在机器上」等价（同时兼容 Oritech 的 `additiveAddons` 默认加法模式与乘法模式）；
   - **特殊功能转发**（Ⅱ型）：为每个插件合成一个 `AddonBlock` 并回调机器的
     `getAdditionalStatFromAddon`，因为 Oritech 的机器是通过「方块类型」识别这类插件的，
     这样矿场/精准采集/流体/作物过滤/蒸汽锅炉/生物校位等功能就等同直接装在机器上。
4. **红石控制**（Ⅱ型 + **控制单元插件**）：把控制单元插件放进Ⅱ型后，**给方块通电就停机器、断电恢复**。
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
5. **插件绞接器**：`AddonSplicerBlockEntityMixin` 在绞接器评估插件前把这三个方块从列表里剔除，
   所以绞接器既不会消耗它们、也不会把它们的插件算进「机器之心」；同一个绞接器上的其他插件照常工作。

## 配置 / Config

配置文件在 `config/oritechaddonsone-common.toml`（首次启动自动生成）：

```toml
#Number of plugin slots of the Extension Plugin Type I.
#扩展插件Ⅰ型的物品栏格数。
# Default: 5
# Range: 1 ~ 72
type1Slots = 5
#Number of plugin slots of the Extension Plugin Type II.
#扩展插件Ⅱ型的物品栏格数。
# Default: 5
# Range: 1 ~ 72
type2Slots = 5
#Stack limit of every single plugin slot of the Extension Plugin Type III.
#扩展插件Ⅲ型每一格插件槽的堆叠上限。
# Default: 256
# Range: 1 ~ 2147483647
type3SlotCapacity = 256
```

- Ⅰ/Ⅱ型分别配置格数，范围 **1–72**；超出范围会自动夹取到边界。
- Ⅲ型**格数固定为 6**（每种数值型插件一格，格位顺序固定为：速度、效率、协同矩阵、辅助加工室、机器容量、机器适配器），
  它配置的是**每一格的堆叠上限** `type3SlotCapacity`：默认 **256**，范围 **1 ~ 2147483647**。
  这个上限只作用于Ⅲ型方块自己的 6 个格子（通过重写 `Slot#getMaxStackSize` 实现），
  **不会**影响玩家背包或其他容器里插件的 64 上限。
- **也支持游戏内编辑**：`模组列表 → Oritech Addons One → 配置`（NeoForge 标准配置界面，
  已通过 `IConfigScreenFactory` + `ConfigurationScreen` 注册），改完点保存即可，
  下一次打开插件界面就用新格数/新上限（不需要重启，界面上的名称也有中英翻译）。
  手动改文件当然也行，改完保存重启游戏生效。
- 每个方块内部**始终保留 72 格存储**，配置只决定"可见可用"的格数，所以把格数调小**不会销毁**里面的插件——
  调大后原来的插件会重新出现在对应格子里；挖掉方块时也会把全部 72 格里的东西都掉出来。
  （Ⅲ型只用到前 6 格；由于插件是按数量保存的，256 个一组的插件也能正常存读档与掉落。）

## 已知限制 / Limitations

- 控制单元插件经Ⅱ型生效时只能用作**输入控制**（通电停机器）：它的模式与比较器输出依赖它自己的方块实体，
  而作为物品存放时没有方块实体。需要其它模式时请把控制单元直接装在机器上。
- Ⅱ型的特殊功能转发依赖机器实现了 `getAdditionalStatFromAddon`（Oritech 的粉碎机、离心机、锻造台、
   末影激光器、无人机港、发电机等）；个别机器对产量类插件有上限（如产量最多 3），转发时不会二次夹取。
- 机器之心插件与物品栏代理插件三种型号都放不进去（前者只能单独作为机器之心生效，后者自带物品栏能力
   无法转发），请直接装在机器上。
- Ⅲ型的单格上限（默认 256）只在**方块自己的 6 个格子**里生效；用鼠标拖动**批量均分**时，
  原版逻辑按物品自身的 64 上限分配，所以超过 64 的部分请用 Shift 点击或分次放置。
- 绞接器 mixin 依赖 Oritech 2.0.0 的 `AddonSplicerBlockEntity#gatherAddonStats`；已设为
  `required: true` + `defaultRequire: 1`，若未来 Oritech 大改会明确报错而不是静默失效。

## 开发 / Development

```powershell
git switch 26.1.2          # 本分支（1.21.1 版源码在 1.21.1 分支）
.\gradlew build            # 产物 build\libs\oritechaddonsone-1.0.0+mc26.1.2.jar
.\gradlew runClient        # 开发客户端（自动带上 Oritech / Athena / GeckoLib）
.\gradlew runData          # 数据生成
```

- **依赖不用手动准备**：`oritech`（`compileOnly` + `localRuntime`）、`athena`（Athena CTM）与 `geckolib`
  都从 **Modrinth Maven** 解析，坐标（Modrinth 版本 ID）在 `gradle.properties`，
  换版本与完全离线的做法见 `libs/README.md`；它们**不会**被打进本模组的 jar。
- **JDK 25**（1.21.1 分支用 JDK 21），由 `build.gradle` 的 toolchain 指定。
- 本机两个版本同时放在磁盘上用 **git worktree**（共享同一个 `.git`）：
  `D:\Games\MC\MOD\26.1.2-NeoForge\Oritech Addons One`（26.1.2 分支）与
  `D:\Games\MC\MOD\1.21.1-NeoForge\Oritech Addons One`（1.21.1 分支）；工作区总览见 `D:\Games\MC\MOD\README.md`。
- 界面：**没有底图贴图**，`ExtensionPluginScreen` 按格数在运行时画面板与槽位（`ExtensionPluginLayout` 计算 9 格一行、
  面板高度 `114 + 行数 × 18`）；所以格数可配置也无需准备多张贴图。
- 侧边贴图：`tools/generate-side-texture.ps1`（从 Oritech jar 读取扩展坞贴图，抹掉插件口；
  Ⅱ型再叠加蓝/红能量条纹，颜色取自 Oritech 扩展坞贴图的插件口配色）；
  Ⅲ型由 `tools/generate-plugin3-textures.ps1` 在同一套贴图上做**创造紫**重着色：机壳/面板的每种颜色
  按亮度排进紫阶 `#2B0B38`→`#B53FF0`，**插件口的像素不做映射**，所以「已连接＝蓝色 / 未连接＝红色」
  的状态提示和原版扩展坞完全一致。对比图输出到 `build\ref-textures\plugin3_port_states.png`（不随包分发）。
- 配置：`Config.java`（`type1Slots` / `type2Slots`，1–72；`type3SlotCapacity`，1–2147483647，`ModConfig.Type.COMMON`）。
- 方块模型：`assets/oritechaddonsone/models/block/extension_plugin_<n>*.json`。
- mixin 配置：`oritechaddonsone.mixins.json`（`compatibilityLevel: JAVA_25`，与 Oritech 自身一致）。

## 目录 / Layout

```
src/main/java/io/github/xiao232ming/oritechaddonsone/
├── OritechAddonsOne.java                        # 注册三个方块 / 方块实体 / 菜单 / 标签页 / 能量能力 / 配置
├── Config.java                                  # 配置文件：type1Slots / type2Slots（1–72）、type3SlotCapacity（1–2147483647）
├── ModEvents.java                               # 注入 Oritech 机械标签页
├── block/ExtensionPluginType.java               # 型号定义：ID、可接受插件集合、Ⅲ型固定格位顺序（格数由配置决定）
├── block/ExtensionPluginBlock.java              # 竖/横半砖外形、右键开界面、破坏时掉出插件、Ctrl 提示文本
├── block/entity/ExtensionPluginBlockEntity.java # 容器（内部固定 72 格）+ 白名单 + 数值合并 + 特殊功能转发 + 适配器供能
├── item/ExtensionPluginItem.java                # BlockItem：把方块的提示桥接到物品提示（Oritech 同款做法）
├── menu/ExtensionPluginMenu.java                # 容器菜单（按配置的格数生成槽位，Ⅲ型每格限一种插件且上限可配）
├── menu/ExtensionPluginLayout.java              # 由格数计算界面几何（9 格一行、面板高度等）
├── mixin/AddonSplicerBlockEntityMixin.java      # 让插件绞接器无法使用这三个方块
└── client/                                      # 界面（程序化绘制面板与槽位）与界面注册（仅客户端）
```

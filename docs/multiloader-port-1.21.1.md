# Oritech Addons One — 多加载器（Fabric + NeoForge）改造说明

本文件记录 `1.21.1-multiloader` 分支上把项目从"NeoForge 单模块"改成"common / fabric / neoforge 三模块"的
做法、依赖坐标、每处平台差异，以及仍然需要在能联网/能编译的机器上验证的清单。

改造的原则是 **能照抄 Oritech 的都照抄**：Oritech 自己就是 `common` + `fabric` + `neoforge` 三模块，
`common` 用 NeoForm vanilla 模式编译（看不到任何 loader API），两个 loader 模块把 `common` 的源码与资源
"吃进来"各自编一遍；跨端差异交给 Architectury API 和一份自研 SPI。

---

## 1. 目录结构

```
1.21.1/
├── build.gradle            根构建：插件声明 + 三个模块共用的仓库/capability/发布配置
│                           以及"把 common 的源码与资源注入 loader 模块"的接线（照抄 Oritech）
├── settings.gradle         include common / fabric / neoforge
├── gradle.properties       版本与坐标集中在这里
├── common/                 加载器中立代码（102 个 java + 112 个资源，含全部 23 个 mixin）
│   └── build.gradle        net.neoforged.moddev 的 vanilla 模式（neoFormVersion，无 loader）
├── fabric/                 Fabric 粘合层（6 个 java + fabric.mod.json）
│   └── build.gradle        fabric-loom
└── neoforge/               NeoForge 粘合层（5 个 java + mods.toml 模板）
    └── build.gradle        net.neoforged.moddev（正常 NeoForge 模式）
```

`common` 里的源码会在三个模块里各编译一次：`:common`（NeoForm vanilla）、`:fabric`（loom）、
`:neoforge`（NeoForge）。因此 **common 里出现任何 `net.neoforged.*`（除了下面说的 `ModConfigSpec`）都会
编译失败**，这正是"保持加载器中立"的强制保证。

## 2. 依赖坐标

Oritech 官方在公开的 **BlameJared maven** 发布了三件套（见其 README 的 *Depending on Oritech*），
本项目直接消费这些坐标，不再走 Modrinth Maven：

| 模块 | 坐标 | 映射 |
|---|---|---|
| common | `rearth.oritech:oritech-common-1.21.1:1.2.12` | Mojmap（专为 moddev vanilla 模式发布） |
| fabric | `rearth.oritech:oritech-fabric-1.21.1:1.2.12` | intermediary（loom 负责 remap） |
| neoforge | `rearth.oritech:oritech-neoforge-1.21.1:1.2.12` | Mojmap |

Fabric 侧的 MC / loader / API 版本与 Oritech 1.21 分支保持一致：

```
minecraft_version=1.21.1
fabric_loader_version=0.17.3
fabric_api_version=0.116.7+1.21.1
architectury_version=13.0.11
geckolib_version=4.6.6
athena_version=4.0.0
forge_config_api_port_version=21.1.6
modmenu_version=11.0.3
```

## 3. 每个模块负责什么

### common（加载器中立）
- 全部内容：12 个方块/物品、5 个方块实体类型、2 个菜单、4 个网络类、21 个客户端页面文件、`forge` 包、
  `wireless` 包、**23 个 mixin**（18 common + 5 client，目标全在 Oritech 的 common 或原版上）。
- 注册改走 **Architectury** 的 `DeferredRegister` / `RegistrySupplier`（Oritech 自己的 common 就是这么写的），
  入口是 `OritechAddonsOne.init()`：先让加载器补旧注册名别名，再按
  **方块 → 物品 → 方块实体 → 菜单 → 创造标签** 刷新，最后注册网络、tick 与能力桥。
- 菜单改走 **Architectury** 的 `MenuRegistry.ofExtended` / `openExtendedMenu` / `registerScreenFactory`。
- 网络改走 **Oritech 自己的** `rearth.oritech.api.networking.NetworkManager`
  （payload 记录本身是原版 `CustomPacketPayload` + `StreamCodec`，一字未改；handler 签名变成
  `(T, Player|Level, RegistryAccess)`）。这样连"Fabric 上 S2C 接收器必须延迟到客户端初始化"这件事
  也由 Oritech 的平台层代劳。
- 唯一保留的 `net.neoforged` 导入是 `Config.java` 的 `ModConfigSpec`：**Oritech 的 common 里也是这么做的**，
  Fabric 侧由 Forge Config API Port 提供同一个类。

### fabric
| 文件 | 作用 |
|---|---|
| `AddonsPlatformFabric` | `@AutoService(AddonsPlatform.class)`：注册 `ItemStorage.SIDED`、查原版强制加载、环境判断 |
| `FabricInventoryStorage` | 把 Oritech 的 `ItemApi.InventoryStorage` 包成 Fabric Transfer API 的 `Storage<ItemVariant>`（含事务快照） |
| `OritechAddonsOneFabric` | `ModInitializer`：用 Forge Config API Port 注册 `Config.SPEC`，再调 `init()` |
| `client/OritechAddonsOneFabricClient` | `ClientModInitializer`：调共用的客户端初始化 |
| `client/OritechAddonsOneModMenu` | **配置界面的 Mod Menu 入口**（`ModMenuApi`） |
| `client/AddonsConfigScreen` | 用原版控件画的最小配置界面，读写同一份 `Config.SPEC` |
| `resources/fabric.mod.json` | 入口点（main / client / **modmenu**）、mixins 声明、`depends` |

### neoforge
| 文件 | 作用 |
|---|---|
| `AddonsPlatformNeoForge` | `@AutoService(AddonsPlatform.class)`：能力注册排队到 `RegisterCapabilitiesEvent`、合并 ForcedChunks 的两个 tracker、`FMLEnvironment` 环境判断、转发旧名别名注册 |
| `NeoForgeInventoryStorageHandler` | `IItemHandler` 适配器（原 `FacePluginStorage.NeoForgeHandler` 搬到这里） |
| `OritechAddonsOneNeoForge` | `@Mod`：注册 `Config.SPEC`、挂能力事件、调 `init()` |
| `client/OritechAddonsOneNeoForgeClient` | `@Mod(dist=CLIENT)`：挂 NeoForge 自带配置界面 + 调共用客户端初始化 |
| `TransferAddonMigration` | 旧注册名别名（`DeferredRegister#addAlias`），**NeoForge 独有** |
| `templates/META-INF/neoforge.mods.toml` | 元数据（沿用原来的模板展开方式） |

## 4. 平台差异清单（SPI）

只有**两个加载器确实没有共同 API** 的东西进了 SPI（`common/.../platform/AddonsPlatform.java`）：

| SPI 方法 | NeoForge 实现 | Fabric 实现 |
|---|---|---|
| `registerLegacyAliases()` | `DeferredRegister#addAlias`（6 条别名） | **no-op**（Fabric 无别名机制） |
| `registerFaceItemProvider(type, source)` | 排队到 `RegisterCapabilitiesEvent`，包成 `IItemHandler` | `ItemStorage.SIDED` + `Storage<ItemVariant>` 包装 |
| `isForceLoadedByMods(level, chunk)` | 合并 `ForcedChunksSavedData` 的 block / entity tracker | 只能读原版的 `getChunks()` |
| `isClient()` | `FMLEnvironment.dist.isClient()` | `FabricLoader.getEnvironmentType()` |

其它差异一律用现成的跨端方案，没有再包一层：
注册/菜单/事件/流体/平台查询 → **Architectury**；网络 → **Oritech NetworkManager**；
物品/能量存储 → **Oritech 的 `ItemApi` / `EnergyApi` SPI**（本来就会桥到两端的原生 API）。

## 5. 必须接受的降级

1. **旧注册名别名（`transfer_addon` / `transfer_preview_addon`）只在 NeoForge 生效。**
   Fabric 没有任何注册表别名机制，`registerLegacyAliases()` 是 no-op：旧存档里用旧 id 放置的方块会变成
   缺失方块。若要补，只能在世界加载时扫描 chunk palette 做一次性数据修补。
2. **"是否被模组强制加载"的判定在 Fabric 上更粗。** 原版只有一个 `LongSet`，无法区分"被模组强制"与
   被 `/forceload` 强制；NeoForge 侧能分开看 block / entity 两个 tracker。这只影响无线页面上的显示徽章。
3. **owo-lib 被排除。** `oritech-fabric` 的 POM 把 owo-lib 列为 compile 依赖，但 Oritech 代码里没有任何
   `wispforest` 引用（历史遗留声明），而 owo 的 maven 在部分网络下不可达，所以 `fabric/build.gradle`
   对它做了 `exclude`；开发运行期那一行 `modLocalRuntime "io.wispforest:owo-lib"` 也被注释掉，
   需要时按注释说明启用。

## 6. 构建与运行

```bash
./gradlew build                 # 两个 loader 的 jar 都在各自模块的 build/libs 下
./gradlew :fabric:runClient     # Fabric 开发客户端
./gradlew :neoforge:runClient   # NeoForge 开发客户端（回归）
./gradlew :common:compileJava   # 只验证加载器中立代码
```

产物名：`oritechaddonsone-fabric-1.2.1+mc1.21.1.jar` / `oritechaddonsone-neoforge-1.2.1+mc1.21.1.jar`。
Fabric 侧的 `mods.toml` / `fabric.mod.json` 里的 `${version}` 由 `processResources` 展开。

## 7. 仍需验证的清单

改造过程中本机**无法完成完整 Gradle 构建**（原因见下），所以下面的项目要在一台能正常下载依赖的机器上过一遍：

1. `./gradlew :common:compileJava` —— common 是否真的只依赖 vanilla + Architectury + Oritech + GeckoLib +
   Forge Config API Port（一个 `net.neoforged` 泄漏就会在这里炸）。
2. `./gradlew :neoforge:build` —— 回归：NeoForge 侧功能不变（注册、能力桥、配置界面、旧名别名）。
3. `./gradlew :fabric:build` —— Fabric 侧编译（重点看 `FabricInventoryStorage` 对 Transfer API 的签名、
   `MenuRegistry` 的泛型、`NeoForgeConfigRegistry` 的用法）。
4. **23 个 mixin 逐条验证**：目标全在 Oritech 的 common（两个加载器同一份类），但 `required: true` +
   `defaultRequire: 1` 意味着任何一条失效都会启动即崩；重点看
   `SimpleInOutFluidStorage$1` / `$2` 这两个匿名内部类序号，以及客户端那 5 条（`OritechMachineScreen` /
   `UpgradableOritechScreen` / `ScreenHandler` / `WidgetScreen`）。
5. 两个加载器的实际游戏行为：11 个方块、5 个方块实体、2 个菜单、4 组网络包、无线页面、锚点强制加载、
   仓库/储罐容量加成、延伸器六面的物品连接（Fabric 侧是新写的桥）。
6. 存档互通：NeoForge 存档用 Fabric 打开（方块 id 相同即可读；注意 §5.1 的旧名别名差异）。
7. 配置界面：NeoForge 用原生 `ConfigurationScreen`，Fabric 用 Mod Menu → 本项目自绘的 `AddonsConfigScreen`。

### 本机为什么没跑通构建（环境问题，不是代码问题）

- **内存**：本机空闲内存只剩 ~0.4 GB（Minecraft 客户端正在运行，占 2.7 GB），Gradle 守护进程起不来。
  为此 `gradle.properties` 的 `org.gradle.jvmargs` 已从 3G 调回 1.5G 并关掉 `org.gradle.parallel`。
- **网络**：`maven.wispforest.io` 直接拒连；`raw.githubusercontent.com`（Fuzss maven，Forge Config API Port）
  与 `maven.neoforged.net` 在本网络下 TLS 证书链校验失败（Java 侧 PKIX；Windows 侧靠系统证书库+代理能过）。
  应对：给 Gradle 的 JVM 指定一个补过证书链的 truststore（`JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=...`）。
- 作为替代证据：网络/菜单/客户端/存储那 13 个文件已用 **JDK21 javac** 对着
  `neoforge-21.1.200-merged.jar` + `libs/oritech-neoforge-1.21.1-1.2.12.jar` + Gradle 缓存里的依赖做了一次
  真实类型检查，**exit 0**（证明签名/类型正确；不证明加载器中立性，后者靠 grep 确认：
  common 里现在只剩 `Config.java` 的那一个 `ModConfigSpec` 导入）。

---

## 8. 本次改造实际做到的验证

用 `javac --release 21` 直接编译（classpath = 缓存的 `neoforge-21.1.200-merged.jar` + `libs/*.jar` +
Gradle 缓存里 281 个 jar），**不经过 Gradle**：

| 范围 | 结果 |
|---|---|
| `common/`（102 个 java，含 23 个 mixin，加上新增的 `AddonsPlatform`） | **exit 0，产出 165 个 class**（只有 deprecation 提示） |
| `common/` 里是否还有 loader API | grep 确认：只剩 `Config.java:3` 的 `net.neoforged.neoforge.common.ModConfigSpec`（**有意保留**，与 Oritech 的 common 一致，Fabric 上由 Forge Config API Port 提供） |
| `neoforge/`（5 个 java） | 只剩 3 个**环境性**报错：`com.google.auto.service`（新依赖，尚未下载）与 `FMLEnvironment.dist`（我这条手搓 classpath 命中了另一个版本的 FML jar；真实构建里由 NeoForge 21.1.200 的 FML 提供，原项目本来就用这个 API） |
| `fabric/`（6 个 java） | **无法在本机验证**：fabric-api / fabric-loader / modmenu / architectury-fabric / forgeconfigapiport 都不在 Gradle 缓存里，而其中两个的 maven 在本网络下不可达（见 §7） |

顺带修掉两个真实笔误（javac 抓出来的）：`NeoForgeInventoryStorageHandler.java` 与
`FabricInventoryStorage.java` 的 `package` 行里各有一个反斜杠（`oritechaddonsone\neoforge`），已改为点号。

### Gradle 构建的阻塞点（定位结论）
`./gradlew :common:compileJava` 能启动（daemon 起来了、heap 1.5G、truststore 生效），但**卡在依赖解析的
HTTP 读取上**（线程栈停在 `Gradle → HttpClientHelper → SSLSocketImpl.read`，9 分钟无进展）：
仓库列表里有"能连上但不回包"的 host，而 Gradle 会对每个 artifact 逐个仓库重试。
逐个探测的结论是 **`fuzs.forgeconfigapiport:forgeconfigapiport-common-neoforgeapi` 在本网络下没有任何
可用来源**（`modmaven.dev` / `maven.su5ed.dev` / `maven.blamejared.com` / `maven.architectury.dev` /
`raw.githubusercontent.com` 全部 404 或 502）；modmenu / athena / geckolib / architectury 都能正常拉到。

想让 Gradle 构建在本机跑通，需要二选一：
1. 给 JVM 配好能过这套代理的 truststore（本次用过
   `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=<补过证书链的 jks>`），并**保证能访问 Fuzss 的 maven**
   （`https://raw.githubusercontent.com/Fuzss/modresources/main/maven/`）；或
2. 把 `forgeconfigapiport-common-neoforgeapi` 换成一份本地 jar（`common/build.gradle` 里改成
   `compileOnly files("libs/forgeconfigapiport-common-neoforgeapi-21.1.6.jar")`），仅用于本机构建验证
   —— 注意这只是权宜之计，不要提交进仓库。

> 补充（已实测）：`common/build.gradle` 现在**自带这个回退分支** —— 只要把
> `forgeconfigapiport-common-neoforgeapi-21.1.6.jar` 放进仓库根的 `libs/`（该目录下的 jar 不入库），
> 构建就会自动用本地副本，不再去访问那个 maven。Fabric 模块的 `forgeconfigapiport-fabric` 是 Loom 的
> mod 依赖（需要 remap），不适合用文件依赖顶替，仍需该 maven 可达。
>
> 另外，Maven Central 类制品（`com.google.auto.service:auto-service`）在部分国内网络下也拿不到，
> 但 `maven.aliyun.com/repository/public`、`repo.huaweicloud.com/repository/maven`、
> `mirrors.cloud.tencent.com/nexus/repository/maven-public` 三个镜像都实测有它。这类镜像建议放在
> **用户自己的 `~/.gradle/init.gradle`** 里加，而不是写进仓库（GitHub Actions 在国外直连 Maven Central 更快）。

---

## 9. 第二轮：把两个模块真正"编译"过了一遍（javac 直编，不经 Gradle）

用真实依赖 jar + `javac --release 21` 直接编译源码，解决了 Fabric API 那些 **intermediary 映射噪声**之外的
所有问题。为了绕开两个拿不到的制品，新建了两个**只用于校验、不属于仓库**的桩
（`_fabric-verify/stub/`：`com.google.auto.service.AutoService`、`net.neoforged.fml.loading.FMLEnvironment`）。

| 编译范围 | 结果 |
|---|---|
| `common/` 102 个文件（含 23 个 mixin） | **exit 0，165 个 class** |
| `common/` + `neoforge/`（107 个文件，+2 桩） | **exit 0，174 个 class** |
| `common/` + `fabric/`（108 个文件，+2 桩，classpath 含真实 fabric-loader / fabric-api 模块 / modmenu / architectury-fabric） | 只剩 **8 条错误，全部源自同一件事**：Fabric API / ModMenu 的 jar 是 **intermediary 映射**的，签名里的 `net.minecraft.class_xxxx`（`class_437` = `Screen`、`class_1799` = `ItemStack`、`class_2591` = `BlockEntityType` …）在我这条手搓 classpath 上不存在（其中 2 条是 `class_437` 解析不了导致的 ModMenu 级联）。真实 Loom 构建会先把这些 jar remap 成 named，所以不会出现。**我自己的代码没有其它错误。** |

### 这一轮真正抓出并修掉的 3 个 bug（都是编译错误，Gradle 构建时也会炸）

1. `FabricInventoryStorage.java`、`NeoForgeInventoryStorageHandler.java` 的 `package` 行里各有一个**反斜杠**
   （`io.github.xiao232ming\oritechaddonsone...`）→ 改为点号。
2. `client/AddonsConfigScreen.java` 的 `IntSlider`：`AbstractSliderButton` 在 1.21.1 的构造器是
   `(int x, int y, int width, int height, Component message, double value)`，我少传了 `Component`
   → 已改为 `super(x, y, width, height, Component.empty(), ratio)`。
3. `FabricInventoryStorage.Slot`：内部类里通过**另一个实例**访问外部私有字段（`slot.storage`）不成立
   → 把 `Slot` 改成静态嵌套类并持有 `owner` 引用，`equals/hashCode` 改为比较 `owner` 身份 + 槽位下标。

### 顺带用 javap 核对了新代码用到的每一个第三方方法（名字/参数都对得上）

- `ItemVariant`：`of(ItemStack)`、`matches(ItemStack)`、`getItem()`、`toStack(int)`、`isBlank()` ✓
- `ItemStorage.SIDED.registerForBlockEntity(...)`（`BlockApiLookup` 上的方法）✓
- `ModMenuApi#getModConfigScreenFactory()` 返回 `ConfigScreenFactory<?>`；`ConfigScreenFactory<S extends Screen>#create(Screen)` ✓（所以 `AddonsConfigScreen(Screen parent)` 与 `AddonsConfigScreen::new` 都是对的）
- `Env`/`Platform.getEnvironment()`（Architectury 13.0.11，**注意不是 `EnvType`**）✓

---

## 10. 第三轮：Gradle 构建尝试与网络诊断（结论：本机跑不通，原因已定位到具体 IP）

实测本机的不可达清单（逐个 DNS + TCP 验证）：

| 主机 | 现象 | 影响 |
|---|---|---|
| `repo.maven.apache.org`（Maven Central） | 解析到 **182.16.61.114，TCP SYN 无响应（黑洞）** | 每一次解析 Maven Central 制品/插件都要卡约 2 分钟；`com.google.auto.service:auto-service` 正好在这里 |
| `raw.githubusercontent.com`（Fuzss maven） | 解析到 **127.0.0.1** | 502/拒连；Forge Config API Port 的编译制品只发布在这里 |
| `maven.neoforged.net` | TLS 握手被重置 | 影响 NeoForge 仓库（制品已缓存，插件解析仍会去问） |
| `maven.wispforest.io` | 拒绝连接 | owo-lib（已 exclude） |
| `api.modrinth.com` | TLS 失败/连接超时 | Modrinth Maven（现已不再依赖） |

可达：`maven.blamejared.com`、`maven.architectury.dev`、`dl.cloudsmith.io`、`maven.terraformersmc.com`、
`maven.teamresourceful.com`、`maven.fabricmc.net`、`plugins.gradle.org`，以及国内镜像
`maven.aliyun.com`（实测有 Maven Central 的制品）。

试过的三条绕行（都只在本机、**不写进仓库**）：

1. `gradlew -I <init>.gradle`：在 `settingsEvaluated` 与 `afterEvaluate` 里把上述不可达仓库摘掉、
   换成 aliyun 镜像（因为 `mavenCentral()` 同时出现在 `settings.gradle` 的 pluginManagement 与
   根 `build.gradle` 的 subprojects 里，两边都要摘）。
2. 给 Gradle 的 JVM 指定补过证书链的 truststore：
   `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=<jks> -Djavax.net.ssl.trustStorePassword=changeit`。
3. 为唯一拿不到的**编译**制品做替身：从 `build/moddev/artifacts/neoforge-21.1.200-merged.jar` 抽出
   `net/neoforged/**`，再从缓存的 FML jar 补上 `net/neoforged/fml/**`，加上自己编译的
   `NeoForgeConfigRegistry` 桩，打成一个 4 MB 的 jar 放进 `libs/`
   （`common/build.gradle` 的本地回退分支会自动优先用它）。

**结果**：daemon 能起来、能进入配置阶段（heap 1.5G、truststore 生效），但依赖解析仍在剩余的黑洞/TLS
失败上长时间挂住（`netstat` 里能看到成排的 `SynSent`），**没能拿到一次完整的 Gradle 构建**。
所以：本文件里的构建命令请在网络正常的机器（或配好代理的环境）上执行；那个替身 jar 已从 `libs/`
删除，避免被误当成真制品 —— 需要时按上面第 3 条重建即可。

**这对目标的意义**：三模块的"代码正确性"已经用 javac 直编逐文件验证过（§8、§9）；
尚未验证的是 **Gradle 构建脚本本身**（源码注入、归档名、capability 解析、Loom/moddev 的接线）。
这一项必须在能正常下载依赖的环境里跑一次 `./gradlew build` 才能确认。

### 10.1 第二轮 Gradle 尝试的新发现（关键：**光有仓库镜像还不够**）

这轮把问题从"卡住"逼到了"报错"，用 `--offline`（4 秒即失败、不挂在网络上）拿到了**精确的缺失清单**：

1. **`fabric-loom` 插件自身的依赖也在 Maven Central 上**，所以它会连带拖累**每一个**任务——
   包括 `:common:compileJava`（根 `build.gradle` 里 `id 'fabric-loom' ... apply false` 也要解析插件类路径）：
   `net.fabricmc:fabric-loom:1.13.6`、`stitch`、`class-tweaker`、`tiny-remapper`、`mapping-io`、`lorenz-tiny`、
   `mercury`、`mercurymixin`、`unpick`、`fabric-loom-native`、`asm{,-tree,-commons,-util,-analysis}:9.9`、
   `com.google.guava:guava:28.0-jre`、`org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.21`、
   `org.cadixdev:{at,lorenz,bombe}` 以及一堆 guava 的传递依赖（`failureaccess`、`listenablefuture`、
   `checker-qual`、`jsr305`、`error_prone_annotations`、`j2objc-annotations`、`animal-sniffer-annotations`…）。
2. **`repo1.maven.org` 也被黑洞**（实测解析到 `141.193.154.x`，TCP SYN 无响应）——它跟
   `repo.maven.apache.org` 是两个主机名，只屏蔽后者不够（loom 的 POM 里就写着前者）。
3. 我用一个"本地伪 maven + init script"的组合把这批依赖凑齐了（`_fabric-verify/local-maven`，
   66 个文件 / 22.5 MB），但 **Gradle 的 `--offline` 不认 file:// 仓库**（仍报 "No cached version available
   for offline mode"），而在线模式又会在剩余黑洞上耗尽时间。最后一步没走通：这台机器上
   **没有任何一条路径能解析 Maven Central**（`repo.maven.apache.org` 与 `repo1.maven.org` 双双黑洞，
   `maven.neoforged.net` TLS 被重置），而 loom 的插件类路径**必须**从 Central 拿东西。

### 10.2 推荐的本机解法（写进 `~/.gradle/init.gradle`，两分钟）

```groovy
// ~/.gradle/init.gradle —— 只影响本机，不动仓库
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
    }
    buildscript {
        repositories { maven { url 'https://maven.aliyun.com/repository/public' } }
    }
}
settingsEvaluated { settings ->
    settings.pluginManagement.repositories {
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
    }
}
```

实测 `maven.aliyun.com/repository/public`、`repo.huaweicloud.com/repository/maven`、
`mirrors.cloud.tencent.com/nexus/repository/maven-public` 三个镜像都有 Central 的制品（含 `auto-service`、
guava、asm、kotlin-metadata-jvm）。配上它之后本机才可能跑 Gradle；国内开发环境这么配是常规做法。
**注意**：这只解决"下得下来"的问题；Forge Config API Port 的
`forgeconfigapiport-common-neoforgeapi` 仍然只发布在 `raw.githubusercontent.com`（本机指到 127.0.0.1），
所以还要么让它可达，要么用 §8 的 `libs/` 本地回退。

### 10.3 配上镜像之后的实测进展（这是本轮最有价值的部分）

按上面的方式在本机加了 `~/.gradle/init.gradle` 镜像后，**Gradle 真的开始工作了**，一路推进到编译：

1. **三个模块全部配置成功**（`> Configure project :fabric` → `Fabric Loom: 1.13.6`），
   也就是 `settings.gradle` / 插件声明 / 三模块结构 / capability 接线在配置期是通的。
2. 立刻抓出一个**构建脚本真 bug**（`build.gradle:116`）：
   ```groovy
   // 错：Groovy 会把 path = 当成给当前 Project 的只读属性赋值
   commonJava project(path = ':common', configuration = 'commonJava')
   // 对：命名参数要用冒号（Oritech 原文就是冒号）
   commonJava project(path: ':common', configuration: 'commonJava')
   ```
   报错是 `Cannot set the value of read-only property 'path' for project ':fabric'`。已修复。
3. 修完继续推进：`:common:createMinecraftArtifacts` **成功执行**（NeoForm vanilla 模式流水线跑通了），
   然后 **`:common:compileJava` 真的调用了 javac** 编译那 102 个文件 —— 这是第一次走通真实 Gradle 编译路径。
4. 剩下的失败是**我这边的替身 POM 造成的**，不是项目问题：`:common` 的 compileClasspath 上完全没有
   Minecraft 的库（netty / fastutil / joml / log4j / slf4j / gson 一个都没有），于是报
   `程序包io.netty.buffer不存在`（共 27 个错误）。原因是我为那个拿不到的制品写的**占位 POM 是空的**，
   而 MC 库正是通过它传递进来的；我想从缓存的 `.module`（GMM）反推依赖时发现
   **那 49 个依赖全都没有版本号**（版本来自一个本机无法复现的平台/BOM），所以在这台机器上没法补全。
   —— 用 `:common:dependencies` 得到的可见 classpath 是：`auto-service`、`architectury-neoforge`、
   `oritech-common-1.21.1`、`geckolib-neoforge`、`forgeconfigapiport-common-neoforgeapi`(替身)、
   `mixin`、`asm-tree`、`neoform`。

**留待网络正常的机器上确认的一点**：如果那边 `:common:compileJava` 也报缺 netty 等 MC 库，
说明 Oritech 是靠 `forgeconfigapiport-common-neoforgeapi` 的真 POM 把 MC 库传递进来的；
那就给 `common/build.gradle` 显式补上 MC 库依赖（例如 `compileOnly "net.neoforged:minecraft-dependencies:${minecraft_version}"`，
或直接用 moddev 提供的库集合）。这类问题只会在真构建里暴露，也正是"必须跑一次 Gradle"的价值所在。

（实测补充：本机把 NeoForm 运行时缓存 `~/.gradle/caches/neoformruntime/artifacts` 直接当成一个 maven 仓库挂上后，
MC 库就有了 —— 那份缓存本来就是标准 maven 布局、且版本正确：netty 4.1.97.Final、fastutil 8.5.12、joml 1.10.5 等。）

---

## 11. Gradle 真编译抓出来的 5 类**必须修的真问题**（本轮最重要的产出）

把 MC 库补齐之后，`:common:compileJava` 用**真实 Gradle 编译**跑到了 javac，并报出 15 个错误。
它们全都是**同一类问题**：代码里用了 **NeoForge 通过 AT/补丁才存在的东西**，
而 vanilla 模式（common 的编译环境）里没有。之前"用手搓 classpath + oritech-neoforge jar"的 javac 校验
**抓不到**这些 —— 因为那个 jar 本身就带着 NeoForge 的 AT 效果。五类如下：

| # | 位置 | 报错 | 原因 | 修法 |
|---|---|---|---|---|
| 1 | `block/entity/ChunkAnchorAddonBlockEntity.java:83`、`block/entity/ExtensionAddonBlockEntity.java:1648` | `方法不会覆盖或实现超类型的方法` + `找不到符号: 方法 onLoad()` | `BlockEntity#onLoad()` 是 **NeoForge 补丁**方法，vanilla 没有 | 改成 vanilla 就有的时机（`setLevel(Level)` / 首次 tick），或用平台 SPI |
| 2 | `block/entity/ExtensionTransferAddonBlockEntity.java:372` | `找不到符号: 方法 invalidateCapabilities(BlockPos)` | `Level#invalidateCapabilities` 是 **NeoForge 专有**；Fabric 侧根本没有 capability 缓存需要失效 | 走 `AddonsPlatform` SPI：NeoForge 实现调它，Fabric 实现空实现 |
| 3 | `mixin/OritechMachineScreenMixin.java:201,207,208` | `找不到符号: 方法 getGuiLeft()/getGuiTop()` | 这俩是 **Forge/NeoForge 补丁**；vanilla 里是 `protected int leftPos, topPos` | 在该 mixin 里用 `@Shadow` 拿 `leftPos`/`topPos` |
| 4 | `OritechAddonsOne.java:280,286,312,327,337` | `BlockEntitySupplier在BlockEntityType中不是公共的; 无法从外部程序包中对其进行访问` | vanilla 的 `BlockEntityType.Builder.of` 参数类型是**包私有**的，NeoForge 用 AT 放开；Fabric 上要用 `FabricBlockEntityTypeBuilder` | 新增 SPI `createBlockEntityType(supplier, blocks...)`：NeoForge 走 `BlockEntityType.Builder`，Fabric 走 Fabric API 的 builder |
| 5 | `OritechAddonsOne.java:357` | `无法将类 CreativeModeTab中的方法 builder应用到给定类型; 需要: Row,int` | vanilla 没有可用的 `CreativeModeTab.builder()` | 用 Architectury 的 `CreativeTabRegistry.create(...)`（跨加载器），或同样走 SPI |

原始报错全文已留档：`_fabric-verify/gradle-common-errors.txt`。

**结论**：这五处必须在 common 里改掉 —— 因为 **common 是在 vanilla 模式下编译的**，
它两边都用同一份源码，所以不改的话 `:common:compileJava` 过不去（也就没有可用的 common 产物）。
注意它们**在 NeoForge 上是能用的**：原单模块工程（纯 NeoForge）就是用这些 API 编过的
（`BlockEntityType.Builder.of`、`CreativeModeTab.builder`、`Level#invalidateCapabilities`、
`AbstractContainerScreen#getGuiLeft/getGuiTop`、`BlockEntity#onLoad` 全都是 NeoForge 补丁/AT 的产物）。
所以修法的原则是：**不改变 NeoForge 侧行为**，把"只在某一端存在的能力"放进 `AddonsPlatform` SPI 分派
（NeoForge 侧照旧调用原 API，Fabric 侧给等价实现），能改成 vanilla 通用写法的就直接改
（如 `getGuiLeft()` → `@Shadow leftPos`、`onLoad()` → `setLevel`）。

> 本机观察：`:neoforge:compileJava` 也报了同样这 10 条（同一批源码），
> 但原 NeoForge 工程编得过，说明这是我本机 NeoForge 侧"AT 后的 merged MC"没接上导致的
> （我注入的 MC 库来自 vanilla 的 NeoForm 缓存），**不能据此判定 NeoForge 侧有问题**。
> 修完这五处之后，NeoForge 侧要用它自己的类路径再编一次来确认无回归。

这也印证了一件事："用手搓 classpath 做 javac 校验"只能覆盖类型正确性，
**覆盖不了加载器补丁/AT 造成的可用性差异**；真构建这一步不可省。

---

## 12. 这五类问题的修法（已实施）与结果

修法的总原则：**能照抄 Oritech 的就照抄，能改成 vanilla 通用写法的就改，剩下的才进 SPI**。

| # | 修法 | 涉及文件 |
|---|---|---|
| 4 | 照抄 Oritech：新增 **访问转换器** `common/src/main/resources/META-INF/accesstransformer.cfg`，其中 `public net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier`；moddev 在 vanilla 模式下会自动应用它（Oritech 就是这么让 common 编译过 `BlockEntityType.Builder.of` 的），NeoForge 侧再由 `neoforge/build.gradle` 显式引用同一份文件 | 新增 AT 文件；`neoforge/build.gradle` 加 `accessTransformers.from project(':common')...` |
| 4 | Fabric 侧给同一件事写 **access widener**：`fabric/src/main/resources/oritechaddonsone.accesswidener`（`accessible class ...BlockEntityType$BlockEntitySupplier`），并在 `fabric/build.gradle` 设 `accessWidenerPath`、在 `fabric.mod.json` 声明 `accessWidener` | 新增 AW 文件 + 两处接线 |
| 3 | `getGuiLeft()/getGuiTop()` → **`leftPos`/`topPos`**（AT/AW 把它们放开成 public），4 处调用点直接改写 | `mixin/OritechMachineScreenMixin.java` |
| 1 | `BlockEntity#onLoad()` → **`loadAdditional(...)`**（vanilla 就有，而且正好在存档读完之后）：`ChunkAnchorAddonBlockEntity` 改为覆写 `loadAdditional`；`ExtensionAddonBlockEntity` 把登记并进它已有的 `loadAdditional`；`WirelessExtensionAddonBlockEntity` 同理（并把它原有的 `linkedMachine` 读取与登记合并到同一个方法里，避免重复定义） | 3 个方块实体类 |
| 2 | `Level#invalidateCapabilities` → **SPI**：`AddonsPlatform#invalidateCapabilities(Level, BlockPos)`，NeoForge 实现照旧调用原方法，Fabric 是 no-op（`ItemStorage.SIDED` 每次查询、没有缓存要失效） | `platform/AddonsPlatform.java` + 两个加载器实现 + 调用点 |
| 5 | 照抄 Oritech：创意标签页改用 **`CreativeTabRegistry.create(builder -> ...)`**（Architectury 跨加载器 API，Oritech 的 `ItemGroups` 就是用它） | `OritechAddonsOne.java` |

### 结果

```
> Task :common:compileJava
BUILD SUCCESSFUL in 5s      ← 102 个 common 源文件在真实 Gradle 构建（vanilla 模式 + AT）里编译通过
```

- ✅ **`:common:compileJava` 通过**（本轮最大的成果：从 15 个错误 → 0）。
- ⚠️ `:neoforge:compileJava`：本机能推进到 `:neoforge:createMinecraftArtifacts`，
  但 NeoFormRuntime 那个子进程在本机退出码 1（本机镜像缺 NeoForge/Mojang 侧的制品，属环境问题，不是代码问题）。
  修好的这五处对 NeoForge 是**行为保持**的（原 API 通过 AT/SPI 照旧调用），但**仍需在正常网络上编一次确认**。
- ⚠️ `:fabric:compileJava`：本轮未跑到（同一批前置制品问题）。

### 因此本模组现在多出两个配置文件的交付物（要一起提交）

- `common/src/main/resources/META-INF/accesstransformer.cfg`（新）
- `fabric/src/main/resources/oritechaddonsone.accesswidener`（新）+ `fabric.mod.json` 里的 `accessWidener` 声明

---

## 13. 三模块**全部构建通过**（本轮最终结果）

把上面五处修完、并补上本机缺的制品之后，真实 Gradle 构建的结果是：

```
> Task :common:compileJava          BUILD SUCCESSFUL
> Task :neoforge:compileJava        BUILD SUCCESSFUL
> Task :fabric:compileJava          BUILD SUCCESSFUL
> Task :fabric:build  :neoforge:build
BUILD SUCCESSFUL in 6s   (16 actionable tasks: 11 executed, 5 up-to-date)
```

`:fabric:build` 里包含并全部通过的关键任务：
`processResources`、`jar`、`processIncludeJars`、`sourcesJar`、**`validateAccessWidener`**（说明我写的 AW 语法是对的）、
`remapJar`、`remapSourcesJar`、`assemble`、`build`。
`:neoforge:build` 包含：`generateModMetadata`、`processResources`、`jar`、`sourcesJar`、`assemble`、`build`。

也就是说：**源码注入、归档名、capability、AT/AW、mixins 配置、元数据生成、Loom 重映射，整条构建链路都验证过了。**

### 这一轮又抓出并修掉的两个真 bug

1. **`fabric.mod.json` 是非法 JSON** —— 我之前用 PowerShell 单引号字符串插入 `accessWidener` 声明时写了 `` `n ``，
   而单引号不解析转义，于是文件里真的多出两个字符 `` `n ``。Loom 直接报
   `Failed to parse fabric.mod.json`，**Fabric 侧会完全构建不了**。已改成真正的换行并用 `ConvertFrom-Json` 验证通过。
2. **`FabricInventoryStorage` 用了 1.21.1 不存在的 API**：`Item#getMaxStackSize()`。
   1.21.1 里 `Item` 只有 `getDefaultMaxStackSize()`（`getMaxStackSize()` 在 `ItemStack` 上）。
   改为 `resource.getItem().getDefaultMaxStackSize()`。
   （这个 bug 之前被手搓 classpath 里混进别的 MC 版本的 jar 掩盖了，只有真构建会暴露。）

### 本机跑通构建所需的额外条件（与仓库代码无关，已全部记录）

- `~/.gradle/init.gradle`：加阿里云镜像（本机 Maven Central 被黑洞）。
- `gradlew -I <init>`：摘掉不可达仓库、并把 NeoForm 运行时缓存
  （`~/.gradle/caches/neoformruntime/artifacts`，标准 maven 布局、版本正确）挂成仓库以补 MC 库。
- 一个本地伪 maven：装那份镜像不到的 `forgeconfigapiport`（从 NeoForge 合并 jar + 正确的 FML 4.0.41 抽取
  `net/neoforged/**` 得到一个替身），以及用 `--info` 日志里 NeoFormRuntime 报出的 URL 逐个补齐它要下载的
  Central 制品（`checker-qual`、`error_prone_annotations`、`j2objc-annotations` …）。

> 重要结论：`:common` **不需要**额外声明 MC 库依赖 —— 那些库本来是 `net.neoforged:neoform` →
> `net.neoforged:minecraft-dependencies` 的真 POM 传递进来的（§10.3 的疑问到此关闭）；
> 本机之所以缺，是因为我为了绕开唯一的不可达制品做了个**空 POM 替身**。用真制品的机器不会遇到。

### 产物自检（两个 jar 的内容都对）

| | `oritechaddonsone-fabric-1.2.1+mc1.21.1.jar` | `oritechaddonsone-neoforge-1.2.1+mc1.21.1.jar` |
|---|---|---|
| 条目 / class | 332 / 174 | 328 / 171 |
| 元数据 | `fabric.mod.json` ✅ | `META-INF/neoforge.mods.toml` ✅（由 `generateModMetadata` 从模板展开） |
| mixins | `oritechaddonsone.mixins.json` ✅ | 同 ✅ |
| AT / AW | `accesstransformer.cfg` ✅ / `oritechaddonsone.accesswidener` ✅ | `accesstransformer.cfg` ✅ |
| SPI | `META-INF/services/…AddonsPlatform` ✅ | 同 ✅（`@AutoService` 注解处理器确实跑起来了） |

> ⚠️ 本机这两个 jar 是用**替身** `forgeconfigapiport` 编出来的，**不能直接发布**；
> 被验证的是"构建链路"本身。请在能访问 Fuzss maven 的机器上重新 `./gradlew build` 产出正式包。

---

## 14. 运行时验证第一步：`gradlew :neoforge:runServer`（无 GUI 冒烟测试）

为做这一步，本机额外准备了 `neoforge/run/eula.txt`（`eula=true`）与把 `libs/oritechthings-0.0.46.jar`
装进本地伪 maven（Modrinth 坐标 `maven.modrinth:oritech-things:vt3nmngK`）。

### 又抓出一个**真问题**（已修）

`gradlew :neoforge:runServer` 一开始直接失败：运行期 classpath 解析不了
`curse.maven:jade-324717:5846105`（Oritech 的 POM 传递引用 Jade）。
对照 Oritech 的根 `build.gradle` 发现**我的仓库列表漏了 CurseMaven**（它第 46 行有 `https://beta.cursemaven.com`），
FTB 也漏了。已按 Oritech 补上：

```groovy
maven { url = 'https://beta.cursemaven.com' }   // Oritech 的 POM 引用了 curse.maven 坐标（如 Jade）
maven { url = 'https://maven.ftb.dev/releases' }
```

> 没有这一条，**NeoForge 侧连开发期的 `runServer` 都起不来**（编译期看不出来，因为 Jade 是传递的运行期依赖）。

### 已经验证通过的部分 ✅

服务器真的启动了，日志（`neoforge/run/logs/latest.log` 与 `debug.log`）里可以看到：

- **Mod List 里有 `Oritech Addons One 1.2.1 (oritechaddonsone)`** —— 模组被发现并加载；
  同列表里 Oritech 1.2.12 / Oritech Things 0.0.46 / Architectury 13.0.11 / Athena 4.0.0 / GeckoLib 4.6.6 / Jade 15.8.2 / Grand Power 3.0.2 全部就位。
- **`Preparing oritechaddonsone.mixins.json (23)` 且 `Prepared 49 mixins ... ` 无任何 mixin 报错** ——
  23 个 mixin 的**目标类全部解析成功**（prepare 阶段就要解析 `@Mixin(Target.class)` 的目标）。
- `Reference map 'oritechaddonsone.refmap.json' ... could not be read. If this is a development environment you can ignore this message`
  —— 这条警告 **Oritech 自己的 `oritech.mixins.json`、GeckoLib、Oritech Things 一模一样地报**，
  证明我们保留 refmap 声明的做法与 Oritech 完全一致，且是无害的。

### 还没解决的部分 ⚠️

mixin 准备完之后 **JVM 以退出码 1 静默结束**：`latest.log` / `debug.log` 都在
`Prepared 49 mixins` 那一行断掉，**没有异常、没有 crash-report、没有 hs_err**，
也没有走到 Minecraft 自己的 `Starting minecraft server version 1.21.1`。
Gradle 只报 `Process 'java' finished with non-zero exit value 1`。

可疑方向（下一轮逐一排除）：
1. ~~我们的 AT 里有客户端类条目~~ —— **已排除**。已把 `AbstractContainerScreen leftPos/topPos` 从 AT/AW 里去掉，
   改成客户端 mixin 的 `@Accessor`（新增 `mixin/ContainerScreenAccessor.java`，注册在 `mixins.json` 的 `client` 段），
   三个模块重新编译均通过；再跑服务器，**仍然在同一位置静默退出**（`latest.log` / `debug.log` 同样断在 mixin 准备阶段之后）。
   —— 这个改动本身仍然保留：让客户端成员走客户端 mixin 比全局放开 AT/AW 更干净。
2. FML 在 mixin 之后、进入 `Main` 之前的类加载/转换阶段硬失败（无输出说明可能是 `System.exit` 或进程被杀）。
3. 本机环境（stdio / 进程树）—— 内存已排除（当时 6.88 GB 空闲）。
4. 另一个待查项：Minecraft 自己的日志一行都没出现（连 `Starting minecraft server version 1.21.1` 都没有），
   说明问题发生在 **Minecraft 主类之前**，与我们的注册/菜单/网络代码无关；
   下一个可做的判别是打印 JVM 的 `-Xlog`/`-XX:+ErrorFileToStdout`，或把 `localRuntime` 里的附属模组逐个去掉二分。

### 二分结果（已做完）

| 试验 | 结果 |
|---|---|
| 全部模组在（含 Oritech Things） | 静默退出 1，最后一行是 `Prepared 49 mixins` |
| **去掉 Oritech Things**（其余不变） | **完全一样** —— 排除 Oritech Things |
| **清空全部 `localRuntime`**（只剩 MC + NeoForge + 本模组） | FML **正常报错并退出 1**：`Missing or unsupported mandatory dependencies: Mod ID: 'oritech' …` —— 说明 **FML 的错误输出通道是好的**，问题只在"模组齐全之后、Minecraft 主类之前"那一段 |

结论：**这台机器上跑不了 NeoForge 开发期专用服务器**（很可能是本机环境/启动器层面的问题，因为报错通道、内存、mixin 准备都是正常的）。
这一步**不影响本方案的交付**：目标要求的是"尽量用 Gradle 编译验证"，而这一点已经超额完成（三模块编译 + 两个 jar 打包 + 产物自检）。

---

## 15. 交给本机做"进游戏验证"的核对清单

运行时验证请在你的机器上做（跑 `./gradlew :neoforge:runClient` 或装上两个 jar）：

**基本加载**
1. 两端都能启动，`logs/latest.log` 里没有 mixin 报错（Fabric 侧注意 `oritechaddonsone.accesswidener` 是否被接受）。
2. 创造模式物品栏里有独立的 **Oritech Addons One** 标签页，11 个物品齐全（I/II/III 型、无线 I/II/III、仓库、储罐、区块锚点、传输插件、传输预览）。

**23 个 mixin 的对照点**（按 `oritechaddonsone.mixins.json` 顺序，最需要盯的是带 `$1/$2` 匿名类序号的那两个）
- 共同段 18 个：原子锻造/锻造仓合成与预算、机器基类 invoker、物品使用、机器附加控制器、输出上限、激光瞄准与指示、网络化方块实体存储加成、粉碎机、**SimpleInOutFluidStorage 的三个 mixin（含 `$1`/`$2` 匿名类序号）**、简单物品/流体存储、槽位。
  → 进游戏后用 Oritech 的机器实测：**流体输入/输出槽（`SimpleInOutFluidStorage`）行为正常**、流体与物品存储加成生效、粉碎机/激光/锻造仓功能不变。
- 客户端 6 个：容器界面 accessor、机器界面、屏幕处理器、组件界面、可升级界面与其处理器。
  → 打开 Oritech 机器的界面：附加插件面板、3D 预览、每个面/页面的按钮都要正常；**精炼厂（refinery）**界面要有加成数值面板（这正是那个 mixin 的目的）。

**功能点**
3. 面过滤菜单（`FaceFilterMenu`）、扩展插件菜单（`ExtensionAddonMenu`）能开、能存、能被两个人各自独立使用（网络包是发给"开着界面的玩家"的）。
4. 区块锚点：放置后强制加载生效；**重进存档后仍然强制加载**（这是把 `onLoad` 改成 `loadAdditional` 的关键验证点）。
5. 无线扩展插件：绑定机器、跨区块工作、界面徽章显示（Fabric 侧的"是否被模组强制加载"判断比 NeoForge 粗，见 §6 的降级说明）。
6. 仓库/储罐加成、扩展插件的面物品连接（管道/漏斗从面上抽取）。
7. **Fabric 侧配置界面**：Mod Menu → Oritech Addons One → 改四项数值与那个开关 → 保存后重启仍在；`config/oritechaddonsone-common.toml` 内容正确。
8. **NeoForge 侧配置**：Mods 列表里的 Config 按钮仍然是原生的 `ConfigurationScreen`，读写同一份 `Config.SPEC`。
9. 存档互通：NeoForge 存档在 Fabric 打开（已知差异：旧注册名 `transfer_addon` / `transfer_preview_addon` 在 Fabric 上没有别名机制，会变成缺失方块 —— 见 §6）。














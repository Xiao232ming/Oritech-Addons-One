# 贡献与维护指南 / Contributing & maintenance

本仓库 <https://github.com/Xiao232ming/oritech-addons-one> 用**分支**区分 Oritech Addons One 的两个版本。

## 分支模型 / Branch model

| 分支 | 游戏环境 | JDK | 说明 |
|---|---|---|---|
| **`26.1.2`**（默认分支） | MC 26.1.2 + NeoForge 26.1.2.107 + Oritech 2.0.0-exp6 | 25 | 最新版本，主要开发分支 |
| **`1.21.1`** | MC 1.21.1 + NeoForge 21.1.250 + Oritech 1.2.12 | 21 | 长期维护的旧版本 |

- 仓库里**没有 `main`**：克隆后默认停在 `26.1.2`，`git switch 1.21.1` 切到旧版本。
- 两个分支的注册 API / 渲染管线 / 数据格式差异很大，**源码分别维护，不共用**；
  移植时逐处对照另一分支的源码，不要凭记忆改。
- 新增 MC 版本 = 从当前最新分支切一条新分支（建议 `git switch -c <新游戏版本> 26.1.2` 再改 API）。

### 本机的双工作区（git worktree）

两个版本在同一台机器上并行开发时，用 `git worktree` 共享同一个 `.git`，不用来回切分支：

```powershell
$repo = "D:\Games\MC\MOD\26.1.2-NeoForge\Oritech Addons One"   # 主检出（26.1.2 分支）
git -C $repo worktree list
git -C $repo worktree add "D:\Games\MC\MOD\1.21.1-NeoForge\Oritech Addons One" 1.21.1   # 链接工作区
```

链接工作区里的 `.git` 是**文件**（指向主仓库的 `.git\worktrees\...`）：不要在里面再 `git init`，
也不要把主检出目录删掉/移走，否则链接工作区会失效（用 `git worktree remove <路径>` 正常移除）。
工作区总览见 `D:\Games\MC\MOD\README.md`（该文件在工作区容器里，不进版本库）。

## 构建 / Build

```powershell
git branch --show-current   # 确认自己在 26.1.2 或 1.21.1 分支上
.\gradlew build             # 产物 build\libs\oritechaddonsone-<mod_version>+mc<游戏版本>.jar
.\gradlew runClient         # 开发客户端：自动带上 Oritech / Athena / GeckoLib（Modrinth Maven）
.\gradlew runData           # 数据生成（产物进 src\generated\resources）
```

- 依赖不用手动准备：Oritech / Athena / GeckoLib 等从 **Modrinth Maven** 解析，Architectury 从
  **官方 Architectury Maven** 解析（它没发布到 Modrinth）；坐标都在 `gradle.properties`。
  换版本改 `gradle.properties` 里的 `*_version`；要完全离线时，把 `build.gradle` 里的 Maven 坐标换成 `libs/` 下的文件依赖。
- `.gitattributes` 把文本行尾固定成 LF（`*.bat`/`*.cmd` 保持 CRLF），所以任何机器上检出的源码与
  构建产物都一致；提交前不要用会改行尾的编辑器批量重写文件。

## 在两个版本之间同步改动 / Porting a change

1. 先在**改动所属**的分支提交（例如先在 `1.21.1` 修好一个 bug）。
2. 用 `git cherry-pick` 挑到另一分支，**不要**直接 `git merge` 整条分支：
   两条分支的历史各自独立（移植时是重新写的源码），合并会试图用一版的源码覆盖另一版。
   ```powershell
   git switch 26.1.2
   git cherry-pick <sha>          # 冲突处按另一分支的实际 API 解决
   .\gradlew build                # 另一分支也必须重新构建验证
   ```
3. 只对某个版本成立的改动（例如某版本特有的 mixin 目标）不必强行同步，
   在提交信息里注明 `(1.21.1 only)` / `(26.1.2 only)`。
4. 面向玩家的文案（`lang/*.json` 的通用条目、功能说明）尽量两版保持一致，便于对照。

## 版本与发布 / Versioning & release

- `gradle.properties` 的 `mod_version` 是模组版本（两个分支各自维护）。
- 产物名由 `build.gradle` 拼成 `<mod_id>-<mod_version>+mc<minecraft_version>.jar`，全小写、无空格。
- 打标签（标签带游戏版本，两个分支的标签互不冲突）：
  ```powershell
  git tag -a "v1.1.0+mc1.21.1" -m "Oritech Addons One 1.1.0 for MC 1.21.1"
  git push origin "v1.1.0+mc1.21.1"
  ```
- 发布 GitHub Release 时附上 `build\libs\*.jar`（或直接下载 CI 的 artifact）。
- 升级 Oritech：改 `gradle.properties` 里的 `*_version`（Modrinth 版本 ID / 版本号），
  跑一次 `runClient` 确认 mixin 目标与 API 未变。1.21.1 侧的目标：`ShrinkerBlockEntity`、
  `MachineAddonController#gatherAddonStats` 与 `#initAddons(BlockPos)`、`ItemUseMixin` /
  `LaserTargetDesignatorMixin` 指向的物品，以及客户端 UI 的
  `OritechMachineScreen#addExtensionContent` / `#tickExtra`、`OritechWidgetScreen`（组件表访问）、
  `OritechScreenHandler#showRedstoneAddon`、`UpgradableOritechScreenHandler#showRedstoneAddon`。
  两边都用到 `MachineAddonController` 与能量 API；升级后重点回归精炼厂 GUI 的插件数值面板。

## 客户端 UI 补丁 / Client UI patches

精炼厂（含污染精炼厂）在 Oritech 里用的是普通 `OritechScreenHandler`，而插件数值面板
（速度/效率/爆发/腔室 + “插件”浮层按钮）只由 `UpgradableOritechScreen` 添加，所以精炼厂 GUI 一直
没有这块面板——但它的方块实体本身就是 `MachineAddonController`（`MultiblockMachineEntity` →
`UpgradableMachineBlockEntity`），我们的无线扩展坞可以链接它并让插件真正生效。补丁构成：

- `OritechMachineScreenMixin`：在 `addExtensionContent` 末尾为「handler 不是
  `UpgradableOritechScreenHandler`、但方块实体是 `MachineAddonController`」的机器补上面板，
  并在 `tickExtra` 里刷新爆发标签（与上游 `UpgradableOritechScreen` 的行为一致）。浮层是
  `UpgradableOritechScreen#toggleAddonOverlay` 的副本，额外把无线扩展坞按方块名列出来。
- `MachineAddonControllerMixin`：`initAddons` 结束时会用扫描到的方块重建 `connectedAddons`，
  把无线坞全部抹掉；这里在 `initAddons` 返回后把仍然链接的无线坞放回去并推送 `GUI_OPEN`，
  这样浮层与红石面板才看得到它们。
- `OritechScreenHandlerMixin`：普通 handler 的 `showRedstoneAddon` 只看
  `screenData.hasRedstoneControlAvailable()`（精炼厂恒为 false），这里补上与
  `UpgradableOritechScreenHandlerMixin` 相同的「连接的插件里存有控制单元」判断。

**Mixin 限制（踩过的坑）**：本项目的 Mixin 只在**目标类自身**解析 `@Shadow` 成员，
不会去父类找。所以 `addComponent` / `removeComponent`（声明在 `OritechWidgetScreen`）
不能从 `OritechMachineScreen` 的 mixin 里 shadow，改用
`OritechWidgetScreenMixin` + `AddonOverlayHost` 鸭子接口；`menu` / `leftPos` / `topPos` /
`width` / `height` 这类继承成员改用 `getMenu()` / `getGuiLeft()` / `getGuiTop()` 与
`Screen.width` / `Screen.height` 公共字段。新增 UI mixin 时按同样的写法来。

## 提交约定 / Commit conventions

- 提交信息用**简洁的英文**，一行说清做了什么（例：`Add the mod logo` / `Fix the 1.21.1 recipes`）。
- 不强制 Conventional Commits 前缀（用不用 `feat:` / `fix:` 都行），但**不要**写多行长篇说明。
- 一次提交只做一件事。
- 不要提交 `build/`、`run/`、`dist/`、`libs/*.jar`、日志与贴图预览图（`.gitignore` 已覆盖）。

## 许可 / License

- 本模组以 **MIT** 发布，见 `LICENSE`。
- `TEMPLATE_LICENSE.txt` 是 NeoForge MDK 模板自身的许可（MIT, © NeoForged project），保留以注明出处。
- Oritech 本体及其前置（Athena CTM / Architectury / GeckoLib）各有自己的许可，本仓库不重新分发它们的 jar。

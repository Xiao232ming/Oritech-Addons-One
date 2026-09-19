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
  git tag -a "v1.0.0+mc1.21.1" -m "Oritech Addons One 1.0.0 for MC 1.21.1"
  git push origin "v1.0.0+mc1.21.1"
  ```
- 发布 GitHub Release 时附上 `build\libs\*.jar`（或直接下载 CI 的 artifact）。
- 升级 Oritech：改 `gradle.properties` 里的 `*_version`（Modrinth 版本 ID / 版本号），
  跑一次 `runClient` 确认 mixin 目标与 API 未变：26.1.2 是 `AddonSplicerBlockEntity#gatherAddonStats`，
  1.21.1 是 `ShrinkerBlockEntity#gatherAddonStats`，两边都用到 `MachineAddonController` 与能量 API。

## 提交约定 / Commit conventions

- Conventional Commits 前缀：`feat:` `fix:` `docs:` `build:` `chore:`，可带作用域（如 `fix(client):`）。
- 一次提交只做一件事；改动影响游戏内行为时，在正文里写清**怎么验证**（哪台机器 / 哪个界面）。
- 不要提交 `build/`、`run/`、`dist/`、`libs/*.jar`、日志与贴图预览图（`.gitignore` 已覆盖）。

## 许可 / License

- 本模组以 **MIT** 发布，见 `LICENSE`。
- `TEMPLATE_LICENSE.txt` 是 NeoForge MDK 模板自身的许可（MIT, © NeoForged project），保留以注明出处。
- Oritech 本体及其前置（Athena CTM / Architectury / GeckoLib）各有自己的许可，本仓库不重新分发它们的 jar。

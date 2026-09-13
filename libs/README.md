# libs/ — 本地编译期依赖 / Local compile-time dependencies

这个目录里的 jar **不入版本库**（`.gitignore` 里忽略了 `libs/*.jar`）：它们是第三方模组，
体积大（Oritech 一个就 10 MB），而且有新版本时直接换文件即可。

**从零开始构建本项目前，必须先把下面三个 jar 放到这里**，否则 `gradlew build` 会报找不到
`rearth.oritech.*` 之类的符号：

| 文件 / File | 用途 / Purpose | 来源 / Where to get it |
|---|---|---|
| `oritech-2.0.0-exp6.jar` | `compileOnly` + `localRuntime`：本模组是它的附属，编译与开发运行都要 | 整合包实例的 `mods\` 目录，或 CurseForge/Modrinth |
| `athena-4.7.3.jar` | `localRuntime`：Oritech 的前置（贴图/模型） | 同上 |
| `geckolib-neoforge-26.1.2-5.5.2.jar` | `localRuntime`：Oritech 的前置（动画） | 同上 |

本机这三个文件是从 HMCL 实例复制的：

```powershell
$mods = 'D:\Games\MC\HMCL\.minecraft\versions\26.1.2-NeoForge\mods'
Copy-Item "$mods\oritech-2.0.0-exp6.jar"                       'D:\Games\MC\MOD\Oritech Addons One\libs\'
Copy-Item "$mods\athena-4.7.3.jar"                             'D:\Games\MC\MOD\Oritech Addons One\libs\'
Copy-Item "$mods\geckolib-neoforge-26.1.2-5.5.2.jar"           'D:\Games\MC\MOD\Oritech Addons One\libs\'
```

说明 / Notes：

- 这三个 jar **不会**被打进本模组的产物 jar，只在编译和开发运行时使用
  （见 `build.gradle` 里的 `compileOnly files(...)` 与 `localRuntime files(...)`）。
- 换 Oritech 版本时：替换 `libs\oritech-*.jar`，同时同步修改 `build.gradle` 里的文件名，
  并核对本模组用到的 Oritech 内部 API（`MachineAddonController`、
  `AddonSplicerBlockEntity#gatherAddonStats` 等）是否仍然存在。
- 如果你更希望仓库自包含（一个新克隆就能直接构建），把 `.gitignore` 里的 `libs/*.jar` 删掉再提交即可。

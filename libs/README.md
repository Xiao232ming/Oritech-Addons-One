# libs/ — 本地编译期依赖 / Local compile-time dependencies

这个目录里的 jar **不入版本库**（`.gitignore` 里忽略了 `libs/*.jar`）：它们是第三方模组，
体积大（Oritech 一个就 10 MB），有新版本时直接换文件即可。

**从零开始构建本项目前，必须先把下面四个 jar 放到这里**：

| 文件 | 用途 | 来源 |
|---|---|---|
| `oritech-neoforge-1.21.1-1.2.12.jar` | `compileOnly` + `localRuntime`：本模组是它的附属 | 整合包实例的 `mods\`，或 CurseForge/Modrinth |
| `architectury-13.0.11-neoforge.jar` | `localRuntime`：Oritech 的前置（其注册系统基于 Architectury） | 同上 |
| `athena-neoforge-1.21.1-4.0.6.jar` | `localRuntime`：Oritech 的前置（贴图/模型） | 同上 |
| `geckolib-neoforge-1.21.1-4.9.2.jar` | `localRuntime`：Oritech 的前置（动画） | 同上 |
| oritechthings-0.0.46.jar | 可选 localRuntime：用于验证其它附属模组的分级插件能被Ⅰ/Ⅲ型识别（构建不需要它） | CurseForge/Modrinth |

本机是从 HMCL 的 1.21.1 实例复制的：

```powershell
$mods = 'D:\Games\MC\HMCL\.minecraft\versions\1.21.1-NeoForge\mods'
$libs = 'D:\Games\MC\MOD\1.21.1-NeoForge\Oritech Addons One\libs'
Copy-Item "$mods\oritech-neoforge-1.21.1-1.2.12.jar"  $libs
Copy-Item "$mods\architectury-13.0.11-neoforge.jar"    $libs
Copy-Item "$mods\athena-neoforge-1.21.1-4.0.6.jar"     $libs
Copy-Item "$mods\geckolib-neoforge-1.21.1-4.9.2.jar"   $libs
```

说明：

- 这四 jar **不会**被打进本模组的产物 jar，只在编译和开发运行时使用（见 `build.gradle`）。
- 换 Oritech 版本时：替换 `libs\oritech-*.jar`，同步修改 `build.gradle` 里的文件名，
  并核对 mixin 目标 `rearth.oritech.block.entity.interaction.ShrinkerBlockEntity#gatherAddonStats`
  与 `rearth.oritech.util.MachineAddonController` 的 API 是否还在。
- 想让仓库自包含（新克隆即可构建），把 `.gitignore` 里的 `libs/*.jar` 删掉再提交即可。

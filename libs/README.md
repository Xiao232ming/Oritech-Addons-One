# libs/ — 可选的本地依赖 / Optional local dependencies

**构建不再需要手动放 jar。** `oritech`（编译期 + 开发运行）与它的前置
`architectury-api` / `athena`（Athena CTM）/ `geckolib` 现在都从 **Modrinth Maven** 解析，
坐标（Modrinth 版本 ID）写在 `gradle.properties` 里，仓库地址见 `build.gradle` 的 `repositories`：

```groovy
maven {
    name = 'Modrinth'
    url = 'https://api.modrinth.com/maven'
    content { includeGroup 'maven.modrinth' }
}
```

所以 `libs/` 现在只是**离线备用**目录：里面的 jar 不入版本库（`.gitignore` 忽略 `libs/*.jar`）。

| 依赖 / Dependency | 坐标 | 用途 / Purpose |
|---|---|---|
| `oritech` 1.2.12 | Modrinth `lxLMO7bV` | `compileOnly` + `localRuntime`：本模组是它的附属 |
| `architectury` 13.0.11 | `dev.architectury:architectury-neoforge:13.0.11`（[官方 Maven](https://maven.architectury.dev/)，Modrinth 没有） | `localRuntime`：Oritech 的前置（注册系统） |
| `athena` (Athena CTM) 4.0.6 | Modrinth `dJgL278E` | `localRuntime`：Oritech 的运行时前置 |
| `geckolib` 4.9.2 | Modrinth `tPkJmim6` | `localRuntime`：Oritech 的运行时前置 |
| `oritech-things` 0.0.46（可选） | Modrinth `vt3nmngK` | `localRuntime`：仅用于验证分级插件识别，不影响构建 |

**换依赖版本**：Modrinth 上的那几个打开 `https://modrinth.com/mod/<slug>/versions`，挑对应
**NeoForge + 1.21.1** 的那个文件，在文件列表里复制它的 **Version ID**，替换 `gradle.properties` 里对应的
`*_version`，并同步更新上表。注意选对加载器：同一个版本号可能有 fabric / neoforge 两个 ID
（例如 Oritech 1.2.12 的 fabric 是 `lYkwnT9Q`、neoforge 是 `lxLMO7bV`）。
Architectury 直接改 `architectury_version` 为版本号即可（走官方 Maven）。

**想用本地 jar 构建（例如完全离线）**：把 `build.gradle` 里的

```groovy
compileOnly "maven.modrinth:oritech:${oritech_version}"
localRuntime "maven.modrinth:oritech:${oritech_version}"
localRuntime "dev.architectury:architectury-neoforge:${architectury_version}"
localRuntime "maven.modrinth:athena-ctm:${athena_version}"
localRuntime "maven.modrinth:geckolib:${geckolib_version}"
```

换成文件依赖即可（本目录里已经放着这几份本机副本，来自 HMCL 实例
`D:\Games\MC\HMCL\.minecraft\versions\1.21.1-NeoForge\mods`）：

```groovy
compileOnly files('libs/oritech-neoforge-1.21.1-1.2.12.jar')
localRuntime files('libs/oritech-neoforge-1.21.1-1.2.12.jar')
localRuntime files('libs/architectury-13.0.11-neoforge.jar')
localRuntime files('libs/athena-neoforge-1.21.1-4.0.6.jar')
localRuntime files('libs/geckolib-neoforge-1.21.1-4.9.2.jar')
localRuntime files('libs/oritechthings-0.0.46.jar')   // 可选
```

为什么默认不入库：第三方模组 jar 体积大（Oritech 一个就 10 MB）且各自有自己的许可，
随仓库分发既不必要也不合适；从 Modrinth Maven 拉取还能让 GitHub Actions 直接构建。

升级 Oritech 时记得核对本模组用到的内部 API 是否还在：
mixin 目标 `rearth.oritech.block.entity.interaction.ShrinkerBlockEntity#gatherAddonStats`
与 `rearth.oritech.util.MachineAddonController`、`rearth.oritech.api.energy.EnergyApi`。

# libs/ — 可选的本地依赖 / Optional local dependencies

**构建不再需要手动放 jar。** `oritech`（编译期 + 开发运行）、`athena`（Athena CTM）与 `geckolib`
现在都从 **Modrinth Maven** 解析，坐标（Modrinth 版本 ID）写在 `gradle.properties` 里，
仓库地址见 `build.gradle` 的 `repositories`：

```groovy
maven {
    name = 'Modrinth'
    url = 'https://api.modrinth.com/maven'
    content { includeGroup 'maven.modrinth' }
}
```

所以 `libs/` 现在只是**离线备用**目录：里面的 jar 不入版本库（`.gitignore` 忽略 `libs/*.jar`）。

| 依赖 / Dependency | 本分支使用的 Modrinth 版本 ID | 用途 / Purpose |
|---|---|---|
| `oritech` 2.0.0-exp6 | `2xLWeZUn` | `compileOnly` + `localRuntime`：本模组是它的附属 |
| `athena` (Athena CTM) 4.7.3 | `8KRMFzZ7` | `localRuntime`：Oritech 的运行时前置 |
| `geckolib` 5.5.2 | `xfVfPcoC` | `localRuntime`：Oritech 的运行时前置 |

**换依赖版本**：打开 `https://modrinth.com/mod/<slug>/versions`，挑对应 **NeoForge + 26.1.2** 的那个文件，
在页面上的文件列表里复制它的 **Version ID**，替换 `gradle.properties` 里对应的 `*_version`，
并同步更新上表。注意选对加载器：同一个版本号可能有 fabric / neoforge 两个 ID
（例如 Oritech 1.2.12 的 fabric 是 `lYkwnT9Q`、neoforge 是 `lxLMO7bV`）。

**想用本地 jar 构建（例如完全离线）**：把 `build.gradle` 里的

```groovy
compileOnly "maven.modrinth:oritech:${oritech_version}"
localRuntime "maven.modrinth:oritech:${oritech_version}"
localRuntime "maven.modrinth:athena-ctm:${athena_version}"
localRuntime "maven.modrinth:geckolib:${geckolib_version}"
```

换成文件依赖即可（本目录里已经放着这几份本机副本，来自 HMCL 实例
`D:\Games\MC\HMCL\.minecraft\versions\26.1.2-NeoForge\mods`）：

```groovy
compileOnly files('libs/oritech-2.0.0-exp6.jar')
localRuntime files('libs/oritech-2.0.0-exp6.jar')
localRuntime files('libs/athena-4.7.3.jar')
localRuntime files('libs/geckolib-neoforge-26.1.2-5.5.2.jar')
```

为什么默认不入库：第三方模组 jar 体积大（Oritech 一个就 10 MB）且各自有自己的许可，
随仓库分发既不必要也不合适；从 Modrinth Maven 拉取还能让 GitHub Actions 直接构建。

升级 Oritech 时记得核对本模组用到的内部 API 是否还在：
`MachineAddonController`、`AddonSplicerBlockEntity#gatherAddonStats`
（1.21.1 分支是 `ShrinkerBlockEntity#gatherAddonStats`）。

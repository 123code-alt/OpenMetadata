# 原架构裁剪版构建入口

本目录用于构建术语库、本体浏览器、分类标签、上下文中心与 MCP 的裁剪分支，基于 OpenMetadata 2.0.8。实施状态与依赖边界见 [计划](../docs/governance-context/PLAN.md)。当前仍在建立与验证可运行基线，不应替换已有标准环境。

## 构建模块范围

`pom.xml` 只聚合 common、spec、shaded-deps、service、mcp、ui-core、ui；保留 JSON Schema 和原生成链路。根工程仍保留原始模块定义，便于对比。

本阶段仅缩小构建模块范围、收拢导航和移除无关 MCP 工具；共享服务、实体注册、路由和设置尚未完成物理裁剪。没有把这些变化当作最终裁剪结果。

## IDEA / Maven 命令

在 IDEA 打开仓库根目录，Project SDK、Maven Runner 与 Importer 都选择 JDK 21。下面命令在仓库根目录逐条执行；IDEA Maven Run Configuration 的 Working directory 也选仓库根，Command line 填相同参数但不包含 `mvn.cmd`。

先安装父 POM（仅根项目，不编译子模块）：

```powershell
mvn.cmd -B -ntp -N install
```

检查后端与 MCP 的裁剪构建图：

```powershell
mvn.cmd -B -ntp -f governance-context/pom.xml -pl :openmetadata-service,:openmetadata-mcp -am validate
```

编译安装后端与 MCP：

```powershell
mvn.cmd -B -ntp -f governance-context/pom.xml -pl :openmetadata-service,:openmetadata-mcp -am -Dmaven.test.skip=true install
```

这一条跳过测试，仅用于建立编译基线，不代表功能验收。后端主要运行入口仍为 `org.openmetadata.service.OpenMetadataApplication`，参数为 `server conf/openmetadata.yaml`，工作目录为仓库根。需完成数据库初始化、依赖隔离、前端资源构建后再启动，完整部署步骤随验证补充。

## 隔离配置草案

[windows.env.example](windows.env.example) 列出原 `conf/openmetadata.yaml` 实际使用的环境变量。应把本机值填进 IDEA 运行配置的 Environment variables，**Java 不会自动读取该文件**。不要填入旧最小版的 `windows-config.json`，两个项目的配置结构不同。

- PostgreSQL 使用独立账号与 `governance_context_v208` schema，并连接现有数据库；迁移及 Flowable 行为仍需验证。
- ES 使用非空 `ELASTICSEARCH_CLUSTER_ALIAS`，索引、别名和重建操作仍需验收。
- Fuseki 使用独立 dataset，S3 使用独立 bucket/prefix。
- `SEMANTIC_SEARCH_ENABLED=false` 与 `LLM_PROVIDER=noop` 用于无模型凭据的基线验证；不代表向量检索、自动内容提取已经可用。
- 必须替换示例中的凭据和实际数据库名。配置当前仍是草案，不能据此宣称共享环境隔离已通过。

## Windows 前端的显式构建步骤

以下步骤按当前源码的实际生成和构建顺序展开。完整 Windows / IDEA 部署尚未验收；请先将 JDK 21、Node 22.23.4+、Yarn 1.22 配到 Path。原前端较大，Node 构建默认使用最多 6144 MB 堆，构建机建议预留 8 GB 以上可用内存。

先分别安装三个项目的锁定依赖，跳过会自动串联其他构建的生命周期脚本：

```powershell
yarn.cmd install --frozen-lockfile --ignore-scripts --non-interactive
yarn.cmd --cwd openmetadata-ui-core-components/src/main/resources/ui install --frozen-lockfile --ignore-scripts --non-interactive
yarn.cmd --cwd openmetadata-ui/src/main/resources/ui install --frozen-lockfile --ignore-scripts --non-interactive
```

单独构建原共享组件：

```powershell
$env:NODE_OPTIONS = '--max-old-space-size=6144'
yarn.cmd --cwd openmetadata-ui-core-components/src/main/resources/ui vite build
```

前端 JavaScript 解析器使用 **ANTLR 4.9.2**，与 Maven 生成 Java 模型时使用的 ANTLR 版本不同。下载官方 [antlr-4.9.2-complete.jar](https://www.antlr.org/download/antlr-4.9.2-complete.jar)，例如保存为 `C:\tools\antlr-4.9.2-complete.jar`，在仓库根目录逐条执行：

```powershell
$grammar = (Resolve-Path .\openmetadata-spec\src\main\antlr4\org\openmetadata\schema).Path
$antlrOutput = Join-Path (Get-Location).Path 'openmetadata-ui\src\main\resources\ui\src\generated\antlr'
java -jar C:\tools\antlr-4.9.2-complete.jar -Dlanguage=JavaScript -o $antlrOutput "$grammar\EntityLink.g4" "$grammar\Fqn.g4" "$grammar\JdbcUri.g4"
```

进入前端目录，生成连接与应用表单所需的 JSON Schema，再单独打包：

```powershell
Set-Location .\openmetadata-ui\src\main\resources\ui
node parseSchemas.js
yarn.cmd vite build
Test-Path .\dist\index.html
```

每条命令成功后再继续。这里直接运行 Vite，避免原 `build` 和 `js-antlr` 脚本中的 Bash 环境变量语法影响 Windows PowerShell。静态资源位于 `openmetadata-ui/src/main/resources/ui/dist`；与服务端合并运行和 IDEA 的完整 classpath 步骤将在隔离验证后补充。

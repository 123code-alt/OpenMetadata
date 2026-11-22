# Windows + IntelliJ IDEA：逐步构建和启动

适用于 **`codex/semantic-context-minimal`** 最小分支，后端为 JDK 21，前端构建为静态资源，随 Java 应用一起通过 **8585** 提供服务。本文从 IDEA 导入源码、逐项构建到运行配置逐步展开，每一步完成并检查结果后，再执行下一步。

你的 PostgreSQL 和 Elasticsearch 已安装，并已初始化标准 OpenMetadata 2.0.8。本教程复用现有 PostgreSQL 实例及数据库，**另建 `semantic_context_poc` schema** 存放最小版数据。最小版目前不调用 ES，不需要修改已有 ES 索引或标准 OpenMetadata 配置，也不执行原版的 bootstrap / migrate。

## 1. 准备工具和源码

| 工具 | 要求 |
| --- | --- |
| IntelliJ IDEA | 安装 Java、Maven 支持；Community / 免费版即可完成构建和运行 |
| JDK | **21**，包含 `java`、`javac`、`jar` |
| Maven | 3.9+；IDEA 和终端使用同一个 Maven 版本 |
| Node.js | 推荐 22.x，至少 **22.23.4** |
| Yarn | **1.22.22**，Classic |
| Git | Git for Windows |

工具下载：[JDK 21](https://adoptium.net/temurin/releases/?version=21&os=windows)、[Maven](https://maven.apache.org/download.cgi)、[Node.js](https://nodejs.org/en/download)、[Git](https://git-scm.com/downloads/win)。将 JDK 21 配到 `JAVA_HOME`，并将 JDK、Maven 的 `bin` 加入 `Path`。建议至少 4 GB 可用内存、3 GB 空闲磁盘。

在 PowerShell 中逐条检查；尚未安装 Yarn 时先执行第一条：

```powershell
npm.cmd install --global yarn@1.22.22

git --version
java -version
javac -version
mvn.cmd -version
node --version
yarn.cmd --version
```

`java`、`javac` 及 Maven 显示的 Java 都应为 21。项目依赖使用 Yarn；Windows 中使用 `.cmd` 后缀，避免 `yarn.ps1` 执行策略问题。

新目录拉取源码：

```powershell
$projectParent = Join-Path $env:USERPROFILE 'source'
New-Item -ItemType Directory -Path $projectParent -Force
Set-Location $projectParent

git clone --config core.autocrlf=false --depth 1 --single-branch --branch codex/semantic-context-minimal https://github.com/123code-alt/OpenMetadata.git semantic-context
Set-Location .\semantic-context

git branch --show-current
(Get-Location).Path
```

分支应为 `codex/semantic-context-minimal`。记下最后一条输出的**仓库根目录绝对路径**，后续 IDEA 的 Working directory 都用它。`core.autocrlf=false` 保留 LF，避免 Windows 自动换行转换影响格式检查。

如果已经在该最小分支：先检查并保留本地改动，再执行 `git pull --ff-only`，无需重新克隆。

## 2. 在现有 PG 数据库中创建独立 schema

### 2.1 确认当前连接的数据库

用已有的 pgAdmin、DBeaver 或 psql 连接 PostgreSQL；IDEA 有 Database 功能时也可使用。**连接到已经初始化 OpenMetadata 2.0.8 的那个数据库**，用有权限创建角色和 schema 的管理员执行：

```sql
SELECT current_database() AS database_name, current_user AS connected_user;
SHOW search_path;

SELECT schema_name
FROM information_schema.schemata
WHERE schema_name = 'semantic_context_poc';
```

下面以现有数据库名 **`openmetadata_db`** 为例；请将本文 SQL、JDBC URL 中的这个名字替换成第一条查询返回的真实库名。无需另建数据库。schema 查询首次应返回 0 行；如果名称已被其他应用占用，换一个新名字，并同步修改后续 SQL 和 JDBC URL。

### 2.2 创建专用账号和 schema（只执行一次）

仍在该现有数据库的管理员连接中，先把示例密码换成自己的密码，再逐条执行。SQL 密码字符串内若含单引号，用两个单引号转义：

```sql
CREATE ROLE semantic_context_app LOGIN PASSWORD 'REPLACE_WITH_YOUR_NEW_PASSWORD';

GRANT CONNECT ON DATABASE "openmetadata_db" TO semantic_context_app;

CREATE SCHEMA semantic_context_poc AUTHORIZATION semantic_context_app;

REVOKE ALL ON SCHEMA semantic_context_poc FROM PUBLIC;
```

`semantic_context_app` 为本验证版的新账号，拥有新 schema，可在其中创建和读写自己的表；配置中使用这个新账号和对应密码。若角色或 schema 已存在，先核实归属，不要删除或覆盖已有对象。重启时跳过本节。

这些 SQL 不修改标准 OpenMetadata 的 schema、账号、`search_path` 或原有表，也不更改数据库级别的默认配置。无需给新账号授予原有业务表的读写权限。

### 2.3 显式指定应用访问的 schema

应用的 JDBC URL 必须包含：

```text
jdbc:postgresql://127.0.0.1:5432/openmetadata_db?currentSchema=semantic_context_poc
```

将主机、端口、库名改成你的现有 PG 信息。**保留 `currentSchema=semantic_context_poc`，只指定这个新 schema**。已有 URL 含 `?` 参数时，用 `&currentSchema=semantic_context_poc` 追加，例如：

```text
jdbc:postgresql://pg.example.internal:5432/openmetadata_db?sslmode=require&currentSchema=semantic_context_poc
```

应用使用连接池，每条 JDBC 连接都由这个参数设置 `search_path`。单独在数据库工具里执行 `SET search_path` 只影响那个工具的当前会话，不能代替 JDBC 参数；不要把 `public` 或原 OpenMetadata schema 加到应用的 `currentSchema` 列表中。

## 3. 用 IDEA 导入工程并设置 JDK / Maven

1. `File → Open`，选择仓库根目录的 **`pom.xml`**，作为 Maven 项目打开。根目录应同时能看到 `backend`、`frontend`、`ui-core`。
2. `File → Project Structure → Project`：Project SDK 选择 **JDK 21**，Language level 为 21；Modules 使用同一个 SDK。
3. `Settings → Build, Execution, Deployment → Build Tools → Maven`：Maven home 选择已安装的 Maven 3.9+，核对使用的 `settings.xml` 和本地仓库路径。
4. Maven 的 `Importing` 中将 importer JDK 设为 21；`Runner` 中将 JRE 设为 21。不同 IDEA 版本字段位置可能稍有变化。
5. 点击 Maven 工具窗口的 **Reload All Maven Projects**，等待依赖解析结束。
6. Maven 面板应出现根聚合工程，以及 `contracts`、`glossary`、`context`、`platform-adapters`、`app` 五个模块。不要只导入 `backend/app/pom.xml`。
7. 打开 IDEA 底部 Terminal，选择 **PowerShell**，确认当前目录为仓库根目录，再执行 `mvn.cmd -version`、`node --version`、`yarn.cmd --version`。

如果 IDEA Terminal 仍找不到新安装的命令，重新打开 IDEA，使其获取更新后的 `Path`。Maven 导入、IDEA 编译器、运行配置和终端的 Java 版本都应为 21。

## 4. 手动准备运行配置文件

仓库提供可阅读、可手工修改的模板：[config/windows-idea.example.json](../config/windows-idea.example.json)。在 IDEA Terminal 创建本机目录：

```powershell
New-Item -ItemType Directory -Path .\.runtime -Force
```

**首次配置**时执行下面这一条命令，按模板创建本机文件；若文件已存在会报错，请直接编辑原文件，不要覆盖：

```powershell
New-Item -ItemType File -Path .\.runtime\windows-config.json -Value (Get-Content .\config\windows-idea.example.json -Raw) -ErrorAction Stop
```

在 IDEA 中打开 `.runtime/windows-config.json`，手工修改以下字段：

| 字段 | 要填写的内容 |
| --- | --- |
| `jdbcUrl` | 上一节的真实地址、端口、现有数据库名，保留 `currentSchema=semantic_context_poc` |
| `databaseUser` | `semantic_context_app`，或你在第 2 节实际创建的专用账号 |
| `databasePassword` | 新账号的真实密码 |
| `identities[0].token` | 为本验证版生成并保存的 API 令牌 |
| `blobDirectory` | 默认 `.runtime/blobs`，相对于仓库根目录；保存上传文件 |
| `server.applicationConnectors[0].port` | 默认 8585 |
| `server.adminConnectors[0].port` | 默认 8586，仅本机管理与健康检查 |

在 IDEA Terminal 执行这一条命令生成随机令牌，将输出复制到 `identities[0].token`，以后重启保留同一个值：

```powershell
[Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
```

模板是标准 JSON，不能写注释。密码包含 `"` 时写成 `\"`，包含 `\` 时写成 `\\`；IDEA 会提示 JSON 语法错误。令牌和密码只填写在被 Git 忽略的 `.runtime` 文件中，不修改提交到仓库的模板。

**如果标准 OpenMetadata 服务也在运行，先检查端口：**

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8585,8586 -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,OwningProcess
```

端口已占用时，可将最小版 JSON 的端口改为 **18585 / 18586**，后文访问和检查命令也改成对应端口。不要为本验证版停止不相关进程。本文后续命令按 8585 / 8586 展示。

应用端口默认绑定 `127.0.0.1`。需要其他电脑访问时，将应用连接器的 `bindHost` 改为 `0.0.0.0`，并在防火墙放行应用端口；管理端口继续只绑定本机。

如果按旧教程已生成 JSON，保留原文件，手工更新 JDBC URL 和新账号。新 schema 是空环境，原 schema 数据不会自动搬过来；不要删除原表。

## 5. 在 IDEA 中逐步构建

### 5.1 先设置 Maven 运行方式

在 `Run → Edit Configurations → + → Maven` 创建 Maven 配置：

| 配置项 | 填写 |
| --- | --- |
| Name | 例如 `SC Maven` |
| Working directory / Run | 选择**仓库根目录**，包含根 `pom.xml` |
| Command line | 按后续步骤逐次填写对应 Maven 目标，不包含 `mvn.cmd` 前缀 |
| Runner JRE | JDK 21 |

每次修改 Command line 后点击 **Run**，在 IDEA Run 窗口确认 `BUILD SUCCESS` 再继续。也可为各目标分别创建 Maven 配置。下文同时给出 IDEA 目标和等价的 Terminal 命令，选择一种方式执行即可。

### 5.2 清理后端旧产物

IDEA Maven 的 Command line：

```text
-B -ntp clean
```

等价的 IDEA Terminal 命令：

```powershell
mvn.cmd -B -ntp clean
```

只清理 Maven `target`，保留 `.runtime` 配置、上传对象和数据库数据。清理后先完成前端构建，再打包后端。

### 5.3 安装前端依赖

在 IDEA Terminal 的仓库根目录执行：

```powershell
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
yarn.cmd install --frozen-lockfile --non-interactive
```

等待成功后再继续。这里安装根工作区中的 `frontend` 和 `ui-core`，不下载浏览器测试运行时。

### 5.4 构建共享 UI 组件

```powershell
yarn.cmd workspace @openmetadata/ui-core-components run build
```

这一步执行 `ui-core/package.json` 中的 **`vite build`**，生成 `ui-core/dist`。

### 5.5 单独检查前端类型和 lint

```powershell
yarn.cmd workspace semantic-context-ui run lint
```

这一步执行 **`tsc --noEmit`** 和 **`eslint src --max-warnings=0`**，有错误先修复。

### 5.6 构建页面静态资源

```powershell
yarn.cmd workspace semantic-context-ui run build
Test-Path .\frontend\dist\index.html
```

这一步执行 **`tsc --noEmit`** 和 **`vite build`**，生成 `frontend/dist`；`Test-Path` 应返回 `True`。运行时由 Java 提供这些资源，不另开 Vite 服务。

### 5.7 在 IDEA 执行 Java 格式检查

IDEA Maven 的 Command line：

```text
-B -ntp spotless:check
```

等价命令：

```powershell
mvn.cmd -B -ntp spotless:check
```

### 5.8 在 IDEA 执行后端测试

IDEA Maven 的 Command line：

```text
-B -ntp test
```

等价命令：

```powershell
mvn.cmd -B -ntp test
```

当前后端集成测试使用临时 H2 测试库，不连接已初始化的 PostgreSQL 或 ES。测试通过后继续打包。

### 5.9 在 IDEA 打包 JAR

IDEA Maven 的 Command line：

```text
-B -ntp -DskipTests package
```

等价命令：

```powershell
mvn.cmd -B -ntp -DskipTests package
```

这里跳过重复测试，因为上一节已单独执行。**从根聚合工程运行**，不要只对 `app` 模块执行 package。

打包后在 IDEA Terminal 逐项检查：

```powershell
Get-Item .\backend\app\target\app-0.1.0-SNAPSHOT.jar
Test-Path .\backend\app\target\classes\assets\index.html
jar tf .\backend\app\target\app-0.1.0-SNAPSHOT.jar | Select-String '^assets/index.html$'
```

应看到 JAR 文件、`True` 和 `assets/index.html`。Maven 在本步将 `frontend/dist` 复制到 `app` 的类路径和最终 JAR 中；只点击 IDEA 的 Build Project 不能替代这一步。

## 6. 先校验配置，再从 IDEA 启动

### 6.1 显式校验本机 JSON

在 IDEA Terminal 执行：

```powershell
java -jar .\backend\app\target\app-0.1.0-SNAPSHOT.jar check .\.runtime\windows-config.json
```

应看到 `Configuration is OK`。此命令检查配置格式，不验证数据库密码、schema 权限或网络连通性；这些在真正启动时检查。

### 6.2 从 IDEA 运行已打包的应用

`Run → Edit Configurations → + → JAR Application`，填写：

| 配置项 | 填写 |
| --- | --- |
| Name | `SC JAR` |
| Path to JAR | 选择 `backend/app/target/app-0.1.0-SNAPSHOT.jar` 的实际绝对路径 |
| Program arguments | `server .runtime/windows-config.json` |
| Working directory | 选择**仓库根目录**，与第 1 节打印的路径一致 |
| JRE | JDK 21 |
| VM options | `-Xms128m -Xmx512m`，未显示时用 Modify options 添加 |

点击 **Run**，保持 IDEA 的 Run 窗口运行。完全等价的显式命令是：

```powershell
java -Xms128m -Xmx512m -jar .\backend\app\target\app-0.1.0-SNAPSHOT.jar server .\.runtime\windows-config.json
```

首次启动时，应用会通过专用账号在 **`semantic_context_poc`** 中创建 `sc_write_lock`、`sc_entity`、`sc_history`、`sc_outbox` 四张表。schema 本身由第 2 节提前创建；缺少 schema 或权限时应处理启动错误，不要删除 JDBC 中的 `currentSchema` 来绕过。

### 6.3 在 IDEA 从源码运行和调试

需要设置 Java 断点时，先停止 `SC JAR`，再创建 `+ → Application`：

| 配置项 | 填写 |
| --- | --- |
| Name | `SC Source` |
| Main class | `org.openmetadata.standalone.app.SemanticContextApplication` |
| Use classpath of module | 选择 **`backend/app/pom.xml` 对应的 `app` 模块**，不选根聚合模块 |
| Program arguments | `server .runtime/windows-config.json` |
| Working directory | **仓库根目录** |
| JRE | JDK 21 |
| VM options | `-Xms128m -Xmx512m` |

先完成第 5 节 Maven 打包，再点击 **Run / Debug**；同一端口只运行一个实例。IDEA 导入 Maven 后，`app` 的编译输出应对应 `backend/app/target/classes`，其中已有 `assets/index.html`。若自定义了 IDEA 编译输出路径，在 `Project Structure → Modules → app → Paths` 核对输出位置；页面资源缺失时先恢复 Maven 输出目录并重新执行第 5.9 节。

源码断点可从 `backend/app/src/main/java/org/openmetadata/standalone/app/SemanticResource.java` 的接口方法开始。前端此时仍使用 Maven 打包时复制的静态页面。

## 7. 检查页面、API 和 schema 隔离

### 7.1 健康检查和令牌登录

在 IDEA Terminal 执行：

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8586/healthcheck' | ConvertTo-Json -Depth 5
(Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8585/').StatusCode
```

`semantic-database` 和 `static-ui` 的 `healthy` 都应为 `true`，页面状态应为 `200`。浏览器打开 **<http://127.0.0.1:8585>**。复制本机配置中的令牌：

```powershell
$config = Get-Content .\.runtime\windows-config.json -Raw | ConvertFrom-Json
$config.identities[0].token | Set-Clipboard
```

粘贴到页面的“API 令牌”输入框。然后显式检查术语库 API：

```powershell
$headers = @{ Authorization = 'Bearer ' + $config.identities[0].token }
Invoke-RestMethod -Uri 'http://127.0.0.1:8585/api/v1/glossaries' -Headers $headers
```

在页面新建术语库和术语，再测试文章、记忆、UTF-8 TXT/MD/CSV/JSON 上传和显式上下文组装。最小版使用新令牌和新 schema，不使用标准 OpenMetadata 的登录账号或已有元数据。

### 7.2 在数据库工具中确认表的位置

仍连接到同一个现有 PG 数据库，执行：

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'semantic_context_poc'
ORDER BY table_name;

SELECT entity_type, id, fqn, revision
FROM semantic_context_poc.sc_entity
ORDER BY entity_type, fqn;
```

第一条应只显示上述四张表，第二条应能看到刚才页面新建的数据。它们应在 `semantic_context_poc` 下。标准 OpenMetadata 原 schema 的数据保持原样；最小版不会初始化或重建 ES 索引。

要核对连接的 schema，可用专用账号在数据库工具中建立新连接，JDBC URL 同样带 `currentSchema=semantic_context_poc`，再执行：

```sql
SELECT current_database(), current_user, current_schema();
SHOW search_path;
```

应显示专用账号、`semantic_context_poc` 和仅该 schema 的显式搜索路径。管理员原连接里的默认 `search_path` 无需修改。

## 8. 停止、重启和日常改代码

- **停止：**IDEA Run / Debug 窗口点击 Stop；若从 Terminal 启动，使用 `Ctrl+C`。
- **重启：**再次运行同一个 IDEA 配置，不重建账号、schema 或令牌。
- **改 Java：**停止实例，重新执行格式检查、测试及 package，再运行；源码调试也要保留静态资源所在的 Maven 输出。
- **改前端：**重新执行共享 UI 构建（改到 `ui-core` 时）、前端检查和构建，再 Maven package，然后重启；页面来自 JAR 或 `app` 类路径中的静态资源副本。
- **更新源码：**停止实例，保留本地改动后 `git pull --ff-only`，按第 5 节逐项重建，再用原本机 JSON 启动。
- **备份：**同时备份新 schema、`.runtime/blobs` 和 `.runtime/windows-config.json`。移动工程时，保留这些内容并检查 `blobDirectory`。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| Maven 面板只有 app 或找不到本工程的 SNAPSHOT 依赖 | 用根 `pom.xml` 重新导入并刷新五个模块；从根执行 Maven 目标。 |
| `release version 21 not supported` / Java 版本不符 | 同时检查 IDEA Project SDK、Maven importer、Runner、运行配置的 JRE，以及终端 `mvn.cmd -version`。 |
| `yarn.ps1` 执行策略报错 | 使用 `yarn.cmd` / `npm.cmd`，不需要改变执行策略。 |
| Node engine 不满足 | 使用至少 22.23.4 的 Node，并重新打开 IDEA。 |
| `no schema has been selected to create in` | schema 未创建、名字不匹配或专用账号缺少 USAGE；核对 SQL 和 JDBC `currentSchema`。 |
| `permission denied for schema/table` | 确认 schema 属于本验证版专用账号；不要通过给原 OpenMetadata 表加权限来解决配置错误。 |
| 看到标准环境的数据或页面 | 检查 IDEA 指向的工程/JAR、启动日志端口、JSON 的 `currentSchema` 和浏览器地址；可能访问了原来占用 8585 的服务。 |
| `Address already in use` | 检查原服务是否占用 8585/8586；可让最小版使用 18585/18586，并同步修改检查地址。 |
| `Connection refused` / 密码错误 | 核对真实 PG 地址、端口、现有库名和新账号密码；`check` 命令不会验证这些值。 |
| `static-ui` 不健康 / 页面 404 | 核对 `frontend/dist/index.html`、`backend/app/target/classes/assets/index.html` 和 JAR 的 `assets/index.html`；先构建前端再 Maven package。 |
| 源码 Run 找不到主类或页面 | 核对 app 模块 classpath、Maven 导入及模块输出路径；先用 `SC JAR` 验证打包结果，再检查源码运行配置。 |
| Java 找不到本机 JSON / 文件上传路径不对 | 将 Working directory 设为仓库根目录；Program arguments 为 `server .runtime/windows-config.json`。 |
| JSON 解析失败 | 修正引号、反斜杠、逗号；不要添加注释。密码有特殊字符时按 JSON 字符串规则转义。 |
| 页面 API 返回 401 | 使用当前启动配置中的 API 令牌，不使用标准 OpenMetadata 的登录凭据。 |
| 修改 `.env` 或原 `openmetadata.yaml` 无效 | 本教程的 IDEA 配置只读取 `.runtime/windows-config.json`；修改该文件并重启。 |

验证记录见 [VALIDATION.md](VALIDATION.md)。当前云端验证环境为 Linux，Windows 实机与 IDEA 界面操作仍需在目标电脑执行确认。

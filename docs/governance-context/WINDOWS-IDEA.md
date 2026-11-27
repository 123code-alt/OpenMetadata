# Windows / IDEA：原架构裁剪版逐步部署

适用分支：`codex/governance-context-2.0.8`，基于 OpenMetadata 2.0.8。后端为 JDK 21，原前端构建为静态文件，由同一个 Java 服务在 8585 提供。应用和前端均不使用 Docker。本文不安装 PG / ES，复用已有服务。

本文命令使用 **PowerShell**，每段成功后再执行下一段。没有一键部署脚本。Linux 上已验证构建和联合运行；Windows 本机与 IDEA 界面操作仍需按本文实测。

## 1. 工具、源码与 IDEA

准备 JDK 21、Maven 3.9.9、Node 22.23.4 或更新的 22.x、Yarn Classic 1.22.x、Git。构建原前端建议至少 8 GB 可用内存，源码和依赖建议预留 12 GB 磁盘。不要使用 Node 18 或 Yarn Berry。8 GB 构建机上按顺序完成 Maven 与 Vite，避免同时运行前端构建、全量类型检查和 IDEA 编译。

```powershell
java -version
mvn.cmd -version
node --version
yarn.cmd --version
git --version
```

在准备存放源码的位置执行；已有检出则 fetch 后切换分支，不必重复 clone：

```powershell
git clone --branch codex/governance-context-2.0.8 https://github.com/123code-alt/OpenMetadata.git governance-context
Set-Location .\governance-context
$repo = (Get-Location).Path
```

IDEA：File → Open → 选择 `$repo\pom.xml`。Settings → Build Tools → Maven：Maven home 选择 3.9.9，Importer JDK 与 Runner JRE 都选 JDK 21；Project Structure → Project SDK 也选 JDK 21。Maven 工具窗口点击 **Add Maven Projects**，加入 `governance-context\pom.xml`。使用下面指定的裁剪构建命令，不运行根工程的全量 Lifecycle install。

本文 `mvn.cmd ...` 可在 IDEA Terminal 逐条执行；也可以建立 Maven Run Configuration：Working directory 选仓库根，Command line 填相同参数、去掉开头的 `mvn.cmd`。这就是在 IDEA 中打包，不依赖外部部署脚本。

## 2. 复用 PG / ES，但隔离数据

按 [POSTGRES.md](POSTGRES.md) 在**现有 PG 实例**创建专用数据库、账号及 schema，名称均为 `governance_context_v208`。这是用户已同意的隔离方案；不要连接已初始化标准版的 `openmetadata_db`，也不要向标准版的 schema 执行迁移。

配置要点：

```text
OM_DATABASE=governance_context_v208
DB_USER=governance_context_v208
DB_PARAMS=currentSchema=governance_context_v208,public
ELASTICSEARCH_CLUSTER_ALIAS=governance_context_v208
```

`public` 是新库内的扩展命名空间。用管理员在新库预置 pgcrypto 和 pg_trgm。原版迁移和 Flowable 共用该专用库；不要执行 drop-create 或手动导入原版 postgres.sql。

ES 保持你已有的可用版本与连接方式。配置 `SEARCH_TYPE=elasticsearch`、实际 host / port；如启用了认证，配置 `ELASTICSEARCH_USER`、`ELASTICSEARCH_PASSWORD`，HTTPS 使用 `ELASTICSEARCH_SCHEME=https` 和原配置中的证书选项。先检查 `conf/openmetadata.yaml` 中 search 配置再填写，不能关闭认证来适配示例。云端联合验证使用 OpenSearch 3.4.0，用户现有 ES 的具体版本尚未实测。

```powershell
Test-NetConnection 127.0.0.1 -Port 5432
Test-NetConnection 127.0.0.1 -Port 9200
```

远程服务替换为实际 IP；端口以已有安装为准。

## 3. 构建后端与 Fuseki 扩展

在仓库根：

```powershell
mvn.cmd -B -ntp -N install
mvn.cmd -B -ntp -f governance-context/pom.xml -pl :openmetadata-service,:openmetadata-mcp -am validate
mvn.cmd -B -ntp -f governance-context/pom.xml -pl :openmetadata-service,:openmetadata-mcp -am '-Dmaven.test.skip=true' install
mvn.cmd -B -ntp -f docker/rdf-store/pom.xml -DskipTests package
```

第三条只跳过测试来打包，不代表通过全量测试。可另运行已采用的定向检查：

```powershell
mvn.cmd -B -ntp -pl openmetadata-service,openmetadata-mcp spotless:check test '-Dtest=GovernanceContextProfileTest,DefaultToolContextTest,HonestResponseTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`docker/rdf-store` 在这里只是原项目的源码目录名，执行 Maven 和 Java，不启动 Docker。

## 4. 用 Java 单独启动 Fuseki

固定使用与原扩展一致的 Jena 6.2.0。建立本机独立数据目录；示例使用 `C:\om-governance`，也可改为其他盘。不要指向标准版 Fuseki 数据目录。

```powershell
New-Item -ItemType Directory -Force C:\om-governance\fuseki
New-Item -ItemType Directory -Force C:\om-governance\fuseki-data
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/jena/jena-fuseki-server/6.2.0/jena-fuseki-server-6.2.0.jar' -OutFile C:\om-governance\fuseki\fuseki-server.jar
Copy-Item .\docker\rdf-store\target\openmetadata-fuseki-extensions.jar C:\om-governance\fuseki\
```

逐步生成配置：复用原配置中的 union default graph、全文索引和超时设置，仅换数据目录及 dataset 名称。不要改用简单的 `--mem` 或 `--loc`，它们不会加载这些 assembler 设置。

```powershell
$fusekiConfig = Get-Content .\docker\rdf-store\config.ttl -Raw
$fusekiConfig = $fusekiConfig.Replace('/fuseki-data', 'C:/om-governance/fuseki-data')
$fusekiConfig = $fusekiConfig.Replace('openmetadata', 'governance_context_v208')
$fusekiConfig = $fusekiConfig.Replace('file:C:/om-governance/fuseki-data/', 'file:///C:/om-governance/fuseki-data/')
[IO.File]::WriteAllText('C:\om-governance\fuseki\governance.ttl', $fusekiConfig, [Text.UTF8Encoding]::new($false))
```

另开一个 IDEA Terminal 标签页，前台启动并保持窗口开启。Windows classpath 分隔符是分号：

```powershell
java -Xms128m -Xmx512m -cp 'C:\om-governance\fuseki\openmetadata-fuseki-extensions.jar;C:\om-governance\fuseki\fuseki-server.jar' org.apache.jena.fuseki.main.cmds.FusekiMainCmd --port=3030 --localhost --config=C:\om-governance\fuseki\governance.ttl
```

另一个窗口检查：

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:3030/governance_context_v208/query' -Method Post -Body @{ query = 'ASK { ?s ?p ?o }' } -Headers @{ Accept = 'application/sparql-results+json' }
```

空库返回 boolean=false 属正常；关键是 HTTP 请求成功。该方案仅监听本机，原启动方式没有 HTTP 用户认证，模板中的 RDF_REMOTE_USERNAME/PASSWORD 不会为 Fuseki 启用认证；本机模式可留空。若 Fuseki 在别的机器上，先为该服务配置访问控制和 TLS，再填写真实凭据，不把本示例的无认证服务直接暴露到局域网。三个 dataset 为 `governance_context_v208`、`..._a`、`..._b`，后两者供原版重建切换使用。

## 5. 配置已有 S3，或另外准备 MinIO

优先复用已有 S3 兼容服务。单独创建 bucket `governance-context-v208`，为应用建立具有该 bucket 读写权限的凭据，前缀使用 `governance-context-v208`。不要使用 InMemory / NoOp 作为文档持久化。

```text
ASSET_UPLOADER_ENABLE=true
ASSET_UPLOADER_PROVIDER=s3
ASSET_UPLOADER_S3_ENDPOINT=http://S3服务器IP:9000
ASSET_UPLOADER_S3_BUCKET_NAME=governance-context-v208
ASSET_UPLOADER_S3_REGION=us-east-1
ASSET_UPLOADER_S3_ACCESS_KEY=实际访问密钥
ASSET_UPLOADER_S3_SECRET_KEY=实际秘密密钥
ASSET_UPLOADER_S3_PREFIX_PATH=governance-context-v208
```

没有 S3 时参考 [OBJECT-STORAGE.md](OBJECT-STORAGE.md) 准备 MinIO。依赖服务可以运行于其他机器或 Docker 中；不要求与 Java 应用一起打包。S3 未就绪时，文章/记忆仍可单独检查，但不能宣称文档上传已部署成功。

## 6. 逐步构建静态前端

回到仓库根；以下三次安装都使用锁文件，并跳过会自动串联其他构建的生命周期脚本：

```powershell
Set-Location $repo
yarn.cmd install --frozen-lockfile --ignore-scripts --non-interactive
yarn.cmd --cwd openmetadata-ui-core-components/src/main/resources/ui install --frozen-lockfile --ignore-scripts --non-interactive
yarn.cmd --cwd openmetadata-ui/src/main/resources/ui install --frozen-lockfile --ignore-scripts --non-interactive
$env:NODE_OPTIONS = '--max-old-space-size=6144'
yarn.cmd --cwd openmetadata-ui-core-components/src/main/resources/ui vite build
```

JavaScript 解析器必须用 **ANTLR 4.9.2**，不是 Java 模型生成使用的 ANTLR 版本：

```powershell
New-Item -ItemType Directory -Force C:\tools
Invoke-WebRequest 'https://www.antlr.org/download/antlr-4.9.2-complete.jar' -OutFile C:\tools\antlr-4.9.2-complete.jar
$grammar = (Resolve-Path .\openmetadata-spec\src\main\antlr4\org\openmetadata\schema).Path
$antlrOutput = Join-Path $repo 'openmetadata-ui\src\main\resources\ui\src\generated\antlr'
java -jar C:\tools\antlr-4.9.2-complete.jar -Dlanguage=JavaScript -o $antlrOutput "$grammar\EntityLink.g4" "$grammar\Fqn.g4" "$grammar\JdbcUri.g4"
Set-Location .\openmetadata-ui\src\main\resources\ui
node parseSchemas.js
$env:APP_VERSION = '2.0.8'
yarn.cmd vite build
Test-Path .\dist\index.html
Set-Location $repo
```

`Test-Path` 应为 True。直接执行 Vite，避开原 `build` / `js-antlr` 中的 Bash 语法。无需常驻 Node 服务。将静态资源装入 UI JAR（直接执行插件目标，避免重复触发整个前端生命周期）：

```powershell
mvn.cmd -B -ntp -f openmetadata-ui/pom.xml resources:resources jar:jar install:install -DskipTests
mvn.cmd -B -ntp -f governance-context/runtime/pom.xml -DskipTests package
```

产物：`governance-context\runtime\target\governance-context-runtime-2.0.8-runtime.zip`。它包含启动 JAR、lib、静态 UI、配置模板和迁移 SQL，不包含 Node、Maven、业务源码或私钥（仅附密钥生成小工具）。

## 7. 解压运行包，生成本机 JWT 密钥

在仓库根，首次部署到新的目录：

```powershell
Expand-Archive .\governance-context\runtime\target\governance-context-runtime-2.0.8-runtime.zip -DestinationPath C:\om-governance\app
$runtime = 'C:\om-governance\app\governance-context-runtime-2.0.8'
java .\governance-context\GenerateJwtKeys.java "$runtime\conf\local-keys"
Set-Location $runtime
```

密钥工具仅做密钥生成；目录已存在会拒绝覆盖。重新部署时保留现有密钥和配置。源码里的上游示例密钥不会进入运行包，不要用于部署。

## 8. 显式设置环境变量

打开仓库的 `governance-context\windows.env.example`，按实际连接信息逐项填写。**Java 不会自动读取 .env**。两种方式任选一种：

- 在 IDEA 每个 Run Configuration 的 Environment variables 编辑器中逐项粘贴变量。
- 在当前 PowerShell 中逐条 `$env:变量名 = '值'`，只对该窗口和其子进程有效。终端变量不会自动传递给 IDEA Run 按钮启动的进程。

下面给出基线的显式命令。口令自行替换；有认证 / TLS 的 ES 同时设置对应变量。模型能力先关闭，普通全文检索仍保留：

```powershell
$env:OPENMETADATA_DISTRIBUTION = 'governance-context'
$env:DB_DRIVER_CLASS = 'org.postgresql.Driver'
$env:DB_SCHEME = 'postgresql'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:OM_DATABASE = 'governance_context_v208'
$env:DB_USER = 'governance_context_v208'
$env:DB_USER_PASSWORD = '替换为实际数据库密码'
$env:DB_PARAMS = 'currentSchema=governance_context_v208,public'
$env:DB_CONNECTION_POOL_MAX_SIZE = '15'
$env:DB_CONNECTION_POOL_MIN_SIZE = '2'
$env:DB_CONNECTION_POOL_MIN_IDLE = '2'
$env:DB_CONNECTION_POOL_INITIAL_SIZE = '2'
$env:OPENMETADATA_CLUSTER_NAME = 'governance_context_v208'
$env:SEARCH_TYPE = 'elasticsearch'
$env:ELASTICSEARCH_HOST = '127.0.0.1'
$env:ELASTICSEARCH_PORT = '9200'
$env:ELASTICSEARCH_CLUSTER_ALIAS = 'governance_context_v208'
$env:SERVER_HOST = '0.0.0.0'
$env:SERVER_ADMIN_HOST = '127.0.0.1'
$env:SERVER_PORT = '8585'
$env:SERVER_ADMIN_PORT = '8586'
$env:AUTHENTICATION_PROVIDER = 'basic'
$env:AUTHENTICATION_PUBLIC_KEYS = '[http://localhost:8585/api/v1/system/config/jwks]'
$env:RSA_PRIVATE_KEY_FILE_PATH = './conf/local-keys/private_key.der'
$env:RSA_PUBLIC_KEY_FILE_PATH = './conf/local-keys/public_key.der'
$env:JWT_KEY_ID = 'governance-context-v208-local'
$env:PIPELINE_SERVICE_CLIENT_ENABLED = 'false'
$env:RDF_ENABLED = 'true'
$env:RDF_STORAGE_TYPE = 'FUSEKI'
$env:RDF_ENDPOINT = 'http://127.0.0.1:3030/governance_context_v208'
$env:RDF_DATASET = 'governance_context_v208'
$env:RDF_REMOTE_USERNAME = ''
$env:RDF_REMOTE_PASSWORD = ''
$env:ASSET_UPLOADER_ENABLE = 'true'
$env:ASSET_UPLOADER_PROVIDER = 's3'
$env:ASSET_UPLOADER_S3_ENDPOINT = 'http://127.0.0.1:9000'
$env:ASSET_UPLOADER_S3_BUCKET_NAME = 'governance-context-v208'
$env:ASSET_UPLOADER_S3_REGION = 'us-east-1'
$env:ASSET_UPLOADER_S3_ACCESS_KEY = '替换为实际S3访问密钥'
$env:ASSET_UPLOADER_S3_SECRET_KEY = '替换为实际S3秘密密钥'
$env:ASSET_UPLOADER_S3_PREFIX_PATH = 'governance-context-v208'
$env:SEMANTIC_SEARCH_ENABLED = 'false'
$env:LLM_PROVIDER = 'noop'
```

## 9. 首次迁移，再启动

确认当前目录是 `$runtime`，PG / ES / Fuseki / S3 均可达。先迁移，看到进程成功退出才继续：

```powershell
java -Xmx2g '-Dbootstrap.dir=./bootstrap' -cp 'governance-context-runtime-2.0.8.jar;lib/*' org.openmetadata.service.util.OpenMetadataOperations -c conf/openmetadata.yaml migrate
$LASTEXITCODE
```

应为 0。后续普通启动不重复执行迁移：

```powershell
java -Xms512m -Xmx2g '-Dbootstrap.dir=./bootstrap' -jar governance-context-runtime-2.0.8.jar server conf/openmetadata.yaml
```

IDEA 中启动同一运行包：Run → Edit Configurations → **JAR Application**。

| 字段 | 填写 |
| --- | --- |
| Path to JAR | `$runtime\governance-context-runtime-2.0.8.jar` 的实际绝对路径 |
| Program arguments | `server conf/openmetadata.yaml` |
| VM options | `-Xms512m -Xmx2g -Dbootstrap.dir=./bootstrap` |
| Working directory | `$runtime` 的实际绝对路径 |
| JRE | JDK 21 |
| Environment variables | 第 8 节模板全部变量，填实际值 |

不要把 PowerShell 中的 `$runtime` 字符串原样填进 IDEA；填它代表的路径。运行包已经在前面通过 IDEA Terminal / Maven 配置打包，点击 Run 后是一个 Java 服务。

需要源码断点时，另外建立 **Application**：Main class `org.openmetadata.service.OpenMetadataApplication`，Use classpath of module 选 `governance-context-runtime`，工作目录用仓库根，参数同上；先执行第 6 节 UI JAR 安装，再 Reload Maven。确认运行 classpath 包含 `openmetadata-ui-2.0.8.jar` 或 `openmetadata-ui/target/classes`，否则后端正常但没有首页。环境变量与密钥路径改为实际绝对路径。IDEA 的 module 名可能带项目名前缀，以 artifactId 为准。

## 10. 验证与局域网访问

另开窗口：

```powershell
Invoke-WebRequest http://127.0.0.1:8585/ -UseBasicParsing | Select-Object StatusCode
Invoke-RestMethod http://127.0.0.1:8586/healthcheck
Get-NetTCPConnection -LocalPort 8585 -State Listen
```

浏览器打开 `http://localhost:8585`。新空环境原版初始登录为 `admin@open-metadata.org` / `admin`，首次登录立即修改密码。进入设置检查 OpenMetadata URL，将它设为同事实际使用的 `http://本机局域网IP:8585`，MCP OAuth discovery 与返回链接需要这个地址。若使用其他认证方式，按现有身份系统配置。

局域网客户端访问服务器的 IP，不使用客户端自己的 127.0.0.1。管理员 PowerShell 中为可信局域网开放业务端口：

```powershell
New-NetFirewallRule -DisplayName 'Governance Context 8585 LAN' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8585 -RemoteAddress LocalSubnet -Profile Private,Domain
ipconfig
```

该防火墙规则适用于 Private / Domain 网络配置档；可信局域网网卡的网络类型需与之匹配。只开放 8585；8586、PG、ES、Fuseki、MinIO 不需要向普通客户端开放。若 8585 只监听 127.0.0.1，核对 **IDEA 运行配置**里的 SERVER_HOST，改成 0.0.0.0 后重启。若本机能访问而同事不通，检查 Windows 网络配置文件、VLAN / 访客 Wi-Fi 隔离和路由防火墙。停用服务时在 IDEA 点 Stop 或前台 Ctrl+C；停止后再备份 PG、S3 和 Fuseki 数据目录。

验收页面：治理 → 术语库 / 本体浏览器 / 分类标签；上下文中心 → 概览 / 文章 / 文档 / 记忆 / 归档。MCP 地址为 `http://服务器IP:8585/mcp`，使用原版身份认证，不能直接匿名调用。

## 常见错误

- `gin_trgm_ops does not exist`：确认连接的是新库，扩展在新库 public，DB_PARAMS 包含业务 schema 和 public。
- `relation does not exist`：检查迁移退出码、数据库名、schema、运行时与迁移使用的变量是否相同。
- Java 正常但首页 404：确认先完成 Vite、UI JAR 安装、运行包打包，再运行新包；刷新旧包不会自动更新静态资源。
- 前端找不到 generated/antlr：检查 JS ANTLR 是否为 4.9.2、输出目录是否正确，重新执行第 6 节生成步骤。
- MCP / OAuth 返回 localhost：在设置中修改 OpenMetadata URL，不能只改 SERVER_HOST。
- 文档上传失败：检查 bucket 已创建、S3 endpoint、凭据、bucket 权限；不要用关闭权限检查来解决。
- 本体图为空：先创建词条及关系；分别检查 RDF 启用状态、Fuseki dataset、持久化目录。页面可能使用数据库回退；看到图不等于 RDF 投影已经成功。

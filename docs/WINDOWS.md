# Windows 原生源码部署（连接已有数据库）

适用于本仓库的 **`codex/semantic-context-minimal`** 分支，即术语库与 AI 上下文中心的最小验证版。以下命令在 **Windows PowerShell 5.1 或 PowerShell 7** 中执行；后端使用 JDK 21，前端构建成静态资源打进同一个 JAR，通过 **8585** 访问页面和 API。

本文直接连接你已准备好的 **PostgreSQL**，省略数据库安装、建库和 Docker 操作。当前最小版无需 Elasticsearch、OpenSearch 或模型服务。这里的配置和命令对应最小分支，不适用于完整 OpenMetadata 主分支。

## 1. 准备构建工具

建议至少 4 GB 可用内存、3 GB 空闲磁盘，并能访问 GitHub、Maven Central 和 Yarn 依赖源。

| 工具 | 版本要求 | 下载地址 |
| --- | --- | --- |
| Git | Git for Windows | [git-scm.com](https://git-scm.com/downloads/win) |
| JDK | **21**，包含 `javac` | [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21&os=windows) |
| Maven | 3.9+ | [Maven](https://maven.apache.org/download.cgi) |
| Node.js | 推荐 22.x，至少 **22.23.4** | [Node.js](https://nodejs.org/en/download) |
| Yarn | **1.22.22**，Classic | 通过下面的命令安装 |

安装 JDK 后，将 `JAVA_HOME` 设为 JDK 21 的安装目录；将 `%JAVA_HOME%\bin` 和 Maven 解压目录的 `bin` 加入用户 `Path`。Git、Node.js 安装时选择添加到 `Path`，然后重新打开 PowerShell。

先安装 Yarn，再检查版本：

```powershell
npm.cmd install --global yarn@1.22.22

git --version
java -version
javac -version
mvn.cmd -version
node --version
yarn.cmd --version
```

`java`、`javac` 和 `mvn.cmd -version` 中的 Java 都应为 21；Yarn 应为 1.22.22。后续项目依赖统一用 Yarn 安装。使用 `.cmd` 后缀可避开 PowerShell 对 `npm.ps1`、`yarn.ps1` 的执行策略限制。

## 2. 拉取最小分支

以下示例将工程放到当前用户的 `source\semantic-context` 目录：

```powershell
$projectParent = Join-Path $env:USERPROFILE 'source'
New-Item -ItemType Directory -Path $projectParent -Force | Out-Null
Set-Location $projectParent

git clone --config core.autocrlf=false --depth 1 --single-branch --branch codex/semantic-context-minimal https://github.com/123code-alt/OpenMetadata.git semantic-context
if ($LASTEXITCODE -ne 0) { throw '源码拉取失败，请先处理 Git 报错' }
Set-Location .\semantic-context

git branch --show-current
```

分支应显示 `codex/semantic-context-minimal`。`core.autocrlf=false` 保留仓库的 LF 换行，避免 Windows 自动换行转换影响格式检查。浅克隆只获取最小分支当前版本，无需下载完整 OpenMetadata 历史。

以下命令均从这个仓库根目录执行。

## 3. 构建前端和后端

```powershell
$ErrorActionPreference = 'Stop'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'

yarn.cmd install --frozen-lockfile --non-interactive
if ($LASTEXITCODE -ne 0) { throw '依赖安装失败，请先处理 Yarn 报错' }

yarn.cmd run build:frontend
if ($LASTEXITCODE -ne 0) { throw '前端构建失败，请先处理上面的报错' }

# 清除旧静态资源，防止重复构建时将已删除的文件留在 JAR 中。
if (Test-Path .\backend\app\target\classes\assets) {
    Remove-Item .\backend\app\target\classes\assets -Recurse -Force
}

mvn.cmd -B -ntp spotless:check package
if ($LASTEXITCODE -ne 0) { throw '后端构建失败，请先处理 Maven 报错' }

Get-Item .\backend\app\target\app-0.1.0-SNAPSHOT.jar
```

这会构建共享 UI 和静态前端，执行类型、lint、Java 格式检查及后端测试，再将静态资源打入 `backend\app\target\app-0.1.0-SNAPSHOT.jar`。构建测试使用临时测试库，不连接你的 PostgreSQL；首次构建会下载依赖。

运行时只需这个 JAR、配置和 JDK 21，无需单独启动前端开发服务器。

## 4. 保存本机连接配置

准备现有 PostgreSQL 的 **JDBC URL、用户名和密码**。例如 `jdbc:postgresql://127.0.0.1:5432/semantic_context`，将地址、端口、库名换成你已有的值；远程数据库可在 URL 中附带所需的连接参数。

使用本验证版专用的数据库或 schema。应用首次启动会在连接用户的默认 schema 下创建自己的 `sc_` 表，因此账号需要该 schema 的 `USAGE`、`CREATE` 及这些表的读写权限；应用不会创建数据库，也不会自动导入完整 OpenMetadata 的已有数据。

执行下面一整段代码，按提示输入连接信息。密码输入不回显；API 令牌自动随机生成。配置保存在已被 Git 忽略的 **`.runtime\windows-config.json`**，重启时直接复用。

```powershell
New-Item -ItemType Directory -Path .\.runtime -Force | Out-Null
$configPath = Join-Path (Get-Location).Path '.runtime\windows-config.json'
if (Test-Path $configPath) {
    throw '本机配置已存在。请直接复用，或编辑现有文件；不要重复生成令牌。'
}

$jdbcUrl = Read-Host '请输入现有 PostgreSQL 的完整 JDBC URL'
$dbUser = Read-Host '请输入数据库用户名'
if ([string]::IsNullOrWhiteSpace($jdbcUrl) -or [string]::IsNullOrWhiteSpace($dbUser)) {
    throw 'JDBC URL 和用户名不能为空'
}
$dbPasswordSecure = Read-Host '请输入数据库密码' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPasswordSecure)
try {
    $dbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $dbPasswordSecure.Dispose()
}

$tokenBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($tokenBytes)
$rng.Dispose()
$apiToken = -join ($tokenBytes | ForEach-Object { $_.ToString('x2') })

$localConfig = @{
    server = @{
        applicationConnectors = @(@{
            type = 'http'
            port = 8585
            bindHost = '127.0.0.1'
            maxRequestHeaderSize = '16KiB'
        })
        adminConnectors = @(@{
            type = 'http'
            port = 8586
            bindHost = '127.0.0.1'
        })
        requestLog = @{ appenders = @(@{ type = 'console'; threshold = 'WARN' }) }
    }
    jdbcUrl = $jdbcUrl
    databaseUser = $dbUser
    databasePassword = $dbPassword
    blobDirectory = Join-Path (Get-Location).Path '.runtime\blobs'
    development = $false
    identities = @(@{
        token = $apiToken
        id = '00000000-0000-0000-0000-000000000001'
        name = 'admin'
        admin = $true
        scopes = @('glossary:write', 'context:write')
    })
    logging = @{ level = 'INFO'; loggers = @{ 'org.eclipse.jetty' = 'WARN' } }
}

$localConfig | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $configPath -Encoding UTF8
Remove-Variable dbPassword, localConfig, apiToken
Write-Host "配置已保存：$configPath"
```

应用支持 JSON 配置；`ConvertTo-Json` 会处理密码引号、反斜杠和 Windows 路径的转义。配置文件包含明文数据库密码与管理员令牌，请保留在本机。`.runtime` 已在 `.gitignore` 中。

这里默认只监听本机。需要其他电脑访问时，在本机配置中将 `applicationConnectors` 的 `bindHost` 改成 `0.0.0.0`，并允许 Windows 防火墙放行 8585；管理端口 8586 保持 `127.0.0.1`。

## 5. 启动

在仓库根目录执行：

```powershell
java -Xms128m -Xmx512m -jar .\backend\app\target\app-0.1.0-SNAPSHOT.jar server .\.runtime\windows-config.json
```

保持该窗口打开。Java 会连接数据库、初始化 `sc_` 表并启动 HTTP 服务；如果命令退出，请先处理终端中的错误。浏览器打开本机 **<http://127.0.0.1:8585>**。

再开一个 PowerShell，进入同一个仓库目录，读取令牌并复制到剪贴板：

```powershell
Set-Location (Join-Path $env:USERPROFILE 'source\semantic-context')
$config = Get-Content .\.runtime\windows-config.json -Raw | ConvertFrom-Json
$config.identities[0].token | Set-Clipboard
```

将令牌粘贴到页面的“API 令牌”输入框。这里使用配置中的管理员令牌，不是完整 OpenMetadata 的邮箱和密码登录。

## 6. 检查部署结果

在第二个 PowerShell 窗口执行：

```powershell
# semantic-database 与 static-ui 的 healthy 都应为 true。
Invoke-RestMethod -Uri 'http://127.0.0.1:8586/healthcheck' | ConvertTo-Json -Depth 5

# 页面应返回 200。
(Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8585/').StatusCode

# 使用配置中的令牌检查术语库 API；新库返回空列表。
$config = Get-Content .\.runtime\windows-config.json -Raw | ConvertFrom-Json
$headers = @{ Authorization = 'Bearer ' + $config.identities[0].token }
Invoke-RestMethod -Uri 'http://127.0.0.1:8585/api/v1/glossaries' -Headers $headers
```

在页面依次验证：新建术语库和术语 → 新建文章或记忆 → 上传 UTF-8 TXT/MD/CSV/JSON → 选择来源生成上下文。

## 7. 停止、重启与更新

- **停止：**回到运行 Java 的窗口按 `Ctrl+C`，等待进程退出。
- **重启：**进入仓库根目录，再执行第 5 步同一条 `java` 命令。无需重新生成配置或令牌。
- **更新源码：**停止应用，执行 `git pull --ff-only`，重新执行第 3 步构建，再用原配置启动。
- **保留数据：**保留数据库、`.runtime\blobs` 和 `.runtime\windows-config.json`。清理 `target` 不会删除这些数据。

上传文件位于配置中 `blobDirectory` 指定的绝对路径；移动仓库或将 JAR 复制到别的目录后，如需一起移动文件，应修改该配置并迁移已有 `blobs` 目录。备份时同时保留数据库、文件对象和本机配置。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| `无法加载 yarn.ps1` / 禁止运行脚本 | 使用教程中的 `yarn.cmd`、`npm.cmd`；这些命令无需修改执行策略。 |
| `java`、`mvn.cmd`、`yarn.cmd` 找不到 | 检查对应安装目录是否加入 `Path`，重新打开 PowerShell。Yarn 的全局命令通常位于 `%APPDATA%\npm`。 |
| `release version 21 not supported` / Java 版本错误 | 执行 `java -version`、`javac -version`、`mvn.cmd -version`，确保它们都使用 JDK 21。 |
| Yarn 提示 Node 版本不满足要求 | 将 Node 升到至少 22.23.4，重新开终端；不要跳过 engine 检查。 |
| `Connection refused` / `password authentication failed` | 核对本机 JSON 中的 JDBC 地址、端口、账号、密码及数据库的远程访问配置。 |
| `permission denied for schema` / `permission denied for table` | 检查连接用户对目标 schema 和 `sc_` 表的权限。 |
| `Address already in use` | 用 `Get-NetTCPConnection -State Listen -LocalPort 8585,8586` 检查占用；确认占用进程后释放端口，或修改 JSON 中端口，并同步调整访问、健康检查地址。 |
| 浏览器拒绝连接 | 确认 Java 进程仍在运行，先在部署机器检查健康地址。本机用 `127.0.0.1`，其他电脑用部署机器 IP，并按第 4 步调整监听和防火墙。 |
| 页面能打开但 API 返回 401 | 从当前使用的 JSON 配置重新复制令牌，确认 Java 启动时指定的是同一个配置文件。 |
| `static-ui` 健康检查失败 / 页面 404 | 先执行 `yarn.cmd run build:frontend`，再执行 Maven 打包，最后重启新的 JAR。 |
| 修改 `.env` 后没有变化 | 本教程直接运行 Java，读取命令指定的 `.runtime\windows-config.json`；修改该 JSON 并重启。仓库的 `.sh` 脚本适用于 Linux / WSL。 |

已在 Linux 上用 PowerShell 7 校验全部命令块语法，并执行配置生成、密码转义、令牌与防覆盖检查；生成的 JSON 已通过应用的 `check` 配置校验。尚未在 Windows 实机执行完整部署。

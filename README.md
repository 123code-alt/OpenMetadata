# Semantic Context：最小可部署验证版

这是 `123code-alt/OpenMetadata` 的 **`codex/semantic-context-minimal`** 分支。根目录只包含验证版应用、必要的共享 UI、契约和部署脚本；采用独立 Git 历史。使用下面的浅克隆命令即可取得这份最小工程。

- 后端：JDK 21、Dropwizard/JDBI，五个 Maven 模块。
- 前端：复用 OpenMetadata 2.0.8 的组件和主题；共享 UI 已裁剪到 18 个组件及其 32 份源码依赖。
- 部署：前端编译成静态资源放进 Java JAR，页面与 API 共用 **8585**。应用直接运行 Java，Docker 仅运行 PostgreSQL。
- 功能：术语库/术语层级、同义词与关联、文章、记忆共享、文本文件版本、显式 AI 上下文组装。当前验证版只依赖 PostgreSQL，无需搜索或模型服务。

## 在 Linux / WSL 上从源码部署

构建机需要 **JDK 21、Maven 3.9+、Node 22.23.4+、Yarn 1.22**；准备数据库的机器需要 Docker Engine + Compose v2.20+。脚本还使用 Bash、curl、openssl。源码构建建议至少 4 GB 内存和 3 GB 空闲磁盘。

### 1. 只拉取最小分支

```bash
git clone --depth 1 --single-branch --branch codex/semantic-context-minimal \
  https://github.com/123code-alt/OpenMetadata.git semantic-context
cd semantic-context
```

### 2. 生成本机配置

```bash
bash scripts/init-env.sh
```

这会生成 `.env`，包括随机数据库密码和 `SCM_API_TOKEN`，不会覆盖已有文件。保留此文件，重启时继续使用。默认应用端口 8585、数据库端口 5432；如有冲突，在后续启动前修改 `.env` 中端口和 JDBC URL。

### 3. 启动数据库

```bash
docker compose --env-file .env -f compose.dependencies.yml up -d --wait
docker compose --env-file .env -f compose.dependencies.yml ps
```

首次启动会创建独立的 `semantic_context` 数据库。已有 PostgreSQL 15 时，可先创建该数据库、在 `.env` 填好 `SCM_JDBC_URL`、`SCM_DB_USER` 和 `SCM_DB_PASSWORD`，然后跳过 Docker 步骤。应用首次连接时自动初始化自己的 `sc_` 表。

### 4. 构建

```bash
bash scripts/build.sh
```

脚本安装锁定的 Yarn 依赖，构建本仓库内的共享组件与静态前端，执行类型/lint、Java 格式检查和后端集成测试，最后生成 `backend/app/target/app-0.1.0-SNAPSHOT.jar`。不需要下载或构建原 OpenMetadata 工程。

### 5. 启动应用并检查

```bash
bash scripts/start.sh
bash scripts/health.sh
```

`start.sh` 在后台运行 Java，健康检查通过后返回。前台运行可用 `bash scripts/run.sh`。

在同一台机器浏览器打开 **http://127.0.0.1:8585**；从你的电脑访问服务器时，打开 **http://服务器IP:8585**，并允许服务器安全组/防火墙的 8585 访问。客户端的 `127.0.0.1` 指向客户端本机。8586 为仅回环可用的健康检查端口。

打开 `.env`，将 `SCM_API_TOKEN` 的值填入页面的“API 令牌”。可依次验证：新建术语库和术语 → 新建文章/记忆 → 上传 UTF-8 TXT/MD/CSV/JSON → 选择来源生成上下文。当前默认配置始终要求令牌。

### 6. 日常操作

```bash
# 查看日志
 tail -f .runtime/server.log

# 停止 / 重新启动应用
bash scripts/stop.sh
bash scripts/start.sh

# 停止数据库（保留数据卷）
docker compose --env-file .env -f compose.dependencies.yml stop
```

数据包括 PostgreSQL 数据卷和 `.runtime/blobs`，配置保存在 `.env`。升级或备份前停止应用，将数据库、上传对象和配置一起备份；不要把运行数据放到源码构建输出目录。之后使用同一份配置启动即可。

## 构建机和运行机分开

在构建机完成第 4 步后：

```bash
bash scripts/package.sh
```

得到 **`dist/semantic-context.tar.gz`**。将它复制到运行服务器并解压：

```bash
tar -xzf semantic-context.tar.gz
cd semantic-context
bash scripts/init-env.sh
docker compose --env-file .env -f compose.dependencies.yml up -d --wait
bash scripts/start.sh
```

运行包内含静态资源的 JAR、配置和运行脚本。运行服务器只需要 JDK 21、PostgreSQL 和上述基础命令，不需要 Node、Yarn、Maven 或源码。已有数据库同样可以跳过 Docker。运行包细节见 [docs/RUNTIME.md](docs/RUNTIME.md)。

## 验证与代码范围

```bash
mvn spotless:check test
# 启动应用后，使用 .env 的令牌执行真实浏览器流程
yarn run test:e2e
# 可选：检查复制源码与许可证来源记录，需要 Python 3
python3 scripts/verify_manifest.py
```

浏览器测试需要已安装的 Chromium（可设置 `SCM_CHROMIUM_PATH`），或先执行 `yarn playwright install chromium`。具体结果见 [验证记录](docs/VALIDATION.md)，接口见 [docs/API.md](docs/API.md)。

```text
backend/    contracts、glossary、context、platform-adapters、app
frontend/   静态 React 宿主、迁移组件和生成类型
ui-core/    页面实际需要的 OpenMetadata 共享组件与样式
config/     默认服务器配置
scripts/    构建、环境初始化、启停、健康检查、运行包
```

这是此前验证版的部署整理，不代表完整功能迁移完成。完整原版页面、审批、自定义关系、PDF/Office、语义检索/模型、Persona、本体浏览器和 MCP 仍待迁移；现有元数据搬迁也未实现。权限目前为配置令牌映射，正式接入时使用已有平台的 SSO/RBAC、资产和对象存储接口。当前仓储适合小规模验证，每种实体扫描上限 5,000 行。

来源为 OpenMetadata **2.0.8-release**，首次验证版提交 `3c17586028ec914a40eb8c56bb5997fb6613dc3e`。许可证和逐文件来源记录见 [SOURCE_MANIFEST.md](SOURCE_MANIFEST.md)。

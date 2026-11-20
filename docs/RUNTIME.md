# 运行包部署

运行包由 `bash scripts/package.sh` 生成，包含带静态前端的 `semantic-context.jar`、配置、运行脚本和数据库 Compose 文件。适用 Linux / WSL。

需要 JDK 21、Bash、curl、openssl，以及 PostgreSQL 15。使用附带的数据库时还需要 Docker Compose v2.20+。运行机无需 Node、Yarn、Maven。

```bash
tar -xzf semantic-context.tar.gz
cd semantic-context
bash scripts/init-env.sh
docker compose --env-file .env -f compose.dependencies.yml up -d --wait
bash scripts/start.sh
bash scripts/health.sh
```

本机访问 `http://127.0.0.1:8585`；其他电脑访问 `http://服务器IP:8585`，并在服务器开放 8585。登录令牌为 `.env` 中的 `SCM_API_TOKEN`。默认配置要求令牌；8586 健康端口仅在本机监听。

已有数据库时，在 `.env` 设置 `SCM_JDBC_URL`、`SCM_DB_USER`、`SCM_DB_PASSWORD`，跳过 Docker 步骤。数据库应为本验证版独立创建；应用会初始化自己的 `sc_` 表。

```bash
bash scripts/stop.sh
bash scripts/start.sh
tail -f .runtime/server.log
```

保留 `.env`、PostgreSQL 数据卷及 `.runtime/blobs`。停应用后备份数据库和文件对象。升级替换 JAR/脚本时保留这三项，不要重新生成数据库密码。`BUILD_INFO` 记录来源提交。

这是术语库与上下文中心的功能验证子集：支持文本文件、手工记忆和显式上下文组装，尚无审批、向量/模型服务、完整原版页面或 MCP。正式服务可接入已有 HTTPS 网关与身份系统。

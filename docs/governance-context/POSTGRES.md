# 复用已有 PG 实例，建立独立数据库

本分支与标准 OpenMetadata 2.0.8 使用同一 PostgreSQL 服务实例，但使用不同的数据库。以下名字与 `governance-context/windows.env.example` 一致。无需重新安装 PG 或 ES。

## 1. 创建账号和数据库

在已有管理员连接中，替换密码，**逐条执行**。CREATE DATABASE 必须在事务外执行：

```sql
CREATE ROLE governance_context_v208 LOGIN PASSWORD 'REPLACE_WITH_DATABASE_PASSWORD';
```

```sql
CREATE DATABASE governance_context_v208 OWNER governance_context_v208;
```

已存在同名对象时，先核实是否属于本验证版，不要删除标准环境对象。这里创建的是专用账号，不复用标准环境的业务账号。

## 2. 切换到新数据库再准备 schema 和扩展

在 pgAdmin / DBeaver / IDEA Database 中**新建或切换连接到 `governance_context_v208` 数据库**。创建数据库不会自动切换现有查询窗口的连接。

确认当前连接：

```sql
SELECT current_database(), current_user;
```

仍用管理员账号，在该新库执行下面的事务。保护检查会拒绝在其他数据库中执行本段初始化：

```sql
BEGIN;
DO $$
BEGIN
  IF current_database() <> 'governance_context_v208' THEN
    RAISE EXCEPTION 'Switch to the dedicated governance_context_v208 database first';
  END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS governance_context_v208 AUTHORIZATION governance_context_v208;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
COMMIT;
```

应用与迁移使用：

```text
jdbc:postgresql://127.0.0.1:5432/governance_context_v208?currentSchema=governance_context_v208,public
```

第一个 schema 为专用业务 schema，`public` 是**新数据库内部**的扩展命名空间，供 `gin_trgm_ops` 等原迁移引用解析。它不会跨数据库访问标准环境的 public schema。不要把数据库名改回原来的 `openmetadata_db`。

## 3. 执行原版迁移

原版配置仍为 `conf/openmetadata.yaml`，在 IDEA 环境变量中设置模板里的 DB_* 值。迁移入口为 `org.openmetadata.service.util.OpenMetadataOperations`，工作目录为仓库根，参数：

```text
-c conf/openmetadata.yaml migrate
```

先完成后端与 MCP 构建、配置好隔离的 ES 索引别名，再执行。该命令还会初始化 Flowable 表并更新搜索索引；应保持专用数据库与非空 `ELASTICSEARCH_CLUSTER_ALIAS=governance_context_v208`。

运行服务的入口另为 `org.openmetadata.service.OpenMetadataApplication`，参数为：

```text
server conf/openmetadata.yaml
```

首次迁移和正常启动是两个步骤。这里使用 migrate，不运行 drop-create。原维护命令仍有 public 假设，尚未纳入本裁剪版的支持范围。

## 4. 检查结果

在专用数据库中检查：

```sql
SELECT table_schema, count(*)
FROM information_schema.tables
WHERE table_schema IN ('governance_context_v208', 'public')
GROUP BY table_schema;

SELECT extname, extnamespace::regnamespace
FROM pg_extension
WHERE extname IN ('pgcrypto', 'pg_trgm');
```

用专用应用账号及同一 JDBC 参数连接后，`SELECT current_database(), current_schema();` 应分别返回专用库和业务 schema。备份数据库、S3 对象和 Fuseki 数据时，均以本分支的隔离名称为范围。

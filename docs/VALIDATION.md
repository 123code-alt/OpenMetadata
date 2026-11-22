# 最小分支验证记录

验证日期：2026-11-20。环境：JDK 21、Node 22.23.5、Yarn 1.22.22、Maven 3.9.9、PostgreSQL 15、Chromium。

| 检查 | 结果 |
| --- | --- |
| 最小工程完整构建 | 全新安装本仓库 Yarn workspaces 后，`bash scripts/build.sh` 通过；未引用原 OpenMetadata 检出目录 |
| 共享 UI 裁剪 | 18 个组件、32 份生产源码；入口按使用符号收窄；类型检查、静态构建和真实页面通过 |
| 后端集成 | 13 项真实 HTTP/SQL 测试通过（H2 PostgreSQL 模式），覆盖层级/关联、并发版本、记忆隐私/历史、文件与来源版本、预算和标题 |
| 浏览器流程 | 3 项通过，连接新的 PostgreSQL 容器；完整术语/文章/记忆/上传/组装、过期写入和令牌认证均验证；默认配置不使用免令牌回退 |
| 部署脚本 | 配置初始化、后台启动、健康检查、停止和重新启动通过；重复初始化保留原配置，文件权限为 0600 |
| 运行包 | 约 23 MB 的 tar.gz，内含 JAR/配置/脚本；解压目录不含源码、Node 依赖、Maven 工程或实际 .env |
| 独立运行与持久化 | 停止源码应用后，从运行包启动成功；数据库实体、文件 v1/v2 和记忆来源版本保留；验证后恢复源码应用 |
| 来源检查 | 100 个复制目标文件和上游 Git 对象哈希一致 |

运行包在独立目录启动，仅使用 JDK 和系统运行命令；数据库在容器内运行。测试使用专用数据并在完成后软归档，实际凭据、运行数据、依赖和构建产物不提交。

截图来自当前最小分支的真实浏览器流程：[术语库](screenshots/glossary.png)、[记忆](screenshots/memories.png)、[上下文预览](screenshots/context-preview.png)。

验证仅覆盖 README 列明的功能子集，完整原版页面、审批、向量/模型服务和 MCP 不在本轮范围。

## 2026-11-22：Windows / IDEA 教程与 schema 隔离

验证环境为 Linux、JDK 21、Maven 3.9.9、PostgreSQL 15.20。测试在云端 PG 中创建临时数据库，模拟同库中已有的原 schema 和数据；没有连接用户的 Windows 数据库。

| 检查 | 结果 |
| --- | --- |
| Windows 配置模板 | `config/windows-idea.example.json` 通过应用 `check` 校验；默认 JDBC URL 显式包含 `currentSchema=semantic_context_poc` |
| 文档 SQL | 从 Windows 教程提取角色、CONNECT 授权、schema 创建和权限 SQL，在临时数据库中执行通过 |
| 同库 schema 隔离 | 模拟 `public` 中已有业务表及同名 `sc_entity`；以新账号启动后，四张应用表全部创建在 `semantic_context_poc`，原表和数据保持不变 |
| 启动、页面和持久化 | JAR 入口与普通 Java 主类入口分别启动成功；页面 200、数据库与静态 UI 健康、带令牌创建和读取术语库、重启后读取均通过 |
| 缺失 schema | JDBC 指向不存在的 schema 时启动失败，未回退到 `public` 写入 |
| Java 检查 | `mvn spotless:check test` 通过，13 项后端测试全部通过 |

临时数据库及专用角色已在验证后删除；实际测试凭据与输出只保存在被忽略的 `.runtime` 下。本轮没有修改 Java 业务代码、原版 OpenMetadata schema 或 ES 索引。

Windows 教程逐项列出 Maven/Yarn 命令、IDEA Maven 打包及 JAR/Application 运行配置；尚未在 Windows 实机上执行 IDEA 导入、构建和 Run/Debug。

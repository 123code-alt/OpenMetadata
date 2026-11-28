# 治理与上下文中心：原架构裁剪版

基于 OpenMetadata **2.0.8**，分支 `codex/governance-context-2.0.8`。保留原 JSON Schema、Java 实体／仓储、权限和版本体系、原静态前端，以及原 MCP 服务端。独立最小原型仍在 `codex/semantic-context-minimal`。

- 治理：术语库、本体浏览器、分类标签。
- 上下文中心：概览、文章、文档、记忆、归档；保留文档和 Drive 所需依赖。
- 设置：用户／团队、角色／策略、Persona、应用与机器人、治理审批、通知和基础配置；服务菜单收拢至 Drive，移除血缘／Profiler 入口。
- MCP：保留 10 个工具，移除血缘与数据质量的 5 个工具实现及分派；通用工具限制到保留实体。

后端 JDK 21，前端编译成静态资源，与后端同端口 8585。运行依赖 PG、ES/OpenSearch、Fuseki 和 S3；应用无需 Docker。数据库、搜索索引、RDF 数据集、对象存储均使用独立命名。

## 部署与验证

**完整 Windows／IDEA 手动步骤：[WINDOWS-IDEA.md](../docs/governance-context/WINDOWS-IDEA.md)**。包括显式构建命令、PG 隔离 SQL、Java 启动 Fuseki、S3、静态前端、首次迁移、IDEA Run 配置和局域网开放。

- [PG 专用数据库与 schema](../docs/governance-context/POSTGRES.md)
- [S3 / MinIO](../docs/governance-context/OBJECT-STORAGE.md)
- [实际验证与限制](../docs/governance-context/VALIDATION.md)
- [实施范围与依赖](../docs/governance-context/PLAN.md)
- [环境变量模板](windows.env.example)：Java 不会自动读取此文件。

## 构建范围与裁剪边界

`pom.xml` 聚合 common、spec、shaded-deps、service、mcp、ui-core、ui 和 runtime，排除 SDK、operator、客户端及全量集成测试项目。IDEA 直接导入本目录的 `pom.xml`；根 POM 仍保留上游模块定义。后端同时过滤 REST、仓储和搜索索引注册，前端清理无关路由、页面和组件。

已完成第二轮物理裁剪：删除 145 个后端 Java 实现文件（包括 58 个 REST Resource、32 个仓储）、295 个前端生产代码文件及其失去用途的测试、41 份 Schema。运行时仅注册治理和上下文依赖的仓储，搜索索引集合收窄到 20 类。保留 JSON Schema 生成链路，剩余 877 份 Schema。

当前仍保留共享实体、迁移、部分未注册仓储、搜索与公共组件，不能视为彻底独立的小型重写项目。裁剪数量、代码行统计与具体边界见 [PRUNING.md](../docs/governance-context/PRUNING.md)，运行证据见 [VALIDATION.md](../docs/governance-context/VALIDATION.md)。

默认 `OPENMETADATA_DISTRIBUTION=governance-context`。`full` 仅放宽剩余后端注册／通用工具范围用于排查，不能恢复已删除的资源、仓储、页面或 MCP 工具，不是完整产品切换开关。

模型默认关闭：`LLM_PROVIDER=noop`、`SEMANTIC_SEARCH_ENABLED=false`。文章、记忆、普通检索与文档存储可验证；语义检索、模型提取、远程 Drive 同步、第三方应用不在本轮联合验收内。

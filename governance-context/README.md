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

`pom.xml` 聚合 common、spec、shaded-deps、service、mcp、ui-core、ui 和 runtime，排除 SDK、operator、客户端及全量集成测试项目。根 POM 仍保留上游模块定义。后端通过分发配置过滤 REST 资源注册；前端移除无关主路由及不再引用的页面文件。

本版是**保留共享底座的首轮裁剪版**。共享实体、生成模型、迁移、仓储、搜索与公共组件仍保留，不是所有无关代码都已物理删除。该取舍保留原术语审批及上下文依赖，避免替换原架构。

默认 `OPENMETADATA_DISTRIBUTION=governance-context`。`full` 只恢复后端资源注册／通用工具范围用于兼容排查，不能恢复已删除的前端页面或 MCP 工具，不是完整产品切换开关。

模型默认关闭：`LLM_PROVIDER=noop`、`SEMANTIC_SEARCH_ENABLED=false`。文章、记忆、普通检索与文档存储可验证；语义检索、模型提取、远程 Drive 同步、第三方应用不在本轮联合验收内。

# 治理与上下文中心裁剪计划

## 基线与当前状态

- 基线：OpenMetadata `2.0.8-release`，提交 `dd013e20428ae77fb837332fb646d9b01301ed81`。
- 新分支：`codex/governance-context-2.0.8`。
- 当前阶段：完成首轮入口裁剪，以及第二轮仓储／索引注册收窄、无关后端实现删除、前端组件清理、Schema 集合收窄；范围见 PRUNING.md，验收证据与边界见 VALIDATION.md。
- 既有 `codex/semantic-context-minimal` 是独立实现的验证版；本分支从原版开始，不复制其替代后端。

## 已确认的产品范围

| 功能 | 要求 |
| --- | --- |
| 术语库 | 保留原前后端、实体契约、版本、权限、关联以及审批所需流程 |
| 本体浏览器 | 保留原页面，先验证保留实体的关系；不要求首版接入外部资产系统 |
| 分类标签 | 保留完整前后端及依赖 |
| 上下文中心 | 优先保留概览、文章、文档、记忆、归档；需要额外服务或改变功能范围时确认 |
| MCP | 保留原 MCP 服务端及认证；逐项审查工具，适配保留实体并移除无目标能力的工具 |
| 设置 | 保留用户、团队、角色、策略和上述功能需要的配置；首版独立认证，不要求 SSO 对接 |
| 数据 | 独立空环境，不迁移原版历史数据，不接入现有平台资产 |
| 开发部署 | JDK 21、Windows IDEA 分步构建和 Run/Debug；静态前端共用 8585 |
| 隔离 | 复用 PG/ES 实例，但必须验证数据库和搜索索引隔离 |
| RDF | 用户接受增加 Fuseki，优先提供 Windows Java 启动步骤 |
| 文档存储 | 用户确认使用 S3 兼容存储；已有则复用，否则部署 MinIO |

JSON Schema、生成模型和构建链路继续作为契约来源。第二轮已按剩余代码引用、运行时契约及 Schema 依赖闭包收窄 spec；共享模型仍然保留。不能把菜单隐藏当作后端裁剪完成。

## 已核实的代码依赖

### 共享底座

- `openmetadata-service/.../Entity.java`：全局仓储与实体能力注册、实体引用解析。
- `openmetadata-service/.../jdbi3/EntityRepository.java`：关系、版本、权限、缓存、事件、工作流、搜索和 RDF 更新。
- 保留 JSON Schema → Java/TypeScript 生成链路；不能手工改生成类型。
- 术语关联、审批、Owner/Reviewer、文档引用均需核查对应实体和权限；不能按目录直接删除。

### 本体浏览器

`openmetadata-ui/src/main/resources/ui/src/components/OntologyExplorer/hooks/useOntologyExplorer.ts` 同时包含数据库与 RDF 路径：

- 全局图可从术语库和术语接口构图。
- 单术语库启用 RDF 时优先使用 RDF；无图结果时可走数据库路径。
- 原页面还调用指标 API，裁剪指标时需要处理请求、图合并及界面入口。
- 因此 Fuseki 不是所有关系浏览的硬依赖。基础图与 RDF 图必须分别验收，不能仅因未部署 Fuseki 就判定页面不可用。
- `rdf/storage/RdfStorageFactory.java` 当前实际实现 Fuseki；QLever 分支抛出未实现异常。
- 用户已接受增加 Fuseki；其 dataset、持久化目录和认证需与标准环境隔离。

### 上下文中心

- 文章：`resources/knowledge/KnowledgePageResource.java`、`jdbi3/KnowledgePageRepository.java`。
- 记忆：`resources/context/ContextMemoryResource.java`、`jdbi3/ContextMemoryRepository.java`；列表与检索需要搜索索引及可见性过滤。
- 文档：`resources/drive/ContextFileResource.java`，依赖 `attachments/AssetServiceFactory.java`。
- 原对象存储实现：S3、Azure、InMemory、NoOp。没有原生持久化磁盘 provider。
- 文档上传需要确认：复用/增加 S3 兼容服务，或新增本地文件 provider；InMemory 不能作为持久化验收方案。
- `aicontext/AIContextBuilder.java`、`AIContextFinder.java` 负责上下文；需要区分显式组装/普通检索与可选的模型、向量检索能力。

### MCP

入口为 `openmetadata-mcp/.../McpServer.java`，原协议挂载于 `/mcp/*`；工具分派见 `tools/DefaultToolContext.java`。

| 原工具 | 本轮处理 |
| --- | --- |
| search_metadata / get_entity_details | 保留，限制查询范围到支持实体，验证权限 |
| get_persona_context / get_user_context / find_context / company_context | 保留对应原实现并核对实际工具定义及引用范围；保留必要 Persona 支撑 |
| create_entity / describe_entity_type / patch_entity | 验证保留实体、Schema、权限、审计；禁止借通用入口访问移除能力 |
| semantic_search | 核查 embedding 和 ES/OS 版本要求；未配置时不要宣称可用 |
| get_entity_lineage / create_lineage | 与本轮范围无直接对应，已从暴露工具及实现中移除；清理分派而非只隐藏 tools/list |
| get_test_definitions / create_test_case / root_cause_analysis | 随数据质量模块退出产品范围，已移除暴露、调用入口及实现 |

工具最终名称以原 JSON 定义和服务端实现为准，验收包括 initialize、tools/list、tools/call、身份传递、拒绝越权及会话行为。

## 隔离需要额外验证的点

最小验证版的 `currentSchema` 验证不能直接证明原版部署安全：

- 原版 bootstrap、迁移、Flowable、维护命令也会访问数据库，必须逐项验证 schema 一致性。
- `OpenMetadataOperations.dropAllTables()` 存在硬编码 `public` 的表枚举；验收前禁止在共享数据库执行清库操作。
- `bootstrap/sql/schema/postgres.sql` 包含显式 `public`，不能未经检查就对已有环境执行。
- ES 提供 `clusterAlias`，需要验证索引、别名、重建、搜索和删除路径均使用隔离命名；仅配置该值还不等于验收通过。
- 对象存储使用独立 bucket/prefix；Fuseki 使用独立 dataset/数据目录。

## 实施与验收顺序

1. **依赖清单**：锁定基线与范围，核查页面/API/实体/任务/存储调用；完成新增外部依赖的选择。
2. **原架构可运行基线**：空数据隔离启动，保留原登录与 Schema，建立 PG/ES/Fuseki/文件存储配置，记录真实启动命令。
3. **功能入口与行为收拢**：导航、路由、设置、搜索范围和 MCP 工具同时收拢；术语审批的依赖保持可用。
4. **实际代码裁剪**：分批清理无关页面、服务、模块和构建依赖；每批记录删除范围与仍需保留的共享代码，不以行数作为唯一目标。
5. **端到端验收**：术语与标签 CRUD/版本/审批、本体图、文章/文档/记忆/归档、权限、MCP、上传与重启持久化；检查原环境未被写入。
6. **交付**：推送用户 fork 的独立分支，提供 Windows IDEA 显式步骤、SQL、ES/Fuseki/对象存储隔离说明和验证记录；如有未完成项明确列出。

## 当前进展

用户已确认 Fuseki 的 Windows Java 启动方案，以及 S3 兼容存储（已有则复用，否则 MinIO）。

已加入独立 Maven 聚合入口，收拢侧栏、主路由和设置入口，移除 5 个无关 MCP 工具的定义、分派及实现。第二轮进一步过滤仓储与搜索索引注册，物理删除 145 个后端 Java 实现文件、295 个前端生产代码文件和 41 份 Schema。保留术语、标签、上下文、MCP、原权限、审批和版本体系；剩余共享代码与尚未覆盖的能力见 PRUNING.md 和 VALIDATION.md。

用户已确认允许在同一 PG 实例中新建专用数据库。采用 `governance_context_v208` 数据库、同名专用业务 schema 和专用账号；该新库的 public 仅用于扩展搜索路径兼容，不与标准环境数据库共享业务表。原版 migrate 与 Flowable 初始化已完成，页面及服务健康检查通过；进一步验证进行中。

验证进展见 [VALIDATION.md](VALIDATION.md)。

部署路径见 [WINDOWS-IDEA.md](WINDOWS-IDEA.md)。用户已同意复用同一 PG 实例、另建独立数据库；该库内仍使用专用业务 schema，扩展位于该新库 public。

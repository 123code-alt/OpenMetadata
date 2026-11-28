# 构建与联合验证记录

更新：2026-11-28。环境：Linux、JDK 21、Maven 3.9.9、Node 22.23.5、Yarn 1.22.22。仅操作本任务云端隔离环境，未连接用户 Windows 的 PG / ES。

下方“原架构基线”至“最终运行包复测”记录首轮结果。第二轮物理裁剪后的结果独立记录在本文末尾；代码量与保留边界见 [PRUNING.md](PRUNING.md)。

## 已验证的原架构基线

| 检查 | 结果与范围 |
| --- | --- |
| 原后端构建 | common / spec / shaded-deps / service / mcp 构建安装通过，保留 JSON Schema 生成链路 |
| 分发入口 | governance-context 聚合 reactor validate 通过 |
| 最终前端静态构建 | 串行构建 exit 0，Vite 打包及后续压缩完成，总进程约 7 分 49 秒 |
| UI 定向回归 | 6 套测试，100 通过、26 原有跳过；修改文件 organize-imports / ESLint / Prettier 通过 |
| 全量 TypeScript | `tsc --noEmit` exit 2，共 527 个诊断；本轮修改文件未出现在诊断列表，尚未排查所有诊断来源；此项未通过 |
| Java 定向测试 | 最终 60 项通过：分发资源／MCP 参数范围 7 项、DefaultToolContextTest 40 项、HonestResponseTest 13 项；两个模块 Spotless 通过；不代表全量集成测试通过 |
| 原共享 UI | Vite 静态资源和类型声明构建通过 |
| UI 定向测试 | 侧栏、设置菜单和 EntityRouter 首批 67 项通过；扩展回归结果随最终验收补充 |
| PG 原版迁移 | 独立数据库 `governance_context_v208`；业务 schema 同名，public 只提供该库内部扩展；原版 migrate 及 Flowable 初始化通过，观察到 202 张业务表落在专用 schema |
| 搜索后端 | 使用 OpenSearch 3.4.0 和独立 clusterAlias；可见 67 个专用前缀索引和 86 个其他索引；未对其他索引做全量内容校验。用户实际 ES 版本未实测 |
| 原登录与静态 UI | 原版管理员登录成功，原导航中术语库、本体、标签、文章、记忆页面打开；基线浏览器记录无 pageerror |
| 术语／标签 | glossary、glossaryTerm、classification、tag 创建和回读；术语 JSON Patch、版本递增、历史版本读取通过 |
| 文章／记忆 | 原知识页面、记忆 API 创建和回读通过 |
| 记忆权限 | 新建普通用户，读取他人私有记忆返回 403，列表不包含该记忆 |
| MCP 协议 | 真实 HTTP initialize、tools/list、get_entity_details 调用成功；已移除工具直接调用失败；未认证请求返回 401 |
| Fuseki | 原扩展 + Jena 6.2.0 Java 独立进程；新术语 RDF 可直接查询。本体 API 可读取术语图，孤立节点场景使用原版数据库 fallback，未将 fallback 冒充 RDF 图响应 |
| Fuseki 持久化 | 重启后 RDF 三元组数量保持 3467，与重启前一致 |
| S3 持久化基线 | 固定 tag 源码构建的独立 MinIO；原文档上传／鉴权下载通过，下载字节一致；MinIO 重启健康检查通过 |

配置、账号、令牌和实际文档对象存于 Git 仓库之外，未提交。PG 是现有实例内新增独立库与角色，已有原验证库保留。

## 本轮裁剪的实际边界

- 删除无关主路由、限制通用详情／导入入口，首页进入原上下文概览。
- 设置保留成员、权限、Persona、Bots、Apps、偏好、术语关系和工作流等依赖；服务菜单只保留 Drive。
- 物理删除 38 个不再被生产入口引用的页面源码、37 个对应测试，以及 5 个无关 MCP 工具实现和对应测试。共享组件、共享实体、Schema、仓储、迁移及部分原版源码继续保留。
- 后端从资源注册阶段收拢业务 REST API；通用 MCP 创建、修改、详情、Schema 查询与检索额外限制实体类型；批量详情检查真实的 `entityType:fqn` 参数和原版兼容的 CSV 形式；禁用详情内的 lineage／quality 扩展。
- 默认是 governance-context 分发；full 兼容选项仅用于后端排查，不能恢复已物理删除的页面和工具。

## 限制与后续验收

Windows / IDEA 步骤已按实际类名、配置和构建链路展开，但尚未在 Windows 实机验证；云端 Linux 结果不能替代该项。部署步骤见 [WINDOWS-IDEA.md](WINDOWS-IDEA.md)，可按 [SMOKE-TEST.md](SMOKE-TEST.md) 复测。

本轮关闭 LLM 和向量检索，未验收语义搜索、模型自动提取、远程 Drive 同步、第三方应用或完整的多人审批流程。术语审批相关底座保留，正式使用前需按业务配置审阅人并单独验收。

首次最终静态构建曾因与类型检查、Maven 并行而触发云端 8 GB 内存上限（exit 137），已改为串行构建并成功完成；不将该次失败记为通过。原前端的依赖体量仍较大，本轮没有宣称所有无关依赖均已删除。

## 最终运行包复测

`governance-context-runtime-2.0.8-runtime.zip` 已在云端生成并解压，使用包内启动 JAR 和 lib 执行，未使用源码目录的 backend.classpath。运行包目录／CRC 校验通过，不含 DER / PEM 私钥。

- 原维护入口 migrate 从包内 bootstrap SQL 成功执行，退出码 0。
- `java -jar ... server conf/openmetadata.yaml` 启动；静态 HTML 200，管理健康检查中的 database、deadlocks、OpenMetadataServerHealthCheck 均 healthy。
- 重启后原先创建的 6 类实体全部可回读；原 S3 文件下载字节仍一致。
- 重新执行术语／标签／文章／记忆读写、版本历史、MCP 协议、权限及文档上传下载检查，全部通过。
- tables、databases、testCases、lineage、metrics、dataProducts、searchIndexes 共 7 类 REST 入口返回 404。
- 不支持的 MCP 类型、移除工具、混合批量读取、CSV 兼容参数及 lineage／quality 扩展被拒绝；有效 glossaryTerm 检索成功。
- 记忆和文档均完成软删除、归档回读和恢复。
- 原版浏览器实际登录并打开术语库、本体、分类、文章、记忆，未捕获 pageerror。验证的是基本导航与上述 API 行为，不代表所有 UI 操作均覆盖。

云端构建后已清理可重新安装的 Node 依赖目录以释放空间；源码、锁文件、构建产物及验证记录保留。再次前端开发时按教程安装依赖。

## 第二轮物理裁剪验证（2026-11-28）

本轮按“仓储注册 → 后端实现 → 前端组件 → Schema”执行，具体删除范围见 [PRUNING.md](PRUNING.md)。

| 检查 | 第二轮结果 |
| --- | --- |
| Schema 引用 | 877 份 Schema，文件及 JSON Pointer 引用错误 0；新增标准库检查入口 `governance-context/check-schema-refs.py` |
| 干净模型生成／后端编译 | 删除旧生成目录后重建 spec，service / mcp 编译安装通过；三处内嵌类型在源 Schema 固定原 Java 类名 |
| Java 定向测试 | 94 项通过：分发范围 8、权限规则 26、身份代入限制 7、MCP 参数／工具范围 40、MCP 响应边界 13；Spotless apply / check 通过 |
| UI 范围与上下文组件测试 | 10 套测试，143 项通过、26 项原有跳过，覆盖侧栏、设置、路由、文件夹／上传／记忆创建组件及本体构图 |
| 修改的保留 UI 测试 | 7 套测试、85 项通过；organize-imports → ESLint → Prettier 按顺序通过 |
| 最终静态构建 | Vite 及资源压缩全部完成，exit 0，总进程约 7 分钟；重新安装 UI JAR 前删除旧静态资源副本 |
| 全量 TypeScript | exit 2，510 个诊断（首轮 527）；按文件、错误码及错误文本归一化后，无新增诊断签名。全量类型检查仍未通过，不能以静态打包代替此项 |
| 空库完整迁移 | 新建任务专用数据库和 schema，原版迁移从初始版本执行至 2.0.8，202 张业务表位于专用 schema，public 业务表为 0；Flowable 初始化通过 |
| 全新搜索前缀 | 新命名空间实际创建 20 个索引；未删除旧验证环境的 67 个索引 |
| 空库业务回归 | 术语库／术语／分类／标签／文章／记忆创建回读，术语 Patch／版本历史，文档上传／鉴权下载字节匹配，普通用户私有记忆隔离均通过 |
| 空库 MCP | initialize / tools/list、术语详情、有效术语检索通过；移除工具与无关实体范围被拒绝；未认证请求 401；普通用户通过 MCP 读取他人私有记忆被拒绝；folder Schema 查询和创建通过 |
| 空库 RDF | 独立 Fuseki 数据目录，保存治理和上下文实体后直接查询得到 5722 条三元组 |

空库验收最初暴露了历史迁移提前获取测试套件等仓储的问题。已为 `migrate` 增加单独的仓储初始化路径，仅加载源码中仍保留的历史迁移依赖；普通服务继续只注册允许的 46 类仓储，搜索范围保持 20 类。修复后在另一个全新空库重跑以上检查并通过，未通过跳过迁移来规避问题。

以上为定向回归，不代表全量上游集成测试通过。仍未实测 Windows / IDEA、用户实际 ES 版本、LLM／向量能力、外部 Drive 同步和完整多人审批。

### 第二轮空环境与最终运行包

- 新建隔离数据库 `governance_phase2_20261128b`，复用验证专用角色，在该库内创建 `governance_context_v208` schema；从空库执行原版 migrate 至 2.0.8，退出码 0。202 张业务表落在专用 schema，public 中业务表为 0。
- 空库测试发现历史迁移会在空资产表上先获取仓储；已增加仅供 `migrate` 使用的仓储初始化路径，保留原迁移，不扩大应用的 REST／仓储／索引范围。修复后重新使用全新库完成迁移。
- 新搜索前缀下实际创建 20 个索引。既有验证环境中的旧索引未删除；不会将旧的 67 个索引自动清理掉。
- 空环境完成术语库／术语／分类／标签／文章／记忆创建回读、术语版本与历史、普通检索、文档上传及字节一致下载；REST 文件夹创建和 MCP `describe_entity_type(folder)`、`create_entity(folder)` 通过。
- 7 类已退出 REST 入口返回 404；MCP 不支持的类型、已移除工具、混合批量／CSV 参数以及 lineage／quality 扩展被拒绝；未认证 MCP 返回 401。
- 新普通用户的 REST 读取私有记忆被拒绝、列表不泄漏；同一用户通过 MCP 读取他人私有记忆也被拒绝。
- 独立 Fuseki 数据目录启动及本体词汇初始化通过。原 RDF 更新使用 SQL 持久化异步队列；首次快速验收停止应用时仍有待处理记录，不能仅凭词汇三元组总数认定新术语已投影。重新启动应用后，队列继续处理，新术语的 19 条三元组可直接查询；再重启 Fuseki，数量保持 19。
- 重新生成约 384 MiB 的运行 ZIP，检查 CRC、迁移 SQL、UI JAR，确认不含 DER／PEM 私钥。解压后使用包内启动 JAR 和 lib，执行 migrate 并启动成功，未使用源码 classpath。
- 在既有隔离验证数据库复测：此前 6 类实体可回读，重启前 S3 文件字节一致；重新执行业务 API、版本、MCP、权限、上传下载和记忆／文档归档恢复，全部通过。
- 使用最终静态前端实际登录并打开术语库、本体、分类、文章、记忆、文档、归档 7 个页面，检查导航目标路径，浏览器 pageerror 为 0。这是基本导航及上述 API 验收，不代表每项页面交互均覆盖。

运行包验证结束后停止应用进程；构建产物、业务数据和外部验证记录保留在任务云端工作区。可重新安装的前端依赖目录已清理，后续开发按教程重新安装。

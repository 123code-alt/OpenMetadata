# 来源与改写说明

来源仓库：<https://github.com/open-metadata/OpenMetadata>；标签：`2.0.8-release`；提交：`dd013e20428ae77fb837332fb646d9b01301ed81`。

所选 Java、UI 和 Schema 文件采用 Apache-2.0，保留原文件的许可证头及本目录的 LICENSE。没有复制 ingestion 代码。共享 UI 组件直接引用同一提交下的 `openmetadata-ui-core-components/src/main/resources/ui`。

`SOURCE_MANIFEST.json` 逐文件记录来源路径、上游 SHA-256、目标路径、目标 SHA-256 和改动。`scripts/verify_manifest.py` 校验当前目标文件，也可从本地 Git 对象校验上游来源。

| 来源 | 迁移方式 |
| --- | --- |
| Glossary、GlossaryTerm、Page、ContextMemory 创建契约及 ContextFile/Content | 递归复制必要 Schema；不改源 Schema；由运行时校验器使用 |
| 上游生成 TypeScript 类型 | 原样复制所选实体与创建请求类型；通过交叉类型增加独立应用的 `revision`，不手改生成文件 |
| `BudgetTracker`、`PromptText`、`DescriptionSanitizer` | 保留原算法，调整包名、导入与格式 |
| `ContextMemoryVisibility` | 将 Owner/Private/Entity/Shared 判断适配为独立 `MemoryVisibility`；用户、团队、域主体集合由调用方身份提供，不查全局 Entity 注册表 |
| `FullyQualifiedName` | 迁移名称段的引号/转义规则；独立实现保留层级路径，拒绝空名称段；不引入资产专用 FQN/ANTLR 依赖 |
| `GlossaryPureUtils.buildTree` | 提取树构建算法，去掉全局路由和实体枚举依赖 |
| `GlossaryTermSynonyms` | 保留展示和编辑结构；全局 GenericProvider 改为 props；版本差异视图交由宿主历史入口；修正空列表更新与保存失败状态 |
| `GlossaryTermReferencesModal` | 保留原表单；补齐 effect 依赖；URL 校验使用宿主 i18n 实例 |
| `MemoriesView`、`CopyLinkButton`、展示卡片及小型通用组件 | 保留原视图；头像、时间、资产色标替换为局部适配；复制链接适配 HashRouter；剪贴板 hook 做 lint 等价修正 |
| 英文/中文词条、SVG | 选取迁移组件实际使用的 29 个原词条及复数形式；SVG 原样复制；宿主新增词条置于单独 namespace |

`GlossaryRepository`、`ContextMemoryRepository`、`KnowledgePageRepository`、`EntityRepository` 及全站 React 页面没有直接搬入独立运行包。新 `GlossaryService`、`ContextService`、`FileService`、REST 资源和 SQL 存储为提取边界后的适配实现。它们复用原契约和明确列出的逻辑，并以集成测试验证本轮行为，不能视为已迁移原仓库全部行为。

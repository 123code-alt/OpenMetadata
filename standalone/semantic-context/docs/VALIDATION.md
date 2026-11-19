# 首轮验证记录

验证日期：2026-11-19。环境：Temurin JDK 21、Node 22.23.5、Yarn 1.22.22、Maven 3.9.9、PostgreSQL 15。

| 检查 | 结果 |
| --- | --- |
| `bash scripts/build.sh` 完整源码构建 | 通过：共享 UI、类型/lint、静态打包、Spotless、Java 测试与应用打包 |
| 后端真实 HTTP 集成测试 | 13 项通过，使用 H2 PostgreSQL 模式；覆盖层级与重命名、双向关系、标签互斥、并发冲突、隐私与历史、文件版本、来源版本和上下文预算/标题 |
| Chromium 浏览器流程 | 3 项通过，连接实际 PostgreSQL 应用；覆盖术语/同义词/引用、文章/记忆/文件/上下文的完整流程、过期版本冲突和无效令牌 |
| PostgreSQL + JVM 重启 | 实体保留；文件 v1/v2 均可读；记忆仍指向创建时的来源内容版本 |
| 中文长 FQN | 3,035 UTF-8 字节的层级 FQN 持久化、重启读取通过；数据库使用 FQN SHA-256 索引 |
| 独立运行 | 原 OpenMetadata 后端和 OpenSearch 进程均停止；新应用首页、会话 API 和健康检查均返回 200 |
| 运行包检查 | 不含 `org/openmetadata/service/` 内核类；返回的静态 JS 与本次构建产物哈希一致 |
| 来源校验 | 68 个目标文件及其上游 Git 对象哈希校验通过 |

浏览器测试使用专用测试数据，结束后软归档。截图来自这次真实流程：[术语库](screenshots/glossary.png)、[记忆](screenshots/memories.png)、[上下文预览](screenshots/context-preview.png)。

本记录验证的是 README 列明的提取子集，未运行原 OpenMetadata 全量回归，也不代表审批、语义检索、完整原版页面或 MCP 已迁移。

# 构建与裁剪验证进展

日期：2026-11-27。环境：Linux、JDK 21、Maven 3.9.9、Node 22.23.5、Yarn 1.22.22。

| 检查 | 当前结果 |
| --- | --- |
| 原 Service / MCP 编译、安装 | 通过，八个后端 reactor 项目成功；使用 maven.test.skip，不代表全部后端测试通过 |
| 裁剪聚合入口 | `mvn -f governance-context/pom.xml -pl :openmetadata-service,:openmetadata-mcp -am validate` 通过 |
| MCP 定向测试 | `mvn -pl openmetadata-mcp test -Dtest=DefaultToolContextTest`，40 项全部通过；包含 5 个被移除工具的列表与直接分派拒绝检查 |
| Java 格式 | MCP 模块 Spotless apply / check 通过 |
| UI 格式 | 修改的侧栏常量执行 organize-imports、ESLint、Prettier，通过 |
| 侧栏现有测试 | LeftSidebarClassBase / LeftSidebarUtils 两套测试，33 项全部通过 |
| 原共享 UI | Vite 静态构建与类型声明生成通过 |
| 服务配置 | 原 OpenMetadataApplication 的 check 命令通过；管理监听地址可独立配置，默认只绑定本机 |
| 原 PG 迁移 | 未通过：专用账号不能创建 pgcrypto、pg_trgm 扩展；管理员预置后进一步复现：仅新 schema 的 search_path 无法解析 public 下的 gin_trgm_ops，需要处理 namespace 与隔离 |
| 完整前端 | 正在执行；需先生成 4.9.2 JavaScript ANTLR 文件，再运行 Vite，不能省略该步骤 |
| Windows / IDEA | 尚未实机验证；完整部署步骤待隔离基线完成后补齐 |

试验 PG 为云端此前验证版使用的实例，专用账号和 schema 为 `governance_context_v208`，迁移停在扩展权限检查；这是临时验证环境，不能替代 Windows 实机验收。没有连接或修改用户的 Windows PG / ES。测试凭据位于仓库外的本机运行目录，不提交。

当前改动缩小构建与导航范围，移除无关 MCP 工具；通用实体工具的业务实体范围、完整后端代码、路由、设置和资源初始化仍需继续收拢。PG、ES、Fuseki、S3 与端到端功能尚未完成联合验收。

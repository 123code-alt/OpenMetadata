# 手工验收清单

先完成 WINDOWS-IDEA.md 的隔离部署，再执行以下检查。使用新建的测试名称，避免操作已有业务数据。云端测试结果另见 VALIDATION.md；本清单不表示每项都已自动验收。

## 浏览器

1. 登录，在治理中新建术语库和术语；编辑定义，查看版本历史。
2. 新建分类和标签，给术语挂标签，刷新后确认仍存在。
3. 建立两个术语的关联，打开本体浏览器检查关系和筛选；无外部资产时资产关联区可为空。
4. 上下文中心新建文章和记忆；上传文本文件，下载比对内容。
5. 归档一份测试文档／记忆，在归档页查看并恢复。
6. 新建普通用户，确认不能读取其他用户的私有记忆；管理员与普通用户看到的设置按原权限变化。
7. 重启 Java、Fuseki、对象存储，再读取同一术语、文章、记忆和文件。
8. 若使用审批，另配置审阅人，提交术语审批并完成一次任务。审批底座保留，业务流程仍需要单独验收。

## REST 和 MCP

以下 PowerShell 命令显式登录并保留令牌在变量中，不把它写入仓库。使用已修改的实际密码：

```powershell
$base = 'http://127.0.0.1:8585'
$password = Read-Host '输入管理员密码'
$body = @{email='admin@open-metadata.org'; password=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($password))} | ConvertTo-Json
$login = Invoke-RestMethod "$base/api/v1/users/login" -Method Post -ContentType 'application/json' -Body $body
$headers = @{Authorization="Bearer $($login.accessToken)"; Accept='application/json, text/event-stream'}
Invoke-RestMethod "$base/api/v1/glossaries?limit=5" -Headers $headers
```

MCP 使用原协议和原身份认证：

```powershell
$initialize = @{jsonrpc='2.0';id=1;method='initialize';params=@{protocolVersion='2025-03-26';capabilities=@{};clientInfo=@{name='manual-check';version='1'}}} | ConvertTo-Json -Depth 8
Invoke-WebRequest "$base/mcp" -Method Post -Headers $headers -ContentType 'application/json' -Body $initialize -UseBasicParsing
$tools = @{jsonrpc='2.0';id=2;method='tools/list';params=@{}} | ConvertTo-Json
Invoke-WebRequest "$base/mcp" -Method Post -Headers $headers -ContentType 'application/json' -Body $tools -UseBasicParsing
```

tools/list 应包含 10 个保留工具，不含 get_entity_lineage、create_lineage、get_test_definitions、create_test_case、root_cause_analysis。移除工具直接 tools/call 也应失败；不能仅凭列表判断。

```powershell
$blocked = @{jsonrpc='2.0';id=3;method='tools/call';params=@{name='get_entity_details';arguments=@{entities=@('page:Handbook','table:removed.asset')}}} | ConvertTo-Json -Depth 8
Invoke-WebRequest "$base/mcp" -Method Post -Headers $headers -ContentType 'application/json' -Body $blocked -UseBasicParsing
```

混入移除实体类型的批量请求应整体拒绝，不受 Handbook 是否存在影响。其他拒绝检查：create_entity 的 table 类型、semantic_search 的 filters.entityType=['table']、get_entity_details 的 include=['lineage']。有效 get_entity_details 使用真实术语 FQN，应返回原版实体内容。

未认证 `/mcp` 应为 401。`/api/v1/tables`、`/api/v1/testCases`、`/api/v1/metrics`、`/api/v1/dataProducts`、`/api/v1/searchIndexes` 应为 404。原始 JSON Schema 和共享 Java 类仍保留，不代表这些 REST 资源被注册。

## 隔离

- PG 使用独立数据库和业务 schema，检查迁移日志与 Flowable 表也落在新 schema。
- 检查 ES / OS 中本分支索引和别名都带 `governance_context_v208` 前缀，保留原环境索引；不对共享实例执行删除通配符命令。
- 检查文档只写专用 S3 bucket/prefix，Fuseki 只写专用 dataset 及数据目录。
- 不执行 drop-create。应用关闭不能删除数据库、S3 对象或 Fuseki 数据目录。

语义搜索、LLM 自动提取、远程 Drive 同步和第三方应用需要另配凭据及版本兼容，不能用 noop 基线代替验收。

# 本轮接口

前缀 `/api/v1`。默认使用 `Authorization: Bearer <配置令牌>`，页面提供令牌登录。Linux / WSL 脚本从根目录 `.env` 加载配置；Windows 原生部署使用 [Windows 教程](WINDOWS.md)生成的 `.runtime/windows-config.json`。

| 路径 | 方法 | 用途 |
| --- | --- | --- |
| `/session`、`/users`、`/capabilities` | GET | 当前身份、开发身份目录、能力状态 |
| `/glossaries`、`/glossaryTerms`、`/pages`、`/contextCenter/memories` | GET / POST | 列表 / 创建 |
| 上述集合 `/{id}` | GET / PUT / DELETE | 读取 / 更新 / 软归档 |
| 上述集合 `/name/{fqn}`、`/{id}/versions` | GET | 按 FQN 读取 / 历史版本 |
| `/contextCenter/drive/files` | GET | 文档列表 |
| `/contextCenter/drive/files/upload` | POST | 原始字节上传 |
| `/contextCenter/drive/files/{id}/content` | GET / PUT | 下载当前内容 / 上传新内容版本 |
| `/contextCenter/drive/files/{id}/content/{contentId}` | GET | 下载不可变历史内容 |
| `/contextCenter/drive/files/{id}`、`/{id}/versions` | GET / DELETE、GET | 文档读取、归档和版本 |
| `/ai/context` | POST | 显式来源组装 |
| `/ai/find?q=...` | GET | 术语与文章的关键词查找，响应 `retrieval=lexical` |

列表参数：`q`、`limit=0..1000`、`after`、`include=non-deleted|deleted|all`。术语支持 `glossary=<FQN>` 和 `parent=<FQN>`。Memory 先按可见性过滤，再计算总数和分页；通用 find 不检索 Memory。游标为当前排序下的 offset，列表变更后应从第一页重取。

所有更新和归档要求 `?revision=<当前 revision>`，过期值返回 `409`。术语库/术语归档可加 `recursive=true`；文章先归档子文章。创建/更新请求沿用所选 2.0.8 Create Schema；更新是全量创建契约，不接受原实体 JSON 中的服务端字段。原平台的 PATCH、多字段过滤、原上传 multipart 协议和全部 REST 端点没有迁入。

文件上传/替换使用 `Content-Type: application/octet-stream`，以及 URL 编码的 `X-File-Name` 头。正文限制 5 MiB，仅接受 UTF-8 TXT/MD/CSV/JSON。数据库提交失败时回收新 blob，旧内容版本保持不变。

创建术语示例：

```json
{"name":"净收入","description":"扣除退款后的收入。","glossary":"业务指标","synonyms":["净营收"],"relatedTerms":[]}
```

创建记忆示例：

```json
{
  "name":"net_revenue_rule",
  "title":"收入统计规则",
  "question":"如何计算净收入？",
  "answer":"扣除退款，并保留原订单所属月份。",
  "shareConfig":{"visibility":"Private","sharedWith":[]}
}
```

显式上下文组装：

```json
{
  "maxChars":8000,
  "sources":[{"type":"glossaryTerm","id":"实体 UUID"},{"type":"contextMemory","id":"记忆 UUID"}]
}
```

响应含 `markdown`、`sources`、`omitted`、`usedChars`、`maxChars`。字符预算按 Java/JavaScript 字符串长度计算（UTF-16），包含标题和引用标记；超预算的完整来源进入 `omitted`，不静默截断定义。文件来源返回内容版本；记忆的 `sourceProvenance` 留存首次选定来源时的实体/内容版本，源文件换版不会悄悄改写旧记忆依据。

# 来源与裁剪说明

- 上游：OpenMetadata `2.0.8-release`，提交 `dd013e20428ae77fb837332fb646d9b01301ed81`。
- 首轮独立验证代码：`3c17586028ec914a40eb8c56bb5997fb6613dc3e`。
- 本分支把验证目录提到仓库根目录，以独立历史保存；后端行为保持该验证范围，共享 UI 按实际使用依赖闭包裁剪。

`SOURCE_MANIFEST.json` 保存上游路径、上游 SHA-256、当前路径、目标 SHA-256 和适配说明。保留 Apache-2.0、NOTICE 与原文件许可头；没有复制 ingestion 代码。

`ui-core` 仅保留 18 个公开组件所需的 32 份上游生产源码，包括内部组件、工具和样式。纯导出入口收窄到实际使用符号，构建配置改为仓库内 Yarn workspace。组件实现保持原样；完整共享组件库、Storybook、演示和其他 OpenMetadata 模块未包含在此分支。

其余来源沿用首次验证版：23 个 JSON Schema、所选生成 TypeScript 类型、术语同义词/引用、上下文视图、预算与文本清理逻辑。生成类型和源 Schema 不手改；宿主、领域服务和存储为独立适配实现。

```bash
python3 scripts/verify_manifest.py
# 若本机另有上游检出，可额外验证上游 Git 对象：
python3 scripts/verify_manifest.py --upstream-repo /path/to/OpenMetadata
```

浅克隆不附带原上游历史；普通校验会验证当前文件，并明确提示未取得的上游对象。无需下载完整上游才能构建部署。

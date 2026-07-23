# 会话持久化与 Redis 上下文缓存验证

## 当前架构

当前企业会话事实不由 Redis 承载：

- PostgreSQL 的 `conversation` / `conversation_message` 保存会话和消息事实，支持顺序恢复、审计和服务重启后继续对话。
- Spring AI `ChatMemory` 使用 `PostgresChatMemory`，写入消息后清除缓存，读取时优先使用近期上下文缓存，缓存未命中再从 PostgreSQL 加载。
- Redis 适配器 `RedisConversationContextCache` 使用 JSON value 和 5 分钟 TTL，键前缀为 `smartkb:conversation:context:`。
- Redis 读取、写入或删除失败只会导致缓存未命中或缓存失效延迟，不会丢失 PostgreSQL 会话事实。

## 自动化验证

默认单元测试不依赖本地 Redis：

```powershell
mvn -B test
```

当前覆盖：

- `PostgresChatMemoryTest`：消息持久化、缓存命中、缓存回源、追加/清理后的失效。
- `RedisConversationContextCacheTest`：JSON 序列化/反序列化、TTL 写入、键删除和 Redis 异常降级。

如需真实 PostgreSQL/Redis 联调，按 [TESTING.md](../TESTING.md) 准备环境；这类联调不应把缓存命中当作会话事实持久化证据。

## 历史记录说明

2026 年 6 月 17 日的旧验证记录曾验证 Redis List 形式的 Redis ChatMemory。该记录对应迁移前实现，不能作为当前 PostgreSQL 会话事实架构的运行结论；保留本文件名是为了兼容已有文档链接，当前实现和验证以源码、单元测试及项目最新文档为准。

## 当前运行边界

Phase 6 的完整 Compose/K3d 运行复验仍受本机外部条件影响：端口占用、MinIO 镜像代理 HTTP 403，以及 K3s 系统镜像无法从 Docker Hub 拉取。详见 [部署验收报告](DEPLOYMENT_VERIFICATION.md)。

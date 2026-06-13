# Ollama 本地 Embedding 配置指南

SmartKB 当前主运行模式是 `hybrid`：

- Chat：OpenAI 兼容中转站
- Embedding：本地 Ollama `nomic-embed-text`
- Vector Store：PostgreSQL + pgvector，向量维度 `768`

项目不再维护单独的 `application-ollama.yml`。这样可以避免多个 profile 对模型和向量维度的配置互相打架。

## 前置要求

1. 安装 Ollama：https://ollama.com/download
2. 启动 Ollama：

```bash
ollama serve
```

3. 下载 Embedding 模型：

```bash
ollama pull nomic-embed-text
```

4. 确认模型可用：

```bash
ollama list
```

## 当前配置

`src/main/resources/application-hybrid.yml` 中的关键配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${TRANSIT_API_KEY:${OPENAI_API_KEY:sk-your-api-key-here}}
      base-url: ${TRANSIT_BASE_URL:https://fufu.iqach.top}
      chat:
        enabled: true
        options:
          model: mimo-v2-pro
    ollama:
      base-url: http://localhost:11434
      embedding:
        enabled: true
        options:
          model: nomic-embed-text
    vectorstore:
      pgvector:
        dimensions: 768
```

## 启动方式

```bash
docker compose -f docker-compose-minimal.yml up -d

export TRANSIT_API_KEY=sk-your-key
export SPRING_PROFILES_ACTIVE=hybrid
mvn spring-boot:run
```

Windows PowerShell：

```powershell
$env:TRANSIT_API_KEY="sk-your-key"
$env:SPRING_PROFILES_ACTIVE="hybrid"
mvn spring-boot:run
```

## 常见问题

### Ollama 连接失败

确认本地服务已经启动：

```bash
ollama serve
```

### 向量维度不匹配

`nomic-embed-text` 使用 `768` 维。数据库已有旧向量数据时，先确认表结构和数据是否仍是同一维度，再重新上传文档。

### 想切换成完全本地 Chat

当前项目主线不维护纯本地 Chat profile。建议先保留 `hybrid`，等基础演示闭环稳定后，再新增一个经过验证的 `local` profile。

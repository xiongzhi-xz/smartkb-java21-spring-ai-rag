# Ollama 本地模型配置指南

## 前置要求

1. **安装 Ollama**：https://ollama.com/download
2. **启动 Ollama**：
   ```bash
   ollama serve
   ```

3. **确认可用模型**：
   ```bash
   ollama list
   ```

   你当前有：
   - qwen2.5-coder:14b
   - qwen3-14b-uncensored
   - huihui_ai/qwen3.5-abliterate

---

## 配置步骤

### 方案 1：使用 application-ollama.yml（推荐）

启动应用时指定 profile：
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

或设置环境变量：
```bash
export SPRING_PROFILES_ACTIVE=ollama
mvn spring-boot:run
```

### 方案 2：修改默认 application.yml

直接修改 `application.yml` 中的配置：
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5-coder:14b
      embedding:
        options:
          model: qwen2.5-coder:14b
```

---

## Embedding 模型选择

### 问题：Ollama 的 Embedding 维度

qwen2.5-coder:14b 可能不支持 Embedding 生成，或者维度不是 1536。

### 解决方案

#### 选项 A：使用专门的 Embedding 模型（推荐）

下载 Ollama 的 Embedding 模型：
```bash
# 下载 nomic-embed-text（768维）
ollama pull nomic-embed-text

# 或下载 mxbai-embed-large（1024维）
ollama pull mxbai-embed-large
```

修改配置：
```yaml
spring:
  ai:
    ollama:
      embedding:
        options:
          model: nomic-embed-text
    vectorstore:
      pgvector:
        dimensions: 768  # 改为对应模型的维度
```

#### 选项 B：使用 OpenAI Embedding（混合模式）

Chat 用 Ollama，Embedding 用 OpenAI：
```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: qwen2.5-coder:14b
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
          dimensions: 1536
```

---

## 推荐配置（最佳实践）

### 1. 下载专门的 Embedding 模型

```bash
ollama pull nomic-embed-text
```

### 2. 修改 application-ollama.yml

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5-coder:14b  # 对话模型
          temperature: 0.7
          num-ctx: 4096
      embedding:
        options:
          model: nomic-embed-text  # Embedding 模型
    vectorstore:
      pgvector:
        dimensions: 768  # nomic-embed-text 的维度
```

### 3. 启动应用

```bash
# 确保 Ollama 已启动
ollama serve

# 启动 SmartKB
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

---

## 性能对比

### OpenAI API
- **优点**：效果好、速度快、稳定
- **缺点**：需要付费
- **Embedding 速度**：~50-100ms/请求

### Ollama 本地模型
- **优点**：免费、数据隐私
- **缺点**：依赖本地硬件
- **Embedding 速度**：~200-500ms/请求（取决于硬件）

---

## 常见问题

### Q1：启动失败，提示 "Connection refused"

**原因**：Ollama 未启动

**解决**：
```bash
ollama serve
```

### Q2：Embedding 维度不匹配

**错误信息**：`Embedding dimension mismatch: expected 1536, got 768`

**解决**：修改 `application-ollama.yml` 中的 `dimensions: 768`

### Q3：模型响应太慢

**原因**：本地硬件性能不足

**优化**：
1. 使用更小的模型（如 qwen2.5-coder:7b）
2. 减少 `num-ctx`（上下文窗口）
3. 使用 GPU 加速（CUDA/ROCm）

---

## 下一步

1. **启动 Ollama**：`ollama serve`
2. **下载 Embedding 模型**：`ollama pull nomic-embed-text`
3. **启动 Docker**：`docker compose up -d`
4. **启动应用**：`mvn spring-boot:run -Dspring-boot.run.profiles=ollama`
5. **访问**：http://localhost:8080

---

## 切换回 OpenAI

如果想切换回 OpenAI：
```bash
# 不指定 profile，使用默认配置
mvn spring-boot:run
```

或设置环境变量：
```bash
export SPRING_PROFILES_ACTIVE=default
export OPENAI_API_KEY=sk-your-key
mvn spring-boot:run
```

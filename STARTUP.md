# SmartKB 启动说明

## 环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop
- Ollama
- 一个 OpenAI-compatible Chat API

## 方式一：Docker Compose

复制环境变量模板：

```bash
cp .env.example .env
```

填写：

```env
TRANSIT_API_KEY=your-chat-api-key
TRANSIT_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat
```

启动：

```bash
docker compose up -d
```

访问：

- SmartKB：http://localhost:8082
- Health：http://localhost:8082/actuator/health
- Grafana：http://localhost:3001

## 方式二：本地 Hybrid 模式

### 1. 启动 PostgreSQL 和 Redis

```bash
docker compose -f docker-compose-minimal.yml up -d
```

### 2. 准备 Ollama Embedding

```bash
ollama pull nomic-embed-text
ollama list
```

### 3. 启动 Spring Boot

先启动 GPU Reranker：

```bash
docker compose up -d reranker
```

首次启动会下载 `BAAI/bge-reranker-v2-m3`，后续通过 Docker volume 复用模型缓存。

工作目录使用仓库根目录。

IDEA 配置：

```text
Main class: com.smartkb.SmartKbApplication
Active profiles: hybrid
Environment variables:
TRANSIT_API_KEY=your-chat-api-key;TRANSIT_BASE_URL=https://api.deepseek.com;AI_MODEL=deepseek-chat
```

命令行也可以使用：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=hybrid
```

访问：http://localhost:8080

## 启动验证

```bash
curl http://localhost:8080/actuator/health
```

期望状态为 `UP`。

## 常见问题

### Chat API 返回 401

- 检查 API key 是否存在。
- 检查 base URL 是否符合供应商要求。
- 不要把真实密钥写入配置文件或提交到 Git。

### Embedding 调用失败

- 确认 Ollama 已启动。
- 确认已安装 `nomic-embed-text`。
- 检查 `application-hybrid.yml` 中的 Ollama 地址。

### Reranker 服务不可用

- 检查 `http://localhost:8090/health`。
- 使用 `docker logs smartkb-reranker` 查看模型加载状态。
- 检查 Docker Desktop 是否启用 NVIDIA GPU 支持。
- 服务不可用时 SmartKB 会自动降级为规则重排。

### PostgreSQL 连接失败

- 检查 Docker 容器状态。
- 检查端口是否被占用。
- 本地演示密码只适用于开发环境。

### 上传成功但检索为空

- 检查向量表是否有记录。
- 检查 Embedding 维度是否一致。
- 检查相似度阈值和文档过滤条件。

## Deployment verification record

The current Compose/K3s checks, historical runtime evidence, and environment blockers are recorded in [docs/DEPLOYMENT_VERIFICATION.md](docs/DEPLOYMENT_VERIFICATION.md).

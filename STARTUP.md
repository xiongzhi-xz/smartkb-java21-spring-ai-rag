# SmartKB 本地启动指南

本文档记录当前推荐的本地开发启动流程：Docker Desktop 启动数据库与 Redis，Ollama 提供本地 Embedding，IDEA 启动 Spring Boot 后端。

## 当前本地架构

```text
Docker Desktop
  ├─ PostgreSQL + pgvector  localhost:5432
  └─ Redis                  localhost:6379

本机 Ollama
  └─ nomic-embed-text       localhost:11434

IDEA / Java 21
  └─ SmartKB Spring Boot    localhost:8080
```

主运行模式是 `hybrid`：

- Chat 使用 OpenAI 兼容中转站
- Embedding 使用本地 Ollama `nomic-embed-text`
- 向量存储使用 PostgreSQL + pgvector

## 1. 启动 PostgreSQL / Redis

推荐使用 Docker Desktop。先确保 Docker Desktop 已启动，然后在项目根目录执行：

```powershell
docker compose -f docker-compose-minimal.yml up -d
```

确认容器状态：

```powershell
docker ps
```

应该能看到：

```text
smartkb-postgres
smartkb-redis
```

也可以验证端口：

```powershell
Test-NetConnection localhost -Port 5432
Test-NetConnection localhost -Port 6379
```

## 2. 启动 Ollama

如果 Ollama 已经作为后台服务运行，可以跳过 `ollama serve`。

```powershell
ollama serve
```

确认 `nomic-embed-text` 已下载：

```powershell
ollama list
```

如果没有，执行：

```powershell
ollama pull nomic-embed-text
```

也可以用 HTTP 检查：

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

## 3. IDEA 启动 Spring Boot

在 IDEA 右上角打开 `Edit Configurations...`，新建或编辑 Spring Boot 配置。

关键配置：

```text
Main class:
com.smartkb.SmartKbApplication

Active profiles:
hybrid

Working directory:
E:\project\work\job\smartkb-java21-spring-ai-rag
```

`Environment variables` 填：

```text
TRANSIT_API_KEY=你的真实中转站Key;TRANSIT_BASE_URL=https://fufu.iqach.top
```

注意：

- `Active profiles` 只填 `hybrid`
- 不要把 `SPRING_PROFILES_ACTIVE=hybrid;TRANSIT_API_KEY=...` 整串填到 `Active profiles`
- `TRANSIT_BASE_URL` 当前不要带 `/v1`

配置完成后点击 IDEA 绿色运行按钮。

## 4. 验证启动成功

后端健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

浏览器访问：

```text
http://localhost:8080
```

如果刚改过前端页面，使用 `Ctrl + F5` 强刷浏览器缓存。

## 5. 完整演示流程

1. 打开 `http://localhost:8080`
2. 上传测试文档：

```text
test-docs/virtual-threads-guide.md
```

3. 在左侧文档列表点击已上传文档，查看 chunk 详情
4. 在对话框提问：

```text
Java 21 Virtual Threads 适合解决什么问题？
```

5. 确认系统基于知识库生成答案

## 常见问题

### 8080 端口被占用

查看占用进程：

```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object LocalAddress,LocalPort,State,OwningProcess
```

停止对应 PID：

```powershell
Stop-Process -Id 具体PID
```

然后重新在 IDEA 中启动。

### localhost:3000 无法访问

`3000` 是 Grafana，不是 SmartKB 主页面。

如果你启动的是：

```powershell
docker compose -f docker-compose-minimal.yml up -d
```

只会有 PostgreSQL 和 Redis，不会有 Grafana。SmartKB 主页面访问：

```text
http://localhost:8080
```

需要 Grafana 时启动完整 compose：

```powershell
docker compose up -d
```

Grafana 默认账号：

```text
admin / admin123
```

### 数据库连接失败

确认 PostgreSQL 容器或本机服务已启动：

```powershell
Test-NetConnection localhost -Port 5432
```

项目默认连接信息：

```text
database: smartkb
username: smartkb
password: smartkb123
```

### Redis 连接失败

确认 Redis 已启动：

```powershell
Test-NetConnection localhost -Port 6379
```

### Ollama 连接失败

确认本地服务已启动并且模型存在：

```powershell
ollama serve
ollama list
```

必须能看到：

```text
nomic-embed-text
```

### 文档列表为空

先上传文档，或检查数据库 `vector_store` 表是否有数据。上传成功后页面会自动刷新文档列表。

### favicon.ico 相关日志

项目已使用内联 favicon，并且静态资源 404 不再按系统异常打印。更新代码后需要重启 Spring Boot 并强刷浏览器。

## 停止服务

停止 Spring Boot：

```text
IDEA 右上角红色停止按钮
```

停止 Docker 中的 PostgreSQL / Redis：

```powershell
docker compose -f docker-compose-minimal.yml down
```

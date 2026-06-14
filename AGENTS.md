# AGENTS.md - SmartKB AI 协作规则

适用范围：整个仓库。

## 项目定位

SmartKB 是一个 Java 21 + Spring AI 企业智能知识库预研项目，目标是形成可本地运行、可演示、可解释的 RAG 工程闭环：

```text
Docker PostgreSQL/Redis -> Ollama Embedding -> IDEA 启动 Spring Boot
-> 上传文档 -> 普通对话流式回答 -> Advanced RAG 指定文档回答 -> 展开引用片段
```

核心技术栈：

- Java 21，重点体现 Virtual Threads 在 IO 密集场景中的价值。
- Spring Boot 3.3+。
- Spring AI，优先沿用 Advisor、ChatModel、VectorStore 等现有抽象。
- PostgreSQL 16 + pgvector 作为向量库。
- Redis 用于会话记忆、缓存、限流等后续增强。
- 当前前端是 Spring Boot 静态页面：`src/main/resources/static/index.html`，不是独立 Vue/Vite 项目。

## 新对话接续

每次新对话或上下文不清时，先读：

```powershell
Get-Content -Raw SPEC.md
git log --oneline -5
git status --short
```

然后按 `SPEC.md` 的当前状态和待办继续。`CLAUDE.md` 保留为 Claude 专用规则，不要改名为 `PROJECT.md`。

## 本地运行约定

推荐本地启动流程：

1. Docker Desktop 启动 PostgreSQL / Redis：

   ```powershell
   docker compose -f docker-compose-minimal.yml up -d
   ```

2. 启动或确认 Ollama：

   ```powershell
   ollama list
   ```

   必须有 `nomic-embed-text`。

3. IDEA 启动 Spring Boot：

   ```text
   Main class: com.smartkb.SmartKbApplication
   Active profiles: hybrid
   Working directory: 本仓库根目录
   ```

   环境变量示例：

   ```text
   TRANSIT_API_KEY=真实Key;TRANSIT_BASE_URL=https://fufu.iqach.top
   ```

4. 访问：

   ```text
   http://localhost:8080
   ```

`http://localhost:3000` 不是 SmartKB 主页面。

## 开发工作流

- 用户说“继续 / 开始 / 补 / 改 / 确认 / 新增一个”时，默认直接执行。
- 改动前先快速阅读相关代码，保持小步修改。
- 一次只做一个聚焦任务，避免顺手大改。
- 改完必须验证，最低要求：

  ```powershell
  mvn test
  git diff --check
  ```

- 验证通过后单独提交，提交格式：

  ```text
  feat: 简短描述
  fix: 简短描述
  docs: 简短描述
  refactor: 简短描述
  ```

## 安全边界

- 不要删除用户数据或配置文件。
- 不要改数据库 schema / migration / 初始化脚本，除非用户明确要求。
- 不要擅自切换技术栈。
- 不要把密钥写入仓库。
- 不要把 `SPRING_PROFILES_ACTIVE=hybrid;TRANSIT_API_KEY=...` 整串写进 IDEA 的 Active profiles；Active profiles 只填 `hybrid`。
- 如果旧文档已经以乱码写进 `vector_store`，代码修复不会自动修复旧数据，需要删除后重新上传。

## 当前优先级

优先稳定演示闭环，再加新功能。

当前下一阶段建议：

- Hybrid Search：向量检索 + 关键词检索融合。
- 引用片段点击后跳转到文档详情中的对应 chunk。
- Advanced RAG 分阶段反馈或流式输出。
- Redis 会话记忆持久化。
- 可观测性指标和性能压测报告。

## 常用验证问题

上传 `test-docs/advanced-rag-demo.md` 后，Advanced 模式选择该文档，验证：

```text
查询改写在 Advanced RAG 中解决什么问题？
为什么引用片段能提升 RAG 系统可信度？
```

预期：

- 第一问引用片段应优先命中第 7 节“Advanced RAG：查询改写”。
- 第二问引用片段应优先命中第 11 节“引用片段与可解释性”。

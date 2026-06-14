# SmartKB SPEC

## 当前目标

把 SmartKB 打磨成一个可本地运行、可演示、可解释的 Java 21 + Spring AI RAG 项目。当前优先级是先稳定完成演示闭环：

```text
Docker 启动 PostgreSQL/Redis -> Ollama 提供 Embedding -> IDEA 启动 Spring Boot
-> 上传测试文档 -> 普通对话流式回答 -> Advanced RAG 指定文档回答 -> 展开引用片段
```

## 已完成

- [x] PostgreSQL + pgvector 向量存储
- [x] Redis 基础设施
- [x] Ollama `nomic-embed-text` 本地 Embedding
- [x] Spring Boot `hybrid` 本地运行模式
- [x] 文档上传、解析、切片、向量入库
- [x] 文档列表、详情、删除、统计接口
- [x] 普通 RAG 问答
- [x] 多轮对话
- [x] 对话流式输出
- [x] 对话发送后等待反馈
- [x] Advanced RAG 查询改写
- [x] Advanced RAG 按文档过滤
- [x] Advanced RAG 引用片段展示
- [x] 已上传文档详情窗口可拖动调整宽度
- [x] Markdown/TXT 按 UTF-8 读取，避免中文乱码
- [x] Advanced RAG 双路召回、过滤下推、关键词重排序
- [x] Advanced RAG 锚点词重排序，优先命中查询改写、引用片段等核心章节
- [x] Advanced RAG Hybrid Search 基础版：向量召回 + 关键词召回融合
- [x] 前端文档删除入口与后端删除 SQL 修复
- [x] 改进与问题复盘文档 `docs/IMPROVEMENTS_AND_ISSUES.md`
- [x] 大文档演示语料 `test-docs/advanced-rag-demo.md`

## 当前已知状态

- 本地服务启动方式：Docker Desktop 启动 PostgreSQL/Redis，Ollama 本机运行，Spring Boot 在 IDEA 中启动。
- SmartKB 主页面：`http://localhost:8080`
- 不是 Vue/Vite 独立前端，`http://localhost:3000` 不是主页面。
- 当前自动化测试通过：`mvn test`，共 9 个测试。
- 如果数据库里有修复前上传的 `advanced-rag-demo.md`，内容可能仍是乱码，需要删除后重新上传。

## 当前演示验证

- [x] `advanced-rag-demo.md` 当前已切分为 10 个 chunk。
- [x] Advanced 模式选择 `advanced-rag-demo.md` 后提问：`查询改写在 Advanced RAG 中解决什么问题？`
- [x] 回答命中第 7 节附近，首个引用片段包含“查询改写是 Advanced RAG 的第一步”。
- [x] 再提问：`为什么引用片段能提升 RAG 系统可信度？`
- [x] 首个引用片段来自第 11 节“引用片段与可解释性”附近。

## 明天优先验证

- [ ] 在 IDEA 重启 Spring Boot，确认新代码生效。
- [ ] 删除旧的 `advanced-rag-demo.md`，重新上传 `test-docs/advanced-rag-demo.md`。
- [ ] 打开文档详情，确认中文不乱码且约 10 个 chunk。
- [ ] Advanced 模式选择 `advanced-rag-demo.md` 后提问：`查询改写在 Advanced RAG 中解决什么问题？`
- [ ] 确认回答命中第 7 节附近，引用片段包含“查询改写是 Advanced RAG 的第一步”。
- [ ] 再提问：`为什么引用片段能提升 RAG 系统可信度？`
- [ ] 确认引用片段来自第 11 节附近。

## 下一步开发

- [ ] 重启 Spring Boot 后在页面回归验证 Hybrid Search 效果。
- [ ] 引用片段点击后跳转到文档详情中的对应 chunk。
- [ ] Advanced RAG 分阶段反馈或流式输出。
- [ ] Redis 会话记忆持久化。
- [ ] 可观测性指标和性能压测报告。

## 新对话接续方式

新对话先读：

```powershell
Get-Content -Raw SPEC.md
git log --oneline -5
```

然后优先从“明天优先验证”继续。

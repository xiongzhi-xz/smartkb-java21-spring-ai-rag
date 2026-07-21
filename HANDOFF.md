# SmartKB Handoff

## 2026-07-21 JDK 21 验证

- 已使用 `maven:3.9-eclipse-temurin-21` 容器执行全新 `mvn -B clean verify`。
- 验证环境为 Temurin JDK 21.0.11、Maven 3.9.16。
- 验证结果：43 个测试全部通过，项目成功编译并生成可执行 Jar。
- `docker compose config --quiet` 与 `git diff --check` 通过。
- 本轮未执行真实 PostgreSQL、Redis、Ollama、Reranker 和 ChatModel 联调，也未执行压力测试。

## 2026-07-21 敏感信息审计

- 当前分支和远程引用仅包含 2 个可达提交，没有标签或额外 Git 引用指向旧历史。
- 可达历史未命中常见 API 密钥、访问令牌或私钥格式；`.env` 从未被 Git 跟踪，并已由 `.gitignore` 忽略。
- 旧版 `k8s/README.md` 中的 `sk-...` 命中均为占位示例，不是真实密钥。
- 本机 `.git` 对象库仍保留 184 个不可达旧提交；正常推送不会包含这些对象，但本地彻底清理需要过期 reflog 并执行 Git GC。
- 未执行不可逆的 reflog/Git 对象清理，也未确认历史密钥是否已在供应商后台作废。

## 2026-07-11 补充进度

- 已使用最新代码重启本地 SmartKB，并完成正常回答、低置信度拒答和答案 Judge 的真实模型验收。
- 已新增 `docs/ACCEPTANCE_TEST_CASES.md`，记录可复现的 6 组验收用例和本次结果。
- 已重新生成 10 幅桌面演示截图、新版 GIF 和 MP4，新增低置信度拒答与答案质量 Judge 画面。

- 已增加 Advanced RAG 低置信度拒答，返回 `confidence`、`refused`、`refusalReason`，拒答时跳过答案生成。
- 已增加 `POST /api/rag/eval/answer`，通过 LLM-as-Judge 输出 Faithfulness、Answer Relevance、Context Relevance 和综合分。
- 前端 Advanced RAG 消息已展示证据置信度与拒答原因。
- 已补充 Judge 解析、分数截断、输入校验和 Controller 契约测试。
- 已更新 README、TESTING、SPEC 与环境变量示例。
- 已验证：`mvn verify` 通过（43 tests），Python 编译、Docker Compose 配置和 `git diff --check` 通过。
- 未执行：未重启当前本地 SmartKB 进程进行真实模型接口手测；未 commit、未 push。

## 当前目标

将 SmartKB 整理为公开 GitHub 项目，聚焦可解释、可评测的 Java RAG 工程闭环。

## 当前阶段

GitHub 发布前清理与验证。

## 已完成

- 移除 Agent 工作台代码、页面、测试和设计文档。
- README、SPEC、DEMO、TESTING 收敛为 RAG 主线。
- 保留文档入库、Hybrid Search、流式问答、引用定位、Redis ChatMemory、评测和监控。
- 已在 JDK 21 环境完成全新构建，43 个自动化测试全部通过。

## 正在做

- 检查敏感信息和历史记录。
- 清理本机路径和无效构建配置。
- 添加 GitHub Actions。

## 下一步

- 确认历史 API 密钥已经在供应商后台作废。
- 用户明确确认后，清理本机 reflog 和不可达 Git 对象。

## 风险

- 本机不可达 Git 对象中可能仍保留旧敏感内容，不应复制或发布整个 `.git` 目录。
- reflog 过期和 Git GC 会永久删除旧对象，执行前需要用户明确确认。

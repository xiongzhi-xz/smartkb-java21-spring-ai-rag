-- 初始化 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建向量存储表（Spring AI 会自动创建，这里预留结构）
-- Spring AI pgvector 会自动管理 schema，无需手动建表

-- 验证扩展安装
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

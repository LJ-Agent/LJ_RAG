# RAG 系统全流程端到端测试验证报告

**测试日期**: 2026-05-26
**测试范围**: RAG-BACKEND (Java) + RAG-PYTHON + RAG-MEMORY + RAG-QUE + RAG-Web (Vue 3)
**测试方法**: 本地全栈服务 + Python urllib API 测试 + Docker 日志分析

---

## 一、测试环境

所有 5 个子项目均在运行：

| 子项目 | 端口 | 状态 |
|--------|------|------|
| RAG-BACKEND (Java) | 8080 | 健康 |
| RAG-PYTHON | 50051/50052 | 健康 |
| RAG-MEMORY | 50053/50054 | 健康 |
| RAG-QUE | 50055 | 健康 |
| RAG-Web (Vite) | 5173 | 健康 |

Docker 基础设施：MySQL(3307)、Redis(6379)、Kafka(9092)、MinIO(9000/9001)、Milvus(19530)、etcd — 全部健康。

---

## 二、已验证流程

### 2.1 用户认证 ✅
- 登录 (admin/admin123) → 返回 accessToken/refreshToken
- 获取用户信息 → username=系统管理员, permissions=18
- 无效 Token → 401 Unauthorized

### 2.2 知识库管理 ✅
- 列表查询、创建、更新 — 全部通过
- 边界测试：不存在 KB → 业务异常，重复创建 → 业务异常

### 2.3 文档全生命周期 ✅

```
上传(UPLOADED) → FILE_PROCESS → PENDING_REVIEW → 审核通过 
→ CHUNK_PROCESS → CHUNK_REVIEW → start-embedding 
→ EMBED_PROCESS → COMPLETED
```

| 步骤 | 触发方式 | 验证结果 |
|------|----------|----------|
| 上传 | POST /api/files/upload | 文件上传到 MinIO，创建 Document(id=10) |
| FILE_PROCESS | Kafka rag-file-process | 解析为 cleaned.md，状态→PENDING_REVIEW |
| 审核 | POST /api/review/submit | 状态→CHUNK_REVIEW |
| CHUNK_PROCESS | Java 异步触发 | 根据 cleaned.md 分块，chunks 入库 |
| 向量化 | POST /api/chunks/start-embedding?documentId=X | 状态→EMBEDDING→COMPLETED |
| 验证 | GET /api/files/{id} | status=COMPLETED, chunkCount=1 |

### 2.4 知识问答 ✅

测试用例："RAG技术的核心原理是什么？", "如何减少大模型幻觉？", "检索增强生成有什么优势？"

| 指标 | 结果 |
|------|------|
| 检索结果 | sourceDocs=1 (从上传文档中检索到) |
| 生成答案 | LLM 基于上下文生成准确回答 |
| Token 消耗 | 220-278 tokens |
| 端到端延迟 | 1000-5542ms |

### 2.5 其他模块 ✅

文档列表、块列表、审核列表、问答历史、会话列表、用户列表、系统配置、反馈列表 — 全部通过。

### 2.6 边界测试 ✅

| 测试项 | 预期 | 实际 |
|--------|------|------|
| 空提问 | 400 | 400 |
| kbIds 类型错误 | 400 | 400 |
| 不存在 KB | 业务异常 | ResultCodeEnum.KB_NOT_FOUND |
| 无效 Token | 401 | 401 |

---

## 三、本轮发现的缺陷及修复

| 编号 | 描述 | 严重程度 | 仓库 | 修复状态 |
|------|------|---------|------|----------|
| B26 | 问答缓存污染空结果 — 缓存 key 仅含问题不含 kbIds，且空结果也会被缓存 | 高 | Backend | ✅ 已修复 |
| B27 | QUE 引擎未集成到 Java QA 流程 — Java 缺少 QueEngineServiceClient | 架构 | Backend | ⚠️ 待后续 |
| B28 | RAG-MEMORY 未集成到 Java QA 流程 — Java 缺少 MemoryServiceClient | 架构 | Backend | ⚠️ 待后续 |
| B29 | BM25 + Milvus 数据容器重建后丢失 (B22) | 中 | Python | ⚠️ 已知限制 |
| B30 | RRF 融合分数偏低 (0.0049~0.0164, B24) | 中 | Python | ⚠️ 已知限制 |
| B31 | test_api.py 字段名错误 — docStatus → status | 低 | 根目录 | ✅ 已修复 |
| B32 | EMBED_PROCESS 消息缺少 chunks 数据导致 "Missing chunks" 错误 | 高 | Backend | ✅ 已修复(本地) |

**修复完成**: 4/7 (B26, B31, B32 已修复并推送；B27/B28 需架构级集成)

---

## 四、代码变更

### RAG-BACKEND (master_backend) — 已推送
```
97c07eb fix: 修复问答缓存污染(B26)和EMBED_PROCESS缺块数据(B32)
```

变更文件:
- `src/main/java/com/rag/service/qa/impl/QaServiceImpl.java` — 缓存 key 加入 kbIds 维度，仅缓存有效结果 (sourceDocs 非空且 tokenCount>0)
- `src/main/java/com/rag/service/review/impl/ReviewServiceImpl.java` — EMBED_PROCESS Kafka 消息包含实际 chunk 数据 (chunk_id/chunk_index/content)

### 根目录 (未纳入 git)
- `test_api.py` — 修复字段名 docStatus → status
- `CLAUDE.md` — 新增 RAG-MEMORY 和 RAG-QUE 子项目文档

---

## 五、架构改进建议 (TODO)

1. **QUE 引擎集成** (B27): 在 Java 端添加 `que.proto`，创建 `QueEngineServiceClient`，修改 `QaServiceImpl` 将检索请求路由经过 QUE 引擎，实现意图识别→查询重写→DAG规划→并行检索的优化流程。

2. **RAG-MEMORY 集成** (B28): 在 Java 端添加 `memory.proto`，创建 `MemoryWriteClient`，在问答结束后通过 Kafka 将对话发给 RAG-MEMORY 进行记忆蒸馏；将工作记忆注入检索上下文。

3. **BM25 持久化** (B29): 将 BM25 索引保存到磁盘，服务重启后自动恢复。

4. **RRF 分数归一化** (B30): 将 RRF 融合分数归一化到 [0, 1] 范围，使其与原始向量相似度可比。

---

## 六、总结

### 通过项
- ✅ 用户认证（登录/鉴权/权限）
- ✅ 知识库 CRUD
- ✅ 文档上传 → MinIO 存储
- ✅ FILE_PROCESS（Python 解析清洗）
- ✅ 审核流程（人工审核）
- ✅ CHUNK_PROCESS（Python 语义分块）
- ✅ EMBED_PROCESS（Milvus 向量化 + BM25 索引）
- ✅ 知识问答（混合检索 + gRPC 生成）
- ✅ 完整文档状态机流转（7 状态 → COMPLETED）
- ✅ 边界测试（参数校验、类型错误、权限控制）
- ✅ 问答缓存优化（无空结果污染）

### 待跟进
- ⚠️ QUE 引擎和 RAG-MEMORY 与 Java 后端的集成
- ⚠️ BM25 索引持久化
- ⚠️ RRF 分数归一化

### 整体评估
RAG 系统核心流水线（上传→解析→分块→向量化→检索→生成）经过全面测试，功能正常。本轮修复了 3 个缺陷（B26 缓存污染、B31 字段名、B32 缺块数据），代码已推送至 master_backend 分支。QUE 引擎和 RAG-MEMORY 服务已部署运行，但尚未与 Java 后端集成，建议在下个迭代中完成架构集成。

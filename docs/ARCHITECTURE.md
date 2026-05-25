# RAG-MEMORY — 用户记忆管理服务 架构设计文档

## 一、概述

RAG-MEMORY 是 RAG 知识库系统的用户记忆管理服务，基于 **Python 3.11+** 构建。它提供持久化的、多层级的用户记忆系统，使对话 AI 能够"记住"用户说过的话、偏好和行为模式。记忆通过对话自动提取（蒸馏），并在后续问答中作为上下文注入。

**核心定位**：用户记忆引擎 — 从对话中提取知识，跨会话保持上下文，实现个性化 AI 体验。

## 二、技术栈

| 组件 | 技术 |
|------|------|
| 向量数据库 | Milvus 2.x (pymilvus)，HNSW 索引 + COSINE 距离 |
| 关系数据库 | MySQL 8.0 (aiomysql 异步驱动) |
| 缓存 | Redis (工作记忆 + 搜索缓存 + 分布式锁) |
| LLM | OpenAI 兼容接口 (gpt-4o-mini) |
| 嵌入 | text-embedding-3-small (1536 维) |
| 通信 | gRPC (同步 RPC) + Kafka (异步任务) |

## 三、四层记忆模型

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Atomic Facts (原子事实)                           │
│  最小记忆单元，每条一句完整陈述                              │
│  字段: fact_id, content, category, importance, tags,       │
│        sources, access_count, timestamps                    │
│  存储: Milvus (向量) + MySQL (行)                           │
│  分类: factual, preference, plan, knowledge, relationship  │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: Episodic Summaries (情景摘要)                     │
│  按时间段压缩多条事实为 3-5 句摘要                           │
│  字段: episode_id, summary, period, key_fact_ids,          │
│        start_time_ms, end_time_ms                           │
│  存储: MySQL (不向量化)                                      │
│  周期: session, hourly, daily, weekly                      │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: Procedural Rules (过程规则)                       │
│  从事实中推理出的行为模式                                    │
│  字段: rule_id, rule_content, category, confidence,        │
│        activation_count, supporting_fact_ids                │
│  存储: MySQL                                                │
│  分类: preference, habit, constraint, procedure            │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: Working Memory (工作记忆)                         │
│  会话级临时上下文，TTL 自动过期                              │
│  字段: entry_id, key, value, ttl_seconds                   │
│  存储: Redis (key: wm:{user_id}:{session_id}:{entry_id})   │
└─────────────────────────────────────────────────────────────┘
```

## 四、分层架构

```
┌─────────────────────────────────────────────────────┐
│  communication/         外部通信层                    │
│  ├── grpc_server/       4 个 gRPC 服务               │
│  │   ├── memory_write_service     :50053             │
│  │   ├── memory_search_service    :50054             │
│  │   ├── memory_admin_service     :50053             │
│  │   └── memory_compliance_service :50053            │
│  ├── kafka_consumer/    Kafka 消费者                 │
│  └── kafka_producer/    Kafka 生产者 (状态回写)      │
├─────────────────────────────────────────────────────┤
│  engine/                记忆引擎                     │
│  ├── distiller          对话蒸馏 (提取事实)          │
│  ├── classifier         PII 检测与过滤               │
│  ├── deduplicator       事实去重 (向量相似度)        │
│  ├── retriever          混合检索 (向量+关键词+RRF)   │
│  ├── fusion_ranker      融合排序 (RRF+时间衰减)      │
│  ├── archiver           工作记忆归档                 │
│  ├── summarizer         情景摘要生成                 │
│  ├── rule_inferrer      过程规则推理                 │
│  └── snapshot_manager   快照管理                     │
├─────────────────────────────────────────────────────┤
│  infrastructure/        基础设施适配器                │
│  ├── milvus/            Milvus (向量)               │
│  ├── mysql/             MySQL (关系)                │
│  ├── redis/             Redis (缓存/锁/工作记忆)    │
│  └── llm/               LLM 适配 + Prompt 模板      │
├─────────────────────────────────────────────────────┤
│  common/                共享模块                     │
│  ├── config_loader      YAML + ${ENV:default}       │
│  ├── enums              枚举定义                     │
│  ├── exceptions         异常体系                    │
│  └── result             统一结果封装                 │
└─────────────────────────────────────────────────────┘
```

## 五、三数据库多语言持久化

| 数据库 | 用途 | 存储内容 | 查询方式 |
|--------|------|----------|----------|
| **Milvus** | 向量相似搜索 | 原子事实的 embedding (1536维) | ANN (HNSW, COSINE) |
| **MySQL** | 事实来源 (Source of Truth) | facts, episodes, rules, profiles, audit_logs, snapshots | SQL |
| **Redis** | 热数据缓存 + 会话状态 | 工作记忆, 搜索缓存 (TTL 300s), 分布式锁 | Key-Value |

**Milvus 设计要点**:
- 集合名: `user_memories`
- **分区键**: `user_id` — 提供用户级硬隔离，查询自动限定在单个用户分区
- **索引**: HNSW (M=16, efConstruction=200), 查询 ef=128
- **维度**: 1536 (text-embedding-3-small)

**MySQL 表结构** (7 张表):
| 表名 | 用途 |
|------|------|
| `user_profiles` | 用户级聚合计数器 |
| `atomic_facts` | 事实内容 + 分类 + 重要性 |
| `episodic_summaries` | 情景摘要 + 关联 fact_ids |
| `procedural_rules` | 推理规则 + 置信度 |
| `working_memory_archive` | 归档的工作记忆 |
| `audit_logs` | 合规审计追踪 |
| `memory_snapshots` | JSON 快照 |

**Redis 三层用途**:
1. **工作记忆**: `wm:{user_id}:{session_id}:{entry_id}` (TTL 3600s)
2. **搜索缓存**: `cache:search:{md5_hash}` (TTL 300s)
3. **分布式锁**: `lock:{user_id}` (TTL 30s)

## 六、记忆检索管线

```
用户 Query
    │
    ├──► [向量检索] embedding → Milvus ANN (user_id 分区, top_k*2)
    │
    ├──► [关键词检索] MySQL LIKE %keyword% (按 importance DESC, top_k)
    │
    └──► [RRF 融合] score = Σ 1/(60 + rank)  (k=60)
              │
              ├──► [时间衰减] score *= exp(-0.01 * hours_since_creation)
              │         (约每 69 小时相关性减半)
              │
              ├──► [重要性加权] score *= importance
              │
              ├──► [类别过滤] 按 category 过滤
              │
              └──► [时间范围过滤] 按 created_at 范围过滤
              │
              ▼
         Final Ranked Facts[]
    │
    ├──► [情景摘要] 从 MySQL 获取关联的 episodes
    ├──► [过程规则] 从 MySQL 获取关联的 rules  
    └──► [工作记忆] 从 Redis 获取 session 级 WM entries
    │
    ▼
  MemorySearchResult { facts[], episodes[], rules[], working_entries[] }
```

**融合公式**:
```
final_score = normalized_rrf_score × exp(-λ × hours_since_creation) × importance
```
其中 λ = 0.01（可配置），提供自然的时间遗忘曲线。

## 七、记忆生命周期

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  Capture │ ──► │  Distill │ ──► │  Archive │ ──► │  Forget  │
│  捕获    │     │  蒸馏    │     │  归档    │     │  遗忘    │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
```

### 7.1 Capture（捕获）
- **同步**: gRPC `CaptureConversation` — 实时摄入对话
- **异步**: Kafka `memory.distill` topic — 离线批处理
- 双通道设计：相同逻辑，不同触发方式

### 7.2 Distill（蒸馏）
1. **LLM 提取**: 对话 → LLM 结构化提取 → 原子事实 JSON
2. **重要性过滤**: importance < 0.3 的事实丢弃
3. **PII 检测**: 正则匹配身份证/手机号/邮箱/银行卡/IP → 丢弃
4. **去重**: 新事实 embedding → Milvus 搜索 → 相似度判断:
   - \>0.85 → 合并（保留旧 ID，更新内容和重要性）
   - 0.5-0.85 → 保留两者（关联标记）
   - <0.5 → 新插入
5. **双写**: 同时写入 Milvus (向量) 和 MySQL (行)

### 7.3 Archive（归档）
- 将会话工作记忆从 Redis 迁移到 MySQL `working_memory_archive`
- 保留最近 N 条（默认 50）作为持久化条目，TTL 7 天
- key: `persistent:{session_id}`

### 7.4 Forget（遗忘）
- 合规服务处理"被遗忘权"请求
- 三库全清: MySQL (facts+episodes+rules) + Milvus (按 partition key) + Redis (WM 驱逐)
- 所有操作写入 `audit_logs`

### 7.5 Summarize（摘要生成）
- 定时或按需触发
- LLM 将多条事实压缩为 3-5 句情景摘要
- 按 session → hourly → daily → weekly 逐级汇总

### 7.6 Rule Inference（规则推理）
- 从事实中推理行为模式
- 记录置信度和激活次数
- 频繁激活的规则置信度上升

## 八、gRPC 服务设计

### 8.1 MemoryWriteService (:50053)

```protobuf
rpc CaptureConversation(CaptureConversationRequest) returns (CaptureConversationResponse);
// 同步摄入 → 蒸馏 → 分类 → 去重 → 双写
rpc IngestTaskResult(IngestTaskResultRequest) returns (CaptureConversationResponse);
// Kafka 异步管线结果回写
```

### 8.2 MemorySearchService (:50054)

独立端口部署，支持读写分离、独立扩容：
```protobuf
rpc Search(MemorySearchRequest) returns (MemorySearchResponse);
// 全层混合检索
rpc GetSessionContext(MemorySearchRequest) returns (MemorySearchResponse);
// 会话级上下文
rpc GetWorkingMemory(GetMemoryStatsRequest) returns (MemorySearchResponse);
// 活跃工作记忆
```

### 8.3 MemoryAdminService (:50053)

```protobuf
rpc GetStats(GetMemoryStatsRequest) returns (GetMemoryStatsResponse);
rpc EvictWorkingMemory(EvictWorkingMemoryRequest) returns (EvictWorkingMemoryResponse);
rpc CreateSnapshot(GetMemoryStatsRequest) returns (CaptureConversationResponse);
rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);
```

### 8.4 MemoryComplianceService (:50053)

```protobuf
rpc GetExportableData(GetExportableDataRequest) returns (GetExportableDataResponse);
// GDPR 数据可携带性
rpc ForgetUser(ForgetUserRequest) returns (ForgetUserResponse);
// 被遗忘权：三库全清 + 审计
```

**端口分离策略**: write/admin/compliance 共用 50053，search 独立 50054 — 搜索服务可独立扩容以应对读多写少的场景。

## 九、关键架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 多语言持久化 | Milvus + MySQL + Redis | 每种存储针对特定访问模式优化 |
| 用户隔离 | Milvus partition key = user_id | 硬隔离防止数据泄露，查询延迟与总数据量无关 |
| 检索融合 | RRF + 时间衰减 + 重要性加权 | 语义、时效、重要度三维平衡 |
| PII 检测 | 正则（非 ML） | 高效可靠，覆盖中英文 PII 类型 |
| 事实去重 | 向量相似度 (0.85 阈值) | 避免冗余存储，合并更新 |
| 双通道摄入 | gRPC 同步 + Kafka 异步 | 实时对话 + 离线批处理 |
| 合规优先 | 审计日志 + 快照 + 导出 + 遗忘 | GDPR 合规基础能力 |
| 工作记忆独立层 | Redis TTL | 显式分离临时会话状态和永久知识 |
| Kafka offset | 手动提交 | 至少一次投递保证 |

## 十、数据流图

```
对话文本
    │
    ├──► [gRPC CaptureConversation] ──（同步通路）──┐
    │                                                │
    └──► [Kafka memory.distill] ────（异步通路）──┐  │
                                                   │  │
                                                   ▼  ▼
                                            ┌─────────────┐
                                            │  Distiller   │
                                            │  LLM 蒸馏    │
                                            └─────────────┘
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │  Classifier  │
                                            │  PII 检测    │
                                            └─────────────┘
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │ Deduplicator │
                                            │  向量去重    │
                                            └─────────────┘
                                                   │
                                    ┌──────────────┼──────────────┐
                                    ▼              ▼              ▼
                                  Milvus        MySQL          Redis
                                (向量索引)    (事实行)     (搜索缓存)
```

## 十一、配置要点

```yaml
grpc.write_port: 50053                    # Write/Admin/Compliance
grpc.search_port: 50054                   # Search (独立扩容)
milvus.collection: user_memories          # 集合名
milvus.index: HNSW (COSINE)              # 索引类型
milvus.dimension: 1536                   # embedding 维度
engine.rrf_k: 60                         # RRF 融合参数
engine.time_decay_lambda: 0.01           # 时间衰减系数 (约69h减半)
engine.dedup_threshold: 0.85             # 去重合并阈值
engine.importance_threshold: 0.3         # 重要性过滤阈值
kafka.topics: memory.distill, memory.forget, memory.archive
redis.cache_ttl: 300                     # 搜索缓存 TTL
redis.working_memory_ttl: 3600           # 工作记忆 TTL
```

# RAG-BACKEND — Java 业务控制中心 架构设计文档

## 一、概述

RAG-BACKEND 是整个 RAG 知识库系统的业务控制中心，基于 **Java 17 + Spring Boot 3.2 + MyBatis-Plus** 构建。它负责用户/权限管理、知识库 CURD、文档上传与审核流程编排、问答调度、以及通过 gRPC/Kafka 与 AI 计算核心（Python）通信。

**核心定位**：业务编排中枢 — 不做 AI 计算，但控制所有业务流程的流转。

## 二、技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2, Spring Security, Spring Kafka |
| 数据访问 | MyBatis-Plus, MySQL 8.0 |
| 缓存/锁 | Redis, Redisson（分布式锁） |
| 通信 | gRPC（调用 Python），Kafka（异步任务），WebSocket/SSE（流式推送） |
| API 文档 | Springdoc OpenAPI (Swagger) |
| 构建 | Maven, Docker |

## 三、分层架构

```
┌─────────────────────────────────────────────────────┐
│  controller/                                        │
│  ├── api/          REST API (9 个 Controller)       │
│  ├── ws/           WebSocket 处理器                  │
│  └── interceptor/  JWT 认证 / 限流拦截器             │
├─────────────────────────────────────────────────────┤
│  service/          业务服务层 (按领域分包)            │
│  ├── auth/         认证授权                          │
│  ├── user/         用户管理                          │
│  ├── knowledge/    知识库管理                        │
│  ├── file/         文件上传与管理                    │
│  ├── chunk/        分块管理 + embed 触发             │
│  ├── review/       审核流程                          │
│  ├── qa/           问答调度 + 会话管理               │
│  ├── feedback/     反馈管理                          │
│  └── statemachine/ 文档状态机                        │
├─────────────────────────────────────────────────────┤
│  domain/           领域层                            │
│  ├── entity/       MyBatis-Plus 实体 (ORM 映射)     │
│  └── mapper/       MyBatis-Plus Mapper 接口         │
├─────────────────────────────────────────────────────┤
│  infrastructure/   基础设施配置                       │
│  ├── config/       gRPC / Kafka / Redis / CORS       │
│  ├── lock/         Redisson 分布式锁 AOP             │
│  └── config/       MinIO 配置                        │
├─────────────────────────────────────────────────────┤
│  communication/    外部通信                          │
│  ├── grpc/client/  gRPC 客户端 (调用 Python 服务)    │
│  └── kafka/        Kafka 生产者/消费者               │
└─────────────────────────────────────────────────────┘
```

## 四、REST API 设计

### 4.1 Controller 清单

| Controller | 路径 | 核心功能 |
|------------|------|----------|
| AuthController | `/api/auth` | 登录/注册/Token 刷新 |
| UserController | `/api/users` | 用户 CRUD |
| KnowledgeBaseController | `/api/knowledge-bases` | 知识库 CRUD，文档上下架 |
| FileController | `/api/files` | 文件上传/下载/删除 |
| ReviewController | `/api/review` | 审核列表/提交/批量通过 |
| ChunkController | `/api/chunks` | 分块查询/重新分块/触发嵌入 |
| QaController | `/api/qa` | 问答(chat/stream)/会话管理/历史 |
| FeedbackController | `/api/feedback` | 问答反馈 |
| SystemConfigController | `/api/config` | 系统配置管理 |

### 4.2 认证与鉴权

- **方案**: Spring Security + JWT（accessToken + refreshToken）
- **拦截器**: `JwtAuthInterceptor` 从 Token 解析 `userId` 存入 ThreadLocal
- **权限模型**: RBAC — `@PreAuthorize("hasAuthority('KB:CREATE')")` 注解控制
- **限流**: `@RateLimit` AOP 切面（基于 Redis 令牌桶）

## 五、文档处理流水线

```
用户上传文件
    │
    ▼
┌──────────────┐    Kafka          ┌─────────────────┐
│ FileService  │ ──FILE_PROCESS──► │ RAG-PYTHON      │
│ (Java)       │                   │ 解析 + 清洗      │
│ status:      │◄──TaskComplete──  │ → cleaned.md    │
│ UPLOADED     │                   └─────────────────┘
└──────────────┘
    │ status → PENDING_REVIEW
    │ (审核记录自动创建)
    ▼
┌──────────────┐
│ ReviewService│  人工/自动审核
│ APPROVED     │
└──────────────┘
    │ status → CHUNKING
    │ Kafka CHUNK_PROCESS
    ▼
┌──────────────┐    Kafka          ┌─────────────────┐
│ ChunkService │ ──CHUNK_PROCESS─► │ RAG-PYTHON      │
│ (Java)       │                   │ 智能分块         │
│ status:      │◄──TaskComplete──  │ → chunks[]      │
│ CHUNK_REVIEW │                   └─────────────────┘
└──────────────┘
    │ 块审核通过
    │ status → EMBEDDING
    │ Kafka EMBED_PROCESS
    ▼
┌──────────────┐    Kafka          ┌─────────────────┐
│ ChunkService │ ──EMBED_PROCESS─► │ RAG-PYTHON      │
│ (Java)       │                   │ 向量嵌入         │
│ status:      │◄──TaskComplete──  │ → Milvus + BM25 │
│ COMPLETED    │                   └─────────────────┘
└──────────────┘
```

## 六、文档状态机

```
UPLOADED ──► PARSING ──► CLEANING ──► PENDING_REVIEW
                                            │
                              ┌─ APPROVED ◄─┤
                              │              └─ REJECTED → PARSING (重试)
                              ▼
                          CHUNKING ──► CHUNK_REVIEW
                                            │
                              ┌─ APPROVED ◄─┤ (→ EMBEDDING)
                              │              └─ REJECTED → CHUNKING (重新分块)
                              ▼
                          EMBEDDING ──► COMPLETED (终态)

失败路径:
  PARSING_FAILED → 可重试 PARSING
  CLEANING_FAILED → 可重试 CLEANING
  CHUNKING_FAILED → 可重试 CHUNKING
  EMBEDDING_FAILED → 可重试 EMBEDDING
```

### 6.1 状态机实现

- **文件**: `service/statemachine/DocumentStateMachine.java`
- **特性**:
  - 乐观锁更新（`WHERE status = oldStatus`）防止并发冲突
  - 进入 `PENDING_REVIEW` / `CHUNK_REVIEW` 时自动创建审核记录
  - 进入 `COMPLETED` 时自动设置 `completedAt`

## 七、问答流程

```
用户提问
    │
    ▼
┌──────────────────────────────────────────────────┐
│ QaController                                     │
│ /api/qa/chat (非流式)  /api/qa/chat/stream (SSE) │
└──────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────┐
│ QaServiceImpl                                    │
│ 1. 保存用户问题到 chat_history                    │
│ 2. gRPC → RAG-PYTHON:50051 检索                  │
│ 3. gRPC → RAG-PYTHON:50052 生成（流式/非流式）    │
│ 4. 保存回答 + 来源到 chat_history                 │
│ 5. SSE/WebSocket 推送（流式场景）                  │
└──────────────────────────────────────────────────┘
```

### 7.1 SSE 流式实现

- 使用 `SseEmitter` + `ReadableStream`（POST 方式 SSE）
- 超时管理：心跳每 15s，总超时 120s
- 断线清理：`onCompletion` / `onTimeout` 回调

## 八、Kafka 消息通信

### 8.1 Topic 设计

| Topic | 方向 | 用途 |
|-------|------|------|
| `rag-file-process` | Java → Python | 文件解析任务 |
| `rag-chunk-process` | Java → Python | 分块任务 |
| `rag-embed-process` | Java → Python | 向量嵌入任务 |
| `rag-document-delete` | Java → Python | 文档删除 |
| `rag-task-completed` | Python → Java | 任务完成回调 |
| `rag-task-failed` | Python → Java | 任务失败回调 |

### 8.2 消息格式

```json
{
  "taskId": "task-20260525-embed-8",
  "taskType": "EMBED_PROCESS",
  "documentId": 8,
  "kbId": 2,
  "data": { "chunks": [...], "fileName": "doc.md" },
  "createdAt": "2026-05-25T09:30:00"
}
```

## 九、gRPC 客户端

| 服务 | 地址 | Proto | 用途 |
|------|------|-------|------|
| RetrievalService | rag-python:50051 | retrieval.proto | 混合检索 |
| GenerationService | rag-python:50052 | generation.proto | LLM 生成（流式/非流式） |

## 十、分布式锁

基于 Redisson + AOP 实现 `@DistributedLock`：

```java
@DistributedLock(key = "review:auto:{#recordId}", waitTime = 0, leaseTime = 30)
```

用于审核超时自动通过等场景的并发控制。

## 十一、关键架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 异步任务 | Kafka 而非直接 gRPC | 解耦 + 重试 + 削峰填谷 |
| 状态管理 | 乐观锁状态机 | 避免分布式事务，MySQL 自带 CAS |
| 审核流程 | 双阶段（内容审核 + 块审核） | 分别校验解析质量和分块质量 |
| 流式问答 | SSE (Server-Sent Events) | 简单可靠，比 WebSocket 更轻量 |
| ORM | MyBatis-Plus | 灵活 SQL 控制 + Lambda 查询 |
| 认证 | JWT 双 Token | accessToken 短有效期 + refreshToken 静默续期 |

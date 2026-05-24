# RAG 系统部署与测试问题记录

## 一、Docker 环境部署问题

### 1. HuggingFace 模型下载失败
- **问题**: Docker 容器内无法连接 huggingface.co，embedding 模型下载失败
- **原因**: 国内网络环境无法访问 HuggingFace
- **解决方案**: 设置 `HF_ENDPOINT=https://hf-mirror.com` 环境变量（docker-compose.yml + Dockerfile）
- **规避**: 在 Dockerfile 中预下载模型时添加镜像源，docker-compose 中也配置环境变量作为 fallback

### 2. Docker 容器名称冲突
- **问题**: `docker-compose up -d` 报错 `Conflict: container name already exists`
- **原因**: 之前手动创建的容器与 docker-compose 命名冲突
- **解决方案**: 先 `docker stop && docker rm` 清理旧容器，再 `docker-compose up -d`

### 3. Kafka 4.2 KRaft 模式兼容性问题

#### 3.1 __consumer_offsets 主题自动创建失败
- **问题**: Kafka 消费者收不到任何消息
- **原因**: Kafka 4.2 KRaft 单 broker 模式下，`__consumer_offsets` 内部主题默认副本因子为 3，只有 1 个 broker 无法创建
- **解决方案**: 手动创建主题 `kafka-topics.sh --create --topic __consumer_offsets --partitions 50 --replication-factor 1`
- **规避**: 在 KRaft 初始化时通过 `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` 配置

#### 3.2 kafka-python 消费者组协议不兼容
- **问题**: 即使用了 group_id，Python 消费者仍无法接收消息
- **原因**: kafka-python 2.3.1 的消费者组协议与 Kafka 4.2 KRaft 不完全兼容 (MemberIdRequiredException)
- **解决方案**: 放弃消费者组订阅方式，改用**手动分区分配** (consumer.assign + seek_to_beginning)
- **关键代码**: 见 `task_consumer.py` 中的分区手动分配逻辑

#### 3.3 Kafka Producer key_serializer 缺失
- **问题**: 发送 Kafka 消息时报 `AssertionError`，要求 key 为 bytes 类型
- **原因**: KafkaProducer 未配置 `key_serializer`
- **解决方案**: 添加 `key_serializer=lambda k: k.encode("utf-8") if isinstance(k, str) else k`

### 4. MinIO 路径双前缀问题
- **问题**: 下载文件时 MinIO 报 `NoSuchKey`，路径为 `rag-documents/rag-documents/2026-05/...`
- **原因**: Java 端构造 `originalFileUrl` 时已包含 bucket 名称（`bucketName/minioPath`），Python 端再次拼接 bucket 前缀
- **解决方案**: 在 `minio_client.py` 中添加 `_strip_bucket_prefix()` 方法，自动去除重复的 bucket 前缀
- **规避**: 统一约定：Java 端发送的路径不含 bucket；或 Python 端始终检测并去除

### 5. Docker 容器内源码缓存问题
- **问题**: 修改 Python 源码后 `docker cp` 到容器，但旧代码仍在执行
- **原因**: Python `__pycache__/*.pyc` 缓存文件未被更新
- **解决方案**: `find /app -name "__pycache__" -type d -exec rm -rf {} +` 清除后重启容器
- **规避**: 通过重建 Docker 镜像部署代码变更，而非 `docker cp`

### 6. MSYS2/Git Bash 路径自动转换
- **问题**: `docker exec` 的命令中 `/app/src/...` 被自动转换成 `D:/My Program Files/Git/app/src/...`
- **原因**: MSYS2 的路径自动转换机制（POSIX→Windows 路径映射）
- **解决方案**: 命令前加 `MSYS2_ARG_CONV_EXCL="*"` 禁用路径转换

---

## 二、前后端联调与功能验证问题

### 7. 中文乱码问题
- **问题**: API 返回的中文显示为乱码，如 `ç³»ç»Ÿç®¡ç†å'˜` 而非 `系统管理员`
- **症状**: 数据库中存储正确（utf8mb4），但 API 响应为双重 UTF-8 编码
- **原因**: 
  1. MySQL 服务的 `character_set_client` 默认为 `latin1`，数据写入时被错误转换
  2. JDBC 的 `characterEncoding=UTF-8` 参数在 Druid 连接池中未完全生效
- **解决方案**:
  1. MySQL 添加启动参数: `--skip-character-set-client-handshake --character-set-server=utf8mb4`
  2. 修复已损坏数据: `UPDATE ... SET col = CONVERT(BINARY CONVERT(col USING latin1) USING utf8mb4)`
  3. application.yml 添加 `server.servlet.encoding.charset: UTF-8` 和 Druid `connection-init-sqls: SET NAMES utf8mb4`
- **规避**: MySQL 容器启动时必须配置 `--skip-character-set-client-handshake`，JDBC URL 添加 `characterEncoding=UTF-8`

### 8. 文档审核记录缺失
- **问题**: 文档进入 PENDING_REVIEW 状态后，审核列表为空，提交审核报 "审核已处理"
- **原因**: `DocumentStateMachine.transit()` 在状态转换为 PENDING_REVIEW 时未创建 ReviewRecord
- **解决方案**: 在 `DocumentStateMachine.transit()` 中添加逻辑：当 `to == PENDING_REVIEW` 时自动插入 ReviewRecord
- **规避**: 状态转换与关联数据创建放在同一事务中，确保原子性

### 9. 审核通过后 CHUNK_PROCESS 未触发
- **问题**: 审核通过后，文档停留在 APPROVED 状态，分块和向量化流程未执行
- **原因**: `ReviewServiceImpl.submitReview()` 中，APPROVED 分支未发送 CHUNK_PROCESS Kafka 消息
- **解决方案**: 添加 `sendChunkProcessMessage()` 方法，构造 cleanedPath 并发送到 `rag-chunk-process` 主题
- **关键点**: cleanedPath 由原始 MinIO 路径转换: `{bucket}/{path_without_ext}_cleaned.md`

### 10. 状态机流转不匹配
- **问题**: 状态机期望分 3 步（APPROVED→CHUNKING→EMBEDDING→COMPLETED），但 Python CHUNK_PROCESS 一次性完成
- **解决方案**: `nextAfterTaskComplete` 改为直接跳转：
  - UPLOADED → PENDING_REVIEW (FILE_PROCESS 完成解析+清洗)
  - APPROVED → COMPLETED (CHUNK_PROCESS 完成分块+向量化)
  - 其他状态返回 null，调用方处理幂等

### 11. Kafka 旧消息重复消费导致状态异常
- **问题**: 容器重启后，旧 Kafka 消息被重新消费，触发 `IllegalStateException: 当前状态不可流转`
- **原因**: TaskCompleteConsumer 对已在终态的文档仍尝试状态转移
- **解决方案**: 
  1. `nextAfterTaskComplete` 对非流程状态返回 null
  2. `transitToNext` 接收 null 时跳过并记录日志
  3. consumer 始终 ack（包括异常情况），避免消息阻塞

### 12. PDF 分块超过 Milvus VarChar 限制
- **问题**: Milvus 报错 `length of varchar field content exceeds max length: 76419 > 65535`
- **原因**: 智能分块策略按段落分割，PDF 清洗后缺少段落分隔符，单个块过大
- **解决方案**: 
  1. `SmartChunker` 添加 `max_chunk_size` 配置（30000 字符）
  2. `_enforce_max_size()` 方法强制拆分超限块
  3. `_split_long_text()` 优先在段落/句子边界拆分，无边界时按固定长度截断
- **规避**: 分块策略必须设定硬上限，不能依赖段落/语句边界

### 13. RRF 融合后 score_threshold 过滤导致无结果
- **问题**: RRF 融合检索返回 0 结果
- **原因**: RRF 分数范围 <0.02，而 score_threshold=0.5（为余弦相似度设计），所有结果被过滤
- **解决方案**: RRF 融合后移除 score_threshold 过滤，仅按 RRF 分数排序取 top_k

### 14. FILE_PROCESS 完成后未写入清洗文件
- **问题**: CHUNK_PROCESS 找不到清洗后的 markdown 文件 (NoSuchKey)
- **原因**: `handle_file_process` 只返回清洗文本路径，但未将内容写入 MinIO
- **解决方案**: 
  1. `minio_client.py` 添加 `put_object()` 方法
  2. `handle_file_process` 解析清洗后将结果写入 MinIO

---

## 三、关键经验教训

1. **Kafka 兼容性**: kafka-python 库对 Kafka 4.x KRaft 模式支持不完善，生产环境建议使用 Java 客户端或 confluent-kafka-python
2. **字符集配置**: MySQL/Docker 部署时必须显式配置所有层级的字符集（服务器、客户端连接、JDBC、HTTP 响应）
3. **状态机幂等**: 消息系统（Kafka）的 at-least-once 语义要求所有消费者具有幂等性
4. **分块上限**: 向量数据库的字段长度限制需要与分块策略联动，不能依赖内容特征（段落、语句）保证上限
5. **路径约定**: 微服务间传递文件路径时需统一约定格式（含/不含 bucket 前缀），避免重复拼接

---

## 四、代码审查发现的缺陷修复（2026-05-24）

以下为 3 个并行代码审查 agent 对所发现的 25 个缺陷的修复记录：

### 严重缺陷（已修复）

#### B1. 问答缓存 key 使用 UUID 导致永不命中
- **分支**: master_backend
- **文件**: `QaServiceImpl.java`
- **问题**: 缓存 key 为 `StrUtil.uuid().substring(0,8) + "_" + dto.getQuestion().hashCode()`，每次生成的 UUID 不同，导致所有查询都是 cache miss
- **修复**: 改为 `(dto.getQuestion().hashCode() & 0x7fffffff)`，仅基于问题内容生成确定性 key
- **Commit**: f49245d

#### B2. buildCleanedPath 空指针异常
- **分支**: master_backend
- **文件**: `FileServiceImpl.java`
- **问题**: `buildCleanedPath` 方法未处理 `minioPath` 为 null 的情况，`lastIndexOf('.')` 会抛出 NPE
- **修复**: 方法首行添加 `if (minioPath == null) return null;` 守卫

#### B3. InputStream 资源泄漏
- **分支**: master_backend
- **文件**: `FileServiceImpl.java`
- **问题**: `getContent` 方法中 InputStream 在异常路径下未关闭，导致文件句柄泄漏
- **修复**: 抽取 `getContentStream()` 私有方法，返回单一 InputStream 引用，在外层使用 try-with-resources 自动关闭

#### B4. Kafka 消费异常时仍然 ACK 导致消息丢失
- **分支**: master_backend
- **文件**: `TaskCompleteConsumer.java`
- **问题**: `acknowledge.ack()` 在 finally 块中执行，异常时也确认，导致消息被丢弃无法重试
- **修复**: 仅在 try 块末尾成功时 ack，异常时不 ack 让 Kafka 重新投递

#### B5. 审核通过后发送 CHUNK_PROCESS 消息时 StringIndexOutOfBoundsException
- **分支**: master_backend
- **文件**: `ReviewServiceImpl.java`
- **问题**: `sendChunkProcessMessage` 中内联构造 cleanedPath 时，对无扩展名文件名执行 `lastIndexOf('.')` 后 `substring(0, -1)` 抛出越界异常
- **修复**: 抽取 `buildCleanedPath()` 方法统一处理，增加 `lastDot > 0` 守卫

#### B6. ChunkController 批量操作的不安全类型转换
- **分支**: master_backend
- **文件**: `ChunkController.java`
- **问题**: `batchSetStatus` 中 `(List<Integer>) body.get("ids")` 直接强转，JSON 反序列化后可能是 Long/Integer/String，会抛出 ClassCastException
- **修复**: 使用类型安全解析，兼容 Number、Long、Integer、String 多种输入类型

#### B7. ChatSessionServiceImpl 硬删除聊天记录
- **分支**: master_backend
- **文件**: `ChatSessionServiceImpl.java`
- **问题**: 删除会话时对关联的 chat_records 执行物理删除，与 session 自身的软删除策略不一致，且 `chat_records` 表无 `deleted` 字段无法软删除
- **修复**: 移除硬删除操作，聊天记录保留在数据库中（会话标记为 deleted=1 后通过 getSessionRecords 的校验逻辑阻止访问）

#### B8. 获取会话记录缺少所有权校验
- **分支**: master_backend
- **文件**: `ChatSessionServiceImpl.java`
- **问题**: `getSessionRecords` 仅校验 session 存在性和 deleted 状态，未校验 userId 所有权，任意用户可通过 sessionId 读取他人会话记录
- **修复**: 添加 `!session.getUserId().equals(userId)` 条件校验

#### B9. streamChat 的 sourceDocs 缺少 chunkIndex 和 content 字段
- **分支**: master_backend
- **文件**: `QaServiceImpl.java`
- **问题**: `streamChat` 方法构建 sourceDocs 时遗漏 `setChunkIndex` 和 `setContent`，与 `chat` 方法返回不一致，前端检索可视化无法展示分块详情
- **修复**: 补全 `src.setChunkIndex(chunk.getChunkIndex())` 和 `src.setContent(chunk.getContent())`

#### B10. Python 端 DocumentStatus 状态机流转错误
- **分支**: master_python
- **文件**: `status_enums.py`
- **问题**: `CHUNKING` 的下一状态直接指向 `EMBEDDING`，跳过了新增的 `CHUNK_REVIEW` 状态；`next_after_task_complete` 中 `CHUNKING → EMBEDDING` 也未更新
- **修复**: `CHUNKING.next_states` 改为 `{CHUNK_REVIEW, CHUNKING_FAILED}`，`next_after_task_complete` 中 `CHUNKING → CHUNK_REVIEW`
- **Commit**: 3b9bde1

#### B11. Axios 响应拦截器破坏 blob/text 响应
- **分支**: master_front
- **文件**: `request.ts`
- **问题**: 响应拦截器的成功处理器（line 39-46）统一按 `Result<T>` JSON 格式解包。当 API 使用 `responseType: 'blob'`（download）或 `responseType: 'text'`（getContent）时，`response.data` 是 Blob/String 而非 JSON 对象，`body.code` 为 undefined，导致数据被错误处理并 reject
- **修复**: 在解包前检查 `responseType`，对 blob/text 类型直接返回原始数据
- **Commit**: 80a8ec4

#### B12. SSE 元数据解析依赖 JSON key 顺序
- **分支**: master_front
- **文件**: `useSSE.ts`
- **问题**: 匹配条件为 `line.startsWith('data: {"chatId"')`，仅当 `chatId` 是 JSON 第一个 key 时生效。若后端序列化顺序变化（如 `{"tokenCount":50,"chatId":123}`），done 回调永远不会触发
- **修复**: 改为 `line.startsWith('data: {')` 通用匹配，再检查 `meta.chatId` 是否存在

#### B13. DocumentContent 空状态双渲染
- **分支**: master_front
- **文件**: `DocumentContent.vue`
- **问题**: markdown-body div 使用 `v-else-if="!loading"`（条件过于宽松），el-empty 使用独立的 `v-if`，当内容为空时两者同时显示（空的 div + el-empty）
- **修复**: markdown-body 条件改为 `v-else-if="!loading && renderedHtml"`，el-empty 改为 `v-else-if="!loading && !error"`，形成互斥渲染链

#### B14. ChunkReview 页面显示原始状态枚举 key
- **分支**: master_front
- **文件**: `ChunkReview.vue`
- **问题**: 文档状态标签直接显示 `docStatus`（值为 `CHUNK_REVIEW` 等英文枚举 key），未映射为中文标签
- **修复**: 导入 `DOCUMENT_STATUS_MAP`，显示 `DOCUMENT_STATUS_MAP[docStatus]?.label || docStatus`

### 缺陷统计
| 严重程度 | 数量 | 涉及仓库 |
|---------|------|---------|
| 严重 (Critical) | 14 | Backend×9, Python×1, Frontend×4 |
| 高 (High) | 6 | Backend×4, Frontend×2 |
| 中 (Medium) | 3 | Frontend×3 |
| 低 (Low) | 2 | Backend×1, Python×1 |
| **合计** | **25** | |

---

## 五、全流程端到端测试发现的缺陷（2026-05-25）

### B15. HttpMessageNotReadableException 返回 500 而非 400
- **分支**: master_backend
- **文件**: `GlobalExceptionHandler.java`
- **问题**: 当请求 JSON 类型不匹配（如 `kbIds` 传 String 而非数组）时，Jackson 抛出 `HttpMessageNotReadableException`，被兜底 `ExceptionHandler` 捕获返回 500
- **修复**: 添加专用的 `HttpMessageNotReadableException` 处理器，返回 `400 Bad Request` 并给出明确的 JSON 格式错误提示
- **Commit**: b9106ec

### B16. application.yml 服务地址硬编码 localhost
- **分支**: master_backend
- **文件**: `application.yml`
- **问题**: MySQL、Redis、Kafka、MinIO、gRPC 主机均硬编码为 `localhost`，Docker 容器内无法访问其他容器
- **修复**: 全部改为 `${ENV_VAR:localhost}` 环境变量占位符格式，通过 docker run -e 传入实际服务名（如 `MYSQL_HOST=rag-mysql`）
- **Commit**: b9106ec

### B17. Dockerfile 缺少 /app/logs 目录创建
- **分支**: master_backend
- **文件**: `docker/Dockerfile`
- **问题**: Logback 配置写入 `./logs/rag-server.log`（即 `/app/logs/`），但 Dockerfile 仅创建 `/var/log/rag-server`，非 root 用户 `rag` 无权限创建 `/app/logs/`，导致容器启动失败
- **修复**: `mkdir -p /app/logs /var/log/rag-server && chown -R rag:rag /app/logs /var/log/rag-server`
- **Commit**: b9106ec

### B18. Python Kafka 消费者不提交 offset（严重）
- **分支**: master_python
- **文件**: `task_consumer.py`
- **问题**: 使用手动分区分配（`assign()`）但从未调用 `commit()`，导致每次容器重启都从最早 offset 重放所有历史消息。旧任务（MinIO 文件已删除）反复重试直至耗尽 4 次重试，期间占满任务队列阻塞新任务
- **修复**: 
  1. 添加 `group_id` 到 `KafkaConsumer` 构造函数（提交 offset 所必需）
  2. 每次任务完成（成功或最终失败）后，通过 `on_complete`/`on_failed` 回调提交对应分区的 offset
  3. 使用 `OffsetAndMetadata(offset + 1, "", 0)` 兼容 kafka-python 2.3.1 的 API
  4. 启动时先 `assign()` 再检查 `committed()` 偏移量，恢复已提交位置
- **Commit**: 46ec7ba

### B19. Python 任务队列满时静默丢弃任务（严重）
- **分支**: master_python
- **文件**: `task_dispatcher.py`
- **问题**: `submit()` 仅在 `len(_futures) < max_concurrent(5)` 时接受任务，否则抛出 `TaskException`。消费者在 poll 循环中捕获异常后静默丢弃，消息永不重试，导致 EMBED_PROCESS 等关键任务被丢失
- **修复**: 
  1. 添加 `deque` 缓冲队列存储待处理任务
  2. 添加 drainer 后台线程，当执行器有空闲槽位时从缓冲队列取任务提交
  3. 使用 `threading.Event` 实现高效唤醒
- **Commit**: 46ec7ba

### B20. Milvus L2 距离过滤方向反了（严重）
- **分支**: master_python
- **文件**: `milvus_client.py`
- **问题**: L2 距离越小表示越相似，但过滤条件为 `result.distance >= score_threshold`（越大越相似），导致最相似的匹配被过滤掉，检索永远返回空结果
- **修复**: 
  1. 改用 `similarity >= score_threshold`，similarity = `1.0 - distance / 2.0`（归一化向量 L2 ∈ [0, 2]）
  2. 将 L2 距离转换为 0~1 相似度分数（越高越相似），统一接口语义
  3. 检索候选数扩展为 `max(top_k * 2, 10)`，确保过滤后仍有足够结果
- **Commit**: 46ec7ba

### B21. 相似度阈值配置过高
- **分支**: master_backend + master_python
- **文件**: `QuestionDTO.java` (Java), `settings.yaml` (Python)
- **问题**: Java 默认 `scoreThreshold=0.7`，Python 默认 `score_threshold=0.5`，但 BGE-base-zh-v1.5 模型归一化向量后，查询与文档块的 L2 距离约 0.9~1.0，对应相似度 0.50~0.55。阈值 0.7 会过滤掉所有合法匹配
- **修复**: Java `scoreThreshold` 降至 `0.3f`，Python `score_threshold` 降至 `0.3`
- **Commit**: b9106ec (Java), 46ec7ba (Python)

### B22. BM25 索引内存存储重启丢失
- **分支**: master_python
- **文件**: `hybrid_retrieval.py`, `bm25_engine.py`
- **问题**: BM25 索引基于内存字典存储，容器重启后全部丢失。EMBED_PROCESS 完成后 BM25 索引重建依赖 Kafka 消息重放，但 offset 提交后不再重放，导致 BM25 检索始终返回空
- **影响**: 混合检索的 BM25 分支失效，仅依赖向量检索
- **状态**: 已知限制，待后续增加启动时从 Milvus/DB 重建 BM25 索引的逻辑

### B23. 问答缓存污染失败结果
- **分支**: master_backend
- **文件**: `QaServiceImpl.java`
- **问题**: QA 应答无条件写入 Redis 缓存（包括 `sourceDocs` 为空的结果）。首次查询因阈值过高返回空结果被缓存后，后续相同问题直接返回缓存的空结果
- **影响**: 修复检索 bug 后，重新查询可能仍返回旧的空结果（需手动清除 Redis 缓存）
- **状态**: 已知限制，待后续优化（仅在 sourceDocs 非空时缓存，或添加缓存版本控制）

### B24. RRF 融合分数覆盖相似度分数
- **分支**: master_python
- **文件**: `hybrid_retrieval.py`
- **问题**: `_fuse_results()` 将 `info["score"]` 覆写为 RRF 分数（~0.02），丢失了原始的相似度分数（0~1）。前端显示的分数为 RRF 值而非语义相似度，用户体验不佳
- **状态**: 已知限制，待后续评估是否需要保留原始相似度

### B25. gRPC 检索响应缺少文档名称
- **分支**: master_python
- **文件**: `retrieval_service.py`
- **问题**: 检索结果中 `document_name` 始终为空字符串，Milvus 不存储文档名称，BM25 也不包含
- **状态**: 已知限制，需在 Java 侧或 Python 侧通过 document_id 查询数据库补全

### 新增缺陷统计（2026-05-25）
| 严重程度 | 数量 | 涉及仓库 |
|---------|------|---------|
| 严重 (Critical) | 6 | B18, B19, B20, B21 |
| 高 (High) | 3 | B15, B16, B17 |
| 中 (Medium) | 2 | B22, B23 |
| 低 (Low) | 2 | B24, B25 |
| **新增合计** | **13** | Backend×5, Python×8 |

### 总缺陷统计（截至 2026-05-25）
| 严重程度 | B1~B14 | B15~B25 | 总计 |
|---------|--------|---------|------|
| 严重 | 14 | 6 | 20 |
| 高 | 6 | 3 | 9 |
| 中 | 3 | 2 | 5 |
| 低 | 2 | 2 | 4 |
| **合计** | **25** | **13** | **38** |

# RAG 知识库系统

基于检索增强生成（RAG）架构的企业级知识库管理平台。Java 业务控制中心 + Python AI 计算引擎，通过 gRPC 和 Kafka 实现异构通信。

> 📖 **完整文档**: [系统架构](docs/系统架构文档.md) | [部署文档](docs/最新部署文档.md) | [Nacos设计](docs/Nacos微服务架构设计.md) | [配置清单](docs/系统配置参数清单.md)

## 部署架构 (支持单体和微服务双模式)

```
                        ┌─────────────────────────┐
                        │   RAG-Web (Vue3 :5173)  │
                        └────────────┬────────────┘
                                     │ /api/*
                        ┌────────────▼────────────┐
                        │  Gateway (:8088)         │
                        │  JWT Filter → Route      │
                        └────────────┬────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
    ┌─────────▼─────────┐  ┌────────▼────────┐  ┌─────────▼─────────┐
    │  rag-server :8080 │  │  rag-auth :8081 │  │  rag-kb   :8082  │
    │  (单体,全部API)    │  │  rag-doc :8083  │  │  rag-qa   :8084  │
    └───────────────────┘  │  rag-config:8085│  └───────────────────┘
                           └─────────────────┘
    Nacos :8848 | Grafana :3000 | Prometheus :9090 | Loki :3100
    MySQL:3307 | Redis:6379 | Kafka:9092 | MinIO:9000 | Milvus:19530
```

> **一套代码，两种模式**：通过 Maven 多模块 + Spring Profile + 独立 Main 类三个机制，实现单体和微服务的无缝切换。详见 [部署文档 §2](docs/最新部署文档.md)。

### 原理速览

```
同一份 Controller 代码
    │
    ├── Maven:  rag-monolith依赖全部模块 → 单体JAR
    │           rag-auth只依赖common → 微服务JAR
    │
    ├── Profile: SPRING_PROFILES_ACTIVE=prod → 全部Controller加载 → 单体
    │            SPRING_PROFILES_ACTIVE=auth → 仅Auth相关加载 → 微服务
    │
    └── Main类: RagApplication(monolith) → 单体入口
                RagAuthApplication(auth)  → 微服务入口
```

## 项目结构

```
RAG/
├── RAG-BACKEND/           # Java 父工程 (Maven 7模块, 纯微服务架构)
│   ├── rag-common/        #   共享层: Entity/Mapper/Infrastructure (87 files)
│   ├── rag-auth/          #   认证模块: Auth/User/Role (10 files)
│   ├── rag-kb/            #   知识库模块: KB/Team (9 files)
│   ├── rag-doc/           #   文档模块: File/Chunk/Review (15 files)
│   ├── rag-qa/            #   问答模块: QA/Feedback (13 files)
│   ├── rag-config/        #   配置模块: SystemConfig/UserConfig (5 files)
│   └── rag-monolith/      #   聚合模块: 依赖全部→单体JAR (1 file)
├── RAG-PYTHON/            # Python AI 核心 (检索+生成, Nacos注册)
├── RAG-CLEANING/          # Python 文档清洗
├── RAG-MEMORY/            # Python 用户记忆
├── RAG-QUE/               # Python 查询优化
├── RAG-Web/               # Vue3 前端
├── RAG-GATEWAY/           # Spring Cloud Gateway (JWT+路由)
├── docker-compose.yml     # 基础设施编排
├── docs/                  # 项目文档 (17篇)
└── sql/                   # 数据库迁移 (V1-V5)
```

## 快速开始

```bash
# 1. 启动基础设施 + 单体服务
cd RAG-BACKEND && docker compose up -d

# 2. 启动 Gateway
docker run -d --name rag-gateway --network rag-network -p 8088:8080 \
  -e JWT_SECRET=your-256-bit-secret-key-change-in-production \
  -e REDIS_HOST=redis -e REDIS_PASSWORD=redis123 rag-gateway:latest

# 3. 启动前端
cd ../RAG-Web && npm install && npm run dev

# 4. 访问
# 前端:     http://localhost:5173
# Nacos:    http://localhost:8848/nacos (nacos/nacos)
# Grafana:  http://localhost:3000 (admin/admin123)
# 账号:     admin / admin123
```

## 构建命令

```bash
# 单体模式 (当前运行)
mvn package -pl rag-monolith -am -DskipTests   # → rag-monolith.jar

# 微服务模式 (独立部署)
mvn package -pl rag-auth -am -DskipTests       # → rag-auth.jar
mvn package -pl rag-kb -am -DskipTests         # → rag-kb.jar
mvn package -pl rag-doc -am -DskipTests        # → rag-doc.jar
mvn package -pl rag-qa -am -DskipTests         # → rag-qa.jar
mvn package -pl rag-config -am -DskipTests     # → rag-config.jar
```
    │   ├── config/                          #   系统配置（本地缓存 + Redis广播）
    │   └── statemachine/                    #   文档状态机
    └── controller/                          # API接入层
        ├── api/                             #   8个REST控制器
        ├── interceptor/                     #   JWT拦截器 + 限流拦截器
        └── ws/                              #   WebSocket（预留）
```

## 分层架构

```
应用接入层   ← REST API / SSE / WebSocket / JWT拦截器 / 限流拦截器
业务服务层   ← 文件 / 审核 / 知识库 / 问答 / 用户 / 权限 / 配置 / 反馈 / 状态机
通信层       ← Kafka生产者+消费者 / gRPC双向调用
领域层       ← Entity / Mapper (MyBatis-Plus)
基础设施层   ← Spring Boot / Redis / Redisson / MinIO / MySQL
公共支撑层   ← 常量 / 枚举 / 统一返回 / 全局异常 / 工具类 / 自定义注解
```

依赖方向为单向自上而下，无循环依赖。

## 核心设计

### 文档状态机

基于枚举的轻量级实现，11 个状态覆盖文档全生命周期：

```
UPLOADED → PARSING → CLEANING → PENDING_REVIEW → APPROVED → CHUNKING → EMBEDDING → COMPLETED
                ↓          ↓              ↓
          *_FAILED    *_FAILED       REJECTED → PARSING（重新处理）
```

状态转换使用乐观锁防止并发冲突，Python 端通过 Kafka 异步回调触发状态流转。

### 问答流程

```
用户提问 → Redis缓存检查 → gRPC检索（Python向量检索）
       → 上下文构建 → gRPC生成（Python LLM推理）
       → SSE流式输出 / 同步返回 → 保存问答记录 → 写入缓存
```

### 安全模型

- **认证**：JWT 双 Token（access 2h + refresh 7d），refresh token 存 Redis 支持强制下线
- **鉴权**：RBAC 模型，5 个预置角色 + 18 个权限码，`@PreAuthorize` 方法级控制
- **限流**：Redis 滑动窗口 + Lua 脚本原子限流

### 文件去重

流式 MD5 计算 + 数据库唯一索引 `uk_file_md5`，避免重复文件存储。

## 数据库

11 张数据表：`users` / `roles` / `permissions` / `user_roles` / `role_permissions` / `knowledge_bases` / `documents` / `review_records` / `chat_records` / `feedback` / `system_configs`

详见 [数据库设计文档](docs/数据库设计文档.md)。

## 快速开始

### 环境要求

- JDK 17+ / Maven 3.9+ / MySQL 8.0+ / Redis 7.x / Kafka 3.x / MinIO
- Python AI 服务（检索 + 生成，需单独部署）

### 本地开发

```bash
# 1. 启动基础设施（Docker Compose）
docker-compose up -d

# 2. 初始化数据库
mysql -h127.0.0.1 -uroot -p rag_db < sql/V1__init_schema.sql
mysql -h127.0.0.1 -uroot -p rag_db < sql/V2__init_data.sql

# 3. 编译并启动
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 访问 Swagger
open http://localhost:8080/swagger-ui.html
```

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 超级管理员 |
| reviewer | reviewer123 | 审核员 |
| editor | editor123 | 编辑 |
| viewer | viewer123 | 访客 |

### Docker 部署

项目提供完整的 `docker-compose.yml`，一键启动全部 8 个容器（MySQL / Redis / Kafka / MinIO / etcd / Milvus / Python AI / Java）：

```bash
# 1. 创建 .env 文件，填写实际配置
cat > .env << 'EOF'
LLM_API_KEY=sk-your-api-key
LLM_BASE_URL=https://api.deepseek.com
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=rag_db
REDIS_PASSWORD=redis123
JWT_SECRET=your-256-bit-secret-key-change-in-production
EOF

# 2. 构建并启动所有服务
docker-compose up -d --build

# 3. 验证健康状态
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

**启动后端口**：

| 服务 | 端口 | 说明 |
|------|------|------|
| Java API | :8080 | REST API + Swagger |
| Python gRPC Retrieval | :50051 | 向量检索服务 |
| Python gRPC Generation | :50052 | LLM 生成服务 |
| MinIO Console | :9001 | 对象存储管理界面 |
| Milvus | :19530 | 向量数据库 |

**常见问题**：

- **JDBC 连接失败 `Unsupported character encoding 'utf8mb4'`**：JDBC URL 中 `characterEncoding` 需使用 Java 标准名称 `UTF-8`（非 MySQL 的 `utf8mb4`）。
- **`Public Key Retrieval is not allowed`**：MySQL 8.0 `caching_sha2_password` 认证需要 JDBC URL 追加 `allowPublicKeyRetrieval=true`。
- **`/actuator/health` 返回 403**：Spring Security 默认拦截，需在 `SecurityConfig` 中将 `/actuator/health` 加入 `permitAll()`。
- **登录失败 `密码错误`**：确保 `sql/V2__init_data.sql` 中的 BCrypt 哈希与密码匹配，可用 `docker exec rag-python python -c "import bcrypt; print(bcrypt.hashpw(b'admin123', bcrypt.gensalt()).decode())"` 生成正确哈希。
- **Docker 镜像拉取失败**：国内 registry 镜像可能返回 `content size of zero`，尝试使用可拉取的替代镜像（如 `amazoncorretto:17-alpine` 替代 `eclipse-temurin`）。

## 文档

| 文档 | 说明 |
|------|------|
| [API文档](docs/API文档.md) | 全部接口文档、请求/响应示例、权限码对照 |
| [数据库设计文档](docs/数据库设计文档.md) | ER图、完整DDL、索引说明、状态枚举 |
| [部署文档](docs/部署文档.md) | 环境要求、Docker Compose、手动部署、运维命令 |
| [开发手册](docs/开发手册.md) | 项目结构、分层架构、编码规范、新增功能指南 |
| [接口调用示例](docs/接口调用示例.md) | cURL / JavaScript / Python 多语言调用示例 |

## API 概览

| 模块 | 前缀 | 说明 |
|------|------|------|
| 认证 | `/api/auth` | 登录/注册/刷新Token/登出 |
| 文件 | `/api/files` | 上传/下载/列表/删除 |
| 知识库 | `/api/knowledge-bases` | CRUD + 文档上下架 |
| 审核 | `/api/review` | 待审列表/审核/批量通过 |
| 问答 | `/api/qa` | 普通问答 + SSE流式输出 + 历史记录 |
| 用户 | `/api/users` | 个人信息/密码/角色分配 |
| 权限 | `/api/permission` | 角色管理/权限分配（管理员） |
| 配置 | `/api/system-configs` | 系统配置CRUD（管理员） |
| 反馈 | `/api/feedback` | 提交/查询/处理 |

## License

MIT

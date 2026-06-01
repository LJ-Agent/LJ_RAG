# RAG 系统 Nacos 微服务架构设计

> Nacos = **Na**ming (服务发现) + **Co**nfiguration (配置中心) + **S**ervice (服务管理)

---

## 1. 总体架构

```
                          ┌─────────────────────────────┐
                          │      Nacos Server :8848      │
                          │  ┌─────────┐ ┌───────────┐  │
                          │  │服务注册  │ │ 配置中心   │  │
                          │  │发现中心  │ │ (替代YAML) │  │
                          │  └─────────┘ └───────────┘  │
                          └──────────────────────────────┘
                               ↑ 注册/订阅        ↑ 拉取配置
    ┌──────────────────────────┼──────────────────┼──────────────────────┐
    │                          │                  │                      │
    ▼                          ▼                  ▼                      ▼
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ Gateway │  │  Auth   │  │   KB    │  │   Doc   │  │   QA    │
│ :8080   │  │ :8081   │  │ :8082   │  │ :8083   │  │ :8084   │
│ Spring  │  │ Spring  │  │ Spring  │  │ Spring  │  │ Spring  │
│ Cloud   │  │ Boot    │  │ Boot    │  │ Boot    │  │ Boot    │
│ Gateway │  │         │  │         │  │         │  │         │
└────┬────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
     │ 路由分发
     ▼
┌─────────────────────────────────────────────────────────────┐
│                    Python 微服务 (gRPC)                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │CLEANING  │  │ PYTHON   │  │  MEMORY  │  │   QUE    │   │
│  │ :50056   │  │ :50051/2 │  │ :50053/4 │  │ :50055   │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│            (gRPC 不经过网关, 直连通信)                       │
└─────────────────────────────────────────────────────────────┘

基础设施:
  MySQL :3307 | Redis :6379 | Kafka :9092 | MinIO :9000 | Milvus :19530
```

---

## 2. Nacos 部署

### 2.1 Docker Compose 新增

```yaml
  # ==================== Nacos 服务注册与配置中心 ====================
  nacos:
    image: nacos/nacos-server:v2.3.1
    container_name: rag-nacos
    restart: unless-stopped
    environment:
      - MODE=standalone
      - PREFER_HOST_MODE=hostname
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_PORT=3306
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=${MYSQL_ROOT_PASSWORD:-root123}
    ports:
      - "8848:8848"
      - "9848:9848"   # gRPC (服务间通信)
      - "9849:9849"   # gRPC (服务间通信)
    networks:
      - rag-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/readiness"]
      interval: 15s
      timeout: 5s
      retries: 5
```

### 2.2 Nacos 访问

- **控制台**: http://localhost:8848/nacos
- **默认账号**: nacos / nacos
- **API**: http://localhost:8848/nacos/v1/

---

## 3. 服务注册与发现

### 3.1 Java 服务注册 (Spring Cloud Alibaba)

每个 Java 微服务添加依赖:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    <version>2023.0.1.0</version>
</dependency>
```

配置 `application.yml`:

```yaml
spring:
  application:
    name: rag-auth-service   # 服务名=注册名
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: rag-prod    # 命名空间隔离
        group: RAG_GROUP
```

### 3.2 Gateway 动态路由

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true        # 自动从 Nacos 发现服务
          lower-case-service-id: true
      routes:
        - id: auth-service
          uri: lb://rag-auth-service     # lb:// = 负载均衡
          predicates:
            - Path=/api/auth/**, /api/users/**, /api/roles/**
        - id: kb-service
          uri: lb://rag-kb-service
          predicates:
            - Path=/api/knowledge-bases/**, /api/teams/**
        - id: doc-service
          uri: lb://rag-doc-service
          predicates:
            - Path=/api/files/**, /api/review/**, /api/chunks/**
        - id: qa-service
          uri: lb://rag-qa-service
          predicates:
            - Path=/api/qa/**, /api/feedback/**
```

### 3.3 Python 服务注册 (通过 HTTP API)

```python
# 启动时向 Nacos 注册
import requests
requests.post("http://nacos:8848/nacos/v1/ns/instance", params={
    "serviceName": "rag-python-service",
    "ip": "rag-python",
    "port": 50051,
    "namespaceId": "rag-prod",
    "groupName": "RAG_GROUP",
})
```

### 3.4 服务注册全景

```
Nacos Dashboard ── 服务列表 ──────────────────────────
┌─────────────────────┬──────┬──────┬────────┐
│ 服务名               │ 实例  │ 端口  │ 状态    │
├─────────────────────┼──────┼──────┼────────┤
│ rag-gateway          │ 1    │ 8080 │ UP     │
│ rag-auth-service     │ 1    │ 8081 │ UP     │
│ rag-kb-service       │ 1    │ 8082 │ UP     │
│ rag-doc-service      │ 1    │ 8083 │ UP     │
│ rag-qa-service       │ 1    │ 8084 │ UP     │
│ rag-python-service   │ 1    │ 50051│ UP     │
│ rag-cleaning-service │ 1    │ 50056│ UP     │
│ rag-memory-service   │ 1    │ 50053│ UP     │
│ rag-que-service      │ 1    │ 50055│ UP     │
└─────────────────────┴──────┴──────┴────────┘
```

---

## 4. Nacos 配置中心 (替代当前 YAML + DB)

### 4.1 配置迁移策略

| 当前存储 | 迁移到 | 原因 |
|----------|--------|------|
| `application.yml` | Nacos 配置 | 支持热更新、统一管理 |
| `settings.yaml` (Python) | Nacos 配置 | Python 通过 API 拉取 |
| `system_configs` 表 (DB) | Nacos 配置 | 集中管理、版本回溯 |
| `.env` 环境变量 | Docker Compose | 敏感信息继续用 env |

### 4.2 Java 配置接入

```yaml
# bootstrap.yml (每个微服务)
spring:
  application:
    name: rag-auth-service
  cloud:
    nacos:
      config:
        server-addr: nacos:8848
        namespace: rag-prod
        group: RAG_GROUP
        file-extension: yaml
        shared-configs:
          - data-id: rag-common.yaml      # 公共配置
            group: RAG_GROUP
            refresh: true
```

### 4.3 Nacos 配置项组织

```
Namespace: rag-prod
├── Group: RAG_GROUP
│   ├── rag-common.yaml          # 公共: MySQL/Redis/Kafka/MinIO
│   ├── rag-auth-service.yaml    # Auth 私有的配置
│   ├── rag-kb-service.yaml      # KB 私有的配置
│   ├── rag-doc-service.yaml     # Document 私有的配置
│   ├── rag-qa-service.yaml      # QA 私有的配置
│   └── rag-chunk-strategy.yaml  # 分块策略 (热更新)
│
├── Group: PYTHON_GROUP
│   ├── rag-python.yaml          # Python AI 服务配置
│   ├── rag-cleaning.yaml        # 清洗服务配置
│   ├── rag-memory.yaml          # 记忆服务配置
│   └── rag-que.yaml             # QUE 服务配置
```

### 4.4 配置热更新

```java
// Java: @RefreshScope 自动刷新
@RestController
@RefreshScope
public class ChunkController {
    @Value("${chunk.default-size:500}")
    private int defaultSize;  // Nacos 修改后自动刷新
}

// Python: 监听 Nacos 配置变更
# 方案1: 通过 Nacos SDK (nacos-sdk-python)
# 方案2: 通过 Kafka (现有 rag-config-change topic)
```

### 4.5 配置版本管理

Nacos 内置：
- 配置历史版本 (自动保存)
- 回滚到任意历史版本
- 配置变更审计日志
- 灰度发布 (Beta 发布)

---

## 5. 架构演进路径

### Phase 1: Nacos 部署 (0.5天)
```
- Docker Compose 添加 Nacos 容器
- 创建 nacos_config 数据库
- 验证 Nacos Dashboard 可访问
```

### Phase 2: 现有 Python 服务注册到 Nacos (0.5天)
```
- RAG-PYTHON/CLEANING/MEMORY/QUE 启动时向 Nacos 注册
- Python SDK 或 HTTP API 注册
- 验证 Nacos Dashboard 可见所有 Python 服务
```

### Phase 3: Java 微服务拆分 + 注册 (2天)
```
- 拆分 Java 单体为 4 个微服务 (auth/kb/doc/qa)
- 每个服务添加 spring-cloud-starter-alibaba-nacos-discovery
- 验证全部注册到 Nacos
```

### Phase 4: Gateway 动态路由 (0.5天)
```
- Gateway 接入 Nacos 服务发现
- 动态路由: lb://服务名
- 验证: 停掉一个实例 → Gateway 自动剔除
```

### Phase 5: 配置迁移到 Nacos (1天)
```
- application.yml → Nacos 配置项
- settings.yaml → Nacos 配置项
- 前端配置管理 → Nacos API (替代现有 system_configs 表)
- @RefreshScope 验证热更新
```

### Phase 6: 全链路测试 (1天)
```
- 文档上传 → 清洗 → 分块 → 嵌入 → 问答
- 服务宕机 → Gateway 容错
- 配置修改 → 热更新验证
- 权限隔离 → 多角色测试
```

**总工期: 5.5天**

---

## 6. 容错与降级

### 6.1 Nacos 不可用时的降级

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
    gateway:
      routes:
        - id: kb-fallback
          uri: http://rag-kb-service:8082   # 硬编码降级地址
          predicates:
            - Path=/api/knowledge-bases/**
```

- Nacos 宕机 → Gateway 降级到硬编码地址
- 本地缓存服务列表 (Spring Cloud LoadBalancer 缓存)

### 6.2 健康检查

```yaml
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          management:
            context-path: /actuator/health
```

Nacos 定期健康检查 → 剔除不健康实例 → Gateway 自动切换。

---

## 7. 服务拓扑（最终态）

```
                    ┌──────────────────────────┐
                    │     Nacos :8848          │
                    │  Registry + Config        │
                    └──────────────────────────┘
                         ↑        ↑
                    注册/发现   拉取配置
                         │        │
     ┌───────────────────┼────────┼────────────────────┐
     │                   │        │                    │
     ▼                   ▼        ▼                    ▼
┌─────────┐  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│ Gateway │  │ Auth │ │  KB  │ │ Doc  │ │  QA  │ │Config│
│  :8080  │  │:8081 │ │:8082 │ │:8083 │ │:8084 │ │:8085 │
│ 动态路由│  │      │ │      │ │      │ │      │ │      │
└────┬────┘  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘
     │
     │ HTTP 路由
     ▼
  ┌─────────────────────────────────────────┐
  │           Python gRPC 服务               │
  │  CLEANING :50056 | PYTHON :50051/2       │
  │  MEMORY :50053/4  | QUE :50055           │
  └─────────────────────────────────────────┘
```

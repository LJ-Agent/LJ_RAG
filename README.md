# RAG API Gateway

Spring Cloud Gateway 微服务网关，提供 JWT 全局鉴权和路由分发。

## 架构位置

```
RAG-Web :5173 → Gateway :8088 → JWT Filter → Route → rag-server :8080
```

## 技术栈

- Spring Cloud Gateway
- JWT (jjwt 0.12.5)
- Redis (限流)
- Spring Boot Actuator (健康检查)

## 快速启动

```bash
docker run -d --name rag-gateway --network rag-network -p 8088:8080 \
  -e JWT_SECRET=your-256-bit-secret-key-change-in-production \
  -e REDIS_HOST=redis -e REDIS_PASSWORD=redis123 \
  rag-gateway:latest
```

## 路由配置

| 路径 | 目标 |
|------|------|
| `/api/**` | rag-server:8080 |
| `/actuator/**` | Gateway 自身 |

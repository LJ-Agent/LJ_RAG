# RAG API Gateway

Spring Cloud Gateway 微服务网关，提供 JWT 全局鉴权和路由分发。系统唯一入口。

## 架构位置

```
浏览器 :8088 → Gateway :8088 ──┬── /api/** → JWT Filter → Route → rag-server :8080
                              └── /**     → Vite :5173 (开发) / dist/ (生产)
```

## 技术栈

- Spring Cloud Gateway
- JWT (jjwt 0.12.5)
- Redis (限流)
- Spring Boot Actuator (健康检查)

## 快速启动

```bash
# Docker Compose（推荐，与 rag-server 统一管理）
cd RAG-BACKEND
docker compose build rag-gateway
docker compose up -d rag-gateway

# 或手动运行
docker run -d --name rag-gateway --network rag-network -p 8088:8080 \
  -e JWT_SECRET=your-256-bit-secret-key-change-in-production \
  -e REDIS_HOST=redis -e REDIS_PASSWORD=redis123 \
  rag-gateway:latest
```

## 路由配置

| 路径 | 目标 | 说明 |
|------|------|------|
| `/api/**` | rag-server:8080 | JWT 鉴权后路由到业务后端 |
| `/**` | host.docker.internal:5173 (开发) | 前端静态资源（生产由 Nginx 托管） |
| `/actuator/**` | Gateway 自身 | 健康检查 |

## JWT 过滤器

拦截所有 `/api/**` 请求，验证 Token 后将用户信息注入 `X-User-Id` / `X-Username` 请求头传给下游。

公开路径跳过：`/api/auth/login`、`/api/auth/register`、`/actuator/**`。

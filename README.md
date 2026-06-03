# RAG 知识库系统 — 前端管理平台

基于 Vue 3 + TypeScript + Vite + Element Plus 构建的企业级知识库管理系统前端。

> 仓库地址：https://gitee.com/yangchangling/rag-basic-platform.git  
> 分支说明：`master_backend`(Java后端) · `master_front`(Vue前端) · `master_python`(Python服务)

---

## 工程结构

```
RAG-Web/
├── index.html                         # Vite 入口
├── vite.config.ts                     # 构建配置 + 开发代理 + 自动导入
├── tsconfig.json                      # TypeScript 配置
├── package.json
└── src/
    ├── main.ts                        # 应用启动入口
    ├── App.vue                        # 根组件 (路由过渡动画)
    │
    ├── api/                           # HTTP 服务层
    │   ├── request.ts                 # Axios 实例 (JWT拦截 + 401静默刷新 + Result解包)
    │   ├── modules/                   # 8 个 API 模块
    │   │   ├── auth.ts                #   认证: 登录/注册/刷新/登出/用户信息
    │   │   ├── files.ts               #   文件: 上传(进度)/列表/详情/下载/删除
    │   │   ├── review.ts              #   审核: 待审核列表/提交审核/批量通过
    │   │   ├── knowledgeBase.ts       #   知识库: CRUD + 文档上下架
    │   │   ├── qa.ts                  #   问答: 非流式聊天/SSE流式/历史
    │   │   ├── users.ts               #   用户: 列表/改密/角色分配/角色移除
    │   │   ├── configs.ts             #   配置: 键值CRUD
    │   │   └── feedback.ts            #   反馈: 提交/列表/详情/处理
    │   └── types/                     # TypeScript 类型定义
    │
    ├── assets/styles/                 # 样式
    │   ├── variables.scss             #   SCSS 变量
    │   ├── global.scss                #   全局样式 + 聊天UI
    │   └── transition.scss            #   页面过渡动画
    │
    ├── components/
    │   └── layout/                    # 布局组件
    │       ├── AppLayout.vue          #   el-container 外壳
    │       ├── AppSidebar.vue         #   el-menu 侧边栏 (折叠/展开)
    │       └── AppHeader.vue          #   顶栏 (面包屑 + 用户下拉)
    │
    ├── composables/                   # 组合式函数
    │   ├── useSSE.ts                  #   POST 方式 SSE 流式读取 (Fetch + ReadableStream)
    │   └── usePagination.ts           #   分页状态管理
    │
    ├── directives/
    │   └── permission.ts              # v-permission 指令 (按钮级权限控制)
    │
    ├── router/
    │   ├── index.ts                   #   createRouter (history 模式)
    │   ├── routes.ts                  #   15 条路由 (含权限 meta)
    │   └── guards.ts                  #   前置守卫: Token验证 → 拉取权限 → 权限校验
    │
    ├── stores/                        # Pinia 状态管理
    │   ├── auth.ts                    #   认证状态: user/token/isAuthenticated
    │   ├── app.ts                     #   应用状态: sidebarCollapsed
    │   └── permission.ts              #   权限状态: permissions[]/roles[]
    │
    ├── utils/
    │   ├── token.ts                   #   localStorage Token 存取
    │   ├── constants.ts               #   枚举映射: 文档状态/审核结果/反馈类型等
    │   ├── format.ts                  #   格式化: 文件大小/日期/文本截断
    │   └── sanitize.ts                #   DOMPurify XSS 防护
    │
    └── views/                         # 页面视图
        ├── auth/                      #   登录 / 注册
        ├── dashboard/                 #   仪表盘 (统计卡片 + 快捷入口)
        ├── knowledge/                 #   知识库管理 (CRUD)
        ├── documents/                 #   文档列表 / 文档上传
        ├── review/                    #   审核管理 (通过/驳回/批量)
        ├── qa/                        #   知识问答 (流式SSE) / 问答历史
        ├── users/                     #   用户管理 (角色分配)
        ├── config/                    #   系统配置 (键值管理)
        ├── feedback/                  #   反馈管理 (查看/处理)
        ├── profile/                   #   个人中心 (信息 + 改密)
        └── error/                     #   403 / 404
```

---

## 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 框架 | Vue 3 Composition API | 3.x |
| 语言 | TypeScript | 5.x |
| 构建 | Vite | 5.x |
| UI 组件库 | Element Plus | 2.x |
| 路由 | Vue Router | 4.x |
| 服务端状态 | @tanstack/vue-query | 5.x |
| 客户端状态 | Pinia | 2.x |
| HTTP 客户端 | Axios | 1.x |
| Markdown 渲染 | marked + DOMPurify | - |
| JWT 解析 | jwt-decode | - |

---

## 整体系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器                                │
│         http://localhost:8088 (唯一入口)                  │
│         所有请求统一经过 Gateway                          │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────▼────────────┐
          │   Gateway (:8088)        │
          │   JWT 鉴权 + 路由        │
          └──┬───────────────────┬──┘
             │ /api/*            │ /*
             ▼                   ▼
┌────────────────────┐  ┌──────────────────┐
│  RAG-BACKEND :8080 │  │  RAG-Web          │
│  Spring Boot       │  │  静态文件 (dist/)  │
│  业务控制中心       │  │                  │
└────────┬───────────┘  └──────────────────┘
         │ gRPC (50051/50052/50055)
         ▼
┌─────────────────────────────────────────────────────────┐
│             Python AI 服务                               │
│   检索服务 (:50051) · 生成服务 (:50052)                   │
│   文档清洗 (:50056) · 记忆引擎 (:50053/54) · QUE (:50055)  │
└─────────────────────────────────────────────────────────┘
```

> **开发环境**：`npm run dev` 启动 Vite 热更新开发服务器（端口 5173），API 通过 Gateway (:8088)。**生产部署**：`npm run build` 输出静态文件到 `dist/`，由 Gateway 或 Nginx 直接托管。

---

## 路由与权限矩阵

| 路径 | 页面 | 所需权限 | 侧边栏 |
|------|------|----------|--------|
| `/login` | 登录 | 无 | ✗ |
| `/register` | 注册 | 无 | ✗ |
| `/dashboard` | 仪表盘 | 登录即可 | ✓ |
| `/knowledge-bases` | 知识库管理 | `KB:VIEW` | ✓ |
| `/documents` | 文档列表 | `DOCUMENT:VIEW` | ✓ |
| `/documents/upload` | 上传文档 | `DOCUMENT:UPLOAD` | ✓ |
| `/review` | 审核管理 | `REVIEW:VIEW` | ✓ |
| `/qa` | 知识问答 | `QA:ASK` | ✓ |
| `/qa/history` | 问答历史 | `QA:HISTORY` | ✓ |
| `/users` | 用户管理 | `USER:VIEW` | ✓ |
| `/configs` | 系统配置 | `CONFIG:MANAGE` | ✓ |
| `/feedback` | 反馈管理 | `FEEDBACK:VIEW` | ✓ |
| `/profile` | 个人中心 | 登录即可 | ✓ |
| `/403` | 无权限 | 无 | ✗ |
| `/404` | 页面不存在 | 无 | ✗ |

---

## 角色与权限体系

| 角色 | 编码 | 包含权限 |
|------|------|----------|
| 超级管理员 | `SUPER_ADMIN` | 全部 18 项权限 |
| 管理员 | `ADMIN` | 除超管专属外的管理权限 |
| 审核员 | `REVIEWER` | `REVIEW:*` `DOCUMENT:VIEW` `QA:*` |
| 编辑者 | `EDITOR` | `DOCUMENT:*` `KB:*` `QA:*` |
| 查看者 | `VIEWER` | 所有 `*:VIEW` + `QA:ASK` |

---

## 核心机制

### 1. JWT 认证流

```
Login → 获取 accessToken + refreshToken → localStorage
       → 请求拦截器自动附加 Authorization: Bearer {token}
       → 401 → 静默刷新 (并发请求排队, 只刷新一次)
       → 刷新失败 → 跳转登录
```

### 2. 权限控制 (双层)

- **路由层**: `router/guards.ts` → 无权限路由跳转 `/403`
- **组件层**: `v-permission="'KB:CREATE'"` → 无权限时移除 DOM 元素

### 3. SSE 流式问答

```
POST /api/qa/chat/stream → Fetch + ReadableStream → 逐行解析 data:
→ 实时渲染 Markdown → event:done → 展示元数据
```

### 4. Axios 响应解包

后端统一返回 `{ code, message, data, timestamp }`，响应拦截器在 `code === 0` 时自动提取 `data`，视图层无需感知包装格式。

---

## 开发指南

### 环境要求

- Node.js 18+
- npm 9+

### 本地启动

```bash
cd RAG-Web
npm install
npm run dev        # 启动开发服务器 (localhost:5173)
                   # /api 代理到 localhost:8088 (Gateway)
npm run build      # 生产构建 → dist/
                   # 静态文件由 Gateway 托管
```

### 生产构建

```bash
npm run build      # 输出到 dist/
```

构建产物由 Gateway 直接托管（`/**` 路由），`/api` 路径由 Gateway 路由到后端服务。

### 开发约定

- 页面组件使用 `<script setup lang="ts">` 语法
- API 调用通过 `api/modules/*.ts` 统一管理
- 服务端数据使用 `@tanstack/vue-query` 的 `useQuery`/`useMutation`
- 客户端状态使用 Pinia store
- 未知状态的枚举值统一使用 `'info'` 类型的灰色标签兜底

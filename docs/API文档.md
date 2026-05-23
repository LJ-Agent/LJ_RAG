# RAG知识库系统 — 核心API文档

## 一、API概述

| 项目 | 说明 |
|------|------|
| 基础路径 | `http://{host}:8080` |
| 协议 | HTTP/HTTPS |
| 认证方式 | JWT Bearer Token（`Authorization: Bearer {token}`） |
| 请求格式 | JSON (Content-Type: application/json) |
| 响应格式 | JSON |
| 文件上传 | multipart/form-data |
| Swagger UI | `http://{host}:8080/swagger-ui.html` |
| OpenAPI JSON | `http://{host}:8080/v3/api-docs` |

## 二、统一响应格式

所有API返回统一格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1716451200000
}
```

### 通用错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权/Token过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 429 | 请求过于频繁 |
| 500 | 系统内部错误 |

## 三、认证接口 `/api/auth`

### 3.1 用户登录

```
POST /api/auth/login
```

**请求体：**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "userInfo": {
      "id": 1,
      "username": "admin",
      "realName": "系统管理员",
      "email": "admin@example.com",
      "status": 1,
      "roles": ["SUPER_ADMIN"],
      "permissions": ["USER:CREATE", "USER:UPDATE", "..."]
    }
  }
}
```

### 3.2 用户注册

```
POST /api/auth/register
```

**请求体：**
```json
{
  "username": "newuser",
  "password": "123456",
  "email": "user@example.com",
  "realName": "新用户"
}
```

### 3.3 刷新Token

```
POST /api/auth/refresh?refreshToken={refreshToken}
```

### 3.4 退出登录

```
POST /api/auth/logout
Header: Authorization: Bearer {accessToken}
```

### 3.5 获取当前用户信息

```
GET /api/auth/me
Header: Authorization: Bearer {accessToken}
```

## 四、文件管理接口 `/api/files`

### 4.1 上传文件

```
POST /api/files/upload
Content-Type: multipart/form-data
```

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |
| kbId | Long | 是 | 目标知识库ID |

**响应 data 字段：**

```json
{
  "id": 1,
  "kbId": 1,
  "fileName": "企业制度.pdf",
  "fileType": "pdf",
  "fileSize": 1024000,
  "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",
  "status": "UPLOADED",
  "uploadUserId": 1,
  "uploadAt": "2026-05-23T10:00:00",
  "createdAt": "2026-05-23T10:00:00"
}
```

### 4.2 文档列表

```
GET /api/files?kbId=1&status=COMPLETED&page=1&size=20
```

### 4.3 文档详情

```
GET /api/files/{id}
```

### 4.4 删除文档

```
DELETE /api/files/{id}
```

### 4.5 下载原始文件

```
GET /api/files/{id}/download
```

## 五、审核管理接口 `/api/review`

### 5.1 待审核列表

```
GET /api/review/pending?page=1&size=20
权限: REVIEW:VIEW
```

### 5.2 提交审核

```
POST /api/review/submit
权限: REVIEW:APPROVE
```

**请求体：**
```json
{
  "documentId": 1,
  "result": "APPROVED",
  "comment": "清洗内容完整，信息准确"
}
```

> result 取值：`APPROVED`（通过）或 `REJECTED`（驳回）

### 5.3 批量审核通过

```
POST /api/review/batch-approve
权限: REVIEW:APPROVE

请求体: [1, 2, 3]   (文档ID数组)
```

## 六、知识库管理接口 `/api/knowledge-bases`

### 6.1 创建知识库

```
POST /api/knowledge-bases
权限: KB:CREATE
```

**请求体：**
```json
{
  "kbName": "企业规章制度",
  "description": "公司内部各项管理制度和规范文档",
  "coverUrl": "https://example.com/cover.jpg",
  "status": 1
}
```

### 6.2 更新知识库

```
PUT /api/knowledge-bases/{id}
权限: KB:UPDATE
```

### 6.3 删除知识库

```
DELETE /api/knowledge-bases/{id}
权限: KB:DELETE
```

### 6.4 知识库列表

```
GET /api/knowledge-bases?kbName=企业&status=1&page=1&size=20
权限: KB:VIEW
```

### 6.5 知识库详情

```
GET /api/knowledge-bases/{id}
权限: KB:VIEW
```

**响应示例：**
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "kbName": "企业规章制度",
    "description": "公司内部各项管理制度和规范文档",
    "coverUrl": "https://example.com/cover.jpg",
    "status": 1,
    "ownerId": 1,
    "ownerName": "系统管理员",
    "documentCount": 15,
    "createdAt": "2026-05-23T10:00:00",
    "updatedAt": "2026-05-23T10:00:00"
  }
}
```

### 6.6 文档上下架

```
PUT /api/knowledge-bases/{kbId}/documents/{docId}?enabled=true
权限: KB:UPDATE
```

## 七、问答服务接口 `/api/qa`

### 7.1 非流式问答

```
POST /api/qa/chat
权限: QA:ASK
限流: 10次/秒
```

**请求体：**
```json
{
  "question": "员工年假有多少天？",
  "kbIds": [1, 2],
  "topK": 5,
  "scoreThreshold": 0.7
}
```

**响应：**
```json
{
  "code": 0,
  "data": {
    "chatId": 100,
    "answer": "根据公司《考勤管理制度》第三章第十二条规定：员工年假...",
    "sourceDocs": [
      {
        "documentId": 5,
        "documentName": "考勤管理制度.docx",
        "chunkId": "chunk-001",
        "chunkIndex": 3,
        "content": "第十二条 年假规定：正式员工...",
        "score": 0.92
      }
    ],
    "tokenCount": 256,
    "latencyMs": 1200
  }
}
```

### 7.2 流式问答（SSE）

```
POST /api/qa/chat/stream
Content-Type: application/json
Accept: text/event-stream
权限: QA:ASK
限流: 5次/秒
```

**请求体同上，响应为SSE事件流：**

```
data: 根据

data: 公司

data: 《考勤

data: 管理制度》

data: 第三章

...

event: done
data: {"chatId":100,"tokenCount":256,"latencyMs":1200}
```

### 7.3 问答历史

```
GET /api/qa/history?page=1&size=20
权限: QA:HISTORY
```

## 八、用户管理接口 `/api/users`

### 8.1 用户列表

```
GET /api/users?page=1&size=20
权限: USER:VIEW
```

### 8.2 修改密码

```
PUT /api/users/password

{
  "oldPassword": "admin123",
  "newPassword": "newPass456"
}
```

### 8.3 分配用户角色

```
POST /api/users/{userId}/roles/{roleId}
权限: USER:UPDATE
```

### 8.4 移除用户角色

```
DELETE /api/users/{userId}/roles/{roleId}
权限: USER:UPDATE
```

### 8.5 查询用户角色

```
GET /api/users/{userId}/roles
权限: USER:VIEW
```

## 九、系统配置接口 `/api/configs`

### 9.1 配置列表

```
GET /api/configs?page=1&size=50
权限: CONFIG:MANAGE
```

### 9.2 获取配置

```
GET /api/configs/{key}
权限: CONFIG:MANAGE
```

### 9.3 保存/更新配置

```
POST /api/configs

{
  "configKey": "review.auto_approve_hours",
  "configValue": "48",
  "configType": "NUMBER",
  "description": "审核超时自动通过小时数"
}
权限: CONFIG:MANAGE
```

### 9.4 删除配置

```
DELETE /api/configs/{id}
权限: CONFIG:MANAGE
```

## 十、反馈管理接口 `/api/feedback`

### 10.1 提交反馈

```
POST /api/feedback

{
  "chatRecordId": 100,
  "feedbackType": "CONTENT_ERROR",
  "content": "回答中关于年假天数的描述与实际制度不符",
  "contact": "user@example.com"
}
```

> feedbackType 取值：`BUG` / `SUGGESTION` / `CONTENT_ERROR` / `OTHER`

### 10.2 反馈列表

```
GET /api/feedback?page=1&size=20&status=PENDING
权限: FEEDBACK:VIEW
```

### 10.3 反馈详情

```
GET /api/feedback/{id}
权限: FEEDBACK:VIEW
```

### 10.4 处理反馈

```
PUT /api/feedback/{id}/handle
权限: FEEDBACK:HANDLE

{
  "handlerNote": "已人工修正该文档内容"
}
```

## 十一、预设角色与权限

| 角色 | 编码 | 说明 |
|------|------|------|
| 超级管理员 | SUPER_ADMIN | 所有权限 |
| 管理员 | ADMIN | 除超管专属外的管理权限 |
| 审核员 | REVIEWER | 文档审核相关权限 |
| 编辑者 | EDITOR | 文档上传、知识库管理、问答 |
| 查看者 | VIEWER | 只读查看和问答 |

完整权限列表：

| 权限编码 | 资源类型 | 说明 |
|----------|----------|------|
| USER:CREATE | USER | 创建用户 |
| USER:UPDATE | USER | 编辑用户 |
| USER:DELETE | USER | 删除用户 |
| USER:VIEW | USER | 查看用户 |
| DOCUMENT:UPLOAD | DOCUMENT | 上传文档 |
| DOCUMENT:DELETE | DOCUMENT | 删除文档 |
| DOCUMENT:VIEW | DOCUMENT | 查看文档 |
| REVIEW:APPROVE | REVIEW | 审核文档 |
| REVIEW:VIEW | REVIEW | 查看审核记录 |
| KB:CREATE | KB | 创建知识库 |
| KB:UPDATE | KB | 编辑知识库 |
| KB:DELETE | KB | 删除知识库 |
| KB:VIEW | KB | 查看知识库 |
| CONFIG:MANAGE | SYSTEM | 管理系统配置 |
| QA:ASK | QA | 使用问答 |
| QA:HISTORY | QA | 查看问答历史 |
| FEEDBACK:VIEW | FEEDBACK | 查看反馈 |
| FEEDBACK:HANDLE | FEEDBACK | 处理反馈 |

## 十二、Swagger使用指南

项目启动后访问：`http://localhost:8080/swagger-ui.html`

1. 先调用 `/api/auth/login` 获取 Token
2. 点击页面右上角 **Authorize** 按钮
3. 输入 `Bearer {你的token}`，点击 Authorize
4. 之后所有接口自动携带认证头，可直接在线调试

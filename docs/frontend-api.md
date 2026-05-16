# Knowflow Agent 前端接口说明

## 前端 Agent 生成提示词

请基于本文件下方的接口说明，生成一个完整可运行的 Vue 3 + TypeScript 前端项目，用于“企业知识库智能助手”后台管理与问答系统。前端应优先面向真实业务操作，而不是营销首页；首屏进入后就是工作台/应用界面。

技术建议：Vue 3、Vite、TypeScript、Pinia、Vue Router、Axios、Element Plus 或 Naive UI、Markdown 渲染组件。请按模块组织代码：`api`、`stores`、`router`、`views`、`components`、`types`、`utils`。所有后端类型、请求参数、返回结构都应从本文档抽象成 TypeScript interface/type，统一封装 `ApiResult<T>`、`PageResult<T>`、错误处理、loading 状态、分页参数和上传进度。

需要实现的完整页面与功能：

1. 登录/注册页：支持用户名密码注册、登录；保存后端返回的 token 和 user，但注意当前后端暂未强制鉴权。
2. 主布局：左侧导航、顶部状态栏、内容区。导航至少包含知识库、文档管理、聊天问答、索引任务、系统配置/说明。
3. 知识库管理：列表分页、关键词搜索、新建、编辑、删除、详情统计展示。状态枚举使用 `ACTIVE`、`DISABLED`。
4. 文档管理：按知识库筛选；上传 `.txt/.md/.pdf/.docx`；展示文档状态、文件类型、大小、切片数量、错误信息；支持删除、重新索引、查看文档切片；上传后根据 `jobId` 轮询索引任务直到成功或失败。
5. 索引任务：支持按文档查看最新任务，展示 `PENDING/RUNNING/SUCCESS/FAILED`、进度、开始/结束时间、错误信息。
6. RAG 检索调试页：输入知识库、query、topK、minScore，展示相似片段、分数、文档名、chunkId 和内容。
7. 聊天问答页：创建/切换会话；加载历史消息；发送普通问题；优先支持 SSE 流式消息接口，实时拼接 `message` 事件；完成后展示引用来源卡片；同时提供非流式发送作为 fallback。
8. Agent 问答入口：可复用聊天问答组件或提供单独调试面板，调用 `/api/agent/chat`。
9. 配置与联调说明页：展示后端地址、Docker 启动命令、AI Key 未配置时的占位回答说明、`vector.type=pgvector` 的提示。

交互与设计要求：

- 界面应是偏企业工具风格：信息密度适中、清晰、稳定、适合反复使用。
- 不要做落地页或大 Hero；直接做可用后台。
- 表格、表单、抽屉/弹窗、状态标签、进度条、上传组件、引用来源卡片、SSE 流式输出状态都要完整。
- 所有接口错误统一通过消息提示和页面状态展示；空状态要清楚说明下一步操作。
- 日期格式化、文件大小格式化、状态颜色映射、枚举 label 映射都要封装。
- API baseURL 默认 `http://localhost:8080`，支持通过 `.env` 配置 `VITE_API_BASE_URL`。
- 文档上传使用 `multipart/form-data` 字段名 `file`。
- SSE 接口不返回 `ApiResult`，要用原生 `fetch`/ReadableStream 或 EventSource polyfill 能力处理 POST SSE；事件包括 `userMessageId`、`message`、`assistantMessageId`、`citations`。
- 前端生成后请提供运行命令、目录结构说明，并确保 `npm install`、`npm run dev` 可用。

本文面向前端 Agent/页面开发，基于当前后端代码整理。

## 已有功能

- 用户注册、登录：返回用户信息和临时 token。当前 token 仅返回给前端保存，后端暂未做鉴权拦截。
- 知识库管理：创建、修改、删除、分页查询、详情查询。
- 文档管理：上传文档、查询文档列表、详情、删除、重新索引、查看切片。
- 文档处理：支持 `txt`、`md`、`pdf`、`docx`，上传后自动创建异步索引任务。
- 向量能力：`VectorStoreService` 已抽象，默认 `memory`；`vector.type=pgvector` 时使用 PostgreSQL pgvector。
- RAG：检索相似切片、问答并返回引用来源。
- 聊天：创建会话、会话分页、消息分页、普通问答、SSE 流式问答。
- Agent：当前是统一企业知识库助手门面，内部调用 RAG。
- 任务查询：查看索引任务详情、查看文档最新索引任务。
- OpenAPI：`/swagger-ui.html`。

## 通用返回

除 SSE 接口外，所有接口统一返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {},
  "timestamp": "2026-05-16T15:20:00"
}
```

分页结构：

```json
{
  "records": [],
  "total": 100,
  "page": 1,
  "size": 10
}
```

常见错误：

```json
{
  "code": "400",
  "message": "请求参数错误",
  "data": null,
  "timestamp": "2026-05-16T15:20:00"
}
```

错误码：`400` 参数错误，`404` 资源不存在，`460` 文件处理失败，`470` AI 服务失败，`480` 向量服务失败，`500` 系统异常。

## 枚举

```text
KnowledgeBaseStatus: ACTIVE, DISABLED
DocumentStatus: UPLOADED, PARSING, PARSED, INDEXING, INDEXED, FAILED, DELETED
FileType: TXT, MD, PDF, DOCX
IndexJobType: INDEX, REINDEX
IndexJobStatus: PENDING, RUNNING, SUCCESS, FAILED
ChatRole: USER, ASSISTANT, SYSTEM
```

## Auth

### 注册

`POST /api/auth/register`

请求：

```json
{
  "username": "admin",
  "password": "123456",
  "displayName": "管理员"
}
```

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "displayName": "管理员",
    "status": "ACTIVE"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 登录

`POST /api/auth/login`

请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "d4e1b7f1b5b64e5a9c6f8f1c2a3b4c5d",
    "user": {
      "id": 1,
      "username": "admin",
      "displayName": "管理员",
      "status": "ACTIVE"
    }
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

## 知识库

### 创建知识库

`POST /api/knowledge-bases`

请求：

```json
{
  "name": "企业制度库",
  "description": "公司制度、流程和报销文档"
}
```

返回 `KnowledgeBaseVO`：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1,
    "name": "企业制度库",
    "description": "公司制度、流程和报销文档",
    "status": "ACTIVE",
    "documentCount": 0,
    "chunkCount": 0,
    "createdAt": "2026-05-16T15:20:00",
    "updatedAt": "2026-05-16T15:20:00"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 分页查询知识库

`GET /api/knowledge-bases?page=1&size=10&keyword=制度`

参数：

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | query | 否 | 默认 `1` |
| size | query | 否 | 默认 `10` |
| keyword | query | 否 | 按名称模糊搜索 |

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "企业制度库",
        "description": "公司制度、流程和报销文档",
        "status": "ACTIVE",
        "documentCount": 2,
        "chunkCount": 18,
        "createdAt": "2026-05-16T15:20:00",
        "updatedAt": "2026-05-16T15:21:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 查询知识库详情

`GET /api/knowledge-bases/{id}`

返回：`KnowledgeBaseVO`。

### 更新知识库

`PUT /api/knowledge-bases/{id}`

请求：

```json
{
  "name": "企业知识库",
  "description": "更新后的描述",
  "status": "ACTIVE"
}
```

返回：`KnowledgeBaseVO`。

### 删除知识库

`DELETE /api/knowledge-bases/{id}`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": null,
  "timestamp": "2026-05-16T15:20:00"
}
```

## 文档

### 上传文档

`POST /api/knowledge-bases/{knowledgeBaseId}/documents/upload`

Content-Type: `multipart/form-data`

参数：

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| knowledgeBaseId | path | 是 | 知识库 ID |
| file | form-data | 是 | 支持 `.txt`、`.md`、`.pdf`、`.docx` |

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "documentId": 1,
    "jobId": 1001,
    "status": "UPLOADED"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

上传成功后后端会异步解析、切片、生成 embedding、写入向量库，并更新文档状态。

### 分页查询文档

`GET /api/knowledge-bases/{knowledgeBaseId}/documents?page=1&size=10&keyword=报销`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "knowledgeBaseId": 1,
        "fileName": "b1a2c3.pdf",
        "originalFileName": "差旅报销制度.pdf",
        "fileType": "PDF",
        "fileSize": 102400,
        "title": "差旅报销制度.pdf",
        "status": "INDEXED",
        "errorMessage": null,
        "chunkCount": 8,
        "createdAt": "2026-05-16T15:20:00",
        "updatedAt": "2026-05-16T15:21:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 查询文档详情

`GET /api/documents/{documentId}`

返回：`DocumentVO`。

### 删除文档

`DELETE /api/documents/{documentId}`

返回：空 `ApiResult`。

### 重新索引文档

`POST /api/documents/{documentId}/reindex`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "documentId": 1,
    "jobId": 1002,
    "status": "INDEXED"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 查询文档切片

`GET /api/documents/{documentId}/chunks?page=1&size=10`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "records": [
      {
        "id": 12,
        "documentId": 1,
        "chunkIndex": 3,
        "content": "差旅报销需提交真实有效发票、审批单、行程单...",
        "tokenCount": 42,
        "vectorId": "9f2a...",
        "createdAt": "2026-05-16T15:21:00"
      }
    ],
    "total": 8,
    "page": 1,
    "size": 10
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

## 索引任务

### 查询任务详情

`GET /api/index-jobs/{jobId}`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1001,
    "documentId": 1,
    "knowledgeBaseId": 1,
    "jobType": "INDEX",
    "status": "SUCCESS",
    "progress": 100,
    "errorMessage": null,
    "startedAt": "2026-05-16T15:20:01",
    "finishedAt": "2026-05-16T15:20:08",
    "createdAt": "2026-05-16T15:20:00",
    "updatedAt": "2026-05-16T15:20:08"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 查询文档最新任务

`GET /api/documents/{documentId}/index-job`

返回：`IndexJobVO`。

## RAG

### 检索相似片段

`POST /api/rag/search`

请求：

```json
{
  "knowledgeBaseId": 1,
  "query": "差旅报销需要哪些材料？",
  "topK": 5,
  "minScore": 0.65
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| knowledgeBaseId | 否 | 为空时不限定知识库 |
| query | 是 | 用户问题 |
| topK | 否 | 默认读取配置 `rag.top-k` |
| minScore | 否 | 默认读取配置 `rag.min-score` |

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "query": "差旅报销需要哪些材料？",
    "chunks": [
      {
        "documentId": 1,
        "documentName": "差旅报销制度.pdf",
        "chunkId": 12,
        "chunkIndex": 3,
        "content": "差旅报销需提交真实有效发票、审批单、行程单...",
        "score": 0.87
      }
    ]
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### RAG 问答

`POST /api/rag/ask`

请求：

```json
{
  "knowledgeBaseId": 1,
  "question": "差旅报销需要哪些材料？",
  "sessionId": 10
}
```

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "answer": "根据知识库内容，差旅报销通常需要提交真实有效发票、审批单和行程证明。",
    "citations": [
      {
        "documentId": 1,
        "documentName": "差旅报销制度.pdf",
        "chunkId": 12,
        "chunkIndex": 3,
        "contentPreview": "差旅报销需提交真实有效发票、审批单、行程单...",
        "score": 0.87
      }
    ]
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

注意：未配置 `AI_API_KEY` 时，后端会返回占位回答，检索和引用仍可联调。

## 聊天

### 创建会话

`POST /api/chat/sessions`

请求：

```json
{
  "knowledgeBaseId": 1,
  "title": "报销制度咨询"
}
```

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 10,
    "knowledgeBaseId": 1,
    "title": "报销制度咨询",
    "createdAt": "2026-05-16T15:20:00",
    "updatedAt": "2026-05-16T15:20:00"
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 会话分页

`GET /api/chat/sessions?knowledgeBaseId=1&page=1&size=10`

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| knowledgeBaseId | 否 | 按知识库过滤 |
| page | 否 | 默认 `1` |
| size | 否 | 默认 `10` |

返回：分页 `ChatSessionVO`。

### 会话详情

`GET /api/chat/sessions/{sessionId}`

返回：`ChatSessionVO`。

### 删除会话

`DELETE /api/chat/sessions/{sessionId}`

返回：空 `ApiResult`。

### 查询会话消息

`GET /api/chat/sessions/{sessionId}/messages?page=1&size=20`

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "records": [
      {
        "id": 101,
        "sessionId": 10,
        "role": "USER",
        "content": "差旅报销需要哪些材料？",
        "citationsJson": null,
        "createdAt": "2026-05-16T15:20:00"
      },
      {
        "id": 102,
        "sessionId": 10,
        "role": "ASSISTANT",
        "content": "根据知识库内容，需要提交发票、审批单和行程证明。",
        "citationsJson": "[{\"documentId\":1,\"documentName\":\"差旅报销制度.pdf\"}]",
        "createdAt": "2026-05-16T15:20:03"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 发送消息，非流式

`POST /api/chat/sessions/{sessionId}/messages`

请求：

```json
{
  "content": "差旅报销需要哪些材料？"
}
```

返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userMessageId": 101,
    "assistantMessageId": 102,
    "answer": "根据知识库内容，需要提交发票、审批单和行程证明。",
    "citations": [
      {
        "documentId": 1,
        "documentName": "差旅报销制度.pdf",
        "chunkId": 12,
        "chunkIndex": 3,
        "contentPreview": "差旅报销需提交真实有效发票、审批单、行程单...",
        "score": 0.87
      }
    ]
  },
  "timestamp": "2026-05-16T15:20:00"
}
```

### 发送消息，SSE 流式

`POST /api/chat/sessions/{sessionId}/messages/stream`

Content-Type: `application/json`  
Response Content-Type: `text/event-stream`

请求：

```json
{
  "content": "差旅报销需要哪些材料？"
}
```

事件序列：

```text
event: userMessageId
data: 101

event: message
data: "根据知识库内容，"

event: message
data: "需要提交发票、审批单和行程证明。"

event: assistantMessageId
data: 102

event: citations
data: [{"documentId":1,"documentName":"差旅报销制度.pdf","chunkId":12,"chunkIndex":3,"contentPreview":"...","score":0.87}]
```

前端建议：

- 收到 `message` 事件时增量拼接回答。
- 收到 `assistantMessageId` 后可标记回答完成。
- 收到 `citations` 后渲染来源卡片。
- SSE 接口不包 `ApiResult`。

## Agent

### Agent 问答

`POST /api/agent/chat`

请求：

```json
{
  "knowledgeBaseId": 1,
  "question": "差旅报销需要哪些材料？",
  "sessionId": 10
}
```

返回：同 `POST /api/rag/ask` 的 `RagAnswerVO`。

当前 Agent 是 RAG 门面，后续可以在 `agent.tools` 下扩展工具调用。

## 前端联调建议

1. 先 `docker compose up -d postgres`。
2. 启动后端主类 `KnowledgeAgentApplication`。
3. 创建知识库。
4. 上传文档，拿到 `jobId`。
5. 轮询 `/api/index-jobs/{jobId}`，直到 `status=SUCCESS`。
6. 创建聊天会话。
7. 使用普通消息接口或 SSE 接口发问。

如果使用 pgvector 检索，建议设置：

```yaml
vector:
  type: pgvector
```

或通过环境变量：

```bash
set VECTOR_TYPE=pgvector
```

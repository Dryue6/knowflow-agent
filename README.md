# Knowflow Agent

Knowflow Agent 是一个面向企业私有资料的知识库问答后端系统，采用 Java 17、Spring Boot 3、Spring Cloud Alibaba 和 Maven 多模块构建。项目围绕“文档入库、异步索引、混合检索、RAG 问答、聊天会话、Agent 门面”拆分微服务，并通过 Docker Compose 编排 PostgreSQL/pgvector、Redis、Nacos、Sentinel、Seata、MinIO、Ollama 和 OCR 服务，形成从企业文档上传到可溯源智能问答的完整链路。

## 技术栈

### 后端与微服务

- Java 17
- Spring Boot 3.3.5
- Spring Cloud 2023.0.4
- Spring Cloud Alibaba 2023.0.3.3
- Spring Cloud Gateway
- Spring Cloud OpenFeign
- Nacos Discovery / Config
- Sentinel
- Seata
- Maven 多模块
- MyBatis-Plus 3.5.7
- Spring Validation
- SpringDoc OpenAPI
- Lombok

### 数据、缓存与对象存储

- PostgreSQL 16
- pgvector
- Redis 7
- MinIO
- 本地文件存储 fallback

### AI、RAG 与文档处理

- LangChain4j 0.35.0
- Ollama
- qwen2.5:7b：本地查询改写与无上下文兜底回答
- qwen3-embedding:0.6b：本地 Embedding
- OpenAI-compatible Chat API：主回答模型接口，默认适配 DeepSeek 风格接口
- OpenAI-compatible Embedding API：可替换 Embedding 后端
- Apache PDFBox
- Apache POI
- OCR HTTP 服务
- SSE 流式回答

### 容器化与基础设施

- Docker Compose
- PostgreSQL + pgvector 初始化脚本
- MinIO Bucket 初始化
- 独立 Ollama Embedding 实例
- 独立 Ollama Query Rewrite 实例
- Python OCR Service

## 模块结构

```text
knowflow-agent
├── knowflow-common        # 通用响应、异常、配置、工具类、MyBatis/Jackson/OpenAPI 配置
├── knowflow-contract      # 微服务间 Feign Client、DTO、fallback factory
├── knowflow-gateway       # API 网关、路由转发、JWT 透传、Sentinel 接入
├── auth-service           # 注册、登录、用户认证
├── knowledge-service      # 知识库 CRUD、知识库统计刷新
├── document-service       # 文档上传、MinIO 直传、解析、OCR、切片、Embedding、向量入库
├── rag-service            # 查询改写、混合检索、rerank、Prompt 构造、模型调用、RAG 问答
├── chat-service           # 聊天会话、消息落库、历史记忆、SSE 流式转发
├── agent-service          # Agent 门面、知识库搜索工具、文档总结工具、当前时间工具
└── infra                  # 数据库初始化、Seata 配置、OCR 服务
```

## 已实现功能

### 认证与知识库

- 用户注册、登录
- 知识库创建、分页查询、详情、更新、删除
- 知识库文档统计刷新
- Gateway 统一路由到认证、知识库、文档、RAG、聊天和 Agent 服务

### 文档管理与索引

- 支持普通后端上传
- 支持 MinIO Presigned URL 前端直传
- 支持上传完成确认并创建异步索引任务
- 支持文档分页查询、详情、删除、重新索引
- 支持文档原文件预览、下载、解析文本预览
- 支持文档 chunk 分页查看
- 支持文档约束级别配置：普通资料、置顶资料、系统约束
- 支持 txt、md、pdf、docx 文档解析
- 支持 PDF/DOCX OCR 补充解析
- 支持 chunkSize + overlap 文本切片
- 支持 Embedding 批量生成
- 支持 pgvector 向量入库
- 支持删除文档时清理 chunk 和向量数据

### RAG 检索问答

- 支持知识库内 RAG 检索
- 支持非流式 RAG 问答
- 支持聊天接口 SSE 流式回答
- 支持 LangChain4j + Ollama 查询改写
- 支持 Redis 缓存查询改写结果
- 支持查询改写启动 warmup
- 支持 queryVariants 多查询变体向量召回
- 支持 phraseTerms、coreTerms、expandedTerms 关键词召回
- 支持关键词检索和 pgvector 向量检索混合召回
- 支持候选 chunk 合并去重
- 支持本地规则 rerank
- 支持固定上下文和相邻 chunk 补全
- 支持会话历史拼接到 Prompt
- 支持引用来源 citations 返回与落库
- 支持无可靠知识库上下文时回退本地模型兜底回答

### 聊天与 Agent

- 支持创建聊天会话
- 支持会话分页查询、详情、删除
- 支持消息分页查询
- 支持用户消息和助手消息持久化
- 支持短期会话历史记忆
- 支持 RAG SSE 双层流式转发
- 支持 Agent 门面调用知识库搜索、文档总结和当前时间工具

## 服务端口

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| Gateway | knowflow-gateway | 8080 | 对外统一入口 |
| auth-service | knowflow-auth | 18081 | 认证服务 |
| knowledge-service | knowflow-knowledge | 18082 | 知识库服务 |
| document-service | knowflow-document | 18083 | 文档与索引服务 |
| rag-service | knowflow-rag | 18084 | RAG 服务 |
| chat-service | knowflow-chat | 18085 | 聊天服务 |
| agent-service | knowflow-agent-service | 18086 | Agent 门面 |
| PostgreSQL/pgvector | knowflow-postgres | 5432 | 业务数据库和向量库 |
| Redis | knowflow-redis | 6379 | 查询改写缓存等 |
| MinIO API | knowflow-minio | 9000 | 对象存储 API |
| MinIO Console | knowflow-minio | 9001 | 对象存储控制台 |
| Nacos | knowflow-nacos | 8848 / 9848 | 服务发现与配置 |
| Sentinel Dashboard | knowflow-sentinel | 8858 | 限流监控 |
| Seata | knowflow-seata | 8091 / 7091 | 分布式事务 |
| Ollama Embedding | knowflow-ollama-embedding | 11434 | Embedding 模型 |
| Ollama Rewrite | knowflow-ollama-rewrite | 11435 -> 11434 | 查询改写模型 |

## 核心链路

### 文档入库链路

```text
前端选择文件
  -> document-service 生成 MinIO Presigned URL
  -> 浏览器直传 MinIO
  -> document-service 确认上传完成
  -> 创建文档记录和异步索引任务
  -> 解析 txt/md/pdf/docx
  -> 必要时调用 OCR 服务补充文本
  -> 按 chunkSize/overlap 切片
  -> 调用 Ollama/OpenAI-compatible Embedding
  -> 写入 document_chunk 和 document_vector
  -> 更新文档索引状态
```

### RAG 回答链路

```text
前端发送聊天消息
  -> chat-service 保存用户消息并读取最近历史
  -> rag-service 接收问题和历史
  -> QueryRewriteService 调用 Ollama qwen2.5:7b 生成查询改写结果
  -> document-service 关键词检索 phraseTerms/coreTerms/expandedTerms
  -> document-service 对 queryVariants 分别生成 embedding 并执行 pgvector 检索
  -> rag-service 合并关键词候选和向量候选
  -> LocalRerankService 执行本地规则重排
  -> 补充系统约束、置顶资料和相邻 chunk
  -> RagPromptBuilder 构造 Prompt
  -> 有知识库上下文时调用主 Chat Model
  -> 无可靠上下文时调用本地 Ollama fallback
  -> 返回 answer 和 citations
  -> chat-service 保存助手消息和引用来源
  -> 前端接收普通响应或 SSE 流式响应
```

## 运行前准备

### 1. 安装基础环境

- Docker Desktop
- Java 17
- Maven 3.9+
- Git

如果只使用 Docker Compose 启动全部后端服务，本机不强制要求安装 Maven；镜像构建阶段会使用 Maven 容器。

### 2. 准备模型

默认 Compose 会把 Ollama 模型目录挂载到 `D:/llm`：

```yaml
volumes:
  - D:/llm:/models
```

请确认本机存在该目录，或者按需修改 `docker-compose.yml` 中两个 Ollama 服务的模型目录挂载。

项目默认需要：

- `qwen3-embedding:0.6b`
- `qwen2.5:7b`

可以让 Compose 使用工具 profile 拉取模型：

```bash
docker compose --profile tools up ollama-pull
```

也可以手动进入对应 Ollama 容器拉取：

```bash
docker exec -it knowflow-ollama-embedding ollama pull qwen3-embedding:0.6b
docker exec -it knowflow-ollama-rewrite ollama pull qwen2.5:7b
```

### 3. 配置主回答模型

在项目根目录创建或修改 `.env`，至少配置主回答模型的地址、模型名和 API Key：

```env
AI_CHAT_BASE_URL=https://api.deepseek.com
AI_CHAT_API_KEY=你的APIKey
AI_CHAT_MODEL_NAME=deepseek-v4-flash
AI_CHAT_TEMPERATURE=0.2
AI_CHAT_TIMEOUT_SECONDS=60
```

如果希望主回答模型也走本地 Ollama，可以把 `AI_CHAT_BASE_URL` 指向可访问的 Ollama 地址，并将 `AI_CHAT_MODEL_NAME` 改成本地 chat 模型名。

## Docker Compose 全量启动流程

### 1. 启动基础设施

```bash
docker compose up -d postgres redis minio minio-init nacos sentinel-dashboard seata-server
```

等待数据库、Redis、Nacos 和 Seata 健康检查通过：

```bash
docker compose ps
```

### 2. 启动 AI 与 OCR 服务

```bash
docker compose up -d ollama-embedding ollama-rewrite ocr-service
```

确认 Ollama 容器可用：

```bash
docker exec -it knowflow-ollama-embedding ollama list
docker exec -it knowflow-ollama-rewrite ollama list
```

### 3. 构建并启动全部业务服务

```bash
docker compose up -d knowflow-gateway auth-service knowledge-service document-service rag-service chat-service agent-service
```

首次启动会构建各微服务镜像，耗时取决于 Maven 下载速度。

### 4. 查看服务状态

```bash
docker compose ps
```

所有核心服务应处于 `running` 或 `healthy` 状态。

### 5. 查看日志

```bash
docker compose logs -f knowflow-gateway
docker compose logs -f knowflow-document
docker compose logs -f knowflow-rag
docker compose logs -f knowflow-chat
```

### 6. 停止服务

```bash
docker compose down
```

如需同时删除数据库、Redis、Nacos 等持久化卷：

```bash
docker compose down -v
```

## 本地开发启动流程

### 1. 只启动依赖服务

```bash
docker compose up -d postgres redis minio minio-init nacos sentinel-dashboard seata-server ollama-embedding ollama-rewrite ocr-service
```

### 2. 编译项目

```bash
mvn clean package -DskipTests
```

### 3. 按依赖顺序启动微服务

建议在不同终端分别启动：

```bash
mvn -pl knowflow-gateway spring-boot:run
mvn -pl auth-service spring-boot:run
mvn -pl knowledge-service spring-boot:run
mvn -pl document-service spring-boot:run
mvn -pl rag-service spring-boot:run
mvn -pl chat-service spring-boot:run
mvn -pl agent-service spring-boot:run
```

本地直启时，各服务默认连接：

- Nacos：`localhost:8848`
- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- Ollama Embedding：`localhost:11434`
- Ollama Rewrite：`localhost:11434`
- MinIO：`localhost:9000`

如果同时使用 Compose 中的两个 Ollama 容器，需要注意 rewrite 容器映射到宿主机 `11435`，可通过环境变量覆盖：

```bash
set RAG_QUERY_REWRITE_BASE_URL=http://localhost:11435
```

PowerShell 下使用：

```powershell
$env:RAG_QUERY_REWRITE_BASE_URL="http://localhost:11435"
```

## 业务验证流程

下面示例均通过 Gateway `http://localhost:8080` 访问。

### 1. 注册用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo\",\"password\":\"demo123456\"}"
```

### 2. 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo\",\"password\":\"demo123456\"}"
```

### 3. 创建知识库

```bash
curl -X POST http://localhost:8080/api/knowledge-bases \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"企业制度库\",\"description\":\"用于测试 RAG 问答\"}"
```

### 4. 上传文档

普通后端上传：

```bash
curl -X POST http://localhost:8080/api/knowledge-bases/1/documents/upload \
  -F "file=@./example.pdf"
```

前端直传流程：

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents/upload-url
PUT  MinIO presignedUrl
POST /api/knowledge-bases/{knowledgeBaseId}/documents/complete-upload
```

### 5. 查询索引任务

```bash
curl http://localhost:8080/api/documents/1/index-job
```

等待文档状态进入已索引后再问答。

### 6. 创建聊天会话

```bash
curl -X POST http://localhost:8080/api/chat/sessions \
  -H "Content-Type: application/json" \
  -d "{\"knowledgeBaseId\":1,\"title\":\"制度问答\"}"
```

### 7. 非流式问答

```bash
curl -X POST http://localhost:8080/api/chat/sessions/1/messages \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"这份制度里的请假流程是什么？\"}"
```

### 8. SSE 流式问答

```bash
curl -N -X POST http://localhost:8080/api/chat/sessions/1/messages/stream \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"总结一下这份文档的核心要求\"}"
```

## 常用管理入口

- Gateway：`http://localhost:8080`
- MinIO Console：`http://localhost:9001`
- Nacos Console：`http://localhost:8848/nacos`
- Sentinel Dashboard：`http://localhost:8858`
- Seata Console：`http://localhost:7091`
- Adminer：`docker compose --profile tools up -d adminer` 后访问 `http://localhost:8081`

Adminer 默认连接信息：

- Server：`postgres`
- Username：`postgres`
- Password：`postgres`
- Database：`knowledge_agent`

## 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_CHAT_BASE_URL` | `https://api.deepseek.com` | 主回答模型接口地址 |
| `AI_CHAT_API_KEY` | 空 | 主回答模型 API Key |
| `AI_CHAT_MODEL_NAME` | `deepseek-v4-flash` | 主回答模型名 |
| `AI_EMBEDDING_BASE_URL` | `http://ollama-embedding:11434` | Embedding 模型地址 |
| `AI_EMBEDDING_MODEL_NAME` | `qwen3-embedding:0.6b` | Embedding 模型名 |
| `AI_EMBEDDING_DIMENSION` | `1024` | 向量维度 |
| `RAG_QUERY_REWRITE_BASE_URL` | `http://ollama-rewrite:11434` | 查询改写模型地址 |
| `RAG_QUERY_REWRITE_MODEL_NAME` | `qwen2.5:7b` | 查询改写模型名 |
| `RAG_TOP_K` | `5` | 最终返回 chunk 数 |
| `RAG_CANDIDATE_TOP_K` | `30` | 候选召回数量 |
| `RAG_FINAL_MIN_SCORE` | `0.6` | 最终上下文最低分 |
| `RAG_QUERY_REWRITE_CACHE_ENABLED` | `true` | 是否启用查询改写缓存 |
| `VECTOR_STORE_TYPE` | `pgvector` | 向量存储类型 |
| `MINIO_BUCKET` | `knowflow-documents` | 文档对象存储桶 |
| `OCR_ENABLED` | `true` in Compose | 是否启用 OCR |

## 常见问题

### 1. 文档上传成功但问答没有引用

先确认索引任务是否完成：

```bash
curl http://localhost:8080/api/documents/{documentId}/index-job
```

如果文档还在解析、OCR、Embedding 或入库阶段，RAG 暂时检索不到上下文。

### 2. 查询改写很慢

查询改写依赖本地 Ollama `qwen2.5:7b`，首次请求可能触发模型加载。可以确认 warmup 日志，或提前执行一次简单 RAG 请求让模型预热。

### 3. Embedding 检索结果异常

确认 embedding 模型存在：

```bash
docker exec -it knowflow-ollama-embedding ollama list
```

并确认 `AI_EMBEDDING_DIMENSION` 与数据库中的向量维度一致。当前项目默认是 `1024`。

### 4. SSE 有返回但引用最后才出现

这是当前设计：模型 token 会通过 `message` 事件实时返回，citations 在 RAG 完成后统一发送并随助手消息落库。

### 5. Compose 启动业务服务失败

先查看基础设施是否健康：

```bash
docker compose ps
```

再查看对应服务日志：

```bash
docker compose logs -f knowflow-rag
docker compose logs -f knowflow-document
```

常见原因包括模型未拉取、Nacos 未健康、PostgreSQL 初始化未完成、主回答模型 API Key 未配置。

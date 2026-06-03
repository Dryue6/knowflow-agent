# Knowflow 微服务整合说明

## 当前实现范围

本次把项目改造成 Spring Cloud Alibaba 练习型多模块工程，包含：

- `knowflow-common`：承载通用响应、错误码、异常、工具类和公共配置。
- `knowflow-contract`：存放 OpenFeign Client、服务间 DTO、索引事件对象。
- `knowflow-gateway`：Spring Cloud Gateway 统一入口，端口 `8080`。
- `auth-service`、`knowledge-service`、`document-service`、`rag-service`、`chat-service`、`agent-service`：独立 Spring Boot 服务入口。

当前阶段已完成业务包向各服务模块下沉，服务间同步调用通过 `knowflow-contract` 中的 OpenFeign 契约完成。

## 技术栈

- Spring Boot：`3.3.5`
- Spring Cloud：`2023.0.4`
- Spring Cloud Alibaba：`2023.0.3.3`
- Nacos：注册中心与配置中心
- Spring Cloud Gateway：统一入口和路由
- OpenFeign：服务间同步调用契约
- Sentinel：Gateway 入口限流与业务资源限流
- Seata：短数据库链路的 AT 分布式事务
- PostgreSQL + pgvector：关系数据和向量数据
- Redis：查询重写缓存
- Ollama：本地查询重写模型

## 模块说明

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| `knowflow-gateway` | `8080` | 统一路由、JWT payload 透传、Gateway Sentinel 限流 |
| `auth-service` | `18081` | 注册、登录、用户表 |
| `knowledge-service` | `18082` | 知识库 CRUD、内部知识库查询和统计更新接口 |
| `document-service` | `18083` | 文档上传、索引任务、解析切片、向量写入 |
| `rag-service` | `18084` | 查询重写、混合召回、RAG 问答 |
| `chat-service` | `18085` | 会话、消息、SSE 流式问答 |
| `agent-service` | `18086` | Agent 问答门面 |

## 数据库边界

`infra/db` 下提供多 schema 初始化脚本：

- `auth`：`user_account`
- `knowledge`：`knowledge_base`
- `document`：`document`、`document_chunk`、`document_vector`、`index_job`
- `chat`：`chat_session`、`chat_message`

每个 schema 都包含 Seata AT 模式需要的 `undo_log` 表。当前业务服务只配置自己的 schema，`rag-service` 和 `agent-service` 不再直接访问业务数据库。

## Sentinel 接入点

- Gateway 默认注册 `rag-api` 和 `chat-api` 两个 API 分组。
- 控制器核心入口增加 `@SentinelResource`：
  - `auth.register`
  - `auth.login`
  - `document.upload`
  - `document.reindex`
  - `rag.search`
  - `rag.ask`
  - `chat.message.send`
  - `chat.message.stream`

Sentinel Dashboard 默认端口为 `8858`，服务配置会从 Nacos 读取规则，Nacos 中的 dataId 形如 `{service-name}-sentinel-flow-rules`。

## Seata 接入点

`document-service` 的短数据库链路加了全局事务标记：

- `document-upload-tx`
- `document-delete-tx`

注意：文档解析、Ollama 调用、embedding 和向量索引属于长耗时或外部副作用流程，不放入 Seata 全局事务，失败时通过任务状态和幂等清理补偿。

## 启动方式

构建全部模块：

```powershell
mvn clean package -DskipTests
```

启动基础设施：

```powershell
docker compose up -d postgres redis nacos sentinel-dashboard seata-server ollama
```

拉取 Ollama 模型：

```powershell
docker compose --profile tools run --rm ollama-pull
```

启动所有微服务：

```powershell
docker compose up -d knowflow-gateway auth-service knowledge-service document-service rag-service chat-service agent-service
```

## Docker 镜像拉取兼容

服务镜像使用 `Dockerfile.service` 统一构建，默认构建镜像为 `maven:3.9-eclipse-temurin-17`，运行镜像为 `eclipse-temurin:17-jre`。如果 Docker Desktop 配置的镜像源拉取官方镜像失败，可以在启动前覆盖基础镜像：

```powershell
$env:MAVEN_IMAGE="maven:3.9-eclipse-temurin-17"
$env:RUNTIME_IMAGE="eclipse-temurin:17-jre"
docker compose build auth-service
```

如果错误中出现类似 `https://mirror.baidubce.com/... EOF`，说明失败点在 Docker 镜像源访问，不在项目编译。处理方式优先级：

1. 在 Docker Desktop 中移除或替换不可用的 registry mirror。
2. 手动执行 `docker pull maven:3.9-eclipse-temurin-17` 和 `docker pull eclipse-temurin:17-jre`，确认基础镜像可以拉取。
3. 如果有公司内网镜像仓库，可以把 `MAVEN_IMAGE`、`RUNTIME_IMAGE` 改成内网镜像地址后再执行 `docker compose build`。

## Knowflow 全面转为微服务的下一步计划

### Summary

当前项目已经完成“多模块外壳 + Nacos/Gateway/OpenFeign/Sentinel/Seata 基础接入”，但业务仍主要复用 `knowflow-core`，多个服务还通过扫描其他领域包来启动。下一步目标是把它推进到真正的微服务：每个服务只拥有自己的 controller/service/mapper/entity/schema，跨服务只通过 Gateway、OpenFeign、事件或明确的内部 API 通信。

默认实施顺序采用“先切编译边界，再切运行边界，再切事务和测试”的低风险路线。

### Key Changes

- 拆除 `knowflow-core` 的业务聚合职责：保留公共能力到 `knowflow-common`，业务包分别迁入 `auth-service`、`knowledge-service`、`document-service`、`rag-service`、`chat-service`、`agent-service`。
- 建立清晰依赖规则：业务服务只允许依赖 `knowflow-common` 和 `knowflow-contract`，禁止服务模块之间直接 Maven 依赖，禁止扫描其他服务的 mapper/entity/service。
- 补全 `knowflow-contract`：
  - `knowledge-service`：知识库存在性校验、统计增减、状态查询。
  - `document-service`：文档元信息、切片查询、向量候选查询、索引任务状态。
  - `rag-service`：RAG 搜索、普通问答、流式问答入口。
  - `auth-service`：用户认证后的用户上下文查询，仅内部使用。
- Gateway 正式承担统一入口：前端仍访问原 `/api/**` 路径，Gateway 做 JWT 验签、用户上下文透传、统一限流响应和路由转发。
- 数据边界彻底隔离：每个服务只连接自己的 schema；删除跨 schema 查询和外键；跨领域校验用 Feign，统计类冗余字段用服务 API 或事件更新。
- Seata 只保留在短数据库链路：文档元数据创建、索引任务创建、知识库统计更新使用 AT；文件落盘、解析、embedding、向量写入、模型调用全部移出全局事务并用任务状态补偿。
- Sentinel 规则从“注解占位”推进到可验证规则：Gateway 配置登录、上传、RAG、SSE 的入口限流；业务服务配置核心资源降级和 fallback；规则持久化到 Nacos。
- Docker Compose 从“能编排”推进到“能一键验收”：补齐服务健康检查、启动顺序、Nacos 初始化配置、Sentinel 规则初始化、Seata 配置说明。

### Implementation Changes

1. 模块拆分阶段：
   - 新建或重命名 `knowflow-common`，只放 `ApiResult`、`ErrorCode`、异常、通用配置、工具类、Jackson/MyBatis 公共配置。
   - 将 `knowflow-core` 中各业务包移动到对应服务模块，迁移后删除服务启动类中的跨领域 `scanBasePackages` 和跨领域 `@MapperScan`。
   - 每完成一个服务拆分，执行 `mvn -pl 服务模块 -am test`，确保该服务不再依赖其他业务包。

2. 契约改造阶段：
   - `document-service -> knowledge-service`：用 Feign 替换 `KnowledgeBaseMapper`、`KnowledgeBaseService` 直接调用。
   - `rag-service -> document-service`：用 Feign 替换 `DocumentMapper`、`DocumentChunkMapper`、`VectorStoreService` 直接调用。
   - `chat-service -> rag-service`：用 Feign 替换本地 `RagService` 和引用定位服务调用。
   - `agent-service -> rag-service`：第一版只保留门面转发，不再扫描 rag/document 包。
   - 所有 Feign Client 统一配置超时、关闭重试、启用 Sentinel fallbackFactory，并返回统一业务错误。

3. 数据与事务阶段：
   - 按 `auth`、`knowledge`、`document`、`chat` schema 分离 mapper 和 datasource 配置，每个服务只保留自己的 `currentSchema`。
   - `knowledge-service` 的统计更新改为显式 DTO 增量或重算接口，禁止直接查 document 表。
   - `document-service` 上传流程拆为：保存文件、开启 Seata 短事务创建 document/index_job 并调用 knowledge 统计、提交后触发异步索引。
   - 索引失败只更新 `index_job` 和 `document` 状态，重试前先清理旧 chunk/vector，保证幂等。

4. 网关与治理阶段：
   - Gateway 完成 JWT 正式验签，生成并透传 `X-User-Id`、`X-Username`，下游服务只信任来自网关的内部头。
   - Sentinel Gateway 配置统一 block response，业务服务补齐 blockHandler/fallback。
   - Nacos Config 拆为 `服务名-dev.yml`，把数据库、Redis、Ollama、Sentinel、Seata 配置从本地文件迁入配置中心，仓库保留本地 fallback 示例。

5. 容器与启动阶段：
   - 为每个服务补 actuator health endpoint，并在 Compose 中使用 healthcheck。
   - 增加 Nacos 配置导入说明或初始化脚本，确保本地首次启动不用手工复制大量配置。
   - 保持 Gateway 暴露 `8080`，其他服务端口仅用于调试；前端和外部调用只走 Gateway。

### Test Plan

- 编译边界测试：根目录执行 `mvn clean test`，并逐个执行 `mvn -pl auth-service,knowledge-service,document-service,rag-service,chat-service,agent-service -am test`。
- 依赖边界测试：检查服务模块不得 import 其他业务服务包；允许 import `knowflow-common`、`knowflow-contract` 和本服务包。
- Feign 测试：覆盖 knowledge、document、rag 的正常调用、超时、Sentinel fallback、下游 5xx。
- Gateway 测试：验证原 `/api/**` 路径不变，未登录请求被拒绝，登录后请求能透传用户上下文。
- Sentinel 测试：压测登录、上传、RAG 问答、SSE，确认限流返回统一 `ApiResult` 错误结构。
- Seata 测试：模拟文档创建成功后知识库统计更新失败，验证 document/index_job 回滚；模拟文件已落盘但事务失败，验证文件补偿删除。
- 全链路测试：注册登录、创建知识库、上传文档、索引成功、RAG 检索、普通聊天、SSE 聊天、Agent 问答全部通过 Gateway 完成。
- 容器测试：执行 `docker compose config --quiet`、构建全部服务镜像、启动基础设施和服务，确认所有服务注册到 Nacos 且健康检查通过。

### Assumptions

- Java 17、Spring Boot `3.3.5`、Spring Cloud `2023.0.4`、Spring Cloud Alibaba `2023.0.3.3` 保持不变。
- 本地练习环境继续使用 Docker Compose，不引入 Kubernetes。
- 初期仍使用单 PostgreSQL 实例多 schema，不做物理分库。
- 外部模型调用、embedding、文件解析、向量索引不进入 Seata 全局事务。
- 原前端 API 路径保持兼容，路由迁移由 Gateway 吸收。
- 中文注释规范继续适用：新增方法、接口、关键逻辑和复杂逻辑内部补充中文注释。

## 本次全面微服务改造记录

### 已完成改动

- Maven 构建链路已从 `knowflow-core` 切换为 `knowflow-common`，`knowflow-core` 不再作为父工程模块参与构建。
- `knowflow-common` 承载通用响应、错误码、异常、工具类、公共配置和基础依赖；业务服务只依赖 `knowflow-common` 与 `knowflow-contract`。
- `auth-service`、`knowledge-service`、`document-service`、`rag-service`、`chat-service`、`agent-service` 已拥有各自业务源码目录，不再通过 `knowflow-core` 复用业务代码。
- 服务启动类扫描范围已收窄：
  - `knowledge-service` 只扫描 knowledge 领域 mapper 和业务包。
  - `document-service` 只扫描 document/job/storage 领域 mapper 和业务包。
  - `rag-service` 不再扫描 document/storage mapper 和业务包。
  - `chat-service` 不再扫描 rag/document/storage mapper 和业务包。
  - `agent-service` 不再扫描 rag/document/storage mapper 和业务包。
- `knowflow-contract` 已补齐独立的跨服务 DTO 和 Feign Client：
  - `KnowledgeClient`：知识库详情、统计更新。
  - `DocumentClient`：向量召回、关键词召回、固定上下文查询。
  - `RagClient`：内部 RAG 检索、内部 RAG 非流式问答。
- `document-service -> knowledge-service` 已改为通过 Feign 更新知识库统计，不再直接注入 `KnowledgeBaseMapper` 或 `KnowledgeBaseService`。
- `rag-service -> document-service` 已改为通过 Feign 获取向量候选、关键词候选和固定上下文，不再直接访问 document mapper 或向量库。
- `chat-service -> rag-service` 已改为通过 Feign 调用 RAG 问答，不再直接注入 `RagService` 或引用定位服务。
- `agent-service -> rag-service` 已改为通过 Feign 调用 RAG 问答和检索，不再扫描 rag/document 包。
- 数据库 schema 边界已收窄：
  - `knowledge-service` 使用 `currentSchema=knowledge`。
  - `document-service` 使用 `currentSchema=document`。
  - `chat-service` 使用 `currentSchema=chat`。
  - `rag-service` 和 `agent-service` 不再配置业务 datasource，并关闭本服务 Seata。
- `document-service` 新增内部检索接口，由文档服务集中持有 document 表、embedding 和 pgvector 访问权限。
- `rag-service` 新增内部 RAG 接口，使用 contract DTO 向 chat/agent 暴露服务间调用能力。
- Gateway 和业务服务已接入 actuator 依赖，为后续容器健康检查和监控打基础。

### 当前行为说明

- 前端原有 `/api/**` 路径仍由 Gateway 路由到对应服务。
- RAG 对外公开接口仍保留原 DTO/VO，服务间调用使用 `knowflow-contract` 的独立 DTO。
- `chat-service` 的 SSE 接口仍保持 SSE 响应协议；当前内部改为 Feign 调用 RAG 非流式问答后，将完整答案作为一次 message 事件发送。后续如需真正逐 token 流式，需要继续增加 RAG 内部流式接口。
- 引用来源当前由 RAG 基于召回切片生成基础 citation；页码、章节、段落等富化信息后续可继续下沉到 `document-service` 内部接口。

### 验证结果

- 已执行 `mvn test`，Reactor 全部模块通过。
- 已执行 `docker compose config --quiet`，Compose 配置校验通过。
- 已执行服务模块跨域 import 扫描，当前业务服务未发现直接 import 其他服务业务包；允许的跨服务交互已迁移到 `knowflow-contract`。

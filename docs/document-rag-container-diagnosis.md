# 文档预览与问答依据问题排查记录

[2026-05-23] 修复 Docker 环境中文档内容访问失败与问答无依据
- 修改内容：定位到文档详情接口本身可用，但文档预览和原文件接口失败的原因是数据库 `document.document.file_path` 中保存的是早期宿主机 Windows 路径，例如 `D:\code\knowflow-agent\data\uploads\...`，而 Docker 容器只能访问挂载到 `/app/data/uploads` 的路径。已将 `document-service` 的上传目录配置为 `${STORAGE_LOCAL_BASE_PATH:/app/data/uploads}`，并把 Docker Compose 中的上传目录从 named volume 改为 `./data/uploads:/app/data/uploads`，让容器能直接读取当前项目下已有的上传文件。同时在 `DocumentServiceImpl` 中增加旧路径兼容逻辑：原路径不存在时，会把旧路径中 `data/uploads` 之后的相对路径映射到当前配置的上传根目录。
- 修改内容：定位到问答服务总是回复“当前知识库中没有找到可靠依据”的原因是微服务拆分后 `rag-service`、`chat-service`、`document-service` 没有完整继承原单体应用的 `rag.*` 配置；当 Nacos 配置缺少这些字段时，`topK`、`maxContextChunks`、`systemConstraintMaxChunks` 等 primitive 配置会绑定为 0，导致 RAG 问答构建 Prompt 时没有检索上下文和固定上下文。已在 `RagProperties` 中补充业务默认值，并在相关微服务配置中补齐 `rag.*` 默认配置。
- 修改内容：为 `document-service` 补齐 `vector.type=${VECTOR_STORE_TYPE:pgvector}`，避免容器重启后因缺省配置退回空的内存向量库，导致历史 `document_vector` 表数据无法参与向量检索。
- 修改内容：验证结果包括：`mvn -pl knowflow-common,document-service,rag-service,chat-service -am -DskipTests compile` 成功，`docker compose config --quiet` 成功；重建并启动 `document-service`、`rag-service`、`chat-service`、`knowflow-gateway` 后，`/api/documents/43/preview-text` 和 `/api/documents/43/file` 均返回 200，`/api/chat/sessions/{id}/messages` 对“你是什么”返回了答案和非空引用来源。
- 注意事项：Nacos 中当前 `document-service-dev.yml`、`rag-service-dev.yml` 只看到了访问日志相关配置，且 `document-service-dev.yml` 内容疑似误写成 `auth-service` 的日志前缀。虽然本次通过本地配置和默认值兜底恢复了服务，但后续应把 Nacos 中对应 dataId 的配置整理成各服务自己的完整配置，至少包含 `storage.local.base-path`、`vector.type`、`rag.*` 和 `ai.*`。
- 注意事项：如果以后迁移上传文件目录，不建议直接修改数据库中的 `file_path` 为宿主机绝对路径。Docker 环境应统一保存容器内路径或保存相对路径，并通过固定挂载目录访问文件。
- 注意事项：如果问答再次出现“没有具体依据”，优先检查 `rag-service` 启动后的实际配置是否存在 `rag.max-context-chunks > 0`、`rag.system-constraint-max-chunks > 0`、`rag.top-k > 0`，再检查 `document-service` 的 `/internal/documents/search/keyword` 与 `/internal/documents/context/fixed` 是否能返回切片。

[2026-05-23] 旧文档路径批量迁移与删除链路验证
- 修改内容：在执行迁移前创建备份表 `document.document_file_path_backup_20260523`，保存当前 `document.document` 表中 43 条文档的 `id` 和原始 `file_path`，用于回溯迁移前路径。
- 修改内容：执行一次性 SQL 修复，将 `document.document.file_path` 中的宿主机 Windows 路径统一转换为容器内路径 `/app/data/uploads/...`。迁移后校验 `file_path like 'D:%'` 或包含反斜杠的记录数为 0。
- 修改内容：保留并加强旧路径兼容逻辑。`LocalFileStorageService.delete` 删除文件时会同时尝试原始路径和按当前 `storage.local.base-path` 重建出的候选路径，避免后续仍有漏网旧路径时无法清理物理文件。
- 修改内容：修复 `PgVectorStoreService` 中 pgvector 类型解析问题。由于文档服务连接串使用 `currentSchema=document`，而 `vector` 扩展安装在 `public` schema，SQL 中已改为显式使用 `public.vector`，避免上传索引时报 `type "vector" does not exist`。
- 修改内容：验证 `GET /api/documents/43/preview-text` 和 `GET /api/documents/43/file` 均可通过迁移后的 `/app/data/uploads/...` 路径返回 200。另上传测试文档 45，临时模拟旧 Windows 路径后再次执行迁移表达式，确认迁移后预览和原文件接口仍返回 200。
- 修改内容：验证删除链路。对测试文档 45 执行 `DELETE /api/documents/45` 后接口返回成功，数据库状态变为 `DELETED`，`document_chunk` 与 `document_vector` 中对应记录为 0，容器内 `/app/data/uploads/...` 对应物理文件已删除。
- 注意事项：后续上传文件不应再保存宿主机绝对路径，Docker 环境应由 `STORAGE_LOCAL_BASE_PATH=/app/data/uploads` 控制落盘位置，并保持 `./data/uploads:/app/data/uploads` 挂载不变。
- 注意事项：如果以后新增 PostgreSQL schema 或调整 `currentSchema`，涉及扩展类型的 SQL 建议使用 schema 限定名，例如 `public.vector`，避免 search_path 不一致导致运行期类型解析失败。
- 注意事项：删除接口当前会软删除文档并清理切片、向量和物理文件；`document.chunk_count` 字段不会在软删除时重置为 0，列表接口已过滤 `DELETED` 状态，排查删除结果时应以状态和关联表实际记录数为准。

[2026-05-23] Chat 无相关文档时改用本地模型回答
- 修改内容：定位到 chat 服务无法在无相关文档时使用本地模型回答的主要原因有三点：RAG Prompt 固定要求“只能依据上下文回答”，无命中时会诱导模型回复“没有可靠依据”；`OpenAiChatModelService` 在 `ai.chat.api-key` 为空时直接返回占位文案，没有调用本地 Ollama；Docker 环境中 `rag-service` 会继承宿主机 `AI_CHAT_*` 配置，导致默认仍指向外部模型。
- 修改内容：调整 `RagPromptBuilder`，当普通检索资料和固定资料都为空时，进入通用回答 Prompt，允许使用本地模型能力回答，并要求不要声称答案来自知识库、网络资料或其他外部来源，也不要编造引用。
- 修改内容：调整 `RagServiceImpl` 的引用生成逻辑。普通资料和固定资料都未命中时，系统约束只作为内部规则，不再作为答案依据返回给前端，最终 `citations` 返回空数组。
- 修改内容：扩展 `OpenAiChatModelService`，当 `ai.chat.base-url` 指向 Ollama 或 11434 端口时，调用 Ollama 原生 `/api/chat` 接口；外部 OpenAI-compatible 服务仍保留 `/chat/completions` 调用方式。
- 修改内容：将 `rag-service` 默认聊天模型配置改为本地 Ollama：`AI_CHAT_BASE_URL=http://ollama:11434`、`AI_CHAT_MODEL_NAME=qwen2.5:7b`，并在 `docker-compose.yml` 中为 `rag-service` 增加 `RAG_AI_CHAT_*` 覆盖入口，避免宿主机通用 `AI_CHAT_*` 干扰本地模型部署。
- 修改内容：发现 chat-service 调用 RAG 的 Feign 默认 `readTimeout=10000` 不足以等待本地 Ollama 生成，已将 `chat-service` 的 Feign 读取超时改为 `${FEIGN_READ_TIMEOUT:120000}`，并在 `docker-compose.yml` 中为 chat-service 增加 `CHAT_FEIGN_READ_TIMEOUT` 覆盖入口。
- 修改内容：补充修复 document-service 的 pgvector 查询兼容问题。由于连接串使用 `currentSchema=document`，而 pgvector 类型和 `<=>` 操作符位于 `public` schema，向量 SQL 改为 `CAST(? AS public.vector)` 与 `OPERATOR(public.<=>)`，并将 `minScore` 过滤移到 Java 层，避免 search_path 导致向量召回报 `bad SQL grammar`。
- 修改内容：验证结果包括：`mvn -pl rag-service -am -DskipTests compile`、`mvn -pl document-service -am -DskipTests compile`、`mvn -pl chat-service -am -DskipTests compile` 均通过；重建相关容器后，`/api/chat/sessions/{sessionId}/messages` 对知识库无关问题可以返回本地模型生成的通用回答，且 `citations` 为空。
- 注意事项：本次用户确认“这种程度足够”，因此未继续追求最终模型措辞完全去除“根据网络资料”等表达；后续如需更严格，可继续收紧无上下文 Prompt 或在响应后处理阶段过滤来源性短语。
- 注意事项：本地 Ollama 首次生成可能较慢，chat-service 调 RAG 的 Feign 超时时间需要大于本地模型最长生成时间；若后续模型换成更大的权重，建议同步提高 `CHAT_FEIGN_READ_TIMEOUT` 或改造为真正流式跨服务调用。
- 注意事项：以后排查“无文档时不能回答”应先分辨是业务 Prompt 拦截、模型配置未指向本地 Ollama、还是 Feign 超时触发降级；三者的表现分别是“没有可靠依据”、占位配置提示、以及“RAG 问答服务不可用/Read timed out”。

[2026-05-24] 改造 Chat 与 RAG 真流式返回
- 修改内容：定位到前端虽然使用 SSE，但 chat-service 仍通过 Feign 调用 RAG 非流式 `/internal/rag/ask`，RAG 的 `streamChat` 也只是先等待完整模型回答再切片，导致 DeepSeek 完整回答超过超时时间时仍会出现 `Chat API 调用失败: request timed out`。本次为 rag-service 增加内部流式接口 `/internal/rag/ask/stream`，由 RAG 直接按 `message` 事件推送模型增量，结束时发送 `citations`，异常时发送 `error`。
- 修改内容：将 `OpenAiChatModelService.streamChat` 改为真实流式调用。DeepSeek/OpenAI-compatible 接口使用 `/chat/completions` 的 `stream: true` 并解析 `choices[0].delta.content`；Ollama 接口使用 `/api/chat` 的 `stream: true` 并解析每行 JSON 中的 `message.content`。非流式 `chat` 保留，兼容普通问答接口。
- 修改内容：将 chat-service 的 `/api/chat/sessions/{id}/messages/stream` 改为通过 Java `HttpClient` 直连 RAG 内部 SSE 地址，先向前端发送 `userMessageId`，随后收到 RAG 的 `message` 就立即转发并累积完整答案，收到 `citations` 后再保存助手消息并发送 `assistantMessageId` 与 `citations`。新增 `chat.rag-stream-base-url` 配置，并在 Docker 中设置 `RAG_STREAM_BASE_URL=http://rag-service:18084`。
- 修改内容：本地无知识库命中兜底模型也改为 Ollama 真流式返回，避免无资料问题仍等待 qwen2.5 完整生成后才给前端响应。
- 修改内容：验证结果包括：`mvn -pl rag-service,chat-service -am -DskipTests compile` 成功，`docker compose config --quiet` 成功，`docker compose up -d --build rag-service chat-service` 成功。测试无知识库命中问题时，chat SSE 先返回 `userMessageId`，随后返回 20 个 `message` 增量事件，最后返回 `assistantMessageId` 和空 `citations`。测试知识库内 UML 问题时，chat SSE 返回 55 个 `message` 增量事件、1 个 `assistantMessageId` 和 1 个 `citations` 事件。
- 注意事项：流式链路解决的是模型生成阶段等待完整回答导致的超时和体验问题；检索阶段仍需先完成关键词、向量和固定上下文查询，首个模型 token 会在检索完成后出现。如果后续仍觉得首包慢，应继续优化向量召回耗时或在 SSE 中增加前端可识别的检索状态事件。
- 注意事项：Windows PowerShell 中使用 `curl.exe` 验证本地服务时需加 `--noproxy "*"`，否则本机代理可能拦截 `localhost` 请求并返回 502，造成误判。

[2026-05-24] 引用来源增加相似度字段
- 修改内容：为跨服务引用 DTO `CitationItem` 增加 `score` 字段，并在 rag-service 的引用转换逻辑中从 `CitationVO.score` 透传到 chat-service。这样非流式回答、流式 SSE 的 `citations` 事件、以及 chat-service 保存到数据库的 `citations_json` 都会包含每个引用切片的相似度/重排分数，前端可直接展示。
- 修改内容：验证结果包括：`mvn -pl knowflow-contract,rag-service,chat-service -am -DskipTests compile` 成功，`docker compose up -d --build rag-service chat-service` 成功。使用知识库内 UML 问题测试 chat SSE，最终 `citations` 事件包含 `score` 字段，示例值包括 `1.0`、`0.93`、`0.665`。
- 注意事项：历史聊天消息中旧的 `citations_json` 可能没有 `score` 字段，前端展示时应允许该字段为空；新生成的引用会统一带上 `score`。

[2026-05-25] Docker 接入 MinIO 作为文档上传存储
- 修改内容：在 `docker-compose.yml` 中新增 `minio` 与 `minio-init` 服务，MinIO 数据目录挂载到现有 `./data/uploads:/data`，并由 `minio-init` 创建默认 bucket `knowflow-documents`。`document-service` 现在依赖 MinIO 初始化完成后启动，并通过 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`MINIO_SECURE` 注入对象存储配置。
- 修改内容：为 `document-service` 增加 MinIO Java SDK 依赖、新增 `storage.minio` 配置和 `MinioFileStorageService`。新上传文件按 `{knowledgeBaseId}/{yyyy-MM-dd}/{uuid}.{ext}` 写入 MinIO，数据库 `document.document.file_path` 保存为 `minio://bucket/objectKey`，不再保存宿主机或容器本地路径。
- 修改内容：扩展 `FileStorageService`，下载/预览通过 `loadAsResource` 读取对象流，索引和 DOCX/TXT/MD 预览通过 `materialize` 临时物化为本地文件后解析，并在关闭时清理临时文件，避免业务层直接 `Path.of(filePath)` 读取 MinIO URI。
- 注意事项：本次按确认不保留旧本地文件兼容逻辑，旧 `D:\code\knowflow-agent\data\uploads\...` 或 `/app/data/uploads/...` 路径记录需要手动清理或重新上传后才能使用新的 MinIO 链路。
- 注意事项：由于 MinIO 的 `/data` 直接使用 `./data/uploads`，该目录后续会由 MinIO 管理对象数据和元信息，不应再作为普通本地上传目录混用。

[2026-05-25] MinIO 文档上传链路验证
- 修改内容：验证结果包括：`docker compose config --quiet` 成功，`mvn -pl knowflow-common,document-service -am -DskipTests compile` 成功，`docker compose up -d --build document-service` 成功，`document-service` 使用新镜像启动并监听 18083。
- 修改内容：上传临时 TXT 文档 47 后，数据库 `document.document.file_path` 保存为 `minio://knowflow-documents/3/2026-05-25/...txt`，MinIO bucket 中可看到对应对象；`GET /api/documents/47/preview-text` 返回文本内容，`GET /api/documents/47/file` 返回 200 且 `Content-Length` 正确。
- 修改内容：执行 `DELETE /api/documents/47` 后，文档状态变为 `DELETED`，`document_chunk` 与 `document_vector` 中对应记录均为 0，MinIO bucket 中测试对象已删除。
- 注意事项：首次 Docker 构建时 Maven 容器曾因 `repo.maven.apache.org` 临时 DNS 解析失败无法下载 MinIO 依赖；重试后构建成功，属于构建网络瞬断，不是代码编译问题。

[2026-05-25] MinIO Presigned URL 前端直传
- 修改内容：新增前端直传接口 `POST /api/knowledge-bases/{knowledgeBaseId}/documents/upload-url` 与 `POST /api/knowledge-bases/{knowledgeBaseId}/documents/complete-upload`。前者校验知识库、文件类型和文件大小后生成浏览器可访问的短期 MinIO PUT URL，后者校验对象 key 归属、对象存在性和大小后创建 `document` 与 `index_job`。
- 修改内容：新增 `storage.minio.public-endpoint` 与 `MINIO_PUBLIC_ENDPOINT`，用于生成面向浏览器的 Presigned URL；容器内访问仍使用 `MINIO_ENDPOINT=http://minio:9000`。MinIO 服务通过 `MINIO_API_CORS_ALLOW_ORIGIN` 允许前端开发域名发起直传 PUT。
- 修改内容：前端 `docApi.upload` 已改为三段式：申请直传地址、使用原生 `fetch` 直接 PUT 到 MinIO、再调用完成确认接口。旧 multipart 上传接口保留，作为兼容兜底。
- 修改内容：前端 mock server 补齐 `upload-url`、mock PUT 和 `complete-upload`，保证 mock 模式下上传交互仍可用。
- 修改内容：验证结果包括：`docker compose config --quiet`、`mvn -pl knowflow-common,document-service -am -DskipTests compile`、前端 `npm run lint`、前端 `npm run build` 均成功。重建 `document-service` 后，申请到的 `uploadUrl` 为 `http://localhost:9000/...`，CORS 预检返回 204 且允许 `PUT`，直传 TXT 文档 48 后数据库 `file_path` 为 `minio://knowflow-documents/...`，预览和原文件接口返回 200，删除后 `document_chunk` 与 `document_vector` 对应记录均为 0，MinIO 对象无残留。
- 注意事项：MinIO 当前镜像对 `mc cors set` 返回 501，因此 CORS 采用服务端环境变量 `MINIO_API_CORS_ALLOW_ORIGIN` 配置；如前端运行域名变化，需要同步调整 `MINIO_CORS_ALLOWED_ORIGIN`。

[2026-05-25] 修复 MinIO 直传本地前端 CORS 来源
- 修改内容：定位到浏览器从 `http://localhost:3000` 直传 MinIO 时预检失败的原因是 `MINIO_API_CORS_ALLOW_ORIGIN` 仅允许 `http://localhost:5173`。已将 `MINIO_CORS_ALLOWED_ORIGIN` 调整为 `http://localhost:3000,http://localhost:5173`，并同步更新 Docker Compose 默认值，使本地 Vite/Node 开发端口都能发起 Presigned PUT。
- 注意事项：MinIO CORS 配置来自服务启动环境变量，修改后必须重建或重启 `minio` 容器才会生效；如果以后前端端口或域名变化，需要继续把新 Origin 加入 `MINIO_CORS_ALLOWED_ORIGIN`。

[2026-05-25] 排查 MinIO 直传仍无法上传
- 修改内容：确认 `minio-init` 为一次性初始化容器，成功创建 bucket 后 `Exited (0)` 属于正常状态，不需要长期运行。当前 `knowflow-minio` 保持运行，`MINIO_API_CORS_ALLOW_ORIGIN` 已包含 `http://localhost:3000,http://localhost:5173`。
- 修改内容：复现并定位到本地前端 `server.ts` 的 `/api` 代理会把 `Expect` 请求头转发给 Node 内置 `fetch`，触发 Undici `NotSupportedError: expect header not supported`，导致申请直传 URL 时返回 `BAD_GATEWAY`。已在前端代理中过滤 `expect` 头，避免上传前置接口被本地代理拦截失败。
- 修改内容：重启 `http://localhost:3000` 前端开发服务后，完整验证 `localhost:3000/api -> knowflow-gateway -> document-service -> MinIO` 链路：`upload-url` 返回 200，MinIO 预检返回 204 且允许 `PUT`，PUT 返回 200，`complete-upload` 返回 `documentId=49/jobId=67/status=UPLOADED`。随后删除测试文档并清理诊断产生的孤立 MinIO 对象。
- 注意事项：浏览器如果仍看到旧的 MinIO CORS 报错，应刷新页面后重新上传，确保使用重启后生成的新 Presigned URL；旧签名 URL 不建议复用。
- 注意事项：本地 `server.ts` 代理只负责 `/api` 请求，文件直传仍应直接请求 `http://localhost:9000/...`，Network 中看到 PUT 目标为 `/api` 说明前端没有走直传实现或页面资源未刷新。

[2026-05-25] 补齐 127.0.0.1 前端来源的 MinIO CORS
- 修改内容：定位到 `upload-url` 返回 success 但浏览器仍上传失败的一个原因是页面可能从 `http://127.0.0.1:3000` 打开，而 MinIO CORS 只允许 `http://localhost:3000` 和 `http://localhost:5173`。这种情况下后端签名接口会成功，但浏览器对 `http://localhost:9000` 的 PUT 预检拿不到 `Access-Control-Allow-Origin`，因此上传被浏览器拦截。
- 修改内容：将 `.env` 与 `docker-compose.yml` 中的 `MINIO_CORS_ALLOWED_ORIGIN` 默认值扩展为 `http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173`，并重启 `minio` 与 `minio-init` 使服务端 CORS 环境变量生效。
- 修改内容：验证结果包括：`docker compose config --quiet` 成功；`Origin: http://localhost:3000` 与 `Origin: http://127.0.0.1:3000` 的 MinIO 预检均返回 204 且带对应 `Access-Control-Allow-Origin`；使用 `127.0.0.1:3000` Origin 执行 PUT 返回 200，随后 `complete-upload` 成功创建测试文档 52，并已删除测试文档。
- 注意事项：MinIO CORS 按 Origin 精确匹配，`localhost`、`127.0.0.1`、局域网 IP、域名都算不同来源；如果前端换成其他访问地址，需要继续追加对应 Origin 并重启 MinIO。

[2026-05-25] 修复 complete-upload 超时和索引回滚
- 修改内容：定位到前端 `AxiosError: timeout of 10000ms exceeded` 和 `complete-upload` 返回 500 的原因有两层：document-service 的 `@Async` 未启用，导致索引实际同步执行在上传确认请求内；同时上传确认事务内同步刷新 knowledge-service 统计，遇到 Seata `knowledge_base:3` 全局锁冲突时会把整个确认上传回滚。
- 修改内容：在 document-service 启动类启用 `@EnableAsync`，使 `DocumentIndexJobServiceImpl.indexDocumentAsync` 真正进入后台线程执行，上传确认接口只负责校验 MinIO 对象、创建 `document` 与 `index_job`。
- 修改内容：从文档创建主流程移除同步知识库统计刷新，并将上传、删除、索引完成后的统计同步调整为尽力而为：统计失败只记录告警，不再回滚文档主流程或把已完成索引标记为失败。
- 修改内容：验证结果包括：`mvn -pl knowflow-common,document-service -am -DskipTests compile` 成功，`docker compose up -d --build document-service` 成功；测试直传文档时 `complete-upload` 约 538ms 返回 `code=0`，返回 `documentId=61/jobId=85/status=UPLOADED`，2 秒后索引任务可查询到 `RUNNING` 状态，测试文档随后已删除。
- 注意事项：索引现在是后台任务，前端上传完成后应以文档状态或 `/api/documents/{id}/index-job` 轮询展示解析进度；`complete-upload` 返回成功不代表索引已经完成。
- 注意事项：当前 knowledge-service 曾出现 Seata 脏 undo log / 全局锁冲突，统计字段可能短时间滞后；文档列表、切片和向量以 document-service 自有表为准。

[2026-05-26 11:51:07] 修改摘要
- 修改内容：为 document-service 增加图片 OCR 文档解析能力：新增 OCR 配置、HTTP OCR 适配层和本地 FastAPI/RapidOCR sidecar；PDF 解析在低文本页渲染页面图片并追加 OCR 文本，DOCX 解析会识别内嵌图片并以固定 `【图片OCR...】` 前缀追加到解析文本；OCR 结果通过 `ParsedDocument.metadata` 写入 `ocrItems`、`ocrEnabled`、`ocrFailedCount`，后续切片、embedding、向量写入和 RAG 召回继续复用原链路。
- 注意事项：第一版只识别 PDF/DOCX 内图片，不新增 jpg/png 上传文档类型；OCR 服务不可用、超时、低置信度或空结果时会降级为无 OCR 文本并继续索引，Docker 环境默认启用 `ocr-service`，本地直跑可通过 `OCR_ENABLED=false` 关闭。

[2026-05-26 12:08:04] 修改摘要
- 修改内容：优化 DOCX 图片 OCR 解析链路，新增结构化图片提取器和图片归一化组件，按正文段落、表格、页眉、页脚提取图片并按内容 hash 去重；DOCX OCR 文本前缀升级为 `【图片OCR DOCX 第 N 张 / 位置】`，并在 `ParsedDocument.metadata` 中补充 `docxImageCount`、`docxOcrAttemptCount`、`docxOcrSuccessCount`、`docxUnsupportedImageCount`、`ocrFailures` 等排障字段。同步增强 `HttpOcrService` 日志，记录图片字节数、耗时、置信度和失败原因。
- 注意事项：本地直跑 document-service 时仍需设置 `OCR_ENABLED=true` 并保证 `OCR_BASE_URL` 可访问 OCR sidecar；无效图片、空白图片、超限图片、低置信度或 OCR 服务异常都只会记录到 metadata/log，不会中断文档索引。

[2026-05-26 13:56:29] 修改摘要
- 修改内容：修复 Word/DOCX 基础图片 OCR 不生效的运行时根因。OCR sidecar 原 `/health` 只验证 HTTP 进程存活，未加载 RapidOCR，导致容器显示 healthy 但 `/ocr` 因缺少 `libxcb.so.1` 等 OpenCV 运行库失败；本次为 `infra/ocr-service` 补充 OpenCV/RapidOCR 所需系统库、CJK 字体和 `opencv-python-headless` 依赖，新增 `/ready` 接口真实加载 OCR 引擎，并将 Docker Compose healthcheck 改为检查 `/ready`。同时新增真实白底黑字图片 OCR 冒烟脚本，并补充 DOCX 基础截图样式图片进入 OCR 调用的 Java 用例；`OcrProperties` 改为 JavaBean 配置绑定，修复容器内 Spring Boot 启动时报无默认构造器的问题。
- 注意事项：后续判断 OCR sidecar 是否可用应以 `/ready` 为准，`/health` 仅代表 HTTP 服务存活；已上传且完成索引的旧 DOCX 需要重新索引才会进入修复后的 OCR 链路，真实端到端上传和 RAG 召回仍需使用业务文档在前端或接口层再验证。

[2026-05-26 14:35:41] 修改摘要
- 修改内容：继续排查 DOCX 图片文字仍无法识别的问题，定位到 document-service 已提取图片并调用 OCR，但 Java HttpClient 默认尝试 HTTP/2 明文升级，Uvicorn/FastAPI 记录 `Unsupported upgrade request` 并让 `/ocr` 返回 422。已将 `HttpOcrService` 的 OCR 请求和客户端固定为 HTTP/1.1，补充异常状态响应体截断日志，并新增单元测试确认请求不再携带 `Upgrade` 头。
- 注意事项：本次重新索引文档 93 验证通过，OCR 日志由 422 变为连续识别成功，最终 34 个 chunk 中有 10 个包含 `【图片OCR DOCX...】`；索引完成耗时仍受 embedding 服务影响，如果 Ollama embedding 不可用，会每批等待超时后 fallback，表现为索引较慢但不影响 OCR 文本入库。

[2026-05-26 19:22:48] 修改摘要
- 修改内容：修正 DOCX 图片 OCR 文本落点和切片策略。`DocxDocumentParser` 不再把 OCR 文本统一追加到全文末尾，而是按正文、表格、页眉、页脚结构遍历 Word 内容，在图片 run 所在位置立即插入 `【图片OCR DOCX 第 N 张 / 位置】` 段落；相同图片仍只调用一次 OCR，重复出现位置复用识别结果并分别插入。`SimpleDocumentSplitterService` 增加 DOCX OCR 块识别，短 OCR 段落独立成 chunk，长 OCR 段落切分时保留前缀；`DocumentIndexServiceImpl` 为 OCR chunk 写入 `ocr=true`、`ocrSourceType=DOCX_IMAGE`、`imageIndex`、`confidence`、`locationText` 等 metadata，并在索引完成日志中输出 `docxImageCount`、`docxOcrSuccessCount`、`ocrChunkCount`。同时补充预览日志，说明 DOCX 预览触发 OCR 但不写入 `document_chunk`。
- 注意事项：旧 DOCX 必须重新索引后才会使用新的 OCR 落点和 chunk metadata；本次已对 `documentId=94` 重新索引验证，chunk 0 为第 1 张图片 OCR，`Serial.cpp` 可命中第 2 张图片 OCR chunk，`/api/rag/search` 使用 `Serial.cpp` 查询时返回 document 94 的 OCR chunk。索引耗时仍可能受 Ollama embedding 超时降级影响，表现为进度停留但最终可完成。

[2026-05-26 20:15:19] 修改摘要
- 修改内容：将本地 embedding 默认模型从生成式 `qwen2.5:7b` 切换为专用向量模型 `qwen3-embedding:0.6b`。更新 Docker Compose 和 document/rag 服务配置默认值，`ollama-pull` 同时拉取 `qwen3-embedding:0.6b` 与保留给查询重写/本地生成使用的 `qwen2.5:7b`。`OpenAiEmbeddingService` 增加 Ollama 模型存在性检查、模型名/批次大小/原始维度/配置维度/耗时/fallback 日志，并把 `embeddingModel`、`embeddingProvider`、`embeddingConfiguredDimension` 写入 chunk metadata，方便 SQL 排查向量来源。
- 注意事项：第一阶段仍保持 `document_vector.embedding vector(1536)` 和 `AI_EMBEDDING_DIMENSION=1536`，`qwen3-embedding:0.6b` 返回的 1024 维向量会按现有逻辑补齐并归一化；旧文档必须重新索引才会换成新模型向量。本次已拉取模型并对 `documentId=94` 重新索引，54 个 chunk 全部写入 `qwen3-embedding:0.6b` metadata，索引日志未再出现 deterministic fallback。

[2026-05-26 21:08:24] 修改摘要
- 修改内容：完成 document 向量库从 1536 维到 1024 维的迁移。迁移前先用当前 qwen3 embedding 配置全量重索引 28 个 INDEXED 文档，确认 610 条有效向量均为 `qwen3-embedding:0.6b` 且第 1025-1536 维尾部能量为 0；随后新增 `infra/db/95-document-vector-1024-migration.sql`，清理 DELETED 文档残留向量，将 `document.document_vector.embedding` 转为 `vector(1024)` 并重建 ivfflat 索引。同步将 Docker Compose、document-service、rag-service 和初始化 SQL 的默认 `AI_EMBEDDING_DIMENSION` 改为 1024，并更新 embedding 单测。
- 注意事项：迁移后当前库 `document.document_vector` 为 610 条 `vector(1024)`，metadata 均为 `embeddingModel=qwen3-embedding:0.6b`、`embeddingConfiguredDimension=1024`，DELETED 文档残留向量为 0；已重建并重启 document-service/rag-service，document 94 重新索引验证 1024 维新写入成功，`Serial.cpp`、`SensorModule.cpp` 和 `数据采集层` 查询仍可召回相关 OCR chunk。

[2026-05-27 14:37:12] 修改摘要
- 修改内容：优化 RAG chunk 语义切片策略，将默认 `RAG_CHUNK_SIZE/RAG_CHUNK_OVERLAP` 从 `800/120` 调整为 `1800/300`，并同步 document-service、rag-service、chat-service 的本地默认配置口径；将 `SimpleDocumentSplitterService` 从固定字符窗口升级为结构感知切分：优先保留标题、段落、列表、表格行和 DOCX 图片 OCR 段落边界，长段落再按句号、分号、换行等边界拆分。`TextChunk` 增加 `splitStrategy`，索引 metadata 写入 `chunkIndex`、`estimatedTokens`、`charLength`、`splitStrategy`、`prevChunkIndex`、`nextChunkIndex`，索引完成日志增加平均/最大 chunk 字符数。新增 document-service 内部相邻上下文接口，RAG 在回答组装阶段会为命中 chunk 默认补充同文档前后各 1 个 chunk，并记录 direct/adjacent/OCR 命中日志。
- 注意事项：本次不新增数据库字段、不改变 embedding 模型和向量维度；旧文档需要重新索引后才会使用新的 chunk 大小、切分策略和 metadata。相邻上下文扩展失败时会降级为原始召回结果，不阻断问答；OCR chunk 仍保留独立切片优先策略，避免图片文字被长正文稀释。

[2026-05-27 15:06:36] 修改摘要
- 修改内容：按 GPU/CPU 分工拆分 Ollama 调度。`docker-compose.yml` 新增 `ollama-embedding` 和 `ollama-rewrite` 两个实例，embedding 实例启用 `gpus: all` 并服务 `qwen3-embedding:0.6b`，rewrite 实例保留 CPU 并服务 `qwen2.5:7b`；服务环境变量改为 `AI_EMBEDDING_BASE_URL=http://ollama-embedding:11434`、`RAG_QUERY_REWRITE_BASE_URL=http://ollama-rewrite:11434`。同时将文档索引第一阶段默认收敛为单线程执行和小批量 embedding：`DOCUMENT_INDEX_EXECUTOR_CORE_SIZE/MAX_SIZE=1/1`、`DOCUMENT_INDEX_EMBEDDING_BATCH_SIZE=8`，避免批量上传时多个索引任务同时打满 Ollama。
- 注意事项：启动新 Compose 时需要使用 `--remove-orphans` 清理旧 `knowflow-ollama`，否则宿主机 `11434` 端口会冲突。已验证 Docker GPU 可用，`ollama-embedding` 中 `qwen3-embedding:0.6b` 显示 `100% GPU`，`ollama-rewrite` 中 `qwen2.5:7b` 显示 `100% CPU`；对 `documentId=94` 重索引时 38 个 chunk 的 embedding batch 耗时约 0.5-5 秒且未出现 fallback。

[2026-05-27 15:37:02] 修改摘要
- 修改内容：修复 DOCX OCR 解析文本在前端预览不稳定的问题。`DocumentServiceImpl.previewText` 对 DOCX 文档优先按 `chunkIndex` 读取并拼接已入库的 `document_chunk.content`，保留 `【图片OCR DOCX...】` 前缀并用分隔符标记 chunk 边界；只有未索引或无 chunk 的 DOCX 才回退到临时解析/OCR。同步增加 `previewSource=CHUNKS/PARSE_ON_DEMAND` 日志，便于区分预览来自数据库切片还是临时解析；前端文档页 DOCX 预览文案更新为“显示索引后的解析文本，包含 OCR 图片文字”。
- 注意事项：已索引 DOCX 的预览现在与 RAG 实际检索内容一致，但不是 Word 原始版式；旧文档如果没有 OCR chunk，仍需要重新索引后才能在预览和问答中看到图片文字。原始版式继续通过下载原文件查看。

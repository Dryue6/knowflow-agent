# Knowflow Agent

企业知识库智能助手后端，基于 Java 17、Spring Boot 3、MyBatis-Plus、PostgreSQL、LangChain4j 依赖、OpenAI-compatible Chat/Embedding API 以及可替换的 VectorStoreService 抽象构建。

## 已实现能力

- 知识库 CRUD、分页查询、统计刷新
- 文档上传、本地文件存储、txt/md/pdf/docx 解析
- chunkSize + overlap 文本切片
- EmbeddingService 抽象与 OpenAI-compatible 实现，未配置 API Key 时提供本地确定性向量便于联调
- VectorStoreService 抽象，提供 PgVector 实现和内存 fallback
- 文档索引任务、重建索引、删除文档向量
- RAG 检索、Prompt 构建、问答和引用来源
- 聊天会话、消息记录、SSE 流式回答接口
- AgentService 门面，后续可扩展工具调用
- SpringDoc OpenAPI、统一 ApiResult、全局异常处理、参数校验

## 启动

1. 创建 PostgreSQL 数据库并安装 pgvector：

```sql
CREATE DATABASE knowledge_agent;
```

2. 执行 `src/main/resources/db/schema.sql`。
3. 修改 `src/main/resources/application.yml` 中的数据库和 AI 配置。
4. 启动：

```bash
mvn spring-boot:run
```

OpenAPI 地址：`http://localhost:8080/swagger-ui.html`

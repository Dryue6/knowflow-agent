# LangChain4J + Ollama 查询重写链路改造

## 改动摘要

本次将 RAG 查询重写从硬编码规则改为 LangChain4J 封装的本地 Ollama 调用。默认使用 `qwen2.5:7b` 生成结构化 JSON，用于后续向量检索、关键词混合检索和本地重排序。

当 Ollama 不可用、超时、返回空内容或返回非法 JSON 时，系统会使用保守兜底策略：保留原始问题，并基于轻量分词生成 `coreTerms`，不再保留业务同义词硬编码。

本次进一步加入“缓存 + 通用轻量分词快路径 + Ollama 复杂问题重写”提速策略。短问题和明确短语不再每次调用 7B 模型；复杂问题仍交给 Ollama；相同问题优先复用内存缓存。

## 后端实现

- `pom.xml` 新增 `dev.langchain4j:langchain4j-ollama`，版本沿用项目现有 `${langchain4j.version}`。
- `rag.query-rewrite` 新增配置项：
  - `provider`
  - `base-url`
  - `model-name`
  - `temperature`
  - `timeout-seconds`
  - `max-query-variants`
  - `max-terms-per-field`
  - `num-predict`
  - `num-ctx`
  - `max-retries`
  - `cache-enabled`
  - `cache-ttl-minutes`
  - `cache-max-size`
  - `fast-path-enabled`
  - `fast-path-max-length`
  - `complex-intent-keywords`
  - `warmup-enabled`
  - `warmup-query`
  - `fallback-mode`
- `QueryRewriteService` 现在通过 `OllamaChatModel.builder()` 创建 LangChain4J 模型：
  - `baseUrl: http://localhost:11434`
  - `modelName: qwen2.5:7b`
  - `temperature: 0.0`
  - `format: json`
  - `timeout: 90s`
  - `numPredict: 96`
  - `numCtx: 2048`
  - `maxRetries: 0`
- 模型输出继续转换为原有 `QueryRewriteResult`，所以 `RagServiceImpl`、`HybridSearchService`、`RerankService` 不需要变更调用接口。

## 提速策略

- 缓存：默认启用 Redis 缓存，规范化 query 和配置指纹组成 key，默认 60 分钟过期，最多保留约 512 条查询重写缓存。
- 快路径：默认启用，长度不超过 12 且不包含复杂意图词的问题走通用轻量分词，不调用 Ollama。
- 复杂意图：默认关键词为 `怎么,如何,为什么,规则,计算,占比,分配,要求,流程`，命中后调用 Ollama。
- 兜底：Ollama 异常时仍返回原始问题和通用分词结果，不使用业务同义词表。
- 预热：应用启动完成后异步执行一次 `成绩怎么计算` 重写，提前加载模型；失败只记录日志，不影响启动。

## Docker 支持

`docker-compose.yml` 新增：

- `redis` 服务：使用 `redis:7-alpine`，暴露 `6379`，持久化到 `redis-data`，healthcheck 使用 `redis-cli ping`。
- `ollama` 服务：暴露 `11434`，将宿主机 `D:\llm` 挂载为容器内模型目录 `/models`。
- `ollama-pull` 工具服务：用于拉取默认模型 `qwen2.5:7b`。
- 如果后端也放入 compose 网络运行，查询重写地址应改为 `http://ollama:11434`。
- Ollama 模型目录固定为宿主机 `D:\llm`，容器内通过 `OLLAMA_MODELS=/models` 写入。
- `OLLAMA_KEEP_ALIVE=30m`，减少每次冷加载模型导致的查询重写超时。

常用启动命令：

```powershell
docker compose up -d postgres ollama
docker compose --profile tools run --rm ollama-pull
docker compose up -d redis
```

执行 `ollama-pull` 后，模型文件会下载到：

```text
D:\llm
```

本机 IDEA 或命令行运行后端时，保持：

```yaml
rag:
  query-rewrite:
    base-url: http://localhost:11434
```

## 提示词输出格式

Ollama 被要求只返回 JSON 对象：

```json
{
  "queryVariants": ["成绩怎么计算", "成绩计算", "考核占比"],
  "coreTerms": ["成绩", "计算"],
  "phraseTerms": ["成绩计算"],
  "expandedTerms": ["分数", "评分", "考核", "分值", "占比", "计算方式"]
}
```

后端会对这些字段做去空、去重和数量限制，并强制将原始问题放入 `queryVariants` 第一位。

## 验证结果

已执行：

```powershell
mvn -q -DskipTests package
docker compose --profile tools config
```

结果：

- Maven 构建通过。
- Compose 配置解析通过。
- 直接调用 Ollama 测试显示：首次冷加载约 48.5 秒，其中模型加载约 42.7 秒；模型加载后再次请求约 6 秒。此前 20 秒超时会在冷加载阶段触发兜底。
- 提速配置已调整为：`max-query-variants: 5`、`max-terms-per-field: 8`、`num-predict: 96`、`cache-enabled: true`、`fast-path-enabled: true`、`warmup-enabled: true`。
- 使用临时端口 `18082` 启动后端验证：应用约 4.6 秒启动完成，Ollama 预热在后台约 8 秒完成；`课程安排` 约 74ms，预热过的 `成绩怎么计算` 约 49ms，第二次命中缓存约 34ms，复杂问题 `考试分值怎么分配` 热调用约 5.4 秒。
- Redis 缓存改造验证：`docker exec knowflow-redis redis-cli ping` 返回 `PONG`；预热后 Redis 出现 `knowflow:rag:query-rewrite:*` key；停止 Redis 后接口仍返回成功，只记录缓存读写 warn；重启 Redis 后缓存 key 仍存在。

本次继续执行时已尝试拉取 `ollama/ollama:latest` 并启动 Ollama，但 Docker Desktop 的 containerd 存储层返回 `input/output error`，`docker pull`、`docker images`、`docker system df` 都受影响，因此尚未完成镜像和模型下载。修复或重启 Docker Desktop 后重新执行：

```powershell
docker compose up -d ollama
docker compose --profile tools run --rm ollama-pull
```

模型文件会写入 `D:\llm`。模型可用后，可用以下问题在“检索调试”页或 `/api/rag/search` 验证：

- `成绩`
- `成绩怎么计算`
- `成绩如何算`
- `考试分值怎么分配`
- `考核占比是多少`
- `火星天气怎么计算`

## 参考

- LangChain4J Ollama 官方文档：https://docs.langchain4j.dev/integrations/language-models/ollama

[2026-05-25] 查询变体向量召回日志
- 修改内容：在 `RagServiceImpl.primaryQueryVariants` 中保留最多 5 个非空查询变体参与向量召回，并逐条输出实际使用的查询变体日志；当查询改写结果为空或全部为空白时，回退到原始问题并记录 fallback 日志。
- 注意事项：该日志会在每次向量召回前输出，便于排查多路 embedding 召回效果和耗时；若线上日志量较大，可后续将日志级别调整为 debug。

[2026-05-27 15:14:45] 修改摘要
- 修改内容：增强 `rag-service` 启动时 qwen2.5 查询重写预热日志。`QueryRewriteService.warmup()` 现在会绕过 Redis 缓存和 fast-path，直接向 `qwen2.5:7b` 发送固定 warmup 输入，并在日志中输出 `module=rag-service`、`component=QueryRewriteService`、provider、baseUrl、model、input、rawOutput、parsedOutput 和 elapsedMs，便于每次启动时确认测试用例输入输出以及归属微服务模块。
- 注意事项：该启动探针只属于 `rag-service` 查询重写链路，不属于 `document-service` embedding 链路；如果 `qwen2.5:7b` 继续运行在 CPU 上，首次冷启动日志中的 elapsedMs 可能达到几十秒。已临时启动 `ollama-rewrite` 和 `rag-service` 验证日志输出，随后停止本次验证启动的 `rag-service`、`ollama-rewrite`、`ollama-embedding`。

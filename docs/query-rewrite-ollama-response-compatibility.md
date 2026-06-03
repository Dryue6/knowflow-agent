# Ollama 查询重写响应兼容修复

## 问题现象

查询重写调用 Ollama 后出现如下告警，并回退到 conservative fallback：

```text
Invalid Ollama query rewrite response, use conservative fallback: Cannot deserialize value of type `java.lang.String` from Object value (token `JsonToken.START_OBJECT`)
... RewritePayload["queryVariants"]->java.util.ArrayList[0]
```

## 根因

`QueryRewriteService` 原先将 `queryVariants`、`coreTerms`、`phraseTerms`、`expandedTerms` 定义为 `List<String>`。模型按提示词应返回字符串数组，但实际可能返回对象数组，例如：

```json
{
  "queryVariants": [
    {"query": "成绩如何计算"}
  ]
}
```

Jackson 在反序列化 `List<String>` 时遇到对象元素会直接失败，导致整个查询重写结果被丢弃并进入 fallback。

## 修复策略

- 将 Ollama 响应 payload 字段改为 `JsonNode`，避免反序列化阶段因数组元素类型漂移失败。
- 统一提取字段文本：
  - 字符串元素直接使用。
  - 对象元素按顺序读取 `query`、`variant`、`text`、`term`、`keyword`、`phrase`、`value` 字段。
  - 单个字符串字段按单元素数组处理。
  - 无法识别的对象、空值和嵌套复杂结构跳过。
- 保持 `QueryRewriteResult` 对外结构不变，调用方仍只接收 `List<String>`。
- 提示词补充约束：四个字段都必须是字符串数组，数组元素不能是对象。
- 查询重写缓存版本提升到 `prompt-v4`，避免旧 fallback 结果继续命中。

## 验证方式

新增 `QueryRewriteServiceTest` 覆盖：

- 标准字符串数组 JSON。
- `queryVariants` 对象数组。
- terms 字段对象数组。
- 无法识别对象元素被忽略。
- 单个字符串字段兼容。
- 原始 query 始终保留在 `queryVariants` 第一位。

执行：

```powershell
mvn test
```

## 测试记录

### 测试方法

采用 JUnit 单元测试验证解析层，不依赖本地 Ollama、Redis 或 Spring 容器启动。测试直接调用 `QueryRewriteService.parseModelResponse(...)`，传入模拟的 Ollama JSON 响应，确认最终得到的 `QueryRewriteResult` 符合预期。

### 测试过程

1. 构造 `QueryRewriteService`，注入测试用 `RagProperties.QueryRewrite`、`ObjectMapper`，缓存服务传入 `null`，避免测试依赖 Redis。
2. 分别传入标准字符串数组、对象数组、无法识别对象、单个字符串字段四类 JSON 响应。
3. 使用 AssertJ 校验 `queryVariants`、`coreTerms`、`phraseTerms`、`expandedTerms` 的提取结果和顺序。
4. 在项目根目录执行 `mvn test`。
5. Maven/Surefire 测试进程自然退出后，检查本次测试未留下残留 Maven/Surefire Java 进程。

### 测试参数

- 测试命令：`mvn test`
- Java 版本：本机 Maven 执行环境显示为 Java 24。
- 测试类：`src/test/java/com/example/knowledgeagent/rag/service/QueryRewriteServiceTest.java`
- 核心配置：
  - `enabled: true`
  - `provider: ollama`
  - `modelName: qwen2.5:7b`
  - `maxQueryVariants: 5`
  - `maxTermsPerField: 8`
  - `cacheEnabled: true`
  - `fallbackMode: conservative`
- 主要输入样例：

```json
{
  "queryVariants": [{"query": "成绩如何计算"}, {"variant": "考核成绩算法"}],
  "coreTerms": [{"term": "成绩"}, {"keyword": "计算"}],
  "phraseTerms": [{"phrase": "成绩计算"}],
  "expandedTerms": [{"text": "评分"}, {"value": "占比"}]
}
```

### 测试结果

最终执行结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

验证结论：

- 字符串数组可正常解析。
- `queryVariants` 中的对象数组不再触发 Jackson 反序列化失败。
- terms 字段中的对象元素可按候选字段提取文本。
- 无法识别的对象元素会被跳过，不会导致整体 fallback。
- 单个字符串字段会被兼容为单元素数组。
- 原始 query 始终保留在 `queryVariants` 第一位。
- 本次测试未启动 Ollama、Redis 或后端服务；未发现本次测试残留进程。

## 运维注意

如果线上已经写入旧缓存，版本升级会生成新的缓存 key。若仍需手动清理，可删除 Redis 中旧前缀：

```powershell
docker exec knowflow-redis redis-cli --scan --pattern "knowflow:rag:query-rewrite:*" | ForEach-Object { docker exec knowflow-redis redis-cli DEL $_ }
```

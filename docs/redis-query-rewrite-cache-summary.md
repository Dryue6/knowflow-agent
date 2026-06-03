# Redis 化查询重写缓存执行总结

## 改动摘要

本次将 RAG 查询重写缓存从 `QueryRewriteService` 内部本地内存迁移到 Redis。查询重写流程仍保持：Redis 缓存优先、fast path 轻量处理、复杂问题调用 Ollama、异常时保守兜底。

## 后端改动

- `pom.xml` 新增 `spring-boot-starter-data-redis`。
- `application.yml` 新增 `spring.data.redis` 本地连接配置。
- `rag.query-rewrite.cache-key-prefix` 默认设置为 `knowflow:rag:query-rewrite:`。
- 新增 `QueryRewriteCacheService`：
  - 使用 `StringRedisTemplate` 读写 Redis。
  - 使用 Jackson 序列化 `QueryRewriteResult`。
  - key 由 `cacheKeyPrefix + compact(query) + configFingerprint` 组成。
  - TTL 使用 `cache-ttl-minutes`，默认 60 分钟。
  - Redis 读写异常只记录 warn，不影响问答流程。
- `QueryRewriteService` 移除了 `ConcurrentHashMap` 本地缓存，缓存逻辑统一委托给 Redis 缓存服务。

## Docker 改动

`docker-compose.yml` 新增 `redis` 服务：

```yaml
redis:
  image: redis:7-alpine
  container_name: knowflow-redis
  restart: unless-stopped
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
```

注释的 app compose 示例已补充：

```yaml
SPRING_DATA_REDIS_HOST: redis
```

## 验证结果

已执行：

```powershell
mvn -q -DskipTests package
docker compose --profile tools config --quiet
docker compose up -d redis
docker exec knowflow-redis redis-cli ping
```

结果：

- Maven 构建通过。
- Compose 配置解析通过。
- Redis 启动成功，`PING` 返回 `PONG`。
- 临时端口 `18083` 启动后端成功，Ollama 预热完成。
- Redis 中出现 `knowflow:rag:query-rewrite:*` 缓存 key，TTL 约 60 分钟。
- 停止 Redis 后请求仍返回成功，缓存读写失败仅记录 warn；Redis 重启后服务恢复连接。

## 使用说明

启动 Redis：

```powershell
docker compose up -d redis
```

查看缓存 key：

```powershell
docker exec knowflow-redis redis-cli --scan --pattern "knowflow:rag:query-rewrite:*"
```

检查 Redis 状态：

```powershell
docker exec knowflow-redis redis-cli ping
```

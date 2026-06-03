# Docker 微服务路由问题排查记录

## 背景

在 Docker 部署环境中，文档服务、RAG 服务和 Chat 服务在容器和端口层面都可以访问，但部分请求通过网关或 Feign 服务间调用时返回通用 500 响应。

## 问题发现

相关容器都处于运行状态，服务启动后也可以注册到 Nacos。因此，本次故障不是 Docker 网络完全不可用、端口未暴露，或服务发现整体失效导致的。

实际导致故障的是两个配置问题：

1. 网关路由重叠，导致文档接口被转发到了错误服务。

   文档服务提供以下接口：

   - `/api/knowledge-bases/{knowledgeBaseId}/documents`
   - `/api/knowledge-bases/{knowledgeBaseId}/documents/upload`

   但网关中 `knowledge-service` 的路由匹配了 `/api/knowledge-bases/**`，所以这些文档路径会先被转发到 `knowledge-service`。`knowledge-service` 并不提供这些文档接口，因此会出现 `NoResourceFoundException`，最终返回通用 500 响应。

2. 内部 Controller 不在 Spring 扫描范围内。

   Feign 客户端调用的内部接口包括：

   - `/internal/documents/search/vector`
   - `/internal/documents/search/keyword`
   - `/internal/documents/context/fixed`
   - `/internal/rag/search`
   - `/internal/rag/ask`

   实现这些接口的 Controller 位于：

   - `com.example.knowflow.document.internal`
   - `com.example.knowflow.rag.internal`

   但服务启动类只扫描了 `com.example.knowledgeagent...` 包和 `com.example.knowflow.contract`，没有扫描这些内部 Controller 所在包。因此 Spring 没有注册这些内部接口，请求会落到静态资源处理逻辑，最终出现类似 `No static resource internal/documents/search/vector` 和 `No static resource internal/rag/ask` 的错误。

## 已应用的修复

1. 更新 `knowflow-gateway/src/main/resources/application.yml`。

   文档服务路由增加了知识库文档相关路径：

   - `/api/knowledge-bases/*/documents`
   - `/api/knowledge-bases/*/documents/**`

   同时将更具体的 `document-service` 路由放在更宽泛的 `knowledge-service` 路由之前，确保文档接口优先进入文档服务。

2. 更新 `document-service` 启动扫描范围。

   在 `DocumentServiceApplication` 的扫描包中加入 `com.example.knowflow.document`，确保 `InternalDocumentSearchController` 被注册。

3. 更新 `rag-service` 启动扫描范围。

   在 `RagServiceApplication` 的扫描包中加入 `com.example.knowflow.rag`，确保 `InternalRagController` 被注册。

## 下次遇到类似问题的推荐排查顺序

当 Docker 微服务请求返回 500，或看起来无法访问时，建议按以下顺序检查：

1. 使用 `docker compose ps` 确认容器和端口状态。
2. 直接访问映射后的服务端口，检查服务自身健康接口。
3. 对同一个路径分别进行直接服务访问和网关访问，比较差异。
4. 检查网关路由断言是否存在路径重叠。更具体的路由应放在更宽泛的路由之前，或者在正确服务的路由中显式加入共享前缀。
5. 检查服务日志中是否存在 `NoResourceFoundException`。如果缺失路径是内部接口，确认 Controller 所在包是否包含在 `@SpringBootApplication(scanBasePackages = ...)` 中。
6. 查看 OpenAPI 输出或 actuator mappings，确认预期 Controller 接口是否已经被 Spring 注册。
7. 对 Feign 调用问题，需要同时检查调用方和提供方：Feign Client 路径必须和提供方 Controller 路径一致，并且提供方 Controller 必须被扫描注册。

常用命令：

```powershell
docker compose ps
docker logs --tail 200 knowflow-gateway
docker logs --tail 200 knowflow-document
docker logs --tail 200 knowflow-rag
docker logs --tail 200 knowflow-chat
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:18083/v3/api-docs
curl.exe -i http://localhost:18084/v3/api-docs
curl.exe -i http://localhost:18085/v3/api-docs
```

## Docker Compose 启动时报 No Such Container

启动时还可能遇到以下错误：

```text
Error response from daemon: No such container: <container-id>
```

这个问题通常不同于应用自身启动失败。当前观察到的情况是：报错中引用的容器 ID 已经不存在，但部分服务容器停留在 `Created` 状态，另一些旧应用容器处于 `Exited (137)` 状态。直接启动 `Created` 状态的容器可以成功，说明 Docker Compose 在上一次构建或启动被中断后，留下了过期或半更新的容器引用。

推荐恢复步骤：

```powershell
docker compose ps -a
docker ps -a --filter "name=knowflow"
docker start knowflow-document knowflow-rag
```

如果直接执行 `docker start` 还不够，可以只删除受影响的已停止或半创建服务容器，再让 Compose 重新创建：

```powershell
docker rm knowflow-document knowflow-rag knowflow-gateway
docker compose up -d --build knowflow-gateway document-service rag-service
```

如果多个服务都混在 `Created` 或 `Exited (137)` 状态，优先使用保留 named volumes 的项目级重启：

```powershell
docker compose down
docker compose up -d --build
```

除非明确要删除数据库、Redis、Nacos、上传文件等 named volumes 数据，否则不要执行 `docker compose down -v`。

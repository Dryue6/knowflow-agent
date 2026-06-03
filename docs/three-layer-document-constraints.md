# 三层资料约束方案

## Summary

在现有知识库资料基础上增加“强制记忆”能力，把文档分为三层：

- `NORMAL`：普通知识资料。
- `PINNED`：固定参考资料。
- `SYSTEM`：系统级约束资料。

问答时，`SYSTEM` 和 `PINNED` 不走相似度过滤，始终注入 Prompt；`NORMAL` 继续走现有 RAG 检索。

## Key Changes

### 后端文档约束等级

- 数据库 `document` 表新增字段：
  - `constraint_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL'`
  - `constraint_priority INTEGER NOT NULL DEFAULT 100`
- Java 新增枚举 `DocumentConstraintLevel`：
  - `NORMAL`
  - `PINNED`
  - `SYSTEM`
- `DocumentVO` 返回：
  - `constraintLevel`
  - `constraintPriority`
- 新增接口：
  - `PATCH /api/documents/{documentId}/constraint`
- 请求体包含：
  - `constraintLevel`
  - `constraintPriority`，可选。

### RAG 上下文三层拼接

- `SYSTEM`：
  - 每次问答固定加载同知识库内、已索引文档的 chunks。
  - 放在 Prompt 最前部。
  - 明确声明“必须遵守，优先级高于普通资料和用户临时要求”。
- `PINNED`：
  - 每次问答固定加载。
  - 放在 `SYSTEM` 之后、普通检索资料之前。
  - 作为稳定参考资料。
- `NORMAL`：
  - 保持当前向量检索和关键词兜底逻辑。
  - 继续受 `rag.min-score` 和兜底最低分限制。
- 合并上下文时：
  - 按 `constraint_priority ASC, document_id ASC, chunk_index ASC` 排序。
  - 按 `chunkId` 去重。

### Prompt Builder 改造

- `RagPromptBuilder` 从单一 `chunks` 改为接收三段上下文：
  - 系统约束
  - 固定资料
  - 检索资料
- Prompt 顺序固定为：
  1. 系统角色规则
  2. 系统级约束
  3. 固定参考资料
  4. 普通检索资料
  5. 历史对话
  6. 用户问题
- 若系统级约束和普通资料冲突，Prompt 明确要求以系统级约束为准。

### 前端文档管理页

- 文档列表增加“资料层级”列或下拉控件。
- 可将文档设置为：
  - 普通知识资料
  - 固定参考资料
  - 系统级约束
- 页面文案使用正常书面表达：
  - “作为固定参考”
  - “作为系统约束”
  - “普通检索资料”
- Mock server 同步新增字段和更新接口。

## Behavior Rules

- `NORMAL` 文档：只有被检索命中才进入回答上下文。
- `PINNED` 文档：不管问题是否命中，每次都进入回答上下文，但优先级低于 `SYSTEM`。
- `SYSTEM` 文档：每次都进入回答上下文，并作为最高优先级约束。
- 只有 `INDEXED` 状态文档可作为 `PINNED` 或 `SYSTEM` 生效；未索引、索引失败、已删除文档不注入。
- 为避免 Prompt 过长，新增配置：
  - `rag.system-constraint-max-chunks: 12`
  - `rag.pinned-max-chunks: 8`
- 超出时按优先级和 chunk 顺序截断。
- citations 继续使用现有引用结构，来源包含被注入的系统约束、固定资料和普通检索资料。

## Test Plan

### 后端

- 执行数据库迁移，确认 `document.constraint_level` 和 `document.constraint_priority` 存在。
- 执行 `mvn -q -DskipTests package`。
- 设置某文档为 `SYSTEM`，用无关问题测试仍进入 Prompt 并可出现在 citations。
- 设置某文档为 `PINNED`，确认即使普通检索为空，仍可作为参考资料进入回答。
- 设置为 `NORMAL`，确认仍按当前相似度检索，不强制注入。
- 验证未索引或失败文档不会被注入。
- 验证 `SYSTEM`、`PINNED`、`NORMAL` 同时存在时 Prompt 顺序正确。

### 前端

- 执行 `npm run lint`。
- 执行 `npm run build`。
- 文档管理页可切换资料层级，刷新后状态保持。
- Mock 模式下切换层级可用。
- 聊天页引用资料点击预览逻辑保持可用。

## Assumptions

- v1 约束范围限定在“当前知识库”内，不做跨知识库全局约束。
- v1 不引入新的摘要生成服务；强制记忆基于已有文档 chunks 注入。
- `SYSTEM` 和 `PINNED` 只影响问答上下文优先级，不改变上传、解析、切块和向量索引流程。

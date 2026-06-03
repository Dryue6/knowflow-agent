package com.example.knowledgeagent.document.service;

/**
 * 文档索引中止异常。
 *
 * <p>当用户在索引过程中删除文档时，索引任务应尽快停止后续解析、embedding 或向量写入，
 * 但这不是业务失败；任务层会把它标记为 CANCELLED，避免误导前端显示处理失败。</p>
 */
public class DocumentIndexCancelledException extends RuntimeException {
    public DocumentIndexCancelledException(String message) {
        super(message);
    }
}

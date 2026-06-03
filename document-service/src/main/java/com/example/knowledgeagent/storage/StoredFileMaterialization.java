package com.example.knowledgeagent.storage;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储对象的本地物化结果。
 *
 * <p>MinIO 等对象存储不能直接提供本地 Path，因此解析前会下载到临时文件；
 * temporary=true 表示关闭时需要删除该临时文件，避免索引和预览频繁调用后残留垃圾文件。</p>
 */
@Slf4j
public record StoredFileMaterialization(Path path, boolean temporary) implements AutoCloseable {
    @Override
    public void close() {
        if (!temporary || path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("清理临时解析文件失败，path={}", path, ex);
        }
    }
}

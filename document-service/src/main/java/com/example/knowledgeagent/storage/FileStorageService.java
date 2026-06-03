package com.example.knowledgeagent.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档原始文件存储接口。
 *
 * <p>上传、删除、下载和解析前物化都必须通过该接口，避免业务代码直接依赖本地磁盘路径。</p>
 */
public interface FileStorageService {
    /**
     * 保存上传文件并返回数据库需要持久化的存储信息。
     */
    StoredFile store(MultipartFile file, Long knowledgeBaseId);

    /**
     * 删除已存储文件；删除失败时由实现决定是否阻断主业务流程。
     */
    void delete(String filePath);

    /**
     * 将存储对象包装为 Spring Resource，供预览和下载接口流式返回。
     */
    Resource loadAsResource(String filePath);

    /**
     * 将存储对象物化为可被现有解析器读取的 Path。
     *
     * <p>对象存储实现会创建临时文件，调用方必须使用 try-with-resources 关闭结果以清理临时文件。</p>
     */
    StoredFileMaterialization materialize(String filePath, String originalFileName);
}

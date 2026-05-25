package com.example.knowledgeagent.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 定义 FileStorageService 接口，约定该模块对外提供的能力。
 */
public interface FileStorageService {
    /**
     * 保存上传文件并返回存储信息。
     */
    StoredFile store(MultipartFile file, Long knowledgeBaseId);

    /**
     * 删除已存储文件。
     */
    void delete(String filePath);
}

package com.example.knowledgeagent.storage;

import org.springframework.web.multipart.MultipartFile;

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

package com.example.knowledgeagent.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile store(MultipartFile file, Long knowledgeBaseId);

    void delete(String filePath);
}

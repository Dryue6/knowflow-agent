package com.example.knowledgeagent.storage;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {
    private final FileStorageProperties properties;

    @Override
    public StoredFile store(MultipartFile file, Long knowledgeBaseId) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String suffix = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String fileName = UUID.randomUUID() + suffix.toLowerCase();
        Path dir = Path.of(properties.basePath(), String.valueOf(knowledgeBaseId), LocalDate.now().toString()).toAbsolutePath().normalize();
        Path target = dir.resolve(fileName).normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
            return new StoredFile(fileName, originalName, target.toString(), file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "保存文件失败: " + ex.getMessage());
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
        }
    }
}

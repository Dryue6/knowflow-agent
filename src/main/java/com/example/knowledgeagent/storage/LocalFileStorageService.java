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

    /**
     * 将上传文件保存到本地磁盘。
     * <p>
     * 路径按 knowledgeBaseId/日期 分目录，文件名使用 UUID，避免原始文件名冲突和路径注入。
     */
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

    /**
     * 删除本地文件；删除失败不阻断主流程，避免文件系统偶发问题影响业务删除。
     */
    @Override
    public void delete(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
        }
    }
}

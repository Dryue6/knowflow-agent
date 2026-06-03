package com.example.knowledgeagent.storage;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本地文件存储实现。
 *
 * <p>该实现保留给非 MinIO 场景或临时回退使用；当前 Docker 部署默认由 MinIO 实现接管。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalFileStorageService implements FileStorageService {
    private final FileStorageProperties properties;

    /**
     * 将上传文件保存到本地磁盘。
     *
     * <p>路径按 knowledgeBaseId/日期 分目录，文件名使用 UUID，避免原始文件名冲突和路径注入。</p>
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
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath).toAbsolutePath().normalize());
        } catch (IOException ex) {
            log.warn("删除本地文件失败，filePath={}", filePath, ex);
        }
    }

    /**
     * 将本地路径包装为 Resource，供下载和预览接口直接读取。
     */
    @Override
    public Resource loadAsResource(String filePath) {
        Path path = existingPath(filePath);
        return new PathResource(path);
    }

    /**
     * 本地文件已经具备 Path 形态，不需要额外临时复制。
     */
    @Override
    public StoredFileMaterialization materialize(String filePath, String originalFileName) {
        return new StoredFileMaterialization(existingPath(filePath), false);
    }

    private Path existingPath(String filePath) {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            return path;
        }
        throw new BusinessException(ErrorCode.FILE_ERROR, "文件不存在或已被移除");
    }
}

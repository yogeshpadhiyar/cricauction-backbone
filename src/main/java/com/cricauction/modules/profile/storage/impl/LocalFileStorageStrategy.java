package com.cricauction.modules.profile.storage.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.profile.storage.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorageStrategy implements FileStorageStrategy {

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/jpg"
    );

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    @Value("${cricauction.storage.local.base-dir:./uploads}")
    private String baseDir;

    @Value("${cricauction.storage.base-url:https://cdn.cricauction.app}")
    private String baseUrl;

    @Override
    public boolean supports(String storageType) {
        return "LOCAL".equalsIgnoreCase(storageType);
    }

    @Override
    public String uploadFile(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("INVALID_FILE", "Upload file cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("FILE_SIZE_EXCEEDED", "File size exceeds the 5MB maximum limit.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("UNSUPPORTED_MEDIA_TYPE",
                    "Invalid image format. Allowed formats: JPEG, PNG, WEBP.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = ".jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            String candidateExt = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            if (ALLOWED_IMAGE_EXTENSIONS.contains(candidateExt)) {
                extension = candidateExt;
            } else {
                throw new BadRequestException("UNSUPPORTED_MEDIA_TYPE",
                        "Invalid image file extension. Allowed extensions: .jpg, .jpeg, .png, .webp");
            }
        }

        String safeFileName = UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Path baseRoot = Paths.get(baseDir).toAbsolutePath().normalize();
            String sub = (subDirectory != null) ? subDirectory.trim() : "";
            Path targetDir = baseRoot.resolve(sub.startsWith("/") ? sub.substring(1) : sub).normalize();
            if (!targetDir.startsWith(baseRoot)) {
                log.warn("Path traversal attempted in uploadFile: [{}]", subDirectory);
                throw new BadRequestException("INVALID_PATH", "Upload path must stay within the base directory.");
            }
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(safeFileName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Uploaded file stored locally at: {}", targetPath);
            return baseUrl + "/" + (sub.startsWith("/") ? sub.substring(1) : sub) + "/" + safeFileName;
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to store uploaded file: {}", e.getMessage(), e);
            throw new BadRequestException("UPLOAD_FAILED", "Could not store file: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            String relative = fileUrl.replace(baseUrl, "");
            Path baseRoot = Paths.get(baseDir).toAbsolutePath().normalize();
            Path filePath = baseRoot.resolve(relative.startsWith("/") ? relative.substring(1) : relative).normalize();
            if (!filePath.startsWith(baseRoot)) {
                log.warn("Path traversal attempted in deleteFile: [{}]", fileUrl);
                return;
            }
            Files.deleteIfExists(filePath);
            log.info("Deleted local file: {}", filePath);
        } catch (Exception e) {
            log.warn("Could not delete file at {}: {}", fileUrl, e.getMessage());
        }
    }
}

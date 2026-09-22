package org.example.reportingservice.common.storage.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.reportingservice.common.storage.FileStorageService;
import org.example.reportingservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final Path baseStorageLocation;

    public LocalFileStorageServiceImpl(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.baseStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseStorageLocation);
            log.info("Khởi tạo thư mục lưu trữ tệp tin tại: {}", this.baseStorageLocation);
        } catch (Exception ex) {
            log.error("Không thể tạo thư mục lưu trữ tệp tin: {}", ex.getMessage());
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu trữ tệp tin", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin tải lên không được để trống");
        }

        String rawOriginalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        if (rawOriginalFilename.contains("..")) {
            throw new BadRequestException("Tên tệp tin không hợp lệ: " + rawOriginalFilename);
        }

        String extension = StringUtils.getFilenameExtension(rawOriginalFilename);
        String fileExtension = StringUtils.hasText(extension) ? "." + extension.toLowerCase() : "";
        String uniqueFileName = UUID.randomUUID() + fileExtension;

        try {
            Path targetFolder = this.baseStorageLocation;
            if (StringUtils.hasText(subDirectory)) {
                targetFolder = this.baseStorageLocation.resolve(subDirectory).normalize();
                Files.createDirectories(targetFolder);
            }

            Path targetLocation = targetFolder.resolve(uniqueFileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Đã lưu tệp tin vật lý thành công: {}", targetLocation);
            return uniqueFileName;
        } catch (IOException ex) {
            log.error("Lỗi I/O khi lưu trữ tệp tin {}: {}", rawOriginalFilename, ex.getMessage());
            throw new RuntimeException("Không thể lưu trữ tệp tin trên máy chủ. Vui lòng thử lại sau.", ex);
        }
    }

    @Override
    public void deleteFile(String relativeFilePath) {
        if (!StringUtils.hasText(relativeFilePath)) {
            return;
        }
        try {
            Path filePath = this.baseStorageLocation.resolve(relativeFilePath).normalize();
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Đã dọn dẹp (xóa) tệp tin vật lý: {}", filePath);
            }
        } catch (IOException ex) {
            log.warn("Không thể xóa tệp tin vật lý {}: {}", relativeFilePath, ex.getMessage());
        }
    }
}

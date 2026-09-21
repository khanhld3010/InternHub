package org.example.employeeservice.system.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.system.dto.request.BackupFilterRequest;
import org.example.employeeservice.system.dto.response.BackupResponse;
import org.example.employeeservice.system.entity.BackupHistory;
import org.example.employeeservice.system.entity.BackupStatus;
import org.example.employeeservice.system.entity.BackupType;
import org.example.employeeservice.system.exception.BackupConflictException;
import org.example.employeeservice.system.exception.InsufficientDiskSpaceException;
import org.example.employeeservice.system.repository.BackupHistoryRepository;
import org.example.employeeservice.system.service.SystemBackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemBackupServiceImpl implements SystemBackupService {

    private final BackupHistoryRepository backupHistoryRepository;
    private final DataSource dataSource;

    @Value("${backup.storage-dir:./data/backups}")
    private String storageDir;

    @Value("${backup.retention-days:30}")
    private int retentionDays;

    @Value("${backup.mysqldump-path:mysqldump}")
    private String mysqldumpPath;

    @Value("${spring.datasource.username:root}")
    private String dbUsername;

    @Value("${spring.datasource.password:123456}")
    private String dbPassword;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3307/internhub_db}")
    private String dbUrl;

    private final AtomicBoolean isBackupRunning = new AtomicBoolean(false);

    private static final long MIN_REQUIRED_DISK_SPACE_BYTES = 1024L * 1024L * 1024L; // 1 GB

    /**
     * Phục hồi các tiến trình Zombie bị treo ở trạng thái IN_PROGRESS khi restart container
     */
    @PostConstruct
    public void recoverZombieProcesses() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
            List<BackupHistory> zombies = backupHistoryRepository.findByStatusAndCreatedAtBefore(BackupStatus.IN_PROGRESS, threshold);
            for (BackupHistory b : zombies) {
                b.setStatus(BackupStatus.FAILED);
                b.setErrorMessage("Tiến trình bị gián đoạn do khởi động lại dịch vụ");
                backupHistoryRepository.save(b);
                log.warn("Đã phục hồi bản ghi backup zombie kẹt IN_PROGRESS id={}", b.getId());
            }
        } catch (Exception e) {
            log.error("Lỗi khi quét bản ghi backup zombie:", e);
        }
    }

    /**
     * Tự động sao lưu định kỳ theo lịch cron (mặc định 02:00 sáng hàng ngày)
     */
    @Scheduled(cron = "${backup.cron:0 0 2 * * ?}")
    public void scheduleDailyBackup() {
        log.info("Bắt đầu tác vụ sao lưu tự động định kỳ (Scheduled Backup)...");
        try {
            triggerBackup(BackupType.AUTOMATIC, "SYSTEM");
        } catch (Exception e) {
            log.error("Tác vụ sao lưu định kỳ thất bại: {}", e.getMessage());
        }
    }

    @Override
    public BackupResponse triggerBackup(BackupType backupType, String createdBy) {
        log.info("Nhận yêu cầu sao lưu: type={}, createdBy={}", backupType, createdBy);

        // 1. Chặn xung đột: Kiểm tra cờ atomic và DB
        if (!isBackupRunning.compareAndSet(false, true)) {
            log.warn("Yêu cầu sao lưu bị từ chối: Đang có tiến trình khác chạy");
            throw new BackupConflictException("Một tiến trình sao lưu khác đang diễn ra. Vui lòng thử lại sau.");
        }

        Path backupFile = null;
        BackupHistory record = null;
        long startTime = System.currentTimeMillis();

        try {
            if (backupHistoryRepository.existsByStatus(BackupStatus.IN_PROGRESS)) {
                throw new BackupConflictException("Một tiến trình sao lưu khác đang diễn ra. Vui lòng thử lại sau.");
            }

            // 2. Kiểm tra dung lượng đĩa khả dụng
            Path storagePath = Paths.get(storageDir).toAbsolutePath().normalize();
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
            }

            File dirFile = storagePath.toFile();
            long usableSpace = dirFile.getUsableSpace();
            log.info("Dung lượng đĩa trống khả dụng: {} MB", usableSpace / (1024 * 1024));

            if (usableSpace < MIN_REQUIRED_DISK_SPACE_BYTES) {
                throw new InsufficientDiskSpaceException("Sao lưu thất bại: Dung lượng đĩa trống không đủ (yêu cầu tối thiểu 1GB).");
            }

            // 3. Đặt tên file chuẩn: internhub_backup_YYYYMMDD_HHmmss.sql.gz
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("internhub_backup_%s.sql.gz", timestamp);
            backupFile = storagePath.resolve(fileName);

            // 4. Tạo bản ghi IN_PROGRESS
            record = BackupHistory.builder()
                    .fileName(fileName)
                    .filePath(backupFile.toString())
                    .fileSize(0L)
                    .backupType(backupType)
                    .status(BackupStatus.IN_PROGRESS)
                    .scope("ALL")
                    .createdBy(createdBy != null ? createdBy : "SYSTEM")
                    .build();
            record = backupHistoryRepository.saveAndFlush(record);

            // 5. Thực thi sao lưu và nén gzip
            boolean dumped = executeMysqldump(backupFile);
            if (!dumped) {
                log.warn("Lệnh mysqldump không khả dụng, chuyển sang cơ chế JDBC Export an toàn...");
                executeJdbcDump(backupFile);
            }

            // 6. Cập nhật thành công
            long durationMs = System.currentTimeMillis() - startTime;
            long fileSize = Files.size(backupFile);

            record.setStatus(BackupStatus.SUCCESS);
            record.setFileSize(fileSize);
            record.setDurationMs(durationMs);
            record = backupHistoryRepository.save(record);

            log.info("Sao lưu hoàn tất thành công: file={}, size={} bytes, duration={} ms",
                    fileName, fileSize, durationMs);

            // 7. Tự động dọn dẹp các bản sao lưu cũ > 30 ngày (Retention Cleanup)
            cleanOldBackups();

            return BackupResponse.fromEntity(record);

        } catch (Exception ex) {
            log.error("Quá trình sao lưu gặp sự cố:", ex);
            if (backupFile != null) {
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException ignored) {}
            }

            if (record != null) {
                record.setStatus(BackupStatus.FAILED);
                record.setErrorMessage(ex.getMessage());
                record.setDurationMs(System.currentTimeMillis() - startTime);
                backupHistoryRepository.save(record);
            }

            if (ex instanceof BackupConflictException) {
                throw (BackupConflictException) ex;
            }
            if (ex instanceof InsufficientDiskSpaceException) {
                throw (InsufficientDiskSpaceException) ex;
            }
            throw new RuntimeException("Sao lưu thất bại: " + ex.getMessage(), ex);

        } finally {
            isBackupRunning.set(false);
        }
    }

    /**
     * Thử thực thi mysqldump với tùy chọn --single-transaction --quick
     */
    private boolean executeMysqldump(Path targetGzFile) {
        try {
            ParsedDbUrl parsed = parseJdbcUrl(dbUrl);
            List<String> command = new ArrayList<>();
            command.add(mysqldumpPath);
            command.add("-h");
            command.add(parsed.host);
            command.add("-P");
            command.add(String.valueOf(parsed.port));
            command.add("-u");
            command.add(dbUsername);
            command.add("-p" + dbPassword);
            command.add("--single-transaction");
            command.add("--quick");
            command.add("--skip-lock-tables");
            command.add(parsed.database);

            log.info("Chạy mysqldump: host={}, port={}, db={}", parsed.host, parsed.port, parsed.database);

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            try (InputStream in = process.getInputStream();
                 OutputStream fos = Files.newOutputStream(targetGzFile);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    gzos.write(buffer, 0, len);
                }
                gzos.finish();
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Lệnh mysqldump thực thi thành công (exitCode=0)");
                return true;
            } else {
                log.warn("Lệnh mysqldump trả về mã lỗi: {}", exitCode);
                Files.deleteIfExists(targetGzFile);
                return false;
            }
        } catch (Exception e) {
            log.warn("Không thể chạy mysqldump (có thể chưa cài trên máy hoặc đường dẫn không đúng): {}", e.getMessage());
            try {
                Files.deleteIfExists(targetGzFile);
            } catch (IOException ignored) {}
            return false;
        }
    }

    /**
     * Cơ chế trích xuất dữ liệu qua JDBC Fallback đảm bảo chạy được trong mọi môi trường
     */
    private void executeJdbcDump(Path targetGzFile) throws Exception {
        try (Connection conn = dataSource.getConnection();
             OutputStream fos = Files.newOutputStream(targetGzFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(gzos, "UTF-8"))) {

            writer.println("-- ========================================================");
            writer.println("-- InternHub Database Backup (JDBC Snapshot Stream)");
            writer.println("-- Date: " + LocalDateTime.now());
            writer.println("-- ========================================================");
            writer.println("SET FOREIGN_KEY_CHECKS=0;\n");

            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    dumpTableData(conn, tableName, writer);
                }
            }

            writer.println("\nSET FOREIGN_KEY_CHECKS=1;");
            writer.flush();
            gzos.finish();
            log.info("JDBC Dump hoàn tất thành công ra file: {}", targetGzFile);
        }
    }

    private void dumpTableData(Connection conn, String tableName, PrintWriter writer) throws Exception {
        writer.println("-- Table: " + tableName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("INSERT INTO `" + tableName + "` VALUES (");
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    if (val == null) {
                        sb.append("NULL");
                    } else if (val instanceof Number) {
                        sb.append(val);
                    } else {
                        String escaped = val.toString().replace("'", "''").replace("\\", "\\\\");
                        sb.append("'").append(escaped).append("'");
                    }
                    if (i < colCount) sb.append(", ");
                }
                sb.append(");");
                writer.println(sb.toString());
            }
            writer.println();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BackupResponse> getBackupHistory(BackupFilterRequest request, Pageable pageable) {
        Specification<BackupHistory> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (request != null && request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request != null && request.getBackupType() != null) {
                predicates.add(cb.equal(root.get("backupType"), request.getBackupType()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<BackupHistory> page = backupHistoryRepository.findAll(spec, pageable);
        return PageResponse.from(page, BackupResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadBackupFile(Long id) {
        BackupHistory backup = backupHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bản sao lưu với ID " + id + " không tồn tại"));

        Path filePath = Paths.get(backup.getFilePath()).normalize().toAbsolutePath();
        Path baseDirPath = Paths.get(storageDir).normalize().toAbsolutePath();

        // Chống Path Traversal (BR-5 / Case 5)
        if (!filePath.startsWith(baseDirPath)) {
            log.warn("Cảnh báo Path Traversal: Đường dẫn file {} nằm ngoài thư mục {}", filePath, baseDirPath);
            throw new ResourceNotFoundException("Đường dẫn file sao lưu không hợp lệ");
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.canRead()) {
            throw new ResourceNotFoundException("File bản sao lưu vật lý không tồn tại trên hệ thống lưu trữ");
        }

        return new FileSystemResource(file);
    }

    @Override
    @Transactional(readOnly = true)
    public String getBackupFileName(Long id) {
        BackupHistory backup = backupHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bản sao lưu với ID " + id + " không tồn tại"));
        return backup.getFileName();
    }

    @Override
    @Transactional
    public void deleteBackup(Long id) {
        BackupHistory backup = backupHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bản sao lưu với ID " + id + " không tồn tại"));

        // Xóa file vật lý trên đĩa
        try {
            Path path = Paths.get(backup.getFilePath());
            Files.deleteIfExists(path);
            log.info("Đã xóa file vật lý: {}", path);
        } catch (Exception e) {
            log.warn("Không thể xóa file vật lý: {}", e.getMessage());
        }

        backupHistoryRepository.delete(backup);
        log.info("Đã xóa bản ghi sao lưu id={}", id);
    }

    @Override
    @Transactional
    public void cleanOldBackups() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            log.info("Bắt đầu quét dọn dẹp các bản sao lưu cũ hơn {} ngày (trước {})", retentionDays, threshold);

            List<BackupHistory> oldBackups = backupHistoryRepository.findByCreatedAtBefore(threshold);
            for (BackupHistory b : oldBackups) {
                try {
                    Path path = Paths.get(b.getFilePath());
                    Files.deleteIfExists(path);
                    log.info("Retention cleanup: Đã xóa file cũ {}", path);
                } catch (Exception ignored) {}
                backupHistoryRepository.delete(b);
            }
            log.info("Dọn dẹp hoàn tất: Đã xóa {} bản sao lưu cũ", oldBackups.size());
        } catch (Exception e) {
            log.error("Lỗi khi dọn dẹp bản sao lưu cũ:", e);
        }
    }

    private static class ParsedDbUrl {
        String host = "localhost";
        int port = 3306;
        String database = "internhub_db";
    }

    private ParsedDbUrl parseJdbcUrl(String url) {
        ParsedDbUrl res = new ParsedDbUrl();
        try {
            // jdbc:mysql://host:port/database?...
            String clean = url.replace("jdbc:mysql://", "");
            int slashIndex = clean.indexOf('/');
            if (slashIndex != -1) {
                String hostPort = clean.substring(0, slashIndex);
                String rest = clean.substring(slashIndex + 1);
                int qIndex = rest.indexOf('?');
                res.database = (qIndex != -1) ? rest.substring(0, qIndex) : rest;

                int colonIndex = hostPort.indexOf(':');
                if (colonIndex != -1) {
                    res.host = hostPort.substring(0, colonIndex);
                    res.port = Integer.parseInt(hostPort.substring(colonIndex + 1));
                } else {
                    res.host = hostPort;
                }
            }
        } catch (Exception e) {
            log.warn("Không thể phân tích JDBC URL {}, sử dụng cấu hình mặc định", url);
        }
        return res;
    }
}

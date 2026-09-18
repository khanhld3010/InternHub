# Technical Implementation Plan: TM-4 Tải Lên CV và Đơn Xin Thực Tập

---

## 1. Overview & Architectural Context (Tổng Quan Kỹ Thuật)
- **Ticket:** [TM-4: Upload CV & Application Letter](https://robluccibn9935.atlassian.net/browse/TM-4)
- **Subsystem:** `employee-service` (Spring Boot 4, Java 17/21, Spring Data JPA)
- **Change Level:** **L3**
- **Core Architecture:**
  - Áp dụng triệt để kiến trúc **Package-by-Feature**: Các thành phần liên quan đến tài liệu của thực tập sinh được đặt trong package `org.example.employeeservice.intern`.
  - Tách tầng lưu trữ (Storage Layer) dùng chung tại `org.example.employeeservice.common.storage`: Định nghĩa interface trừu tượng `FileStorageService` và cài đặt `LocalFileStorageServiceImpl` (lưu file đĩa cục bộ, sẵn sàng mở rộng sang S3/MinIO mà không ảnh hưởng business logic).
  - Tách biệt hoàn toàn trạng thái tài liệu (`intern_documents.status`) với trạng thái hồ sơ (`intern_profiles.status`) theo nguyên lý **Separation of Concerns**.
  - Cho phép lưu giữ toàn bộ lịch sử các lần upload của ứng viên (Lựa chọn A).

---

## 2. Clarification Decisions (Quyết Định Kỹ Thuật Đã Thống Nhất)
1. **Lịch sử tài liệu:** Lưu giữ toàn bộ các bản ghi upload trong `intern_documents`. Tài liệu mới nhất sẽ được truy xuất theo `ORDER BY id DESC / created_at DESC`.
2. **Lưu trữ vật lý:**
   - Đường dẫn: `./uploads/intern-documents/`.
   - Cấu hình linh hoạt qua biến môi trường: `file.upload-dir: ${FILE_UPLOAD_DIR:./uploads}` trong `application.yml` và `employee-service.yml`.
   - Bổ sung `uploads/` vào `.gitignore`.
3. **Trạng thái:** Tách biệt độc lập: giữ nguyên `InternProfile.status`, chỉ khởi tạo `InternDocument.status = PENDING_REVIEW`.

---

## 3. Detailed Component Blueprint (Chi Tiết Thành Phần)

### 3.1. Database & Entities
- **[NEW]** `org.example.employeeservice.intern.entity.enums.DocumentType`:
  - Enum gồm: `CV`, `APPLICATION_LETTER`.
- **[NEW]** `org.example.employeeservice.intern.entity.enums.DocumentStatus`:
  - Enum gồm: `PENDING_REVIEW`, `APPROVED`, `REJECTED`.
- **[NEW]** `org.example.employeeservice.intern.entity.InternDocument`:
  - Kế thừa `BaseEntity` (có sẵn `id`, `createdAt`, `updatedAt`).
  - `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "intern_id", nullable = false) private InternProfile internProfile;`
  - `@Enumerated(EnumType.STRING) private DocumentType documentType;`
  - `private String originalFileName;`
  - `private String fileName;` (tên UUID trên đĩa)
  - `private String filePath;`
  - `private Long fileSize;`
  - `private String contentType;`
  - `@Enumerated(EnumType.STRING) private DocumentStatus status = DocumentStatus.PENDING_REVIEW;`
  - `private String rejectionReason;`

### 3.2. Repository Layer
- **[NEW]** `org.example.employeeservice.intern.repository.InternDocumentRepository`:
  - Kế thừa `JpaRepository<InternDocument, Long>`.
  - Phương thức tìm kiếm:
    - `List<InternDocument> findByInternProfileIdOrderByCreatedAtDesc(Long internId);`
    - `List<InternDocument> findByInternProfileInternCodeOrderByCreatedAtDesc(String internCode);`
    - `Optional<InternDocument> findTopByInternProfileInternCodeAndDocumentTypeOrderByCreatedAtDesc(String internCode, DocumentType documentType);`

### 3.3. DTO Layer
- **[NEW]** `org.example.employeeservice.intern.dto.response.DocumentResponse`:
  - Fields: `id`, `internCode`, `documentType`, `originalFileName`, `fileSize`, `contentType`, `status`, `createdAt`.

### 3.4. Storage Service Layer (Abstraction & Implementation)
- **[NEW]** `org.example.employeeservice.common.storage.FileStorageService`:
  - `String storeFile(MultipartFile file, String subDirectory);` (Ghi file, trả về relative path).
  - `void deleteFile(String relativeFilePath);` (Xóa file vật lý khi cần rollback).
- **[NEW]** `org.example.employeeservice.common.storage.impl.LocalFileStorageServiceImpl`:
  - Sử dụng `Path`, `Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)`.
  - Tạo thư mục cha nếu chưa tồn tại (`Files.createDirectories`).
  - Đặt tên file ngẫu nhiên chống trùng: `UUID.randomUUID().toString() + extension`.
  - Xử lý làm sạch tên file chống tấn công Path Traversal.

### 3.5. Service Layer (Business Logic)
- **[NEW]** `org.example.employeeservice.intern.service.InternDocumentService`:
  - `DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr);`
- **[NEW]** `org.example.employeeservice.intern.service.impl.InternDocumentServiceImpl`:
  - `@Transactional`: Đảm bảo tính toàn vẹn.
  - Kiểm tra `file.isEmpty()`, kích thước `> 5MB`, đuôi file & Content-Type (`.pdf`, `.docx`, `.doc`).
  - Tìm `InternProfile` theo `internCode`. Nếu không có $\rightarrow$ ném `ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode)`.
  - Parse `DocumentType`. Nếu sai $\rightarrow$ ném `BadRequestException("Loại tài liệu không hợp lệ. Chỉ chấp nhận: CV, APPLICATION_LETTER")`.
  - Gọi `FileStorageService.storeFile(...)`.
  - Tạo entity `InternDocument` với `status = PENDING_REVIEW`, lưu vào `InternDocumentRepository`.
  - Cơ chế bọc Rollback: Nếu lưu DB thất bại $\rightarrow$ xóa file vật lý đã ghi để không để lại file rác.
  - Map sang `DocumentResponse`.

### 3.6. Controller & Security Layer
- **[NEW]** `org.example.employeeservice.intern.controller.InternDocumentController`:
  - `@RestController`, `@RequestMapping("/api/employees/interns/{internCode}/documents")`.
  - `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
  - Tiếp nhận `@PathVariable String internCode`, `@RequestParam("file") MultipartFile file`, `@RequestParam("documentType") String documentType`.
  - Trả về `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, "Tải lên tài liệu thành công", response));`.
- **[MODIFY]** `org.example.employeeservice.config.SecurityConfig`:
  - Mở quyền Public không yêu cầu JWT cho endpoint:
    `.requestMatchers(HttpMethod.POST, "/api/employees/interns/*/documents").permitAll()`
- **[MODIFY]** `.gitignore`:
  - Thêm `uploads/` vào danh sách bỏ qua.
- **[MODIFY]** `config-repo-local/employee-service.yml` / `employee-service/src/main/resources/application.yml`:
  - Cấu hình dung lượng multipart tối đa: `spring.servlet.multipart.max-file-size=10MB`, `spring.servlet.multipart.max-request-size=10MB`.
  - Cấu hình `file.upload-dir: ${FILE_UPLOAD_DIR:./uploads}`.

---

## 4. Potential Risks & Mitigations (Rủi Ro Kỹ Thuật & Giải Pháp)
1. **Rủi ro rác đĩa khi lỗi DB:** Tệp vật lý được lưu trước, nhưng DB bị ngắt kết nối.
   - *Giải pháp:* Khối `try-catch` trong Service bắt exception khi gọi `repository.save()`, thực hiện gọi `storageService.deleteFile(...)` trước khi ném lại Exception.
2. **Kích thước file vượt ngưỡng Tomcat/Spring:** Nếu gửi file 20MB, Spring chặn ngay trước Controller với `MaxUploadSizeExceededException`.
   - *Giải pháp:* Bổ sung ExceptionHandler cho `MaxUploadSizeExceededException` trong `GlobalExceptionHandler` trả về thông báo lỗi 400 thân thiện.
3. **Mã thực tập sinh chứa ký tự đặc biệt:**
   - *Giải pháp:* Validate `internCode` theo chuẩn `^[A-Z0-9-]+$`.

---

## 5. Verification Plan (Kế Hoạch Kiểm Thử & Nghiệm Thu)
1. **Unit Tests (`InternDocumentServiceTest`):**
   - Upload CV file PDF thành công $\rightarrow$ 201 Created.
   - Upload Application Letter file DOCX thành công $\rightarrow$ 201 Created.
   - File rỗng $\rightarrow$ 400 Bad Request.
   - File đuôi `.exe` hoặc không thuộc whitelist $\rightarrow$ 400 Bad Request.
   - File kích thước $> 5MB$ $\rightarrow$ 400 Bad Request.
   - Mã `internCode` không tồn tại $\rightarrow$ 404 Not Found.
2. **Integration / WebMvc Tests (`InternDocumentControllerTest`):**
   - Gọi API Public không mang Header Authorization $\rightarrow$ Cho phép truy cập (không bị 401/403).
3. **Build & Package Verification:**
   - Chạy kiểm tra biên dịch Java và đóng gói JAR.

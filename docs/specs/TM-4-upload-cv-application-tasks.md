# Task List: TM-4 Tải Lên CV và Đơn Xin Thực Tập

---

## Danh Sách Task Triển Khai (Chi Tiết & Độc Lập)

- [ ] **Task 1: Cấu hình Môi trường & .gitignore**
  - [ ] Thêm `uploads/` vào `.gitignore` gốc của dự án.
  - [ ] Bổ sung cấu hình `spring.servlet.multipart.max-file-size=10MB`, `spring.servlet.multipart.max-request-size=10MB`, và `file.upload-dir=${FILE_UPLOAD_DIR:./uploads}` trong `employee-service.yml` / `application.yml`.

- [ ] **Task 2: Tạo Enums và JPA Entity `InternDocument`**
  - [ ] Tạo Enum `DocumentType` (`CV`, `APPLICATION_LETTER`) tại `intern/entity/enums/DocumentType.java`.
  - [ ] Tạo Enum `DocumentStatus` (`PENDING_REVIEW`, `APPROVED`, `REJECTED`) tại `intern/entity/enums/DocumentStatus.java`.
  - [ ] Tạo JPA Entity `InternDocument` kế thừa `BaseEntity` với khóa ngoại trỏ tới `InternProfile`.

- [ ] **Task 3: Tạo Repository và DTO Response**
  - [ ] Tạo interface `InternDocumentRepository` kế thừa `JpaRepository`.
  - [ ] Tạo DTO `DocumentResponse` chứa các thông tin tài liệu trả về cho client.

- [ ] **Task 4: Xây dựng tầng File Storage (Common Storage)**
  - [ ] Tạo Interface `FileStorageService` (`storeFile`, `deleteFile`) tại `common/storage/`.
  - [ ] Tạo `LocalFileStorageServiceImpl` cài đặt lưu file an toàn với UUID và kiểm tra Directory Traversal.

- [ ] **Task 5: Xây dựng Business Logic Tải Lên Tài Liệu (Service Layer)**
  - [ ] Định nghĩa `InternDocumentService`.
  - [ ] Cài đặt `InternDocumentServiceImpl` với `@Transactional`:
    - Xác thực file (rỗng, dung lượng $\le 5MB$, đuôi `.pdf`, `.docx`, `.doc`).
    - Kiểm tra sự tồn tại của `internCode`.
    - Gọi `FileStorageService` lưu file và `InternDocumentRepository` lưu metadata.
    - Cơ chế dọn dẹp file vật lý (Cleanup) nếu xảy ra lỗi ghi DB.

- [ ] **Task 6: Mở Quyền Public Endpoint trong SecurityConfig & Xử lý Exception**
  - [ ] Thêm cấu hình `.requestMatchers(HttpMethod.POST, "/api/employees/interns/*/documents").permitAll()` trong `SecurityConfig.java`.
  - [ ] Bổ sung `@ExceptionHandler(MaxUploadSizeExceededException.class)` trong `GlobalExceptionHandler` trả về thông báo 400 rõ ràng.

- [ ] **Task 7: Xây dựng REST Controller**
  - [ ] Tạo `InternDocumentController` với API `POST /api/employees/interns/{internCode}/documents`.
  - [ ] Tiếp nhận `MultipartFile` và `documentType`, trả về HTTP 201 Created cùng `ApiResponse<DocumentResponse>`.

- [ ] **Task 8: Viết Unit Tests & WebMvc Tests**
  - [ ] Viết `InternDocumentServiceTest` bao phủ toàn bộ các kịch bản thành công và edge cases (file rỗng, quá cỡ, sai định dạng, sai mã internCode, rollback storage).
  - [ ] Viết `InternDocumentControllerTest` kiểm tra endpoint không bị chặn bởi Security (Public access).

- [ ] **Task 9: Kiểm tra biên dịch & Đóng gói JAR**
  - [ ] Chạy build đóng gói JAR để đảm bảo mã nguồn hoàn toàn sạch lỗi.

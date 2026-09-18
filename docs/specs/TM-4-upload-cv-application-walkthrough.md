# Walkthrough: Hoàn Thành Triển Khai Tính Năng TM-4 (Upload CV & Application Letter)

---

## 1. Tóm tắt kết quả triển khai
Tính năng **TM-4: Tải lên CV và đơn xin thực tập (Upload CV & Application Letter)** đã được triển khai hoàn chỉnh, vượt qua toàn bộ các bài Unit Tests và đã được đóng gói, chạy thực tế trên môi trường Docker Microservices của dự án `InternHub`.

---

## 2. Các thành phần đã triển khai

### 2.1. Cấu hình & Môi trường
- **`.gitignore`**: Thêm `uploads/` và `**/uploads/` để bảo mật tệp tin người dùng tải lên, không đẩy tài liệu lên kho mã nguồn.
- **`config-repo-local/employee-service.yml`**:
  - `spring.servlet.multipart.max-file-size=10MB`
  - `spring.servlet.multipart.max-request-size=10MB`
  - `file.upload-dir=${FILE_UPLOAD_DIR:./uploads}`

### 2.2. Domain Models & Database (Bảng `intern_documents`)
- **[DocumentType.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/entity/enums/DocumentType.java)**: Hỗ trợ 2 loại tài liệu: `CV`, `APPLICATION_LETTER`.
- **[DocumentStatus.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/entity/enums/DocumentStatus.java)**: Hỗ trợ các trạng thái xét duyệt: `PENDING_REVIEW`, `APPROVED`, `REJECTED`.
- **[InternDocument.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/entity/InternDocument.java)**: Entity kế thừa `BaseEntity`, liên kết với `InternProfile` qua khóa ngoại `intern_id`, tạo index trên `intern_id` và `status`.
- **[InternDocumentRepository.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/repository/InternDocumentRepository.java)**: Truy vấn lịch sử tài liệu theo `createdAtDesc`.

### 2.3. Tầng Lưu Trữ File Vật Lý (Common Storage Layer)
- **[FileStorageService.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/common/storage/FileStorageService.java)**: Interface trừu tượng hóa việc lưu trữ và xóa rollback file.
- **[LocalFileStorageServiceImpl.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/common/storage/impl/LocalFileStorageServiceImpl.java)**: Lưu trữ file cục bộ sử dụng UUID ngẫu nhiên chống trùng lặp và làm sạch đường dẫn chống tấn công Directory Traversal (`..`).

### 2.4. Business Logic (Service Layer)
- **[InternDocumentService.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/service/InternDocumentService.java)**
- **[InternDocumentServiceImpl.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/service/impl/InternDocumentServiceImpl.java)**:
  - Kiểm tra file không được rỗng, dung lượng $\le 5MB$.
  - Whitelist định dạng: chỉ chấp nhận `.pdf`, `.docx`, `.doc`.
  - Whitelist MIME types: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/msword`.
  - Tìm kiếm và xác thực sự tồn tại của `internCode`.
  - Tách bạch trạng thái: giữ nguyên `InternProfile.status`, chỉ khởi tạo `InternDocument.status = PENDING_REVIEW`.
  - **Cơ chế Rollback an toàn:** Nếu lưu database xảy ra lỗi, tự động dọn dẹp file vật lý đã ghi để không để lại rác đĩa.

### 2.5. REST API Controller & Phân Quyền (Public Endpoint)
- **[InternDocumentController.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/controller/InternDocumentController.java)**:
  - `POST /api/employees/interns/{internCode}/documents`
  - Nhận `MultipartFile` và param `documentType`. Trả về `201 Created` kèm `ApiResponse<DocumentResponse>`.
- **[SecurityConfig.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/config/SecurityConfig.java)**:
  - Mở quyền Public cho endpoint: `.requestMatchers(HttpMethod.POST, "/api/employees/interns/*/documents").permitAll()`.
- **[GlobalExceptionHandler.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub/employee-service/src/main/java/org/example/employeeservice/exception/GlobalExceptionHandler.java)**:
  - Bổ sung `@ExceptionHandler(MaxUploadSizeExceededException.class)` báo lỗi 400 rõ ràng.

---

## 3. Kết Quả Kiểm Thử (Verification Results)

### 3.1. Unit Test Suite (`InternDocumentServiceTest.java`)
Đã hoàn thành 7 test cases độc lập:
1. `UT-BE-01`: Upload CV file PDF hợp lệ $\rightarrow$ Lưu file và metadata thành công.
2. `UT-BE-02`: Upload Đơn xin thực tập file DOCX hợp lệ $\rightarrow$ Thành công.
3. `UT-BE-03`: Mã `internCode` không tồn tại $\rightarrow$ Ném `ResourceNotFoundException` (404).
4. `UT-BE-04`: Tệp tin tải lên rỗng $\rightarrow$ Ném `BadRequestException` (400).
5. `UT-BE-05`: Dung lượng file vượt quá 5MB $\rightarrow$ Ném `BadRequestException` (400).
6. `UT-BE-06`: Đuôi file cấm (ví dụ `.exe`) $\rightarrow$ Ném `BadRequestException` (400).
7. `UT-BE-07`: Database gặp sự cố ghi dữ liệu $\rightarrow$ Tự động dọn dẹp xóa file vật lý đã ghi và ném exception rollback.

### 3.2. Kiểm tra thực tế trên Docker Container
- Hibernate tự động cập nhật schema và tạo bảng `intern_documents` thành công trong MySQL `internhub_db`.
- Container `employee-service` đã khởi động lại và ở trạng thái **`healthy`**.
- OpenAPI Definition đã ghi nhận endpoint:
  ```json
  "/api/employees/interns/{internCode}/documents": {
    "post": {
      "tags": ["Intern Document Controller"],
      "summary": "Tải lên CV hoặc đơn xin thực tập (Public Endpoint - không cần đăng nhập)"
    }
  }
  ```

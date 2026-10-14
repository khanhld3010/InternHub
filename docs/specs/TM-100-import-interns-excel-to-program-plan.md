# Implementation Plan: TM-100 Thêm Thực Tập Sinh Vào Chương Trình Bằng Tệp Excel (Excel Batch Import)

> **Mã công việc:** TM-100  
> **Nhánh Git:** `feature/TM-100/add-excel-intern-to-program`  
> **Tài liệu đặc tả:** [TM-100 Spec v1.0](file:///d:/codegym_final_project/InternHub/docs/specs/TM-100-import-interns-excel-to-program-spec.md)  
> **Phạm vi tác động:** `intern-and-program-service` (Backend Microservices - Port 8082)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) và thư mục [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (Mandatory Reuse Audit)

> [!IMPORTANT]
> **Tuân thủ triệt để Quy tắc 31 & Chỉ thị bắt buộc:** *"ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"*.

| Thành phần hệ thống | Hiện trạng quét được trong dự án | Đánh giá & Quyết định tái sử dụng | Lý do kỹ thuật & Giải trình |
| :--- | :--- | :--- | :--- |
| **Cơ sở dữ liệu & Entity** | Đã có `intern_profiles` ([InternProfile.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java)) và `internship_programs` ([InternshipProgram.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/entity/InternshipProgram.java)). | **TÁI SỬ DỤNG 100%** - Tuyệt đối không tạo bảng MySQL mới, không tạo staging table. | Toàn bộ dữ liệu TTS từ file Excel sau khi parse và validate sẽ được lưu thẳng vào bảng `intern_profiles` hiện tại, gán `program_id`. |
| **Chống Race Condition Over-booking** | Đã có query `@Lock(LockModeType.PESSIMISTIC_WRITE)` `findByIdWithLock` trong [InternshipProgramRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/repository/InternshipProgramRepository.java). | **TÁI SỬ DỤNG 100%** | Khóa ghi dòng chương trình mục tiêu trước khi kiểm tra chỉ tiêu và ghi bản ghi mới, triệt tiêu rủi ro over-booking khi nhiều HR cùng nạp tệp. |
| **Kiểm tra trạng thái & Chỉ tiêu** | Đã có hàm đếm chỉ tiêu `countByProgramIdAndStatusIn` và danh sách trạng thái active `ACTIVE_INTERN_STATUSES`. | **TÁI SỬ DỤNG 100%** | Đảm bảo tính toán số lượng slot còn lại đồng bộ hoàn toàn với logic xét duyệt hồ sơ tại `enrollInterns`. |
| **Cơ chế sinh mã TTS** | Hàm `generateInternCode()` trong [InternProfileServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternProfileServiceImpl.java). | **TÁI SỬ DỤNG LOGIC** | Chuẩn hóa sinh mã `INT-yyyyMM-xxxx`. Trong batch import, tối ưu sinh mã tăng dần liên tục theo sequence tháng hiện tại để tránh query COUNT lặp lại trong vòng lặp. |
| **Sự kiện & Thông báo Onboarding** | Đã có sự kiện [InternDecisionProcessedEvent.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/event/InternDecisionProcessedEvent.java). | **TÁI SỬ DỤNG 100%** | Khi import thành công với trạng thái `APPROVED`, bắn sự kiện này để hệ thống tự động gửi email chào mừng/tiếp nhận. |
| **Xử lý Exception & API Response** | Đã có `ApiResponse<T>`, `BadRequestException`, `ResourceNotFoundException`, `DuplicateResourceException`. | **TÁI SỬ DỤNG 100%** | Đóng gói nhất quán cấu trúc phản hồi chuẩn RESTful. |
| **Thư viện Apache POI** | Hiện tại `intern-and-program-service/build.gradle` chưa có thư viện xử lý Excel. | **BỔ SUNG DEPENDENCY** | Khai báo `org.apache.poi:poi-ooxml:5.3.0` để đọc, xử lý và tạo tệp `.xlsx` theo chuẩn công nghiệp. |

---

## 2. Mục Tiêu (Objectives)

1. Tích hợp thư viện **Apache POI (`poi-ooxml:5.3.0`)** an toàn vào `intern-and-program-service`.
2. Xây dựng dịch vụ chuyên trách **`InternExcelImportService`** (tuân thủ **Anti-God-Class < 300 dòng** theo Quy tắc 17).
3. Triển khai 3 REST API endpoints tại `ProgramController`:
   - `GET /api/programs/import-template`: Tải về file Excel mẫu `.xlsx`.
   - `POST /api/programs/{id}/import-interns/preview`: Kiểm tra tính hợp lệ của file Excel, đối soát trùng lặp 2 lớp, tính toán quota còn lại (không ghi DB).
   - `POST /api/programs/{id}/import-interns`: Nhập hàng loạt vào CSDL theo nguyên tắc **All-or-Nothing**, áp dụng khóa **Pessimistic Write Lock**, sinh mã TTS, cập nhật counter và phát sự kiện.
4. Đảm bảo toàn bộ mã nguồn tuân thủ nghiêm ngặt 31 nguyên tắc bất biến của Backend, đạt độ bao phủ kiểm thử đơn vị tối thiểu 85% và biên dịch thành công 100%.

---

## 3. Danh Sách Tệp Tác Động (Impacted Files)

### 3.1. Cấu hình Gradle (`build.gradle`):
- `[MODIFY]` [intern-and-program-service/build.gradle](file:///d:/codegym_final_project/InternHub/intern-and-program-service/build.gradle): Bổ sung dependency `implementation 'org.apache.poi:poi-ooxml:5.3.0'`.

### 3.2. DTOs (`program/dto/` hoặc `intern/dto/`):
- `[NEW]` `ExcelRowError.java`: DTO chi tiết lỗi theo dòng (`rowNumber`, `fieldName`, `cellValue`, `errorMessage`).
- `[NEW]` `ExcelInternRowDto.java`: DTO trung gian chứa dữ liệu thô đọc từ từng dòng của bảng tính Excel.
- `[NEW]` `ExcelImportPreviewResponse.java`: Response DTO cho API preview (`programId`, `programName`, `totalRows`, `validRowsCount`, `invalidRowsCount`, `availableSlots`, `isQuotaExceeded`, `errors`, `previewRows`).
- `[NEW]` `ExcelImportResultResponse.java`: Response DTO cho API import (`programId`, `programName`, `importedCount`, `currentInterns`, `maxInterns`, `importedInternCodes`).

### 3.3. Repository:
- `[MODIFY]` [InternProfileRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternProfileRepository.java): Bổ sung 2 phương thức batch query:
  - `List<InternProfile> findAllByEmailIn(Collection<String> emails);`
  - `List<InternProfile> findAllByPhoneIn(Collection<String> phones);`

### 3.4. Service Layer (Package-by-Feature & Anti-God-Class):
- `[NEW]` `InternExcelImportService.java`: Interface định nghĩa 3 phương thức nghiệp vụ: `generateTemplate()`, `previewImport()`, `importInterns()`.
- `[NEW]` `InternExcelImportServiceImpl.java`: Hiện thực xử lý:
  - Phân tích cú pháp ô Excel với `DataFormatter` và `DateUtil` (xử lý SĐT mất số 0 đầu, đa dạng định dạng ngày tháng).
  - Kiểm tra dung lượng (<= 5MB), giới hạn số dòng (<= 200 dòng).
  - Đối soát trùng lặp 2 lớp (nội bộ file Set check, Database batch query check).
  - Quản lý giao dịch `@Transactional`, khóa `@Lock(PESSIMISTIC_WRITE)`, kiểm soát chỉ tiêu.
  - Sinh mã `INT-yyyyMM-xxxx` tuần tự, lưu batch `saveAll`, bắn sự kiện `InternDecisionProcessedEvent`.
  - Quản lý đóng tài nguyên Stream/Workbook bằng `try-with-resources`.

### 3.5. Controller Layer:
- `[MODIFY]` [ProgramController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/controller/ProgramController.java): Bổ sung 3 REST Endpoints có phân quyền `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`.

### 3.6. Audit Logging:
- `[MODIFY]` [AuditAction.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/system/audit/entity/AuditAction.java): Thêm giá trị `IMPORT_INTERNS_EXCEL`.

### 3.7. Unit Testing:
- `[NEW]` `InternExcelImportServiceTest.java`: Bộ test cases kiểm thử các kịch bản: file hợp lệ, file lỗi email/phone, vượt chỉ tiêu chương trình, trùng nội bộ file, chương trình không ở trạng thái mở nhận hồ sơ.

---

## 4. Kế Hoạch Triển Khai Chi Tiết (Implementation Steps)

### Bước 1: Khảo Sát & Bổ Sung Dependency Gradle
1. Mở file [build.gradle](file:///d:/codegym_final_project/InternHub/intern-and-program-service/build.gradle) của `intern-and-program-service`.
2. Thêm dependency Apache POI:
   ```groovy
   implementation 'org.apache.poi:poi-ooxml:5.3.0'
   ```
3. Chạy lệnh kiểm tra biên dịch Gradle để đảm bảo kéo dependency về thành công.

### Bước 2: Bổ Sung AuditAction & Batch Query trong Repository
1. Thêm `IMPORT_INTERNS_EXCEL` vào enum `AuditAction.java`.
2. Bổ sung `findAllByEmailIn` và `findAllByPhoneIn` vào `InternProfileRepository.java` để hỗ trợ đối soát trùng lặp hàng loạt trong 1 câu query duy nhất (chống N+1 query đối soát).

### Bước 3: Tạo DTOs Chuyên Trách
Tạo các DTOs trong package `org.example.internservice.program.dto` (hoặc `intern.dto`):
1. `ExcelRowError`: Gồm `rowNumber`, `fieldName`, `cellValue`, `errorMessage`.
2. `ExcelInternRowDto`: Ánh xạ đầy đủ các cột dữ liệu từ file Excel (Họ tên, email, SĐT, ngày sinh, giới tính, trường, ngành, niên khóa, địa chỉ, vị trí, ghi chú).
3. `ExcelImportPreviewResponse`: Dữ liệu trả về cho màn hình xem trước.
4. `ExcelImportResultResponse`: Dữ liệu trả về khi import hoàn tất.

### Bước 4: Xây Dựng Service `InternExcelImportService`
1. Khởi tạo interface `InternExcelImportService.java`.
2. Tạo `InternExcelImportServiceImpl.java` với các helper chuyên sâu:
   - `buildTemplateWorkbook()`: Tạo Workbook `.xlsx` với Header style màu xanh chuyên nghiệp, độ rộng cột tự động điều chỉnh, và 1 dòng dữ liệu mẫu minh họa.
   - `parseExcelRows(InputStream inputStream)`: Đọc Workbook với `DataFormatter`, bóc tách dữ liệu từng cell, chuẩn hóa khoảng trắng `trim()`, chuẩn hóa SĐT 10 số (thêm '0' nếu mất đầu số).
   - `validateRows(List<ExcelInternRowDto> rows)`: Kiểm tra format Email (Regex RFC 5322), SĐT (10 số VN), trường bắt buộc, giới tính, ngày sinh.
   - `checkDuplicates(List<ExcelInternRowDto> rows, List<ExcelRowError> errors)`: Kiểm tra trùng nội bộ file (Set) và trùng Database qua `findAllByEmailIn` / `findAllByPhoneIn`.
   - `previewExcelImport(Long programId, MultipartFile file)`: Đọc và trả về preview DTO mà không ghi vào DB.
   - `importInternsFromExcel(Long programId, MultipartFile file, String targetStatus, String importedBy)`:
     - Khóa ghi Pessimistic Lock trên Program.
     - Kiểm tra trạng thái chương trình (`PLANNING`, `OPEN`) và cờ `isRecruitmentOpen`.
     - Validate toàn bộ file. Nếu có bất kỳ lỗi nào ➔ ném `BadRequestException` (All-or-Nothing).
     - Kiểm tra Quota: `activeCount + validRows.size() <= program.getMaxInterns()`.
     - Sinh mã `INT-yyyyMM-xxxx` tuần tự cho từng TTS.
     - Chuyển đổi sang `InternProfile` và gọi `internProfileRepository.saveAll(profiles)`.
     - Cập nhật `program.setCurrentInterns((int) newActiveCount)`.
     - Phát sự kiện `InternDecisionProcessedEvent` cho từng TTS (nếu `status == APPROVED`).
     - Ghi nhận Audit Log.

### Bước 5: Tích Hợp Endpoints Tại `ProgramController`
Bổ sung 3 endpoints vào `ProgramController.java`:
1. `GET /api/programs/import-template`: Trả về `ResponseEntity<byte[]>` với `HttpHeaders.CONTENT_DISPOSITION` attachment `mau_nhap_thuc_tap_sinh.xlsx`.
2. `POST /api/programs/{id}/import-interns/preview`: Nhận `@RequestPart("file") MultipartFile file`, trả về `ApiResponse<ExcelImportPreviewResponse>`.
3. `POST /api/programs/{id}/import-interns`: Nhận `@RequestPart("file") MultipartFile file` và `@RequestParam(value = "status", defaultValue = "APPROVED") String status`, trả về `ApiResponse<ExcelImportResultResponse>`.

### Bước 6: Viết Unit Tests & Kiểm Tra Biên Dịch Tự Động
1. Viết `InternExcelImportServiceTest.java` kiểm thử đầy đủ các kịch bản:
   - Preview file hợp lệ trả về toàn bộ dòng valid.
   - Preview file có email/phone lỗi trả về danh sách lỗi chi tiết.
   - Import khi chương trình quá tải ném `BadRequestException`.
   - Import khi file có lỗi format ném `BadRequestException` (All-or-Nothing).
   - Import thành công lưu toàn bộ bản ghi và bắn sự kiện.
2. Chạy lệnh Gradle biên dịch và kiểm thử:
   ```powershell
   .\gradlew :intern-and-program-service:compileJava
   .\gradlew :intern-and-program-service:test --tests "org.example.internservice.program.service.InternExcelImportServiceTest"
   ```

---

## 5. Tiêu Chuẩn Nghiệm Thu & Kiểm Chứng (Verification Criteria)

1. **Gradle Build Success:** `.\gradlew :intern-and-program-service:compileJava` biên dịch thành công 0 lỗi.
2. **Unit Tests Pass 100%:** Toàn bộ test cases trong `InternExcelImportServiceTest` chạy pass.
3. **Data Integrity:** Không có bất kỳ bản ghi nào bị ghi dở dang nếu file Excel xảy ra lỗi (Rollback bảo toàn tính toàn vẹn 100%).
4. **Boundary Isolation:** Không chỉnh sửa bất kỳ tệp tin nào thuộc phân hệ Frontend (`InternHub-Frontend/`) trong suốt quá trình triển khai Backend này.

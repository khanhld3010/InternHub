# Specification: Thêm Mới Hồ Sơ Thực Tập Sinh (Intern Profile Creation)

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Thêm mới hồ sơ thực tập sinh để lưu trữ thông tin (Create Intern Profile)
- **Jira Ticket:** [TM-1](https://robluccibn9935.atlassian.net/browse/TM-1)
- **Target Subsystems:** `employee-service` (Backend Microservice), `api-gateway` (Routing & Authentication)
- **Target Users:** HR (Nhân sự), Quản trị viên hệ thống (Admin)
- **Phân loại thay đổi (Change Level):** **L3** (Ảnh hưởng Database, API, Schema & Validation nghiệp vụ)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
Chuẩn hóa và số hóa quy trình tiếp nhận thực tập sinh ban đầu vào doanh nghiệp:
1. **Lưu trữ tập trung:** Thay thế việc nhập liệu rời rạc trên file Excel hoặc Google Sheets bằng hệ thống cơ sở dữ liệu MySQL chuẩn mực.
2. **Dữ liệu đầy đủ & chính xác:** Đảm bảo mỗi hồ sơ thực tập sinh có đủ thông tin cá nhân, thông tin liên lạc, học vấn (trường, chuyên ngành) và trạng thái tiếp nhận.
3. **Định danh duy nhất:** Mỗi thực tập sinh có một mã định danh duy nhất (`intern_code` hoặc `student_code`) cùng email/số điện thoại không trùng lặp trong hệ thống.
4. **Sẵn sàng tích hợp:** Cung cấp RESTful API chuẩn mực để sau này kết nối với Frontend Web Form tiếp nhận và module Upload CV ([TM-4](https://robluccibn9935.atlassian.net/browse/TM-4)).

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- Tạo bảng `intern_profiles` lưu trữ thông tin thực tập sinh kế thừa `BaseEntity`.
- Cung cấp API `POST /api/employees/interns` tiếp nhận thông tin hồ sơ mới.
- Validate toàn bộ dữ liệu đầu vào:
  - Họ và tên, Email (đúng định dạng RFC, không trùng lặp).
  - Số điện thoại (chuẩn 10 chữ số Việt Nam, không trùng lặp).
  - Trường đại học/cao đẳng, chuyên ngành, năm học/khóa học.
  - Vị trí thực tập mong muốn (Frontend, Backend, Tester, DevOps, v.v.).
  - Ngày dự kiến bắt đầu (`startDate`).
- Gán trạng thái khởi tạo mặc định: `PENDING` (Chờ tiếp nhận / Chờ duyệt hồ sơ).
- Trả về mã HTTP `201 Created` kèm theo dữ liệu chi tiết của hồ sơ vừa tạo (`InternResponse`).

### 3.2. Ngoài phạm vi (Out of Scope) - *Ngăn chặn suy diễn sai*
- **Không tự động sinh tài khoản User/Keycloak:** Việc tạo tài khoản đăng nhập cho thực tập sinh thuộc riêng ticket [TM-6](https://robluccibn9935.atlassian.net/browse/TM-6).
- **Không xử lý upload file CV vật lý (PDF/Word):** Việc tải và lưu trữ file CV thuộc riêng ticket [TM-4](https://robluccibn9935.atlassian.net/browse/TM-4).
- **Không phân công Mentor tại bước này:** Việc gán Mentor hướng dẫn sẽ thực hiện ở module phân công sau khi thực tập sinh chính thức onboard.
- **Không gửi Email thông báo tự động (SMTP):** Chưa cấu hình Mail Server trong ticket này.

---

## 4. Potential Logic Loopholes & Mitigations (5 Edge Cases Cốt Lõi)

### 4.1. Case 1: Trùng lặp Email hoặc Số điện thoại (Unique Constraint Conflict)
- **Vấn đề:** Ứng viên hoặc HR vô tình submit 2 lần cùng một email hoặc số điện thoại đã tồn tại. Nếu không xử lý khéo, database sẽ quăng `DataIntegrityViolationException` 500 lỗi máy chủ.
- **Khắc phục:** 
  - Đặt `unique = true` cho cột `email` và `phone` ở tầng Entity/Database.
  - Tầng Service kiểm tra `existsByEmail(...)` và `existsByPhone(...)` trước khi lưu.
  - Nếu trùng, ném ngoại lệ `DuplicateResourceException` $\rightarrow$ `GlobalExceptionHandler` bắt và trả về HTTP `409 Conflict` kèm thông điệp tiếng Việt cụ thể: *"Email này đã được đăng ký trong hệ thống."*

### 4.2. Case 2: Double-Submit (Click nút "Tạo hồ sơ" liên tiếp nhiều lần)
- **Vấn đề:** Mạng lag khiến người dùng click 2 lần liên tục vào nút submit trên form, gửi 2 request song song trong cùng 1 vài mili-giây.
- **Khắc phục:** Unique index ở DB chặn bản ghi thứ 2. Tầng Controller/Service chạy trong `@Transactional`, giao dịch sau sẽ bị bắt lỗi trùng lặp và trả về 409 thay vì tạo 2 bản ghi rác.

### 4.3. Case 3: Định dạng ngày tháng không hợp lệ hoặc ngày bắt đầu ở quá khứ quá xa
- **Vấn đề:** Client gửi sai format ngày (`DD/MM/YYYY` thay vì ISO `YYYY-MM-DD`), hoặc nhập ngày bắt đầu vào năm 1900.
- **Khắc phục:** Sử dụng `LocalDate` kết hợp `@JsonFormat(pattern = "yyyy-MM-dd")`. Validate ngày bắt đầu không được để trống và phải có định dạng hợp lệ.

### 4.4. Case 4: Nhập liệu thiếu hoặc chuỗi khoảng trắng vô nghĩa
- **Vấn đề:** Client gửi chuỗi chỉ có dấu cách: `"fullName": "   "`, `"university": "  "`.
- **Khắc phục:** Sử dụng `@NotBlank` (thay vì chỉ dùng `@NotNull`) cho tất cả các trường chuỗi bắt buộc, kết hợp `@Size(min = 2, max = 100)` để chặn chuỗi quá ngắn hoặc quá dài gây tràn bộ đệm.

### 4.5. Case 5: Phân quyền truy cập (Access Control)
- **Vấn đề:** Thực tập sinh hoặc người dùng vãng lai tự ý gọi API thêm hồ sơ của người khác.
- **Khắc phục:** API yêu cầu quyền HR hoặc ADMIN (`@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` khi cấu hình Spring Security hoặc tạm thời mở endpoint cho nội bộ HR qua Gateway).

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tạo hồ sơ):** Cho phép người dùng có quyền HR gửi payload JSON để tạo mới một hồ sơ thực tập sinh.
- **FR-2 (Tự động sinh mã Intern):** Hệ thống tự sinh mã định danh thực tập sinh theo quy tắc: `INT-YYYYMM-XXXX` (ví dụ: `INT-202609-0001`) để quản lý chuyên nghiệp.
- **FR-3 (Trạng thái mặc định):** Mọi hồ sơ mới tạo đều được gán `status = PENDING`.
- **FR-4 (Kiểm tra dữ liệu hợp lệ):** Hệ thống chặn và trả về chi tiết lỗi `400 Bad Request` nếu thiếu bất kỳ trường bắt buộc nào.
- **FR-5 (Kiểm tra dữ liệu không trùng lặp):** Trả về `409 Conflict` nếu email hoặc số điện thoại đã tồn tại.
- **FR-6 (Audit Trail):** Mọi hồ sơ tự động ghi nhận thời gian tạo (`created_at`) và thời gian cập nhật (`updated_at`) thông qua `BaseEntity`.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Độ dài số điện thoại):** Số điện thoại phải đúng quy chuẩn Việt Nam (10 chữ số, bắt đầu bằng đầu số hợp lệ `03, 05, 07, 08, 09`).
- **BR-2 (Trường bắt buộc):** Bắt buộc gồm: Họ tên, Email, Số điện thoại, Trường học, Chuyên ngành, Vị trí ứng tuyển.
- **BR-3 (Trạng thái hồ sơ - InternStatus Enum):**
  - `PENDING`: Mới tiếp nhận hồ sơ.
  - `APPROVED`: Đã duyệt hồ sơ.
  - `INTERNING`: Đang trong quá trình thực tập.
  - `COMPLETED`: Đã hoàn thành khóa thực tập.
  - `REJECTED`: Từ chối hồ sơ.
- **BR-4 (Bảo toàn dữ liệu):** Không bao giờ trả mật khẩu hay thông tin nhạy cảm của hệ thống ra Response DTO.

---

## 7. Data Model (Mô Hình Dữ Liệu MySQL)

Kế thừa `BaseEntity` (`id` BIGINT AUTO_INCREMENT, `created_at` DATETIME, `updated_at` DATETIME).

### 7.1. Entity: `InternProfile` (`intern_profiles`)
**Package:** `org.example.employeeservice.entity.InternProfile`

```sql
CREATE TABLE intern_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_code VARCHAR(30) NOT NULL UNIQUE,       -- INT-202609-0001
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(15) NOT NULL UNIQUE,
    date_of_birth DATE NULL,
    gender VARCHAR(10) NULL,                      -- MALE, FEMALE, OTHER
    university VARCHAR(150) NOT NULL,
    major VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NULL,               -- Ví dụ: K17, Năm 3, Năm 4
    gpa DOUBLE NULL,                              -- Thang điểm 4.0 hoặc 10
    applied_position VARCHAR(100) NOT NULL,       -- Vị trí ứng tuyển: Java Backend, ReactJS Frontend...
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',-- PENDING, APPROVED, INTERNING, COMPLETED, REJECTED
    start_date DATE NULL,                         -- Ngày bắt đầu thực tập dự kiến
    end_date DATE NULL,                           -- Ngày kết thúc thực tập dự kiến
    address VARCHAR(255) NULL,
    notes TEXT NULL,                              -- Ghi chú của HR
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intern_email (email),
    INDEX idx_intern_phone (phone),
    INDEX idx_intern_code (intern_code),
    INDEX idx_intern_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

- **Base URL:** `/api/employees/interns`
- **Content-Type:** `application/json`

### 8.1. API Tạo mới hồ sơ thực tập sinh
- **Method:** `POST`
- **Path:** `/api/employees/interns`
- **Headers:** `Content-Type: application/json`

#### Request Body (`CreateInternRequest`):
```json
{
  "fullName": "Nguyễn Văn An",
  "email": "nguyenvanan@gmail.com",
  "phone": "0987654321",
  "dateOfBirth": "2003-05-15",
  "gender": "MALE",
  "university": "Đại học Bách Khoa Hà Nội",
  "major": "Công nghệ thông tin",
  "academicYear": "Năm 4",
  "gpa": 3.45,
  "appliedPosition": "Backend Java Intern",
  "startDate": "2026-10-01",
  "address": "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội",
  "notes": "Ứng viên có kiến thức tốt về Spring Boot và Docker"
}
```

#### Response: `201 Created`
```json
{
  "code": 201,
  "message": "Tạo mới hồ sơ thực tập sinh thành công",
  "data": {
    "id": 1,
    "internCode": "INT-202609-0001",
    "fullName": "Nguyễn Văn An",
    "email": "nguyenvanan@gmail.com",
    "phone": "0987654321",
    "dateOfBirth": "2003-05-15",
    "gender": "MALE",
    "university": "Đại học Bách Khoa Hà Nội",
    "major": "Công nghệ thông tin",
    "academicYear": "Năm 4",
    "gpa": 3.45,
    "appliedPosition": "Backend Java Intern",
    "status": "PENDING",
    "startDate": "2026-10-01",
    "endDate": null,
    "address": "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội",
    "notes": "Ứng viên có kiến thức tốt về Spring Boot và Docker",
    "createdAt": "2026-09-16T10:15:00",
    "updatedAt": "2026-09-16T10:15:00"
  }
}
```

#### Response Lỗi Thường Gặp:
- **`400 Bad Request`** (Sai định dạng dữ liệu đầu vào):
  ```json
  {
    "code": 400,
    "message": "Dữ liệu đầu vào không hợp lệ",
    "errors": {
      "email": "Email không đúng định dạng",
      "phone": "Số điện thoại phải gồm 10 chữ số hợp lệ",
      "fullName": "Họ và tên không được để trống"
    }
  }
  ```
- **`409 Conflict`** (Trùng Email hoặc Số điện thoại):
  ```json
  {
    "code": 409,
    "message": "Email 'nguyenvanan@gmail.com' đã tồn tại trong hệ thống"
  }
  ```

---

## 9. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Thêm mới thành công):** Gửi request hợp lệ với đầy đủ thông tin $\rightarrow$ Nhận HTTP `201 Created`, database lưu bản ghi với `intern_code` tự sinh và `status = PENDING`.
- [ ] **AC-2 (Chặn thiếu trường bắt buộc):** Gửi request thiếu `fullName`, `email`, `phone`, `university`, `major`, `appliedPosition` $\rightarrow$ Nhận HTTP `400 Bad Request` chỉ rõ danh sách trường vi phạm.
- [ ] **AC-3 (Chặn sai định dạng Email/Phone):** Gửi email không có `@` hoặc phone chữ cái / ít hơn 10 số $\rightarrow$ Nhận HTTP `400 Bad Request`.
- [ ] **AC-4 (Chặn trùng Email):** Gửi request với email đã tồn tại trong DB $\rightarrow$ Nhận HTTP `409 Conflict` kèm thông báo tiếng Việt.
- [ ] **AC-5 (Chặn trùng Số điện thoại):** Gửi request với SĐT đã tồn tại trong DB $\rightarrow$ Nhận HTTP `409 Conflict`.
- [ ] **AC-6 (Audit Timestamp):** Bản ghi tạo ra có `created_at` và `updated_at` khớp với thời điểm gửi request.
- [ ] **AC-7 (Không lọt Entity):** API trả về DTO `InternResponse`, tuyệt đối không phơi bày thực thể JPA trực tiếp.

---

## 10. Unit & Integration Test Checklist

### 10.1. Unit Tests (`InternProfileServiceTest.java`)
- [ ] `createIntern_withValidData_shouldSaveAndReturnResponse()`
- [ ] `createIntern_whenEmailExists_shouldThrowDuplicateResourceException()`
- [ ] `createIntern_whenPhoneExists_shouldThrowDuplicateResourceException()`
- [ ] `createIntern_shouldGenerateUniqueInternCode()`

### 10.2. Integration Tests (`InternProfileControllerTest.java`)
- [ ] `POST /api/employees/interns` - Dữ liệu hợp lệ $\rightarrow$ Trả về `201 Created`.
- [ ] `POST /api/employees/interns` - Thiếu field bắt buộc $\rightarrow$ Trả về `400 Bad Request`.
- [ ] `POST /api/employees/interns` - Email đã tồn tại $\rightarrow$ Trả về `409 Conflict`.

---

## 11. Implementation Checklist (Các File Cần Triển Khai)

- [ ] **Common:** Tạo `BaseEntity` (id, createdAt, updatedAt) trong `org.example.employeeservice.common.entity`.
- [ ] **Enum:** Tạo `InternStatus` và `Gender` trong `org.example.employeeservice.entity.enums`.
- [ ] **Entity:** Tạo `InternProfile` trong `org.example.employeeservice.entity`.
- [ ] **Repository:** Tạo `InternProfileRepository` với các hàm `existsByEmail`, `existsByPhone`, `countByCreatedAtBetween`.
- [ ] **DTO Request:** Tạo `CreateInternRequest` kèm đầy đủ Jakarta Validation annotations.
- [ ] **DTO Response:** Tạo `InternResponse` và `ApiResponse<T>` chuẩn hóa response.
- [ ] **Exception:** Tạo `DuplicateResourceException`, `ResourceNotFoundException` và cập nhật `GlobalExceptionHandler`.
- [ ] **Service:** Tạo interface `InternProfileService` và class triển khai `InternProfileServiceImpl` (`@Transactional`).
- [ ] **Controller:** Tạo `InternProfileController` tiếp nhận HTTP request và validate qua `@Valid`.
- [ ] **Verification:** Chạy `.\gradlew compileJava`, `.\gradlew test` và `.\gradlew bootJar`.

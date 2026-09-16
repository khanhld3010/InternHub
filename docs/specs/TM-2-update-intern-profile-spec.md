# Specification: Cập Nhật Thông Tin Hồ Sơ Thực Tập Sinh (Update Intern Profile)

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Cập nhật thông tin hồ sơ thực tập sinh (Update Intern Profile)
- **Jira Ticket:** [TM-2](https://robluccibn9935.atlassian.net/browse/TM-2)
- **Target Subsystems:** `employee-service` (Backend Microservice), `api-gateway` (Routing & Authentication)
- **Target Users:** HR (Nhân sự), Quản trị viên (Admin)
- **Phân loại thay đổi (Change Level):** **L3** (Ảnh hưởng API, Service Logic, Validation nghiệp vụ & Trạng thái Entity)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
Cho phép HR và Quản trị viên cập nhật, chỉnh sửa thông tin hồ sơ thực tập sinh trong quá trình tiếp nhận và quản lý:
1. **Linh hoạt chỉnh sửa thông tin:** Cho phép cập nhật thông tin cá nhân, liên lạc (bao gồm **Email** và **Số điện thoại**), học vấn, vị trí thực tập, thời gian bắt đầu/kết thúc, ghi chú và trạng thái hồ sơ.
2. **Kiểm soát tính toàn vẹn định danh:**
   - Trường `id` và mã `internCode` là bất biến (immutable), tuyệt đối không cho phép thay đổi.
   - Khi sửa `email` hoặc `phone`, hệ thống bắt buộc kiểm tra xem có bị trùng với hồ sơ của thực tập sinh khác không (loại trừ chính bản ghi đang cập nhật).
3. **Kiểm soát luồng vòng đời thực tập sinh (State Machine Validation):** Trạng thái `status` không được thay đổi tùy tiện hoặc đảo ngược vô lý, mà phải tuân thủ nghiêm ngặt theo luồng vòng đời (State Machine):
   - `PENDING` $\rightarrow$ `APPROVED` hoặc `REJECTED`
   - `APPROVED` $\rightarrow$ `INTERNING` hoặc `REJECTED`
   - `INTERNING` $\rightarrow$ `COMPLETED` hoặc `REJECTED`
   - `COMPLETED` $\rightarrow$ Trạng thái kết thúc (Terminal State), không được phép chuyển sang trạng thái khác.
   - `REJECTED` $\rightarrow$ Có thể chuyển về `PENDING` nếu mở lại xét duyệt hồ sơ.
4. **Phân quyền truy cập (Access Control):** Đảm bảo API chỉ được thực thi bởi người dùng có vai trò `HR` hoặc `ADMIN` (`@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`).
5. **Audit Trail:** Tự động cập nhật `updated_at` để ghi vết thời gian chỉnh sửa.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- Cung cấp RESTful API `PUT /api/employees/interns/{id}` tiếp nhận dữ liệu cập nhật hồ sơ thực tập sinh theo ID.
- Gắn phân quyền bảo mật `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` ở endpoint Controller.
- Tạo `UpdateInternRequest` DTO với validation chặt chẽ:
  - `fullName`: `@NotBlank`, độ dài 2 - 100 ký tự.
  - `email`: `@NotBlank`, định dạng email hợp lệ, tối đa 100 ký tự. Cho phép sửa, kiểm tra trùng lặp có loại trừ ID hiện tại.
  - `phone`: `@NotBlank`, regex 10 chữ số Việt Nam. Cho phép sửa, kiểm tra trùng lặp có loại trừ ID hiện tại.
  - `university`, `major`, `appliedPosition`: Bắt buộc (`@NotBlank`).
  - `startDate`: Bắt buộc (`@NotNull`).
  - `endDate`: Tùy chọn (`optional`), nhưng nếu có thì `endDate >= startDate`.
  - `academicYear`: Tùy chọn (`optional`, tối đa 50 ký tự).
  - `notes`: Tùy chọn (`optional`, dạng TEXT).
  - `status`: Bắt buộc (`@NotNull`, enum `InternStatus`).
- Kiểm tra hợp lệ luồng chuyển đổi trạng thái (State Machine): Nếu vi phạm ném `IllegalStateException` $\rightarrow$ HTTP `400 Bad Request`.
- Kiểm tra sự tồn tại của hồ sơ: Nếu `id` không tồn tại, trả về HTTP `404 Not Found`.
- Trả về HTTP `200 OK` kèm dữ liệu `InternResponse` sau cập nhật.

### 3.2. Ngoài phạm vi (Out of Scope)
- **Không thay đổi mã `internCode`:** Mã thực tập sinh giữ nguyên vĩnh viễn.
- **Không xử lý xóa hồ sơ (Delete):** Thuộc ticket riêng.
- **Không tự động sinh User Keycloak:** Việc tạo tài khoản đăng nhập thuộc ticket [TM-6](https://robluccibn9935.atlassian.net/browse/TM-6).
- **Không gửi Email thông báo tự động (SMTP):** Chưa kích hoạt Mail Server.

---

## 4. Potential Logic Loopholes & Mitigations (5 Edge Cases Cốt Lõi)

### 4.1. Case 1: Trùng lặp Email hoặc Phone khi sửa (Duplicate Exclusion)
- **Vấn đề:** 
  - Người dùng giữ nguyên email cũ của chính mình: Nếu query `existsByEmail(email)` thông thường sẽ báo lỗi trùng với chính mình.
  - Người dùng nhập email/phone của một thực tập sinh khác đã tồn tại trong DB.
- **Khắc phục:**
  - Định nghĩa truy vấn trong Repository:
    `existsByEmailAndIdNot(String email, Long id)`
    `existsByPhoneAndIdNot(String phone, Long id)`
  - Nếu trùng với ID khác $\rightarrow$ ném `DuplicateResourceException` $\rightarrow$ Trả về HTTP `409 Conflict`.

### 4.2. Case 2: Vi phạm luồng chuyển đổi trạng thái (Invalid Status Transition)
- **Vấn đề:** Hồ sơ đang ở trạng thái `COMPLETED` (đã hoàn thành khóa thực tập) bị cố tình chuyển ngược về `PENDING`, hoặc từ `PENDING` nhảy cóc sang `COMPLETED`.
- **Khắc phục:**
  - Xây dựng ma trận chuyển đổi hợp lệ trong Service hoặc Enum:
    - `PENDING` $\rightarrow$ `PENDING`, `APPROVED`, `REJECTED`
    - `APPROVED` $\rightarrow$ `APPROVED`, `INTERNING`, `REJECTED`
    - `INTERNING` $\rightarrow$ `INTERNING`, `COMPLETED`, `REJECTED`
    - `COMPLETED` $\rightarrow$ `COMPLETED` (Terminal)
    - `REJECTED` $\rightarrow$ `REJECTED`, `PENDING`
  - Nếu vi phạm luồng chuyển đổi $\rightarrow$ ném `IllegalStateException("Không thể chuyển đổi trạng thái từ " + currentStatus + " sang " + newStatus)` $\rightarrow$ Trả về HTTP `400 Bad Request`.

### 4.3. Case 3: Cập nhật hồ sơ không tồn tại (Stale or Non-existent ID)
- **Vấn đề:** Client gửi request cập nhật với `id` không tồn tại trong DB (`id = 999999`).
- **Khắc phục:**
  - `internProfileRepository.findById(id)` nếu rỗng $\rightarrow$ ném `ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id)`.
  - Bắt lỗi tại `GlobalExceptionHandler` $\rightarrow$ HTTP `404 Not Found`.

### 4.4. Case 4: Ngày kết thúc trước ngày bắt đầu (Invalid Date Range)
- **Vấn đề:** Client gửi `startDate = "2026-10-01"` nhưng `endDate = "2026-09-01"`.
- **Khắc phục:**
  - Validate tại tầng Service: nếu `request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())` $\rightarrow$ ném `IllegalArgumentException("Ngày kết thúc thực tập không thể trước ngày bắt đầu")` $\rightarrow$ HTTP `400 Bad Request`.

### 4.5. Case 5: Phân quyền truy cập trái phép (Unauthorized Access)
- **Vấn đề:** Người dùng không có vai trò HR hoặc ADMIN cố tình gửi request chỉnh sửa hồ sơ thực tập sinh.
- **Khắc phục:**
  - Gắn `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` ở Controller endpoint.
  - Bật `@EnableMethodSecurity` trong `SecurityConfig`.
  - Người dùng không đủ quyền sẽ nhận HTTP `403 Forbidden`.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Cập nhật hồ sơ):** Cung cấp endpoint `PUT /api/employees/interns/{id}` cho phép sửa thông tin hồ sơ của thực tập sinh.
- **FR-2 (Kiểm tra tồn tại):** Kiểm tra sự tồn tại của hồ sơ theo `id`. Nếu không thấy, trả về `404 Not Found`.
- **FR-3 (Validation dữ liệu):** Xác thực định dạng hợp lệ của tất cả các trường. `academicYear` và `notes` là tùy chọn (`optional`).
- **FR-4 (Kiểm tra trùng lặp có loại trừ ID):** Cho phép sửa email và số điện thoại, đảm bảo không trùng với hồ sơ khác (`existsByEmailAndIdNot`, `existsByPhoneAndIdNot`). Nếu trùng, trả về `409 Conflict`.
- **FR-5 (Kiểm soát State Machine):** Ràng buộc chuyển đổi trạng thái `status` theo đúng luồng vòng đời hợp lệ. Nếu vi phạm, trả về `400 Bad Request`.
- **FR-6 (Bảo toàn định danh):** Giữ nguyên `internCode`, `id`, `createdAt` của hồ sơ ban đầu.
- **FR-7 (Phân quyền truy cập):** Yêu cầu vai trò `HR` hoặc `ADMIN`.
- **FR-8 (Chuẩn hóa Response):** Trả về HTTP `200 OK` bọc trong `ApiResponse<InternResponse>`.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Bất biến định danh):** `id` và `internCode` là bất biến.
- **BR-2 (Ràng buộc Email & Phone):** Email phải hợp lệ chuẩn RFC, số điện thoại đúng 10 số di động Việt Nam. Cho phép giữ nguyên giá trị cũ của chính hồ sơ đó.
- **BR-3 (Ràng buộc khoảng thời gian):** Nếu có `endDate`, bắt buộc `endDate >= startDate`.
- **BR-4 (Quy tắc chuyển đổi trạng thái State Machine):**
  - Từ `PENDING`: Chỉ được chuyển sang `APPROVED` hoặc `REJECTED`.
  - Từ `APPROVED`: Chỉ được chuyển sang `INTERNING` hoặc `REJECTED`.
  - Từ `INTERNING`: Chỉ được chuyển sang `COMPLETED` hoặc `REJECTED`.
  - Từ `COMPLETED`: Đã hoàn thành khóa thực tập, không cho phép đổi sang trạng thái khác.
  - Từ `REJECTED`: Cho phép mở lại thành `PENDING` để tái xét duyệt.
  - Cho phép giữ nguyên trạng thái hiện tại.
- **BR-5 (Trim dữ liệu):** Tất cả các trường văn bản (`fullName`, `email`, `phone`, `university`, `major`, `appliedPosition`) phải được loại bỏ khoảng trắng thừa đầu cuối (`trim()`), riêng email chuyển về chữ thường (`toLowerCase()`).

---

## 7. Data Model (Mô Hình Dữ Liệu MySQL)

Sử dụng bảng `intern_profiles` hiện tại (kế thừa `BaseEntity`):

```sql
-- Cấu trúc bảng intern_profiles hiện có:
-- id BIGINT AUTO_INCREMENT PRIMARY KEY
-- intern_code VARCHAR(50) NOT NULL UNIQUE
-- full_name VARCHAR(100) NOT NULL
-- email VARCHAR(100) NOT NULL UNIQUE
-- phone VARCHAR(20) NOT NULL UNIQUE
-- date_of_birth DATE NULL
-- gender VARCHAR(10) NULL
-- address VARCHAR(255) NULL
-- university VARCHAR(150) NOT NULL
-- major VARCHAR(100) NOT NULL
-- academic_year VARCHAR(50) NULL
-- applied_position VARCHAR(100) NOT NULL
-- start_date DATE NOT NULL
-- end_date DATE NULL
-- status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
-- notes TEXT NULL
-- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

- **Base URL:** `/api/employees/interns`
- **Content-Type:** `application/json`

### 8.1. API Cập nhật thông tin hồ sơ thực tập sinh
- **Method:** `PUT`
- **Path:** `/api/employees/interns/{id}`
- **Security:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Headers:** `Content-Type: application/json`

#### Request Body (`UpdateInternRequest`):
```json
{
  "fullName": "Nguyễn Văn An",
  "email": "nguyenvanan.updated@gmail.com",
  "phone": "0987654321",
  "dateOfBirth": "2003-05-15",
  "gender": "MALE",
  "university": "Đại học Bách Khoa Hà Nội",
  "major": "Kỹ thuật Phần mềm",
  "academicYear": "2021-2025",
  "appliedPosition": "Backend Java Intern",
  "startDate": "2026-10-01",
  "endDate": "2026-12-31",
  "status": "APPROVED",
  "address": "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội",
  "notes": "Đã phỏng vấn đạt yêu cầu, duyệt tiếp nhận"
}
```

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Cập nhật hồ sơ thực tập sinh thành công",
  "data": {
    "id": 1,
    "internCode": "INT-202609-0001",
    "fullName": "Nguyễn Văn An",
    "email": "nguyenvanan.updated@gmail.com",
    "phone": "0987654321",
    "dateOfBirth": "2003-05-15",
    "gender": "MALE",
    "address": "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội",
    "university": "Đại học Bách Khoa Hà Nội",
    "major": "Kỹ thuật Phần mềm",
    "academicYear": "2021-2025",
    "appliedPosition": "Backend Java Intern",
    "startDate": "2026-10-01",
    "endDate": "2026-12-31",
    "status": "APPROVED",
    "notes": "Đã phỏng vấn đạt yêu cầu, duyệt tiếp nhận",
    "createdAt": "2026-09-16T10:15:00",
    "updatedAt": "2026-09-16T15:00:00"
  }
}
```

#### Response Lỗi:
- **`400 Bad Request`** (Vi phạm State Machine hoặc khoảng ngày không hợp lệ):
  ```json
  {
    "code": 400,
    "message": "Không thể chuyển đổi trạng thái từ COMPLETED sang PENDING"
  }
  ```
- **`404 Not Found`** (Không tìm thấy hồ sơ):
  ```json
  {
    "code": 404,
    "message": "Không tìm thấy hồ sơ thực tập sinh với ID: 9999"
  }
  ```
- **`409 Conflict`** (Email hoặc Số điện thoại trùng với hồ sơ khác):
  ```json
  {
    "code": 409,
    "message": "Email 'nguyenvanan.updated@gmail.com' đã tồn tại trong hệ thống"
  }
  ```
- **`403 Forbidden`** (Không có quyền HR hoặc ADMIN):
  ```json
  {
    "code": 403,
    "message": "Access Denied"
  }
  ```

---

## 9. Core Flow / Processing Flow (Luồng Xử Lý Cốt Lõi)

1. **Client Gửi Request:** Gửi HTTP `PUT /api/employees/interns/{id}` kèm JWT Token và payload `UpdateInternRequest`.
2. **Security & Validation:**
   - Spring Security xác thực vai trò (`HR` hoặc `ADMIN`). Nếu không hợp lệ $\rightarrow$ `403 Forbidden`.
   - Jakarta Validation (`@Valid`) kiểm tra định dạng dữ liệu đầu vào. Nếu sai $\rightarrow$ `400 Bad Request`.
3. **Service Execution (`@Transactional`):**
   - Bước 3.1: Tìm kiếm hồ sơ: `internProfileRepository.findById(id)`. Nếu không thấy $\rightarrow$ ném `ResourceNotFoundException`.
   - Bước 3.2: Kiểm tra khoảng ngày: Nếu `endDate != null && endDate.isBefore(startDate)` $\rightarrow$ ném `IllegalArgumentException`.
   - Bước 3.3: Kiểm tra luồng State Machine: Gọi `validateStatusTransition(currentStatus, newStatus)`. Nếu vi phạm $\rightarrow$ ném `IllegalStateException`.
   - Bước 3.4: Kiểm tra trùng Email có loại trừ ID: `existsByEmailAndIdNot(email, id)`. Nếu `true` $\rightarrow$ ném `DuplicateResourceException`.
   - Bước 3.5: Kiểm tra trùng Số điện thoại có loại trừ ID: `existsByPhoneAndIdNot(phone, id)`. Nếu `true` $\rightarrow$ ném `DuplicateResourceException`.
   - Bước 3.6: Cập nhật các trường thông tin vào Entity đã tải, bảo toàn `id` và `internCode`.
   - Bước 3.7: Lưu bản ghi `internProfileRepository.save(profile)` (tự động cập nhật `updatedAt`).
4. **Response Mapping:** Chuyển đổi Entity sang `InternResponse` và trả về qua `ApiResponse.success(200, "Cập nhật hồ sơ thực tập sinh thành công", response)`.

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

- **Framework & Runtime:** Java 17/21, Spring Boot 4.x / 3.x, Spring Data JPA, Spring Security Method Security.
- **Package-by-Feature:** Toàn bộ code đặt trong `org.example.employeeservice.intern.*`.
- **Concurrency & Integrity:** Ràng buộc `unique` cấp Database cho `email` và `phone`; thao tác Service nằm trong `@Transactional`.
- **Coding Standard:** Constructor injection bằng `@RequiredArgsConstructor`, không dùng `@Autowired` trường, không trả Entity JPA trực tiếp.
- **Logging:** Ghi log bắt đầu và hoàn tất cập nhật với cấp độ `INFO`, log các ngoại lệ với cấp độ `WARN`.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Cập nhật thành công):** Gửi request cập nhật hợp lệ cho ID tồn tại bởi HR/ADMIN $\rightarrow$ Trả về HTTP `200 OK`, dữ liệu trong DB được cập nhật chính xác, `updated_at` được làm mới.
- [ ] **AC-2 (Giữ nguyên Email/Phone không bị báo trùng):** Cập nhật hồ sơ với email và phone giữ nguyên giá trị cũ của chính hồ sơ đó $\rightarrow$ Cập nhật thành công, không bị lỗi 409 Conflict.
- [ ] **AC-3 (Chặn trùng Email với người khác):** Đổi email thành một email đã thuộc về hồ sơ khác $\rightarrow$ Trả về HTTP `409 Conflict`.
- [ ] **AC-4 (Chặn trùng Số điện thoại với người khác):** Đổi số điện thoại thành SĐT đã thuộc về hồ sơ khác $\rightarrow$ Trả về HTTP `409 Conflict`.
- [ ] **AC-5 (Ràng buộc State Machine):** Chuyển đổi trạng thái vi phạm luồng (ví dụ `COMPLETED` $\rightarrow$ `PENDING`) $\rightarrow$ Trả về HTTP `400 Bad Request`.
- [ ] **AC-6 (Xử lý ID không tồn tại):** Gửi cập nhật với `id` không tồn tại trong hệ thống $\rightarrow$ Trả về HTTP `404 Not Found`.
- [ ] **AC-7 (Chặn ngày kết thúc trước ngày bắt đầu):** Gửi `endDate < startDate` $\rightarrow$ Trả về HTTP `400 Bad Request`.
- [ ] **AC-8 (Bất biến InternCode):** Mã `internCode` không bị thay đổi dù payload có gửi kèm trường nào.
- [ ] **AC-9 (Phân quyền Security):** Chỉ cho phép `HR` hoặc `ADMIN` gọi endpoint cập nhật.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Unit Tests (`InternProfileServiceTest.java`)
- [ ] **UT-BE-01:** `updateIntern_withValidData_shouldUpdateAndReturnResponse()`: Cập nhật thành công khi dữ liệu hợp lệ.
- [ ] **UT-BE-02:** `updateIntern_whenIdNotFound_shouldThrowResourceNotFoundException()`: Ném lỗi 404 khi không tìm thấy hồ sơ.
- [ ] **UT-BE-03:** `updateIntern_whenEmailExistsForOther_shouldThrowDuplicateResourceException()`: Ném lỗi 409 khi trùng email với người khác.
- [ ] **UT-BE-04:** `updateIntern_whenPhoneExistsForOther_shouldThrowDuplicateResourceException()`: Ném lỗi 409 khi trùng SĐT với người khác.
- [ ] **UT-BE-05:** `updateIntern_whenEndDateBeforeStartDate_shouldThrowIllegalArgumentException()`: Ném lỗi 400 khi khoảng ngày không hợp lệ.
- [ ] **UT-BE-06:** `updateIntern_whenInvalidStatusTransition_shouldThrowIllegalStateException()`: Ném lỗi 400 khi vi phạm State Machine (ví dụ COMPLETED sang PENDING).
- [ ] **UT-BE-07:** `updateIntern_whenEmailAndPhoneUnchanged_shouldUpdateSuccessfully()`: Cập nhật thành công khi email/SĐT giữ nguyên của chính mình.

### 12.2. Controller Tests (`InternProfileControllerTest.java`)
- [ ] **IT-BE-01:** `updateIntern_withValidPayload_shouldReturn200AndApiResponse()`: Controller gọi Service thành công và trả về `200 OK`.
- [ ] **IT-BE-02:** `updateIntern_whenValidationFails_shouldHandleBadRequest()`: Kiểm tra bắt lỗi validate khi thiếu trường bắt buộc.

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

- [ ] **Repository:** Thêm phương thức `existsByEmailAndIdNot(String email, Long id)` và `existsByPhoneAndIdNot(String phone, Long id)` vào `InternProfileRepository`.
- [ ] **DTO Request:** Tạo class `UpdateInternRequest` trong package `org.example.employeeservice.intern.dto.request` kèm đầy đủ validation.
- [ ] **Exception:** Bổ sung bắt ngoại lệ `IllegalStateException` trong `GlobalExceptionHandler` trả về HTTP `400 Bad Request`.
- [ ] **Security Config:** Thêm `@EnableMethodSecurity` vào `SecurityConfig` để kích hoạt kiểm tra `@PreAuthorize`.
- [ ] **Service Interface:** Khai báo phương thức `InternResponse updateIntern(Long id, UpdateInternRequest request)` trong `InternProfileService`.
- [ ] **Service Implementation:** Triển khai phương thức `updateIntern` trong `InternProfileServiceImpl` kèm logic State Machine và `@Transactional`.
- [ ] **Controller:** Bổ sung endpoint `@PutMapping("/{id}")` kèm `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` trong `InternProfileController`.
- [ ] **Tests:** Bổ sung các unit test cases trong `InternProfileServiceTest` và `InternProfileControllerTest`.
- [ ] **Verification:** Chạy `./gradlew compileJava`, `./gradlew test` và `./gradlew bootJar` đảm bảo 100% pass và build thành công.

# Tasks: Triển Khai Tính Năng Tìm Kiếm và Lọc Hồ Sơ Thực Tập Sinh (TM-3)

---

## 📌 Danh Sách Đầu Việc Kỹ Thuật (Task Breakdown)

### 1. Phân Tầng DTO & Specification
- [x] **Task 1.1:** Tạo class dùng chung `PageResponse<T>` tại `employee-service/src/main/java/org/example/employeeservice/common/dto/response/PageResponse.java` đóng gói metadata phân trang (`items`, `currentPage`, `pageSize`, `totalItems`, `totalPages`, `isFirst`, `isLast`, `hasNext`, `hasPrevious`).
- [x] **Task 1.2:** Tạo class `InternFilterRequest` tại `employee-service/src/main/java/org/example/employeeservice/intern/dto/request/InternFilterRequest.java` chứa các tiêu chí lọc (`keyword`, `university`, `major`, `appliedPosition`, `status`).
- [x] **Task 1.3:** Tạo class `InternProfileSpecification` tại `employee-service/src/main/java/org/example/employeeservice/intern/repository/specification/InternProfileSpecification.java` xây dựng dynamic query predicates với JPA Criteria API.

### 2. Service Layer & Business Logic
- [x] **Task 2.1:** Cập nhật interface `InternProfileService` bổ sung `PageResponse<InternResponse> searchInterns(InternFilterRequest request, Pageable pageable);`.
- [x] **Task 2.2:** Cài đặt hàm `searchInterns` trong `InternProfileServiceImpl` với `@Transactional(readOnly = true)`, thực hiện query qua `InternProfileRepository.findAll(Specification, Pageable)` và map sang `PageResponse<InternResponse>`.

### 3. Controller Layer & Security
- [x] **Task 3.1:** Bổ sung endpoint `GET /api/employees/interns` trong `InternProfileController` tiếp nhận filter request và `Pageable` (`@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC, size = 10)`).
- [x] **Task 3.2:** Gắn annotation bảo mật `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`.

### 4. Unit & Integration Testing
- [x] **Task 4.1:** Viết Unit Test cho Service trong `InternProfileServiceTest.java` (7 test cases kiểm thử các trường hợp keyword, filter, sort, empty result).
- [x] **Task 4.2:** Viết WebMvc Test cho Controller trong `InternProfileControllerTest.java` (4 test cases kiểm thử authentication, authorization và response format).

### 5. Verification & Quality Gate
- [x] **Task 5.1:** Chạy `.\gradlew compileJava` kiểm tra biên dịch không lỗi (BUILD SUCCESSFUL).
- [x] **Task 5.2:** Chạy `.\gradlew test` kiểm tra toàn bộ test suite pass 100% (BUILD SUCCESSFUL).
- [x] **Task 5.3:** Đối chiếu từng tiêu chí chấp nhận (AC-1 đến AC-7).

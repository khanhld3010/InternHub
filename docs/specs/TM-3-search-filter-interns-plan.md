# Technical Plan: Tìm Kiếm và Lọc Hồ Sơ Thực Tập Sinh (TM-3)

---

## 1. File Changes Matrix (Danh Sách File Tạo Mới & Sửa Đổi)

| Thao tác | Đường dẫn file | Mục đích / Trách nhiệm |
| :---: | :--- | :--- |
| **NEW** | `employee-service/.../common/dto/response/PageResponse.java` | DTO đóng gói metadata phân trang chuẩn hóa cho toàn service |
| **NEW** | `employee-service/.../intern/dto/request/InternFilterRequest.java` | DTO chứa các query params tìm kiếm & lọc dữ liệu |
| **NEW** | `employee-service/.../intern/repository/specification/InternProfileSpecification.java` | JPA Specification xây dựng Criteria Predicates an toàn |
| **MODIFY** | `employee-service/.../intern/service/InternProfileService.java` | Khai báo method `searchInterns(InternFilterRequest, Pageable)` |
| **MODIFY** | `employee-service/.../intern/service/impl/InternProfileServiceImpl.java` | Xử lý logic nghiệp vụ, sanitize Sort fields, giới hạn page size |
| **MODIFY** | `employee-service/.../intern/controller/InternProfileController.java` | Endpoint `GET /api/employees/interns` kèm `@PreAuthorize` |
| **MODIFY** | `employee-service/.../intern/service/InternProfileServiceTest.java` | Bổ sung 7 unit tests kiểm thử logic tìm kiếm, lọc và phân trang |
| **MODIFY** | `employee-service/.../intern/controller/InternProfileControllerTest.java` | Bổ sung WebMvc tests kiểm thử phân quyền và endpoint contract |

---

## 2. Risk Assessment & Mitigations (Đánh Giá Rủi Ro)

1. **Rủi ro SQL Wildcard Injection:**
   - *Nguy cơ:* Ký tự `%` và `_` trong query làm kết quả sai lệch.
   - *Giải pháp:* Hàm helper tự động escape trước khi đưa vào Criteria API.
2. **Rủi ro Sort Field không tồn tại (PropertyReferenceException):**
   - *Nguy cơ:* Client truyền field lạ gây crash 500.
   - *Giải pháp:* Whitelist validation, fallback an toàn về `createdAt,desc`.
3. **Rủi ro Memory Exhaustion (DoS qua Page Size):**
   - *Nguy cơ:* Client yêu cầu `size=1000000`.
   - *Giải pháp:* Hard limit trần tối đa `100` phần tử/trang.

---

## 3. Verification Commands
```powershell
cd employee-service; .\gradlew compileJava; cd ..
cd employee-service; .\gradlew test; cd ..
cd employee-service; .\gradlew bootJar; cd ..
```

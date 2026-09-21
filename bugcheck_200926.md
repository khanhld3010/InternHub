# 🐛 BÁO CÁO RÀ SOÁT LỖI VÀ ĐIỂM YẾU HỆ THỐNG (BUG AUDIT REPORT)
**Mã báo cáo:** `bugcheck_200926`  
**Ngày thực hiện:** 20/09/2026  
**Dự án:** InternHub (Backend Microservices)  
**Phạm vi rà soát:** `employee-service`, `api-gateway`, `config-server`, `discovery-server`

---

## 📌 BẢNG TỔNG HỢP DANH SÁCH LỖI & ĐIỂM YẾU

| STT | Mã Lỗi | Tên Lỗi / Điểm Yếu | Mức Độ | Trạng Thái / Ghi Chú Đội Ngũ | File Ảnh Hưởng |
| :---: | :---: | :--- | :---: | :---: | :--- |
| 1 | **BUG-01** | Chưa chặn phân quyền tại API tạo mới thực tập sinh | 🔴 High | ⚠️ **Team đang tạm bỏ qua** để thuận tiện test & dev nhanh | `InternProfileController.java` |
| 2 | **BUG-02** | Bỏ sót kiểm tra logic ngày (`endDate < startDate`) khi Tạo mới | 🔴 High | Cần bổ sung validate | `InternProfileServiceImpl.java` |
| 3 | **BUG-03** | Race Condition & Trùng mã `internCode` khi sinh mã tự động | 🔴 High | Nguy cơ crash 500 khi chịu tải | `InternProfileServiceImpl.java` |
| 4 | **BUG-04** | Xung đột kiến trúc: Tồn tại 2 class `ApiResponse` khác nhau | 🟡 Medium | Không đồng nhất schema response | `AuthController.java`, `dto.response.*` |
| 5 | **BUG-05** | Chưa cấu hình CORS tại Gateway và Microservice | 🟡 Medium | Chặn Frontend gọi API từ domain khác | `SecurityConfig.java`, `api-gateway.yml` |
| 6 | **BUG-06** | Thiếu bộ xử lý lỗi tập trung ở tầng Filter (401/403) | 🟡 Medium | Trả về lỗi Spring mặc định | `SecurityConfig.java` |
| 7 | **BUG-07** | Chưa tắt Eureka Client trong môi trường Unit Test | 🟢 Low | Bắn log lỗi kết nối đỏ khi chạy test | `application-test.yml` |
| 8 | **BUG-08** | Truy vấn Database liên tục trên từng Request dù dùng JWT | 🟢 Low | Gây tải DB, làm mất tính stateless | `JwtAuthenticationFilter.java` |
| 9 | **BUG-09** | Thiếu cấu hình datasource fallback khi chạy Local độc lập | 🟢 Low | Crash app nếu không chạy config-server | `employee-service/.../application.yml` |

---

## 🔴 PHẦN 1: CÁC VẤN ĐỀ MỨC ĐỘ CAO (HIGH & CRITICAL)

### 1. BUG-01: Chưa chặn phân quyền tại API tạo mới thực tập sinh
* **Vị trí mã nguồn:** [`InternProfileController.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/controller/InternProfileController.java) (dòng 36 - 42)
* **Ghi chú đội ngũ (Team Status):**
  > [!NOTE]
  > Hiện tại đội ngũ phát triển đang **chủ động bỏ qua phân quyền** tại endpoint này để thuận tiện trong quá trình xây dựng tính năng và kiểm thử nhanh chóng giữa các thành viên.
* **Chi tiết kỹ thuật:**
  * Endpoint `POST /api/employees/interns` không được gắn `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`.
  * Trong cấu hình [`SecurityConfig.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/config/SecurityConfig.java), endpoint này thuộc phạm vi `.anyRequest().authenticated()`.
  * Bất kỳ người dùng nào có Token hợp lệ (kể cả role `Intern`) hiện đều có thể gửi request tạo hồ sơ.
* **Khuyến nghị trước khi Release Production:**
  * Bổ sung `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` lên trên method `createIntern()` để đúng với đặc tả [TM-1 Spec](file:///c:/Users/Admin/InternHub/docs/specs/TM-1-create-intern-profile-spec.md).

---

### 2. BUG-02: Bỏ sót kiểm tra logic ngày (`endDate < startDate`) khi Tạo mới hồ sơ
* **Vị trí mã nguồn:** [`InternProfileServiceImpl.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/service/impl/InternProfileServiceImpl.java) (dòng 49 - 84)
* **Hiện tượng & Nguyên nhân:**
  * Tại hàm cập nhật `updateIntern()` (dòng 94-96), hệ thống có validate:
    ```java
    if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
        throw new IllegalArgumentException("Ngày kết thúc thực tập không thể trước ngày bắt đầu");
    }
    ```
  * Tuy nhiên, trong hàm `createIntern()`, đoạn kiểm tra này **bị bỏ quên**.
* **Hậu quả:**
  * Người dùng có thể submit `startDate: "2026-10-01"` và `endDate: "2020-01-01"`. Hệ thống vẫn lưu thành công vào MySQL, gây sai lệch dữ liệu logic thời gian thực tập.
* **Khuyến nghị khắc phục:**
  * Bổ sung đoạn kiểm tra `endDate.isBefore(startDate)` vào hàm `createIntern()` hoặc tạo custom validator annotation ở tầng `CreateInternRequest`.

---

### 3. BUG-03: Race Condition & Lỗi trùng lặp mã khi sinh `internCode`
* **Vị trí mã nguồn:** [`InternProfileServiceImpl.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/service/impl/InternProfileServiceImpl.java) (dòng 137 - 144)
  ```java
  private synchronized String generateInternCode() {
      String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
      LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
      LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

      long count = internProfileRepository.countByCreatedAtBetween(startOfMonth, endOfMonth) + 1;
      return String.format("INT-%s-%04d", yearMonth, count);
  }
  ```
* **Hiện tượng & Nguyên nhân:**
  1. **Thoát lock trước khi commit Transaction:** Từ khóa `synchronized` chỉ khóa trong hàm `generateInternCode()`. Ngay sau khi hàm trả về chuỗi mã, lock được giải phóng trong khi transaction của hàm `createIntern()` vẫn chưa commit vào Database. Hai request gửi song song sẽ cùng đọc một giá trị `count` và sinh ra mã trùng nhau.
  2. **Vô hiệu hóa khi scale Microservices:** Từ khóa `synchronized` chỉ có tác dụng trong 1 JVM cục bộ. Khi `employee-service` được triển khai nhiều instance (cluster), 2 request đến 2 container khác nhau sẽ đọc cùng giá trị.
  3. **Lỗi khi có dữ liệu bị xóa:** Cơ chế dùng `count + 1` giả định không có dòng nào bị xóa. Nếu trong tháng có bản ghi bị xóa hoặc dọn dẹp, `count + 1` sẽ tái tạo lại mã của bản ghi đã tồn tại trước đó.
* **Hậu quả:**
  * Cột `intern_code` trong bảng `intern_profiles` có ràng buộc `UNIQUE`. Khi bị trùng, database ném `DataIntegrityViolationException`, trả về lỗi HTTP `500 Internal Server Error` cho người dùng.
* **Khuyến nghị khắc phục:**
  * Sử dụng câu truy vấn tìm mã lớn nhất trong tháng: `SELECT MAX(intern_code) FROM intern_profiles WHERE ...` rồi cộng 1, kết hợp cơ chế thử lại (Retry) khi bắt gặp trùng lặp mã.
  * Hoặc dùng Redis Atomic Counter / Database Sequence Table chuyên dụng để cấp phát mã.

---

## 🟡 PHẦN 2: CÁC VẤN ĐỀ MỨC ĐỘ TRUNG BÌNH (MEDIUM)

### 4. BUG-04: Xung đột kiến trúc: Tồn tại 2 class `ApiResponse` độc lập
* **Vị trí mã nguồn:** 
  1. [`org.example.employeeservice.dto.response.ApiResponse`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/dto/response/ApiResponse.java) (chứa `success: boolean`, `message: String`, `data: T`, `timestamp`)
  2. [`org.example.employeeservice.common.dto.response.ApiResponse`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/common/dto/response/ApiResponse.java) (chứa `code: int`, `message: String`, `data: T`, `errors: Object`, `timestamp`)
* **Hiện tượng & Nguyên nhân:**
  * [`AuthController.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/controller/AuthController.java) sử dụng `dto.response.ApiResponse`.
  * [`InternProfileController.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/intern/controller/InternProfileController.java) và [`GlobalExceptionHandler.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/exception/GlobalExceptionHandler.java) sử dụng `common.dto.response.ApiResponse`.
* **Hậu quả:**
  * Vi phạm quy chuẩn Package-by-Feature và Shared DTO quy định tại `.antigravity/rules.md`.
  * Khi gọi API Login thành công: Client nhận JSON chứa `{ success: true, ... }` (không có `code`).
  * Khi gọi API Login thất bại (sai pass): `GlobalExceptionHandler` trả về JSON chứa `{ code: 401, ... }` (không có `success`).
  * Frontend không thể định nghĩa một schema Response thống nhất cho toàn bộ hệ thống.
* **Khuyến nghị khắc phục:**
  * Hợp nhất sử dụng một class duy nhất là `common.dto.response.ApiResponse`.
  * Xóa bỏ class thừa `dto.response.ApiResponse` và cập nhật lại `AuthController`.

---

### 5. BUG-05: Chưa cấu hình CORS tại Gateway và Microservice
* **Vị trí mã nguồn:** [`SecurityConfig.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/config/SecurityConfig.java) và [`api-gateway.yml`](file:///c:/Users/Admin/InternHub/config-repo-local/api-gateway.yml)
* **Hiện tượng & Nguyên nhân:**
  * Không có cấu hình `CorsWebFilter` hoặc `spring.cloud.gateway.globalcors` tại `api-gateway`.
  * Không có cấu hình `cors(Customizer.withDefaults())` tại `SecurityConfig` của `employee-service`.
* **Hậu quả:**
  * Khi ứng dụng Web Frontend (React/Vue/Angular chạy ở port 3000, 5173, ...) gọi API, trình duyệt sẽ gửi request kiểm tra `OPTIONS` (Preflight request). Request này sẽ bị từ chối với lỗi CORS: `No 'Access-Control-Allow-Origin' header is present on the requested resource`.
* **Khuyến nghị khắc phục:**
  * Bổ sung cấu hình `globalcors` trong `config-repo-local/api-gateway.yml` cho phép các HTTP methods (`GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`) và headers (`Authorization`, `Content-Type`).

---

### 6. BUG-06: Thiếu bộ xử lý lỗi tập trung ở tầng Filter (401/403)
* **Vị trí mã nguồn:** [`SecurityConfig.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/config/SecurityConfig.java) (dòng 39 - 57)
* **Hiện tượng & Nguyên nhân:**
  * Khi client gọi vào một API cần xác thực nhưng không gửi token, hoặc gửi token không hợp lệ/hết hạn, request sẽ bị chặn ngay tại Filter Chain.
  * `@RestControllerAdvice` (`GlobalExceptionHandler`) chỉ bắt được exception xảy ra tại Controller hoặc Service, **không bắt được exception ở Filter Chain**.
* **Hậu quả:**
  * Khách hàng nhận về mã lỗi 401/403 dạng cấu trúc lỗi mặc định của Spring Boot (HTML hoặc JSON mặc định), không theo chuẩn `ApiResponse` đồng bộ của dự án.
* **Khuyến nghị khắc phục:**
  * Cấu hình thêm `AuthenticationEntryPoint` và `AccessDeniedHandler` trong `SecurityConfig` để ghi đè response về định dạng JSON `ApiResponse.error(401, "...")`.

---

## 🟢 PHẦN 3: CÁC VẤN ĐỀ MỨC ĐỘ THẤP & TỐI ƯU HÓA (LOW & CODE SMELL)

### 7. BUG-07: Chưa tắt Eureka Client trong môi trường Unit Test
* **Vị trí mã nguồn:** [`application-test.yml`](file:///c:/Users/Admin/InternHub/employee-service/src/test/resources/application-test.yml)
* **Hiện tượng:**
  * Khi chạy `.\gradlew test`, log xuất hiện hàng loạt cảnh báo đỏ:
    `TransportException: Cannot execute request on any known server`
    `Connect to http://localhost:8761 failed: Connection refused`
* **Nguyên nhân:** File `application-test.yml` thiếu cấu hình ngắt kết nối Eureka:
  ```yaml
  eureka:
    client:
      enabled: false
  ```
* **Khuyến nghị khắc phục:** Bổ sung cấu hình trên vào `application-test.yml` để test chạy nhanh hơn và log sạch sẽ.

---

### 8. BUG-08: Truy vấn Database liên tục trên từng Request dù dùng JWT
* **Vị trí mã nguồn:** [`JwtAuthenticationFilter.java`](file:///c:/Users/Admin/InternHub/employee-service/src/main/java/org/example/employeeservice/security/JwtAuthenticationFilter.java) (dòng 40)
  ```java
  UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
  ```
* **Vấn đề:** Bản chất của JWT là mang theo thông tin tự thân (Stateless). Token đã lưu sẵn `role` và `userId`. Việc gọi lại `loadUserByUsername()` ép MySQL phải thực hiện 1 câu `SELECT` trên bảng `accounts` ở mọi HTTP request.
* **Khuyến nghị khắc phục:** Trích xuất quyền hạn (Authorities) và UserId trực tiếp từ Claims của Token để gán vào `SecurityContextHolder`, không cần query lại DB.

---

### 9. BUG-09: Thiếu cấu hình datasource fallback khi chạy Local độc lập
* **Vị trí mã nguồn:** [`employee-service/src/main/resources/application.yml`](file:///c:/Users/Admin/InternHub/employee-service/src/main/resources/application.yml)
* **Hiện tượng:** File `application.yml` nội bộ chỉ có lệnh `import: optional:configserver:...`. Khi dev chạy trực tiếp `EmployeeServiceApplication.java` từ IDE mà không bật `config-server`, ứng dụng bị crash do thiếu `spring.datasource.url`.
* **Khuyến nghị khắc phục:** Bổ sung cấu hình profile `local` hoặc thiết lập giá trị mặc định cho `spring.datasource` tại file `application.yml` nội bộ của service.

---

*Báo cáo được lập tự động bởi Agentic Coding Assistant dựa trên phân tích trực tiếp mã nguồn của dự án InternHub.*

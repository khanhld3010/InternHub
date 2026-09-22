# 03. Quy Định Cần Tuân Thủ & Ranh Giới An Toàn (Compliance & Constraints)

Tài liệu này quy định các tiêu chuẩn bắt buộc về an toàn bảo mật, bảo vệ toàn vẹn cơ sở dữ liệu, quản lý bí mật hệ thống (secrets) và ranh giới phân định phạm vi dự án khi phát triển **InternHub (Backend)**.

---

## 1. Bảo Mật & Phân Quyền (Authentication & RBAC)

### 1.1. Quản lý JWT & Chuẩn Cơ Chế Xác Thực
- **Cơ chế xác thực tập trung**: Toàn bộ request từ bên ngoài vào hệ thống đều đi qua `api-gateway` (Port `8080`).
- **Authorization Header**: Client bắt buộc phải đính kèm Header:
  ```http
  Authorization: Bearer <jwt_access_token>
  ```
- **Xác thực tại Resource Services**:
  - Mỗi microservice sử dụng `JwtAuthenticationFilter` để giải mã và xác thực chữ ký của Token dựa trên cấu hình bảo mật được cung cấp bởi `config-server`.
  - Thông tin người dùng (`userId`, `username`, `roles`) sau khi xác thực thành công sẽ được đưa vào `SecurityContextHolder`.

### 1.2. Kiểm Soát Phân Quyền Theo Vai Trò (Role-Based Access Control - RBAC)
Dự án InternHub phân chia thành 4 vai trò chính:
1. **`ROLE_ADMIN`**: Quản trị viên cao nhất; quản lý tài khoản người dùng, phân quyền, xem log hệ thống, cấu hình toàn cục.
2. **`ROLE_HR`**: Quản lý nhân sự; quản lý chương trình thực tập, tiếp nhận hồ sơ, phân công mentor, xem báo cáo tổng hợp.
3. **`ROLE_MENTOR`**: Người hướng dẫn; theo dõi tiến độ thực tập sinh được giao, giao nhiệm vụ, chấm điểm và đánh giá kỳ thực tập.
4. **`ROLE_INTERN`**: Thực tập sinh; cập nhật hồ sơ cá nhân, xem chương trình thực tập, nộp báo cáo, xem kết quả đánh giá.

### 1.3. Nghiêm Cấm Bypass Cơ Chế Bảo Mật
> [!CAUTION]
> **TUYỆT ĐỐI CẤM TẠO CỜ BYPASS HOẶC MOCK USER ĐỂ NÉ TRÁNH XÁC THỰC:**
>
> 1. **Không mở `permitAll()` tùy tiện**: Tuyệt đối không mở `permitAll()` cho các endpoint nghiệp vụ nhạy cảm trong `SecurityConfig` nhằm mục đích "test cho nhanh".
> 2. **Không fake user trong `SecurityContextHolder`**: Cấm viết code gán cứng tài khoản ảo vào `SecurityContext` trong mã nguồn chính.
> 3. **Không hardcode JWT Secret**: Cấm hardcode chuỗi secret key giải mã JWT trực tiếp trong class Java. Toàn bộ secret key phải được quản lý qua biến môi trường hoặc `config-server`.

---

## 2. Bảo Vệ Toàn Vẹn Cơ Sở Dữ Liệu (Database Integrity & Protection)

> [!CAUTION]
> **NGUYÊN TẮC BẤT KHẢ XÂM PHẠM VỀ BẢO VỆ DATABASE:**
>
> Database `internhub_db` chứa dữ liệu nghiệp vụ quan trọng phục vụ vận hành. Mọi hành vi làm mất mát dữ liệu hoặc làm sai lệch schema đều gây thiệt hại nghiêm trọng.

### 2.1. Nghiêm Cấm Chạy Lệnh SQL Phá Hoại (Destructive SQL)
- **Tuyệt đối CẤM**:
  - Không tự ý thực thi các lệnh phá hoại cấu trúc: `DROP DATABASE`, `DROP TABLE`, `TRUNCATE TABLE`.
  - Không tự ý chạy `ALTER TABLE` xóa cột dữ liệu hoặc thay đổi kiểu dữ liệu gây mất mát dữ liệu lịch sử.
  - Không tự ý chạy `DELETE FROM` diện rộng mà không có mệnh đề `WHERE` cụ thể được phê duyệt trước.
- **Quy trình thay đổi Schema**:
  - Mọi thay đổi về cấu trúc bảng bắt buộc phải được thiết kế thông qua JPA Entity ánh xạ (`BaseEntity`, annotations Hibernate).
  - Nếu sử dụng script di trú dữ liệu (Flyway/Liquibase/SQL scripts), phải có file Kế hoạch (`Plan`) nêu rõ: **Cột cần thêm/sửa**, **Mục đích nghiệp vụ**, và **Phương án khôi phục (Rollback Script)** trước khi chạy.

### 2.2. Kiểm Thử Bằng Dữ Liệu Thực Tế & Xử Lý Sự Cố Database
- **Dữ liệu thực tế**: Mọi kiểm thử phải dùng tài khoản và dữ liệu thực tế đang tồn tại trong Database. Khi cần tài khoản test theo quyền (`ADMIN`, `HR`, `MENTOR`, `INTERN`), Agent **bắt buộc phải hỏi người dùng cung cấp**.
- **Cấm nhét dữ liệu rác**: Không tự ý viết script nhét hàng loạt dữ liệu vô nghĩa (`test1`, `asdf`, `123456`) vào các bảng chính.
- **CHỈ THỊ ĐẶC BIỆT KHI DATABASE GẶP SỰ CỐ**:
  - Nếu trong quá trình phát triển/kiểm thử phát hiện:
    + Container `mysql-db` bị tắt hoặc crash.
    + Lỗi cạn kiệt kết nối: `HikariPool-1 - Connection is not available, request timed out after 30000ms`.
    + Lỗi vi phạm ràng buộc khóa ngoại (Foreign Key Constraint Violation) do dữ liệu thiếu đồng bộ.
  - **Hành động bắt buộc**: Agent **TUYỆT ĐỐI KHÔNG ĐƯỢC TỰ Ý SỬA LỤI DATABASE HAY BẬT MOCK**, mà **BẮT BUỘC PHẢI DỪNG LẠI LẬP TỨC, TRÍCH XUẤT LOG LỖI VÀ BÁO CÁO CHI TIẾT CHO NGƯỜI DÙNG** để cùng kiểm tra.

---

## 3. Quản Lý Thông Tin Nhạy Cảm (Secrets & Credentials)

1. **Không commit Secret vào Git**:
   - Tuyệt đối cấm commit mật khẩu Database (`MYSQL_ROOT_PASSWORD`), secret key ký JWT, hoặc private API keys vào repository.
2. **Cấu hình biến môi trường**:
   - Mọi thông tin nhạy cảm phải được định nghĩa dưới dạng biến môi trường (Environment Variables) trong `docker-compose.yml` hoặc nạp qua Spring Cloud Config Server:
     ```yaml
     spring:
       datasource:
         url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3307}/${DB_NAME:internhub_db}
         username: ${DB_USERNAME:root}
         password: ${DB_PASSWORD:123456}
     ```
3. **Cấu hình CORS an toàn**:
   - Tại `api-gateway`, cấu hình CORS phải giới hạn đúng các origin được phép truy cập (ví dụ: `http://localhost:5173` cho Frontend Vite Dev Server), cấm mở `*` (Allow All) một cách vô tội vạ trong môi trường sản xuất.

---

## 4. Ranh Giới Cô Lập Giữa Các Phân Hệ (Boundary Isolation)

> [!CAUTION]
> **RANH GIỚI BẮT BUỘC: ĐANG LÀM BACKEND ➔ CẤM TỰ Ý SỬA FRONTEND**

1. **Không can thiệp chéo**:
   - Khi nhận nhiệm vụ làm việc với Backend (`InternHub/`), Agent **TUYỆT ĐỐI KHÔNG ĐƯỢC TỰ Ý MỞ VÀ SỬA MÃ NGUỒN FRONTEND** (`InternHub-Frontend/`).
2. **Xử lý khi thay đổi API Contract**:
   - Nếu trong quá trình phát triển Backend mà cấu trúc API (Endpoint URL, Request Body DTO, Response Body DTO) bắt buộc phải thay đổi:
     + Agent **BẮT BUỘC PHẢI THÔNG BÁO VÀ TRÌNH BÀY RÕ RÀNG TRONG PLAN**: API cũ là gì, API mới là gì, ảnh hưởng thế nào đến Frontend.
     + Chờ người dùng xem xét, phê duyệt và chỉ thị tiếp theo.

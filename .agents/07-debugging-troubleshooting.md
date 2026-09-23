# 07. Hướng Dẫn Sửa Chữa & Xử Lý Sự Cố (Debugging & Troubleshooting)

Tài liệu này cung cấp phương pháp chuẩn để điều tra, cô lập và khắc phục các sự cố kỹ thuật đặc thù trong hệ thống **InternHub (Backend Microservices)**.

---

## 1. Nguyên Tắc Cốt Lõi Khi Sửa Lỗi (Core Principles)

1. **Tìm Nguyên Nhân Gốc Rễ (Root Cause Analysis)**:
   - Tuyệt đối không áp dụng các giải pháp "chữa cháy" tạm thời (monkey patch).
   - **CẤM** giấu lỗi bằng khối `catch (Exception e) {}` rỗng hoặc chỉ in log mà không xử lý.
   - Luôn đặt câu hỏi: *Lỗi này xuất phát từ tầng nào? Cấu hình mạng Docker, Discovery Server, Spring Security, logic Service hay tầng JPA/Database?*
2. **Tuân thủ quy trình xin phép trước khi sửa**:
   - Dù là lỗi nhỏ hay lớn, sau khi tìm ra nguyên nhân, Agent phải lập Kế hoạch đề xuất (`implementation_plan.md`) giải thích rõ nguyên nhân và cách khắc phục để người dùng phê duyệt trước khi sửa code.
3. **Tuân thủ ranh giới cách ly (Boundary Isolation)**:
   - Nếu điều tra thấy lỗi do Frontend gửi sai payload (sai tên trường, sai kiểu dữ liệu), **tuyệt đối không được tự ý sang thư mục Frontend sửa code**.
   - Bắt buộc phải thông báo và trích dẫn rõ sự sai khác giữa API Contract thực tế và Payload nhận được cho người dùng.

---

## 2. Quy Trình 5 Bước Xử Lý Sự Cố (5-Step Troubleshooting Protocol)

```
[1. Tái hiện lỗi] ➔ [2. Trích xuất Log & Stacktrace] ➔ [3. Cô lập tầng kỹ thuật] ➔ [4. Lập Plan đề xuất] ➔ [5. Sửa & Kiểm thử hồi quy]
```

### Bước 1: Tái Hiện Lỗi (Reproduce)
- Xác định endpoint xảy ra sự cố, HTTP Method (`GET`, `POST`, `PUT`, `DELETE`).
- Thu thập Header (`Authorization: Bearer ...`), Request Body JSON và các tham số query (`page`, `size`, `sort`).

### Bước 2: Trích Xuất Log & Phân Tích Stacktrace
- Kiểm tra log tại console của service tương ứng hoặc qua docker log:
  ```powershell
  # Kiểm tra log container nếu chạy qua docker:
  docker logs --tail 100 <container_name>
  ```
- Đọc từ dưới lên trong Stacktrace để tìm class của dự án (`org.example...`) gây ra Exception.

### Bước 3: Cô Lập Tầng Kỹ Thuật Gây Lỗi
- **Tại API Gateway (8080)**: Lỗi định tuyến route, lỗi CORS, hoặc lỗi lọc Token JWT.
- **Tại Discovery Server Eureka (8761)**: Lỗi service chưa đăng ký hoặc đăng ký sai IP/hostname.
- **Tại Resource Service (8081 / 8082 / 8083)**: Lỗi ném exception trong Service, sai validation DTO, hoặc lỗi Spring Security filter.
- **Tại Database MySQL (3307)**: Lỗi cú pháp SQL, lỗi ràng buộc khóa ngoại, deadlock, hoặc hết connection pool.

### Bước 4: Lập Kế Hoạch Đề Xuất Phê Duyệt (`implementation_plan.md`)
- Trình bày rõ nguyên nhân gốc rễ, danh sách file cần sửa và phương án kiểm thử.
- Chờ người dùng phê duyệt trước khi tiến hành code.

### Bước 5: Sửa Mã Nguồn & Kiểm Thử Hồi Quy (Regression Testing)
- Sau khi sửa, chạy `compileJava` và `test` để đảm bảo không làm gãy các tính năng hiện có.

---

## 3. Cẩm Nang Chẩn Đoán Các Sự Cố Đặc Thù Microservices

### 3.1. Sự Cố 1: Eureka Desync & Lỗi 503 Service Unavailable
- **Dấu hiệu**: Khi gọi qua Gateway (Port `8080`) nhận về HTTP Status `503 Service Unavailable` hoặc thông báo lỗi `Unable to find instance for <SERVICE-NAME>`.
- **Nguyên nhân**: Service đích chưa kịp đăng ký lên Eureka Server hoặc Eureka Server bị khởi động sau.
- **Cách xử lý**:
  1. Kiểm tra Eureka Dashboard tại `http://localhost:8761/`.
  2. Xem tên service trong danh sách `Instances currently registered with Eureka`. Tên service phải khớp chính xác với `spring.application.name` cấu hình trong route của Gateway (chú ý chữ hoa/thường).
  3. Đảm bảo Discovery Server luôn được start trước và ở trạng thái Healthy.

### 3.2. Sự Cố 2: Config Server Không Kết Nối Được
- **Dấu hiệu**: Service crash ngay khi khởi động với thông báo `Could not resolve placeholder ...` hoặc `ConnectException: Connection refused: no further information: localhost:8888`.
- **Nguyên nhân**: `config-server` chưa sẵn sàng hoặc file cấu hình trong `config-repo-local/` bị lỗi cú pháp YAML (sai thụt lề).
- **Cách xử lý**:
  1. Kiểm tra Actuator của Config Server: `http://localhost:8888/actuator/health`.
  2. Kiểm tra trực tiếp file YAML qua URL: `http://localhost:8888/<service-name>/default`.

### 3.3. Sự Cố 3: Cạn Kiệt Connection Pool (`HikariPool Connection Timeout`)
- **Dấu hiệu**: Log xuất hiện lỗi:
  ```text
  java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
  ```
- **Nguyên nhân**: Quá nhiều truy vấn chạy đồng thời không đóng kết nối; rò rỉ kết nối do thiếu `@Transactional`; hoặc câu truy vấn bị lock/deadlock trong MySQL.
- **Cách xử lý**:
  1. Kiểm tra trạng thái container MySQL: `docker ps` xem có bị restart liên tục không.
  2. Rà soát các method Service có gọi API bên ngoài hoặc thực thi tác vụ nặng trong khi vẫn đang giữ Transaction DB (chuyển các tác vụ I/O nặng ra ngoài phạm vi transaction).

### 3.4. Sự Cố 4: Lỗi `LazyInitializationException` Trong Hibernate
- **Dấu hiệu**:
  ```text
  org.hibernate.LazyInitializationException: could not initialize proxy [org.example...#1] - no Session
  ```
- **Nguyên nhân**: Truy cập vào thuộc tính quan hệ lười (Lazy collection / proxy) ở ngoài phạm vi Transaction (ví dụ: khi đang map DTO tại Controller hoặc lúc Jackson tuần hoàn JSON).
- **Cách xử lý**:
  1. Tuyệt đối không bật `spring.jpa.open-in-view=true` (Anti-pattern làm chậm hệ thống).
  2. Giải pháp đúng: Dùng `JOIN FETCH` trong JPQL Repository hoặc sử dụng `@EntityGraph(attributePaths = {"..."})` tại phương thức Repository để nạp sẵn dữ liệu quan hệ ngay bên trong tầng Service.

### 3.5. Sự Cố 5: Xung Đột Cổng Trên Máy Host (`BindException`)
- **Dấu hiệu**: `java.net.BindException: Address already in use: bind`.
- **Nguyên nhân**: Một process Java cũ chạy ngầm hoặc container Docker khác đang chiếm dụng một trong các port (`8080`, `8081`, `8082`, `8083`, `8761`, `8888`, `3307`).
- **Cách xử lý**:
  1. Tìm tiến trình đang chiếm port (ví dụ port 8081):
     ```powershell
     netstat -ano | findstr :8081
     ```
  2. Xác định PID và tắt tiến trình bị treo nếu cần thiết (phải giải trình cho người dùng trước khi thực hiện).

# 🏢 InternHub - Microservices Architecture

Dự án **InternHub** được xây dựng theo mô hình kiến trúc Microservices sử dụng hệ sinh thái **Spring Cloud** và điều phối bằng **Docker Compose**.

---

## 📐 Kiến trúc hệ thống (System Architecture)

```text
                                Client (Frontend / Postman)
                                             │
                                             ▼ Port 8080
                                   ┌──────────────────┐
                                   │   api-gateway    │ (Spring Cloud Gateway)
                                   └────────┬─────────┘
                                            │ (lb://)
                 ┌──────────────────────────┼──────────────────────────┐
                 ▼                          ▼                          ▼
       ┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
       │  config-server   │       │ discovery-server │       │ employee-service │
       │   (Port 8888)    │       │  (Eureka : 8761) │       │ (Internal : 8081)│
       └──────────────────┘       └──────────────────┘       └─────────┬────────┘
                                                                       │
                                                                       ▼ Port 3307/3306
                                                                ┌──────────────┐
                                                                │   mysql-db   │
                                                                └──────────────┘
```

---

## 🔌 Danh mục dịch vụ & Cổng (Port Mapping)

| Dịch vụ | Công nghệ | Cổng Host | Cổng Container | Mô tả |
| :--- | :--- | :---: | :---: | :--- |
| **api-gateway** | Spring Cloud Gateway | `8080` | `8080` | Gateway định tuyến duy nhất cho toàn bộ hệ thống |
| **discovery-server** | Netflix Eureka | `8761` | `8761` | Quản lý và phát hiện dịch vụ (Service Registry) |
| **config-server** | Spring Cloud Config | `8888` | `8888` | Quản lý cấu hình tập trung |
| **employee-service** | Spring Boot 4 / Data JPA | *Ẩn (nội bộ)*| `8081` | Dịch vụ quản lý nhân viên / thực tập sinh |
| **mysql-db** | MySQL 8.0 | `3307` | `3306` | Cơ sở dữ liệu chính (`internhub_db`) |

---

## 🚀 Hướng dẫn khởi chạy nhanh (Quick Start)

### Yêu cầu môi trường:
- **Docker Desktop** (đã bật và đang chạy)
- **JDK 17** hoặc **JDK 21** (nếu muốn build bằng Gradle local)

### Bước 1: Build toàn bộ mã nguồn Microservices
Chạy script tự động tại thư mục gốc của dự án:
- **Trên Windows:**
  ```cmd
  build-all.bat
  ```
- **Trên macOS / Linux:**
  ```bash
  chmod +x build-all.sh
  ./build-all.sh
  ```
*(Hoặc dùng lệnh Gradle: `./gradlew bootJar -x test`)*

### Bước 2: Khởi động toàn bộ dịch vụ với Docker Compose
```bash
docker compose up -d
```

### Bước 3: Kiểm tra trạng thái
```bash
docker compose ps
```
*Tất cả 5 container phải có trạng thái `Up` và `(healthy)`.*

### Bước 4: Kiểm tra kết nối API qua Gateway
```bash
curl http://localhost:8080/api/employees
```
*Kết quả trả về:*
```text
Xin chào! API Gateway đã gọi sang Employee Service thành công!
```

- **Eureka Dashboard:** Truy cập [http://localhost:8761](http://localhost:8761)
- **Config Server:** Truy cập [http://localhost:8888/employee-service/default](http://localhost:8888/employee-service/default)

---

## 👥 Quy chuẩn làm việc nhóm (Team Collaboration Guidelines)

### 1. Quy tắc phân nhánh Git (Branching Model)
- **`main`**: Nhánh chính, chỉ chứa code ổn định đã được kiểm thử.
- **`develop`**: Nhánh tích hợp chung của cả nhóm.
- **Tạo nhánh chức năng riêng:**
  - `feat/<tên-thành-viên>-<tên-chức-năng>` (Ví dụ: `feat/khanh-student-service`)
  - `fix/<tên-thành-viên>-<tên-lỗi>` (Ví dụ: `fix/thanh-login-401`)

### 2. Quy ước viết Commit Message (Conventional Commits)
- `feat: ...` : Thêm tính năng mới
- `fix: ...` : Sửa lỗi
- `refactor: ...` : Tối ưu hóa code
- `docs: ...` : Cập nhật tài liệu
- `chore: ...` : Cập nhật thư viện, file cấu hình build

### 3. Quy trình nộp code (Pull Request)
1. Trước khi tạo PR, chạy `build-all.bat` (hoặc `./gradlew bootJar -x test`) ở local để chắc chắn toàn bộ dự án compile thành công.
2. Khởi chạy Docker test thử API trước khi đẩy code.
3. Tạo Pull Request vào nhánh `develop`, điền đầy đủ thông tin theo mẫu **PR Template**.
4. Chờ GitHub Actions CI chạy pass và ít nhất 1 thành viên review trước khi merge.

> [!CAUTION]
> **Lưu ý an toàn:** Tuyệt đối không commit file cấu hình chứa thông tin nhạy cảm (passwords, token, secrets) và các file cache IDE vào Git.

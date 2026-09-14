# 📖 Hướng dẫn chi tiết dành cho lập trình viên (Developer Tutorial) - InternHub

Chào mừng bạn đến với dự án **InternHub**! Tài liệu này hướng dẫn chi tiết từ A đến Z cách thiết lập môi trường, tải mã nguồn, khởi chạy hệ thống Microservices bằng Docker Compose, quy trình phát triển trên IDE và các quy chuẩn làm việc nhóm.

---

## 📑 Mục lục
1. [Yêu cầu môi trường](#1-yêu-cầu-môi-trường)
2. [Thiết lập dự án lần đầu (Clone & Setup)](#2-thiết-lập-dự-án-lần-đầu-clone--setup)
3. [Khởi chạy hệ thống bằng Docker Compose](#3-khởi-chạy-hệ-thống-bằng-docker-compose)
4. [Kiểm tra hoạt động của các dịch vụ](#4-kiểm-tra-hoạt-động-của-các-dịch-vụ)
5. [Hướng dẫn mở và phát triển trên IDE](#5-hướng-dẫn-mở-và-phát-triển-trên-ide)
6. [Quy trình làm việc nhóm hàng ngày & Quy tắc Git](#6-quy-trình-làm-việc-nhóm-hàng-ngày--quy-tắc-git)
7. [Xử lý các lỗi thường gặp (Troubleshooting)](#7-xử-lý-các-lỗi-thường-gặp-troubleshooting)

---

## 1. Yêu cầu môi trường

Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã cài đặt các công cụ sau:

| Công cụ | Phiên bản khuyến nghị | Mục đích |
| :--- | :--- | :--- |
| **Git** | Bản mới nhất | Quản lý mã nguồn |
| **Docker Desktop** | Bản mới nhất | Chạy container hóa toàn bộ hệ thống |
| **JDK (Java)** | JDK 17 hoặc JDK 21 (Temurin / Oracle / Corretto) | Compile mã nguồn Spring Boot |
| **IDE** | IntelliJ IDEA hoặc Visual Studio Code | Môi trường lập trình |

> [!IMPORTANT]
> - Hãy chắc chắn rằng **Docker Desktop đã được bật và đang chạy** (icon cá voi màu xanh không báo lỗi).
> - Kiểm tra Java trong Terminal bằng lệnh: `java -version`. Biến môi trường `JAVA_HOME` phải trỏ đúng vào thư mục cài đặt JDK.

---

## 2. Thiết lập dự án lần đầu (Clone & Setup)

### Bước 2.1: Clone repository về máy
Mở Terminal (hoặc PowerShell trên Windows) và chạy lệnh:
```bash
git clone https://github.com/khanhld3010/InternHub.git
cd InternHub
```

### Bước 2.2: Hiểu cấu trúc Monorepo
Dự án được tổ chức theo mô hình **Gradle Multi-Project**, gồm các thư mục chính:
```text
InternHub/
├── api-gateway/              # Dịch vụ cổng API Gateway (Spring Cloud Gateway)
├── config-server/            # Dịch vụ quản lý cấu hình tập trung
├── config-repo-local/        # Thư mục chứa các file cấu hình YAML của các service
├── discovery-server/         # Eureka Service Discovery Server
├── employee-service/         # Microservice quản lý nhân viên / thực tập sinh
├── settings.gradle           # Root Gradle khai báo liên kết toàn bộ subprojects
├── build-all.bat             # Script build 1-click cho Windows
├── build-all.sh              # Script build 1-click cho macOS / Linux
├── docker-compose.yml        # File điều phối khởi chạy toàn bộ hệ thống
└── tutorial.md               # File hướng dẫn này
```

---

## 3. Khởi chạy hệ thống bằng Docker Compose

### Bước 3.1: Build các file JAR cho Microservices
> [!NOTE]
> Vì thư mục `build/` được loại trừ trong `.gitignore` để không làm nặng Git, sau khi clone mới về bạn cần sinh ra các file `.jar` trước khi Docker đóng gói container.

Chạy script tự động tại thư mục gốc:

* **Trên Windows:**
  ```cmd
  build-all.bat
  ```
* **Trên macOS / Linux:**
  ```bash
  chmod +x build-all.sh gradlew
  ./build-all.sh
  ```
*(Lệnh này chạy `./gradlew bootJar -x test` đồng loạt cho 4 service, chỉ mất khoảng 20-30 giây).*

### Bước 3.2: Khởi động toàn bộ container
Chạy lệnh sau tại thư mục gốc của dự án:
```bash
docker compose up --build -d
```

Hệ thống sẽ tự động khởi động các container theo thứ tự phụ thuộc chính xác:
1. `mysql-db` và `config-server` khởi động trước và vượt qua kiểm tra `healthcheck`.
2. `discovery-server` (Eureka) khởi động tiếp theo.
3. `api-gateway` và `employee-service` khởi động sau cùng khi các dịch vụ nền tảng đã sẵn sàng.

---

## 4. Kiểm tra hoạt động của các dịch vụ

### 4.1. Kiểm tra trạng thái các container
```bash
docker compose ps
```
Cả 5 container phải ở trạng thái **`Up`** và **`(healthy)`**:
- `mysql-db`
- `config-server`
- `discovery-server`
- `api-gateway`
- `employee-service`

### 4.2. Kiểm tra Eureka Dashboard
Mở trình duyệt truy cập: [http://localhost:8761](http://localhost:8761)
* Trong mục **Instances currently registered with Eureka**, bạn sẽ thấy danh sách 3 ứng dụng: `API-GATEWAY`, `CONFIG-SERVER`, `EMPLOYEE-SERVICE`.

### 4.3. Gọi thử API qua API Gateway
Mở Terminal hoặc Postman gọi endpoint mẫu:
```bash
curl http://localhost:8080/api/employees
```
*Kết quả trả về thành công:*
```text
Xin chào! API Gateway đã gọi sang Employee Service thành công!
```

### 4.4. Kiểm tra kết nối MySQL
- **Host:** `localhost`
- **Port:** `3307` (Port trong container là `3306`)
- **Username:** `root`
- **Password:** `123456`
- **Database:** `internhub_db`
*(Có thể dùng DBeaver, MySQL Workbench, Navicat hoặc DataGrip để kết nối).*

---

## 5. Hướng dẫn mở và phát triển trên IDE

### Dùng IntelliJ IDEA (Khuyên dùng):
1. Mở IntelliJ IDEA, chọn **Open**.
2. Trỏ trực tiếp vào thư mục gốc **`InternHub`** (chọn mở với tư cách là Gradle Project).
3. IntelliJ sẽ tự động quét file `settings.gradle` và nạp đồng thời cả 4 module con.
4. Bạn có thể mở thanh công cụ **Gradle** bên phải để xem các task của từng service.

### Dùng Visual Studio Code:
1. Mở VS Code, chọn **File** ➔ **Open Folder...** ➔ Chọn thư mục **`InternHub`**.
2. Cài đặt Extension Pack for Java và Spring Boot Extension Pack.
3. VS Code sẽ tự động nhận diện dự án đa module thông qua Language Server.

---

## 6. Quy trình làm việc nhóm hàng ngày & Quy tắc Git

Để tránh xung đột code và đảm bảo mã nguồn luôn ổn định khi nhiều người cùng làm việc:

### 6.1. Quy trình lấy code mới và bắt đầu ngày làm việc
```bash
# 1. Chuyển về nhánh develop và kéo code mới nhất
git checkout develop
git pull origin develop

# 2. Tạo nhánh chức năng mới của bạn
git checkout -b feat/<tên-bạn>-<tên-tính-năng>
# Ví dụ: git checkout -b feat/khanh-student-service
```

### 6.2. Quy ước viết Commit Message
Sử dụng chuẩn **Conventional Commits**:
- `feat: ...` : Thêm tính năng mới (ví dụ: `feat: add student CRUD endpoints`)
- `fix: ...` : Sửa lỗi (ví dụ: `fix: fix null pointer in employee controller`)
- `refactor: ...` : Tái cấu trúc code nhưng không đổi logic
- `docs: ...` : Cập nhật tài liệu
- `chore: ...` : Cập nhật cấu hình build, dependencies

### 6.3. Quy trình nộp code (Tạo Pull Request)
1. Trước khi nộp code, chạy lệnh kiểm tra build ở local:
   ```cmd
   build-all.bat
   ```
2. Đẩy nhánh của bạn lên GitHub:
   ```bash
   git push origin feat/<tên-bạn>-<tên-tính-năng>
   ```
3. Lên GitHub mở **Pull Request (PR)** từ nhánh của bạn vào nhánh **`develop`**.
4. Điền đầy đủ thông tin theo mẫu PR Template có sẵn.
5. GitHub Actions CI sẽ tự động chạy kiểm tra. Đợi ít nhất 1 thành viên review và approve trước khi merge.

> [!CAUTION]
> **Quy tắc an toàn Git:**
> - Tuyệt đối **không commit** các file tạm, file cấu hình chứa mật khẩu, hoặc thư mục `build/`, `.gradle/`, `.vscode/`.
> - Tuyệt đối **không push thẳng vào nhánh `main`** hoặc force push (`git push -f`).

---

## 7. Xử lý các lỗi thường gặp (Troubleshooting)

### ❓ Lỗi 1: Bị trùng cổng (Port already in use)
* **Triệu chứng:** Container báo lỗi `Bind for 0.0.0.0:xxxx failed: port is already allocated`.
* **Nguyên nhân:** Máy của bạn đang chạy một dịch vụ khác trùng cổng (ví dụ: MySQL local đang chiếm port `3306`/`3307`, hoặc một app khác chiếm port `8080`, `8761`, `8888`).
* **Cách khắc phục:**
  - Tắt dịch vụ local đang chiếm cổng (ví dụ tắt dịch vụ MySQL trong `services.msc` trên Windows).
  - Hoặc kiểm tra tiến trình đang giữ port bằng lệnh:
    ```cmd
    netstat -ano | findstr :8080
    taskkill /PID <PID_tìm_thấy> /F
    ```

### ❓ Lỗi 2: Sửa code Java nhưng chạy Docker Compose vẫn thấy code cũ
* **Nguyên nhân:** Bạn chưa re-build file `.jar` trên máy host trước khi build container.
* **Cách khắc phục:**
  1. Chạy lại `build-all.bat` (hoặc `./build-all.sh`).
  2. Chạy `docker compose up --build -d <tên-service>` (ví dụ: `docker compose up --build -d employee-service`).

### ❓ Lỗi 3: Muốn xóa trắng dữ liệu MySQL cũ để tạo lại từ đầu
* Chạy lệnh xóa toàn bộ volume database:
  ```bash
  docker compose down -v
  docker compose up -d
  ```

### ❓ Lỗi 4: Container báo `unhealthy`
* Xem log chi tiết của container bị lỗi:
  ```bash
  docker logs --tail 50 -f <tên-container>
  # Ví dụ: docker logs --tail 50 -f employee-service
  ```

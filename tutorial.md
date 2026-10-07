# 📖 Hướng Dẫn Phát Triển Cho Lập Trình Viên (Developer Tutorial) - InternHub

> **Dự án:** InternHub Backend - Hệ sinh thái Microservices quản lý thực tập sinh doanh nghiệp  
> **Phiên bản tài liệu:** 2.0.0 (Đồng bộ hoàn toàn với bộ Đặc tả Nghiệp vụ `docs/specs/` và Quy chuẩn Kiến trúc `.agents/`)  
> **Cập nhật lần cuối:** 2026-10-06  

---

## 📑 Mục Lục
1. [Yêu Cầu Môi Trường Tiên Quyết](#1-yêu-cầu-môi-trường-tiên-quyết)
2. [Thiết Lập Dự Án & Bản Đồ Kiến Trúc Monorepo](#2-thiết-lập-dự-án--bản-đồ-kiến-trúc-monorepo)
3. [Khởi Chạy Hệ Thống Bằng Docker Compose](#3-khởi-chạy-hệ-thống-bằng-docker-compose)
4. [Kiểm Tra Hoạt Động & Kiểm Thử Kết Nối (Healthcheck & Verification)](#4-kiểm-tra-hoạt-động--kiểm-thử-kết-nối-healthcheck--verification)
5. [Cấu Hình & Phát Triển Trên IDE (IntelliJ IDEA & VS Code)](#5-cấu-hình--phát-triển-trên-ide-intellij-idea--vs-code)
6. [Quy Trình Phát Triển Spec-Driven Development (Triết Lý 14 Phần & 7 Bước)](#6-quy-trình-phát-triển-spec-driven-development-triết-lý-14-phần--7-bước)
7. [Bản Đồ Đặc Tả Tính Năng Cốt Lõi (Jira `TM` Specifications Mapping)](#7-bản-đồ-đặc-tả-tính-năng-cốt-lõi-jira-tm-specifications-mapping)
8. [Quy Chuẩn Làm Việc Nhóm & Nguyên Tắc Git (Team Collaboration Guidelines)](#8-quy-chuẩn-làm-việc-nhóm--nguyên-tắc-git-team-collaboration-guidelines)
9. [Cẩm Nang Xử Lý Sự Cố & Gỡ Lỗi (Troubleshooting & Debugging)](#9-cẩm-nang-xử-lý-sự-cố--gỡ-lỗi-troubleshooting--debugging)

---

## 1. Yêu Cầu Môi Trường Tiên Quyết

Trước khi bắt đầu, lập trình viên cần cài đặt và kiểm tra các công cụ sau trên máy phát triển:

| Công cụ | Phiên bản khuyến nghị | Mục đích sử dụng |
| :--- | :--- | :--- |
| **Git** | `2.40+` (Bản mới nhất) | Quản lý mã nguồn, phân nhánh và đồng bộ nhóm |
| **Docker & Docker Desktop** | Docker v20+ / Desktop mới nhất | Container hóa toàn bộ hệ sinh thái Microservices |
| **Java Development Kit (JDK)** | **JDK 17** hoặc **JDK 21** (Temurin / Corretto / Oracle) | Biên dịch và chạy mã nguồn Spring Boot 3.x |
| **Gradle** | `8.x` (Đã tích hợp sẵn Gradle Wrapper) | Build automation, quản lý dependencies đa module |
| **IDE** | IntelliJ IDEA Ultimate / Community hoặc VS Code | Môi trường phát triển ứng dụng Java |
| **API Client** | Postman / Insomnia / Bruno | Kiểm thử các API Endpoints qua Gateway |
| **Database Tool** | DBeaver / DataGrip / Navicat | Quản lý và tra cứu dữ liệu MySQL 8.0 |

> [!IMPORTANT]
> - **Docker Desktop bắt buộc phải đang chạy** (biểu tượng cá voi màu xanh lá cây sẵn sàng).
> - Kiểm tra phiên bản Java trên máy bằng lệnh: `java -version`. Biến môi trường `JAVA_HOME` phải trỏ đúng vào thư mục cài đặt JDK 17 hoặc JDK 21.

---

## 2. Thiết Lập Dự Án & Bản Đồ Kiến Trúc Monorepo

### 2.1. Clone mã nguồn về máy
Mở Terminal (hoặc PowerShell trên Windows) và chạy lệnh:
```bash
git clone https://github.com/khanhld3010/InternHub.git
cd InternHub
```

### 2.2. Bản đồ cấu trúc Monorepo Gradle Multi-Project
Hệ thống Backend được tổ chức theo mô hình **Gradle Multi-Project**, gồm các vi dịch vụ và thư mục hạ tầng như sau:

```text
InternHub/
├── api-gateway/                      # [Port 8080] Spring Cloud Gateway, Rate Limiting, Security Headers
├── discovery-server/                 # [Port 8761] Netflix Eureka Service Registry & Discovery
├── config-server/                    # [Port 8888] Spring Cloud Config Server (quản lý tập trung)
├── config-repo-local/                # Kho lưu trữ các file cấu hình YAML của từng service
├── identity-and-access-service/      # [Port 8081] Auth, JWT, Dynamic RBAC, Users, Google OAuth2
├── intern-and-program-service/       # [Port 8082] TTS, Chương trình, Hợp đồng, Kanban, Chấm công GPS/QR
├── reporting-and-integration-service/# [Port 8083] Email tự động, Sao lưu CSDL (TM-8), Audit Logs (TM-9)
├── file-service/                     # [Port 8084] S3 Object Storage Utility, Direct-to-S3 Presigned URLs
├── notification-service/             # [Port 8085] WebSocket STOMP, Redis Pub/Sub, Realtime Push, Security Cmd
├── docs/                             # Thư mục tài liệu kỹ thuật
│   └── specs/                        # Kho lưu trữ ĐẶC TẢ VĨNH CỬU các tính năng (TM-1 đến TM-32)
├── .agents/                          # Bộ quy chuẩn hoạt động chi tiết dành cho AI & Developer (01 -> 07)
├── .antigravity/                     # Quy chuẩn lập trình và đặc tả mở rộng
├── data/                             # Thư mục lưu trữ volume cục bộ (MySQL, S3, Backups, Uploads)
├── build-all.bat                     # Script build 1-click cho hệ điều hành Windows
├── build-all.sh                      # Script build 1-click cho macOS / Linux
├── docker-compose.yml                # File điều phối khởi chạy toàn bộ 11 container hệ thống
├── settings.gradle                   # File liên kết đa module Gradle
├── tutorial.md                       # Tài liệu hướng dẫn này
└── README.md                         # Tổng quan kiến trúc kỹ thuật
```

---

## 3. Khởi Chạy Hệ Thống Bằng Docker Compose

Toàn bộ hệ thống Backend phụ thuộc vào quy trình đóng gói container và điều phối mạng nội bộ `micro-network`.

### 3.1. Biên dịch các tệp JAR cho tất cả Microservices
> [!NOTE]
> Do thư mục `build/` được loại trừ trong `.gitignore`, bạn bắt buộc phải tạo mới các tệp `.jar` trên máy host trước khi khởi chạy Docker Compose.

Chạy script tự động tại thư mục gốc `InternHub/`:
- **Trên Windows:**
  ```cmd
  build-all.bat
  ```
- **Trên macOS / Linux:**
  ```bash
  chmod +x build-all.sh gradlew
  ./build-all.sh
  ```
*(Lệnh này thực thi `./gradlew bootJar -x test` đồng loạt cho tất cả các microservices; quá trình diễn ra trong khoảng 30 - 60 giây).*

### 3.2. Khởi động hệ thống với Docker Compose
Chạy lệnh khởi chạy ngầm toàn bộ 11 container:
```bash
docker compose up --build -d
```

### 3.3. Trình tự khởi động (Startup Dependency Order)
Docker Compose sẽ khởi chạy các dịch vụ theo thứ tự phụ thuộc chính xác:
1. **Tầng Hạ Tầng Cốt Lõi:** `mysql-db`, `redis-broker`, `s3-storage` khởi động trước và vượt qua kiểm tra `healthcheck`.
2. **Tầng Quản Lý Cấu Hình:** `config-server` (Port 8888) tải cấu hình từ `config-repo-local/`.
3. **Tầng Đăng Ký Dịch Vụ:** `discovery-server` (Port 8761 - Eureka) sẵn sàng tiếp nhận nhịp tim.
4. **Tầng Cổng & Nghiệp Vụ:** `api-gateway`, `identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`, `file-service`, `notification-service` lần lượt khởi động và tự động đăng ký với Eureka.

---

## 4. Kiểm Tra Hoạt Động & Kiểm Thử Kết Nối (Healthcheck & Verification)

### 4.1. Kiểm tra trạng thái toàn bộ Container
Chạy lệnh kiểm tra danh sách container:
```bash
docker compose ps
```
Toàn bộ **11 container** phải hiển thị trạng thái **`Up`** và **`(healthy)`**:
- `mysql-db` (MySQL 8.0)
- `redis-broker` (Redis Alpine)
- `internhub-s3-storage` (Adobe S3Mock)
- `config-server` (Spring Cloud Config)
- `discovery-server` (Eureka Registry)
- `api-gateway` (Spring Cloud Gateway)
- `identity-and-access-service`
- `intern-and-program-service`
- `reporting-and-integration-service`
- `file-service`
- `notification-service`

### 4.2. Kiểm tra Eureka Service Registry Dashboard
Truy cập trình duyệt: [http://localhost:8761](http://localhost:8761)
- Tại bảng **Instances currently registered with Eureka**, kiểm tra xem các dịch vụ sau đã xuất hiện đầy đủ:
  - `API-GATEWAY`
  - `IDENTITY-AND-ACCESS-SERVICE`
  - `INTERN-AND-PROGRAM-SERVICE`
  - `REPORTING-AND-INTEGRATION-SERVICE`
  - `FILE-SERVICE`
  - `NOTIFICATION-SERVICE`

### 4.3. Kiểm tra các dịch vụ nền tảng qua Actuator
- **API Gateway:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) ➔ `{"status":"UP"}`
- **Config Server:** [http://localhost:8888/actuator/health](http://localhost:8888/actuator/health) ➔ `{"status":"UP"}`
- **Kiểm tra cấu hình Config Server:** [http://localhost:8888/intern-and-program-service/default](http://localhost:8888/intern-and-program-service/default)

### 4.4. Gọi thử API qua API Gateway (Port 8080)
1. **Kiểm tra danh mục chương trình thực tập mở tuyển (Public Endpoint):**
   ```bash
   curl http://localhost:8080/api/programs/open
   ```
2. **Kiểm tra đăng nhập hệ thống (Lấy JWT Access Token & Set-Cookie Refresh Token):**
   ```bash
   curl -X POST http://localhost:8080/api/auth/login ^
     -H "Content-Type: application/json" ^
     -d "{\"username\":\"admin\",\"password\":\"Admin@123\"}"
   ```
   *(Nhận về JSON chuẩn `ApiResponse<LoginResponse>` chứa `accessToken`, `tokenType: "Bearer"`, và cookie `internhub_refresh_token`).*

### 4.5. Thông tin kết nối Cơ sở dữ liệu & Dịch vụ hỗ trợ
- **MySQL Database Server:**
  - **Host:** `localhost`
  - **Port:** `3307` *(Port nội bộ container là `3306`)*
  - **Username:** `root`
  - **Password:** `123456`
  - **Database Chính:** `internhub_db`
  - **Database Thông báo:** `notification_db`
- **Redis Broker:**
  - **Host:** `localhost` | **Port:** `6379`
- **S3 Object Storage (S3Mock):**
  - **HTTP Endpoint:** `http://localhost:9090`
  - **Bucket Mặc Định:** `internhub-documents`

---

## 5. Cấu Hình & Phát Triển Trên IDE (IntelliJ IDEA & VS Code)

### 5.1. Phát triển bằng IntelliJ IDEA (Khuyến nghị)
1. Mở IntelliJ IDEA, chọn **Open**.
2. Trỏ trực tiếp vào thư mục gốc **`InternHub`** (chọn mở với tư cách là **Gradle Project**).
3. IntelliJ sẽ tự động phân tích `settings.gradle` và nạp đồng thời toàn bộ 8 sub-projects.
4. Cài đặt **Lombok Plugin** (đã tích hợp sẵn từ IDEA 2020+) và bật tính năng:
   - `Settings` ➔ `Build, Execution, Deployment` ➔ `Compiler` ➔ `Annotation Processors` ➔ Tích chọn **Enable annotation processing**.
5. Cấu hình SDK: Chọn **Project SDK** là **JDK 17** hoặc **JDK 21**.

### 5.2. Phát triển bằng Visual Studio Code
1. Mở VS Code, chọn **File** ➔ **Open Folder...** ➔ Chọn thư mục **`InternHub`**.
2. Cài đặt các Extensions bắt buộc:
   - `Extension Pack for Java` (Microsoft)
   - `Spring Boot Extension Pack` (VMware)
   - `Lombok Annotations Support for VS Code`
3. Mở Terminal tích hợp và sử dụng trực tiếp các lệnh Gradle Wrapper (`./gradlew`).

---

## 6. Quy Trình Phát Triển Spec-Driven Development (Triết Lý 14 Phần & 7 Bước)

> [!CAUTION]
> **CHỈ THỊ CỐT LÕI DỰ ÁN INTERNHUB:**
> 1. **QUÉT DỰ ÁN & TÁI SỬ DỤNG TỐI ĐA (REUSE FIRST):** Trước khi tạo mới bất kỳ Entity, Table, DTO, Service, Repository hay Helper nào, bắt buộc phải quét toàn bộ 8 services và database schema hiện có để tái sử dụng hoặc mở rộng, tuyệt đối chống tạo mã thừa và phân mảnh.
> 2. **LƯU TRỮ ĐẶC TẢ VĨNH CỬU TẠI `docs/specs/`:** Mọi tài liệu đặc tả phải được lưu cố định trong Git repo.
> 3. **ĐỒNG BỘ NGUYÊN TỬ (ATOMIC SPEC-CODE SYNC):** Khi thay đổi logic code từ cấp L2 trở lên, bắt buộc phải cập nhật file Spec tương ứng và ghi rõ lý do vào bảng `Revision History & Change Rationale`.

### 6.1. Chu trình 5 giai đoạn: Spec ➔ Plan ➔ Tasks ➔ Implement ➔ Converge
```text
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ 1. Spec.md      │ ────► │ 2. Plan.md      │ ────► │ 3. Tasks.md     │
│ Cần đạt điều gì │       │ Làm như thế nào │       │ Các đầu mục con │
└─────────────────┘       └─────────────────┘       └────────┬────────┘
                                                             │
                          ┌─────────────────┐                │
                          │ 5. Converge     │ ◄──────────────┤
                          │ Nghiệm thu &    │   4. Implement │
                          │ Đối chiếu AC    │                ▼
                          └─────────────────┘
```

### 6.2. Phân cấp mức độ thay đổi (Change Levels)
- **Cấp độ L1:** Sửa chính tả log, format code nhỏ (< 10 dòng) ➔ Giải trình ngắn trong commit message, không cần sửa Spec.
- **Cấp độ L2:** Mở rộng API, thêm bộ lọc, đổi validation ➔ Tạo Kế hoạch + **Cập nhật Spec & Bảng Revision History**.
- **Cấp độ L3:** Tính năng nghiệp vụ mới, thay đổi CSDL, sửa đổi phân quyền RBAC ➔ Viết **`spec.md` (chuẩn 14 phần tại `docs/specs/`)** + Kế hoạch chi tiết.
- **Cấp độ L4:** Thay đổi kiến trúc, DB Migration diện rộng, can thiệp Gateway/Config ➔ Bộ tài liệu đặc tả đầy đủ + Rollback Plan + Phê duyệt kỹ thuật.

### 6.3. Cấu trúc tài liệu đặc tả chuẩn 14 phần (`spec.md`)
Mọi file đặc tả lưu tại `InternHub/docs/specs/<mã-task>-<tên-tính-năng>-spec.md` phải tuân theo cấu trúc:
0. **Revision History & Change Rationale:** Nhật ký thay đổi và giải trình căn cứ kỹ thuật/nghiệp vụ.
1. **Feature Overview:** Mã ticket Jira (`TM-X`), Microservice đích, phân quyền vai trò, cấp độ L1 - L4.
2. **Business Goal & Core Objectives:** Mục tiêu và bài toán thực tế cần giải quyết.
3. **Scope of Work:** Phân định rạch ròi *In Scope* và *Out of Scope*.
4. **Potential Logic Loopholes & Mitigations:** Tối thiểu 5 Edge Cases cốt lõi (Concurrency, Duplicate, Validation biên, Lỗi mạng...).
5. **Functional Requirements (FR):** Chi tiết từng yêu cầu chức năng.
6. **Business Rules (BR):** Quy tắc tính toán, ràng buộc dữ liệu và trạng thái.
7. **Data Model:** Thiết kế bảng MySQL, khóa ngoại, JPA Entities kế thừa `BaseEntity`.
8. **API Contract:** HTTP Method, URL Endpoint, Request Body, Response JSON chuẩn `ApiResponse<T>`, Http Status.
9. **Core Flow / Enforcement Flow:** Trình tự xử lý, các bước giao dịch `@Transactional`.
10. **Non-Functional Requirements:** Hiệu năng, bảo mật, Rate limit, Audit logging.
11. **Acceptance Criteria (AC):** Tiêu chí nghiệm thu có thể đo lường và kiểm thử được.
12. **Unit & Integration Test Cases:** Danh sách test cases Service và Controller.
13. **Implementation Checklist:** Bảng kiểm tra danh sách files tạo mới/chỉnh sửa.

### 6.4. Quy trình 7 bước triển khai code chuẩn Package-by-Feature
Khi hiện thực hóa một chức năng trong Microservice, tuân thủ đúng 7 bước:
1. **Bước 1 (Entity):** Tạo JPA Entity trong `<feature>/entity/`, kế thừa `BaseEntity`.
2. **Bước 2 (DTOs):** Phân tách rạch ròi `dto/request/` và `dto/response/` (cấm trả Entity trực tiếp ra Controller).
3. **Bước 3 (Repository):** Tạo Interface kế thừa `JpaRepository` / `JpaSpecificationExecutor`, phòng chống N+1 bằng `JOIN FETCH` / `@EntityGraph`.
4. **Bước 4 (Service):** Thiết kế `Service` interface và `ServiceImpl`, đặt `@Transactional(readOnly = true)` tại class level và `@Transactional` tường minh tại phương thức ghi.
5. **Bước 5 (Controller):** Tạo `@RestController`, nhận `@Valid` DTO, trả về `ResponseEntity<ApiResponse<T>>` (không viết business logic tại Controller).
6. **Bước 6 (Exception Handling):** Bắt lỗi tập trung qua `GlobalExceptionHandler`, sử dụng các ngoại lệ chuẩn (`BadRequestException`, `ResourceNotFoundException`, `DuplicateResourceException`...).
7. **Bước 7 (Testing & Verification):** Viết Unit Test (JUnit 5 + Mockito), chạy biên dịch kiểm tra thành công 100%.

---

## 7. Bản Đồ Đặc Tả Tính Năng Cốt Lõi (Jira `TM` Specifications Mapping)

Toàn bộ hệ thống được định hình thông qua kho đặc tả tại thư mục [`InternHub/docs/specs/`](file:///d:/codegym_final_project/InternHub/docs/specs/):

| Mã Task Jira | Tên Đặc Tả Nghiệp Vụ | Tệp Spec Tương Ứng | Service Chịu Trách Nhiệm |
| :---: | :--- | :--- | :--- |
| **TM-1** | Tạo hồ sơ thực tập sinh | [`TM-1-create-intern-profile-spec.md`](docs/specs/TM-1-create-intern-profile-spec.md) | `intern-and-program-service` |
| **TM-2** | Cập nhật hồ sơ thực tập sinh | [`TM-2-update-intern-profile-spec.md`](docs/specs/TM-2-update-intern-profile-spec.md) | `intern-and-program-service` |
| **TM-3** | Tìm kiếm & Lọc hồ sơ TTS | [`TM-3-search-filter-interns-spec.md`](docs/specs/TM-3-search-filter-interns-spec.md) | `intern-and-program-service` |
| **TM-4** | Tải lên CV & Hồ sơ ứng tuyển | [`TM-4-upload-cv-application-spec.md`](docs/specs/TM-4-upload-cv-application-spec.md) | `intern-and-program` & `file-service` |
| **TM-5** | Xét duyệt tài liệu thực tập | [`TM-5-review-intern-documents-spec.md`](docs/specs/TM-5-review-intern-documents-spec.md) | `intern-and-program-service` |
| **TM-8** | Sao lưu dữ liệu CSDL hệ thống | [`TM-8-system-data-backup-spec.md`](docs/specs/TM-8-system-data-backup-spec.md) | `reporting-and-integration-service` |
| **TM-9** | Nhật ký kiểm toán & Giám sát | [`TM-9-view-activity-log-spec.md`](docs/specs/TM-9-view-activity-log-spec.md) | `reporting-and-integration-service` |
| **TM-10** | Đăng ký & Nộp hồ sơ trực tuyến | [`TM-10-register-account-and-apply-online-spec.md`](docs/specs/TM-10-register-account-and-apply-online-spec.md) | `identity` & `intern-and-program` |
| **TM-11** | Phê duyệt / Từ chối ứng viên | [`TM-11-approve-reject-application-spec.md`](docs/specs/TM-11-approve-reject-application-spec.md) | `intern-and-program-service` |
| **TM-12** | Gửi email thông báo kết quả | [`TM-12-send-decision-email-spec.md`](docs/specs/TM-12-send-decision-email-spec.md) | `reporting-and-integration-service` |
| **TM-13** | Khởi tạo & Upload hợp đồng S3 | [`TM-13-contract-upload-spec.md`](docs/specs/TM-13-contract-upload-spec.md) | `intern-and-program` & `file-service` |
| **TM-14** | Ký cam kết hợp đồng điện tử | [`TM-14-contract-confirm-spec.md`](docs/specs/TM-14-contract-confirm-spec.md) | `intern-and-program-service` |
| **TM-15..18**| Quản lý chương trình thực tập | [`TM-15-18-manage-internship-programs-spec.md`](docs/specs/TM-15-18-manage-internship-programs-spec.md) | `intern-and-program-service` |
| **TM-16** | Phân công & Thay đổi Mentor | [`TM-16-assign-mentor-spec.md`](docs/specs/TM-16-assign-mentor-spec.md) | `intern-and-program-service` |
| **TM-17** | Xem lịch trình thực tập sinh | [`TM-17-view-intern-schedule-spec.md`](docs/specs/TM-17-view-intern-schedule-spec.md) | `intern-and-program-service` |
| **TM-19** | Mentor giao nhiệm vụ (Kanban) | [`TM-19-mentor-assign-tasks-spec.md`](docs/specs/TM-19-mentor-assign-tasks-spec.md) | `intern-and-program-service` |
| **TM-20** | TTS cập nhật tiến độ nhiệm vụ | [`TM-20-intern-update-task-progress-spec.md`](docs/specs/TM-20-intern-update-task-progress-spec.md) | `intern-and-program-service` |
| **TM-21** | TTS nộp báo cáo tuần & tổng hợp task | [`TM-21-intern-weekly-report-spec.md`](docs/specs/TM-21-intern-weekly-report-spec.md) | `intern-and-program-service` |
| **TM-22** | Mentor xem & đánh giá báo cáo tuần | [`TM-22-mentor-review-weekly-report-spec.md`](docs/specs/TM-22-mentor-review-weekly-report-spec.md) | `intern-and-program-service` |
| **TM-25** | Chấm công Check-in / Out GPS & QR | [`TM-25-check-in-check-out-spec.md`](docs/specs/TM-25-check-in-check-out-spec.md) | `intern-and-program-service` |
| **TM-29** | Tiếp nhận & Mời Mentor mới | [`TM-29-create-mentor-spec.md`](docs/specs/TM-29-create-mentor-spec.md) | `intern-and-program` & `reporting` |
| **TM-30** | Đăng nhập Google OAuth2 | [`TM-30-google-oauth2-login-spec.md`](docs/specs/TM-30-google-oauth2-login-spec.md) | `identity-and-access-service` |
| **TM-31** | Phân quyền vai trò động RBAC | [`TM-31-dynamic-role-permission-authorization-spec.md`](docs/specs/TM-31-dynamic-role-permission-authorization-spec.md) | `identity-and-access-service` |
| **TM-32** | Tăng cường an ninh & Bảo mật | [`TM-32-system-security-hardening-spec.md`](docs/specs/TM-32-system-security-hardening-spec.md) | Toàn bộ 6 Microservices & Gateway |
| **Infra** | Hạ tầng Object Storage MinIO/S3| [`infra-object-storage-minio-s3-spec.md`](docs/specs/infra-object-storage-minio-s3-spec.md) | `file-service` & `s3-storage` |
| **Realtime**| Kiến trúc WebSocket & STOMP | [`realtime-websocket-architecture-spec.md`](docs/specs/realtime-websocket-architecture-spec.md) | `notification-service` & `redis` |

---

## 8. Quy Chuẩn Làm Việc Nhóm & Nguyên Tắc Git (Team Collaboration Guidelines)

### 8.1. Mô hình phân nhánh Git (Git Branching Model)
* **`main`**: Nhánh bảo vệ nghiêm ngặt, chỉ chứa code Production đã qua nghiệm thu. **Tuyệt đối cấm commit hoặc push trực tiếp lên `main`**.
* **`develop`**: Nhánh làm việc chung hằng ngày của toàn bộ team. Tất cả nhánh tính năng phải rẽ nhánh từ `develop` và tạo Pull Request (PR) về lại `develop`.
* **Định dạng đặt tên nhánh bắt buộc:**
  - Nhánh tính năng mới: `feature/<mã-task-jira>/<tên-tính-năng-kebab-case>`  
    *Ví dụ:* `feature/TM-1/create-intern-profile`, `feature/TM-25/check-in-gps-qr`
  - Nhánh sửa lỗi: `bugfix/<mã-task-jira>/<tên-lỗi-kebab-case>`  
    *Ví dụ:* `bugfix/TM-16/fix-mentor-cooldown`, `bugfix/TM-32/token-rotation-null`
  - Nhánh tối ưu/tái cấu trúc: `refactor/<mã-task-jira>/<nội-dung-kebab-case>`

### 8.2. Quy trình làm việc đầu ngày của lập trình viên
```bash
# 1. Chuyển về nhánh develop và cập nhật mã nguồn mới nhất
git checkout develop
git pull origin develop

# 2. Tạo nhánh tính năng mới gắn liền với mã Jira TM
git checkout -b feature/TM-1/create-intern-profile
```

### 8.3. Quy ước viết Commit Message (Conventional Commits chuẩn Jira `TM`)
Áp dụng định dạng: `<type>(<mã-task-jira>): <nội dung tiếng Việt>`
- `feat(TM-1): bổ sung API tạo mới hồ sơ thực tập sinh cho HR`
- `fix(TM-25): sửa lỗi sai lệch khoảng cách Geofence khi check-in`
- `refactor(TM-31): tách sub-service kiểm tra quyền dynamic RBAC`
- `docs(TM-14): cập nhật đặc tả luồng phản hồi hợp đồng điện tử`
- `test(TM-10): bổ sung unit test kiểm tra mã OTP kích hoạt tài khoản`
- `chore(TM-32): nâng cấp cấu hình CSP header tại api-gateway`

### 8.4. Quy trình nộp code & Mở Pull Request (PR)
1. **Kiểm tra biên dịch & Test cục bộ trước khi push:**
   - Trên Windows: Chạy `.\gradlew :<service-name>:compileJava` và `.\gradlew :<service-name>:test`.
   - Đảm bảo toàn bộ mã nguồn biên dịch thành công `BUILD SUCCESSFUL`.
2. **Đẩy nhánh lên GitHub:**
   ```bash
   git push -u origin feature/TM-1/create-intern-profile
   ```
3. **Mở Pull Request vào `develop`:**
   - Điền đầy đủ PR Template: Mô tả tính năng, danh sách API, hình ảnh test cURL/Postman, đối chiếu tiêu chí chấp nhận (AC).
   - Kiểm tra xem đã cập nhật Spec và bảng `Revision History` hay chưa.
4. **Code Review & Merge:** Ít nhất 1 Senior/Peer review và Approve trước khi merge vào `develop`.

### 8.5. Các ranh giới an toàn tuyệt đối (Non-Negotiable Guardrails)
> [!CAUTION]
> 1. **Ranh giới cách ly (Boundary Isolation):** Khi đang làm việc trên Backend (`InternHub/`), **tuyệt đối không sang thư mục Frontend (`InternHub-Frontend/`) để sửa code**. Nếu có sự sai khác về API, phải báo cáo cho người phụ trách Frontend.
> 2. **Nghiêm cấm SQL phá hoại:** Cấm chạy các lệnh SQL phá hủy cấu trúc hoặc dữ liệu (`DROP TABLE`, `TRUNCATE`, `ALTER DROP COLUMN`) trực tiếp trên `internhub_db`.
> 3. **Nghiêm cấm tuyệt đối truy cập file `.env` (Zero-Access):** Không đọc, sửa hay parse file `.env`. Mọi biến môi trường nhạy cảm phải thông qua người dùng hoặc Config Server.
> 4. **Cấm sử dụng Mock Bypass bảo mật:** Không tạo cờ `BYPASS_AUTH`, không mở `permitAll()` tùy tiện. Mọi API phải bảo vệ qua Spring Security và JWT thật.

---

## 9. Cẩm Nang Xử Lý Sự Cố & Gỡ Lỗi (Troubleshooting & Debugging)

### ❓ Sự Cố 1: Xung đột cổng trên máy Host (`BindException / Port already in use`)
* **Dấu hiệu:** Container báo lỗi `Bind for 0.0.0.0:xxxx failed: port is already allocated` hoặc `Address already in use`.
* **Nguyên nhân:** Máy của bạn đang chạy một dịch vụ local trùng cổng (MySQL local cổng `3306`/`3307`, hoặc ứng dụng cũ chiếm cổng `8080`, `8761`, `8888`, `6379`, `9090`).
* **Cách xử lý:**
  1. Kiểm tra tiến trình đang chiếm cổng (ví dụ cổng `8080`):
     ```cmd
     netstat -ano | findstr :8080
     ```
  2. Tắt tiến trình bị treo theo PID:
     ```cmd
     taskkill /PID <PID_tìm_thấy> /F
     ```

### ❓ Sự Cố 2: Sửa mã nguồn Java nhưng Docker Compose vẫn chạy code cũ
* **Dấu hiệu:** Gọi API thấy logic vẫn trả về kết quả cũ dù đã sửa code.
* **Nguyên nhân:** Bạn chưa biên dịch lại file `.jar` trên máy host trước khi build lại image Docker.
* **Cách xử lý:**
  1. Chạy lại script build: `build-all.bat` (hoặc `./build-all.sh`).
  2. Build và khởi động lại container tương ứng:
     ```bash
     docker compose up --build -d intern-and-program-service
     ```

### ❓ Sự Cố 3: Eureka Desync & Lỗi `503 Service Unavailable`
* **Dấu hiệu:** Gọi request qua Gateway cổng `8080` bị trả về mã `503 Service Unavailable` hoặc lỗi `Unable to find instance for ...`.
* **Nguyên nhân:** Microservice nghiệp vụ chưa kịp đăng ký lên Eureka Server hoặc Eureka Server khởi động sau microservice.
* **Cách xử lý:**
  1. Mở [http://localhost:8761](http://localhost:8761) kiểm tra danh sách instances đã đăng ký.
  2. Đảm bảo tên service trong `spring.application.name` khớp chính xác với định tuyến trong [config-repo-local/api-gateway.yml](config-repo-local/api-gateway.yml).
  3. Khởi động lại service nếu cần: `docker compose restart <service-name>`.

### ❓ Sự Cố 4: Cạn kiệt Connection Pool (`HikariPool Connection Timeout`)
* **Dấu hiệu:** Log container báo lỗi `SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms`.
* **Nguyên nhân:** Có phương thức `@Transactional` đang thực thi tác vụ I/O nặng (như gọi HTTP bên ngoài, upload tệp lên S3) giữ connection quá lâu, hoặc truy vấn bị deadlock trong MySQL.
* **Cách xử lý:**
  1. Kiểm tra trạng thái container MySQL: `docker compose ps mysql-db`.
  2. Đưa các tác vụ gọi mạng (S3, Email, Feign Client) ra ngoài phạm vi giao dịch `@Transactional`.

### ❓ Sự Cố 5: Lỗi `LazyInitializationException` trong Hibernate
* **Dấu hiệu:** `org.hibernate.LazyInitializationException: could not initialize proxy - no Session`.
* **Nguyên nhân:** Truy cập thuộc tính quan hệ lười (Lazy collection) ở ngoài phạm vi Transaction (ví dụ tại Controller hoặc khi serialize JSON).
* **Cách xử lý:**
  1. **Tuyệt đối không bật** `open-in-view=true`.
  2. Sử dụng `JOIN FETCH` trong JPQL Repository hoặc thêm annotation `@EntityGraph(attributePaths = {"..."})` tại Repository để nạp sẵn dữ liệu quan hệ ngay bên trong Service.

### ❓ Sự Cố 6: Muốn làm sạch và thiết lập lại toàn bộ dữ liệu CSDL từ đầu
* Chạy lệnh hủy toàn bộ container kèm xóa sạch Docker volume:
  ```bash
  docker compose down -v
  docker compose up --build -d
  ```
  *(Hệ thống sẽ tự động khởi tạo lại schema sạch cho `internhub_db` và `notification_db`).*

---

> 📌 **Lời khuyên cho Developer:** Hãy luôn giữ vững tinh thần kỷ luật kỹ thuật, chủ động tra cứu tài liệu tại [`AGENTS.md`](file:///d:/codegym_final_project/InternHub/AGENTS.md), thư mục [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và các đặc tả tại [`docs/specs/`](file:///d:/codegym_final_project/InternHub/docs/specs/) trước khi viết bất kỳ dòng mã nào!

# 02. Cấu Trúc Hệ Thống & Kiến Trúc Microservices (System Architecture)

Tài liệu này mô tả chi tiết kiến trúc kỹ thuật phân tán (Microservices Architecture), danh mục cổng dịch vụ, cấu hình Docker Compose, hạ tầng cấu hình tập trung và cấu trúc mã nguồn chuẩn **Package-by-Feature** của dự án **InternHub (Backend)**.

---

## 1. Công Nghệ Nền Tảng (Technology Stack)

| Thành phần | Công nghệ / Thư viện | Phiên bản | Mục đích |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ & Runtime** | Java (JDK) | `17` / `21` | Ngôn ngữ hướng đối tượng hiện đại, type-safe |
| **Core Framework** | Spring Boot | `3.x` | Xây dựng ứng dụng Microservices độc lập, hiệu năng cao |
| **Cloud Infrastructure** | Spring Cloud | `2023.x / 2024.x` | Hỗ trợ Gateway, Discovery Server, Config Server |
| **API Gateway** | Spring Cloud Gateway | Tích hợp | Cổng định tuyến tập trung, lọc JWT, Rate limiting |
| **Service Discovery** | Netflix Eureka Server | Tích hợp | Đăng ký và phát hiện vị trí các microservices động |
| **Centralized Config** | Spring Cloud Config Server | Tích hợp | Quản lý cấu hình tập trung từ file cấu hình nội bộ |
| **ORM & Data Access** | Spring Data JPA / Hibernate | Tích hợp | Thao tác cơ sở dữ liệu quan hệ, tự động sinh truy vấn |
| **Security & Auth** | Spring Security & JWT | `6.x` | Xác thực người dùng, phân quyền RBAC dựa trên Claims |
| **Code Generation** | Project Lombok | Tích hợp | Tối giản boilerplate (`@Getter`, `@Setter`, `@RequiredArgsConstructor`) |
| **Build Tool** | Gradle Wrapper | `8.x` | Quản lý vòng đời build, dependencies đa dự án (Multi-project) |
| **Database** | MySQL Server | `8.0` | Hệ quản trị cơ sở dữ liệu quan hệ chính |
| **Containerization** | Docker & Docker Compose | Docker v2 | Đóng gói và điều phối các dịch vụ hạ tầng trên máy dev/prod |

---

## 2. Bản Đồ 6 Microservices & Danh Mục Cổng (Port Mapping)

Toàn bộ hệ thống Backend được phân chia thành 6 dịch vụ độc lập kết nối qua mạng nội bộ Docker (`micro-network`):

```text
                                  ┌────────────────────────┐
                                  │   Config Server        │
                                  │   (Port 8888)          │
                                  └───────────┬────────────┘
                                              │ Cung cấp config
                                              ▼
┌─────────────────┐             ┌────────────────────────┐
│  Client (React) │ ──────────> │   API Gateway          │ (Port 8080)
└─────────────────┘             └───────────┬────────────┘
                                            │ Định tuyến dựa trên Eureka
                                            ▼
                                ┌────────────────────────┐
                                │ Discovery Server       │ (Port 8761 - Eureka)
                                └───────────┬────────────┘
                 ┌──────────────────────────┼──────────────────────────┐
                 ▼                          ▼                          ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐ ┌─────────────────────────────┐
│ identity-and-access-service │ │ intern-and-program-service  │ │ reporting-and-integration-   │
│ (Port 8081)                 │ │ (Port 8082)                 │ │ service (Port 8083)         │
│ - Auth & JWT                │ │ - Quản lý thực tập sinh     │ │ - Báo cáo thống kê          │
│ - Users, Roles, Employees   │ │ - Chương trình đào tạo      │ │ - Xuất dữ liệu Excel/PDF    │
│ - Phân quyền RBAC           │ │ - Nhiệm vụ & Đánh giá       │ │ - Sao lưu hệ thống          │
└──────────────┬──────────────┘ └──────────────┬──────────────┘ └──────────────┬──────────────┘
               │                               │                               │
               └───────────────────────┬───────┴───────────────────────────────┘
                                       ▼
                       ┌───────────────────────────────┐
                       │ MySQL Database (Port 3307)    │
                       │ Database: internhub_db        │
                       └───────────────────────────────┘
```

### Chi tiết cổng dịch vụ & cấu hình:

| Tên Dịch Vụ | Thư Mục Dự Án | Port Container | Port Host | Mục Đích Kỹ Thuật |
| :--- | :--- | :---: | :---: | :--- |
| **`mysql-db`** | `./data/mysql` | `3306` | `3307` | Database MySQL 8.0, user `root`, pass `123456`, db `internhub_db` |
| **`config-server`** | `./config-server` | `8888` | `8888` | Quản lý cấu hình tập trung từ `./config-repo-local` |
| **`discovery-server`**| `./discovery-server` | `8761` | `8761` | Eureka Server cho phép các service tự đăng ký và tìm kiếm nhau |
| **`api-gateway`** | `./api-gateway` | `8080` | `8080` | Cổng tiếp nhận toàn bộ request từ Frontend, xác thực JWT, định tuyến |
| **`identity-and-access`** | `./identity-and-access-service` | `8081` | `8081` | Xử lý Login, JWT, User, Role, Employee Profile (pkg: `org.example.employeeservice`) |
| **`intern-and-program`** | `./intern-and-program-service` | `8082` | `8082` | Quản lý Interns, Training Programs, Tasks, Evaluations (pkg: `org.example.internservice`) |
| **`reporting-and-integration`**| `./reporting-and-integration-service` | `8083` | `8083` | Thống kê, Export báo cáo, tích hợp hệ thống (pkg: `org.example.reportingservice`) |

---

## 3. Cấu Trúc Mã Nguồn Chuẩn Package-by-Feature (BẮT BUỘC)

Nhằm đảm bảo tính mở rộng cao (Scalability), tính dễ bảo trì và triệt tiêu nguy cơ sinh ra các class nguyên khối (Anti-God-Classes), mỗi microservice nghiệp vụ (`identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`) bắt buộc phải tổ chức theo mô hình **Package-by-Feature**:

```text
service-folder/src/main/java/org/example/<servicename>/
├── common/                             # Thành phần hạ tầng dùng chung trong service
│   ├── entity/                         # BaseEntity (chứa id, createdAt, updatedAt)
│   ├── dto/                            # ApiResponse<T>, PageResponse<T>, ErrorResponse
│   └── util/                           # Các helper functions thuần túy
├── config/                             # Cấu hình Spring (SecurityConfig, OpenApiConfig, CorsConfig)
├── exception/                          # Xử lý ngoại lệ tập trung
│   ├── GlobalExceptionHandler.java     # @RestControllerAdvice bắt toàn bộ Exception
│   ├── ResourceNotFoundException.java  # Lỗi 404
│   ├── DuplicateResourceException.java # Lỗi 409
│   └── BadRequestException.java        # Lỗi 400
├── security/                           # Bộ lọc Security & JWT Decoder nội bộ
│   ├── JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
└── <feature>/                          # TỪNG FEATURE NGHIỆP VỤ ĐỘC LẬP (VÍ DỤ: intern, evaluation...)
    ├── controller/                     # REST API Endpoints của feature
    │   └── InternController.java       # Chỉ nhận request, @Valid, gọi Service, trả về ApiResponse<T>
    ├── dto/                            # DTO TÁCH BIỆT REQUEST VÀ RESPONSE
    │   ├── request/                    # CreateInternRequest.java, UpdateInternRequest.java
    │   └── response/                   # InternResponse.java, InternDetailResponse.java
    ├── entity/                         # JPA Entities & Enums của feature
    │   ├── Intern.java                 # Kế thừa BaseEntity
    │   └── InternStatus.java           # Enum trạng thái (PENDING, ACTIVE, COMPLETED, DROPPED)
    ├── repository/                     # Spring Data JPA Repositories
    │   └── InternRepository.java       # Kế thừa JpaRepository, JpaSpecificationExecutor
    └── service/                        # Tầng xử lý Business Logic
        ├── InternService.java          # Interface định nghĩa các phương thức
        └── impl/
            └── InternServiceImpl.java  # Cài đặt logic, @Transactional, mapping DTO
```

---

## 4. Mô Hình Luồng Dữ Liệu & Giao Tiếp Chuẩn

```
Client Request (Frontend React 19)
       │
       ▼ [Port 8080]
[API Gateway] ──(Kiểm tra JWT & Phân quyền cơ bản)
       │
       ▼ [Eureka Service Discovery]
[Target Microservice] (Port 8081 / 8082 / 8083)
       │
       ├─► [JwtAuthenticationFilter] ➔ Nạp UserDetails vào SecurityContext
       │
       ├─► [Feature Controller] ➔ Validate @Valid Request DTO
       │
       ├─► [Feature ServiceImpl] ➔ Thực thi nghiệp vụ, kiểm tra ràng buộc logic (@Transactional)
       │
       ├─► [Feature Repository] ➔ Thực thi câu truy vấn Hibernate / JPA tối ưu
       │
       ├─► [MySQL 8.0] ➔ Đọc / Ghi dữ liệu thực tế
       │
       └─► [Response Pipeline] ➔ Map Entity ➔ Response DTO ➔ Đóng gói ApiResponse<T> ➔ Client
```

---

## 5. Tiêu Chuẩn Phân Trang Đồng Bộ (Pagination Standard)

- **Backend Spring Data JPA (0-indexed)**:
  - Spring Boot nhận tham số phân trang bắt đầu từ `0`: `page = 0` (Trang đầu tiên), `size = 10`, `sort = "createdAt,desc"`.
  - Phía Service luôn trả về DTO phân trang chuẩn hóa `PageResponse<T>` bọc trong `ApiResponse<PageResponse<T>>`:
    ```json
    {
      "success": true,
      "message": "Lấy danh sách thực tập sinh thành công",
      "data": {
        "content": [ ... ],
        "pageNumber": 0,
        "pageSize": 10,
        "totalElements": 45,
        "totalPages": 5,
        "isLast": false
      },
      "timestamp": "2026-09-22T14:30:00"
    }
    ```
- **Sự phối hợp với Frontend**:
  - Frontend giao diện hiển thị cho người dùng là `1-indexed` (`Trang 1`, `Trang 2`).
  - Phía Frontend Hook/Service có trách nhiệm tự trừ 1 khi gửi request xuống Backend (`pageUI - 1`), Backend giữ nguyên chuẩn `0-indexed` chuẩn mực của Spring Data.

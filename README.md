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
| **identity-and-access-service** | Spring Boot / JPA / Security | *Ẩn (nội bộ)*| `8081` | Quản trị hệ thống, Quản lý tài khoản, Phân quyền vai trò & JWT |
| **intern-and-program-service** | Spring Boot / JPA / Security | *Ẩn (nội bộ)*| `8082` | Quản lý hồ sơ thực tập sinh (TM-1..4) & Chương trình |
| **reporting-and-integration-service** | Spring Boot / JPA / Security | *Ẩn (nội bộ)*| `8083` | Sao lưu định kỳ (TM-8) & Nhật ký kiểm toán (TM-9) |
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

## 📋 Đặc tả chương trình & Kiến trúc nghiệp vụ (Program Specifications)

### 1. Tầm nhìn & Mục tiêu sản phẩm (Product Vision & Goals)
**InternHub** là hệ thống số hóa toàn diện quy trình quản lý thực tập sinh trong doanh nghiệp, giải quyết bài toán nhập liệu rời rạc trên Excel, thiếu minh bạch trong đánh giá và rời rạc trong giao việc:
1. **Chuẩn hóa & Số hóa:** Quản lý tập trung hồ sơ, hợp đồng, chấm công và đánh giá trên nền tảng Web/API đồng nhất.
2. **Tối ưu chi phí nhân sự:** Tự động hóa các tác vụ lặp lại của HR (sinh mã thực tập sinh, tổng hợp chấm công, gửi thông báo).
3. **Minh bạch trải nghiệm thực tập:** Thực tập sinh chủ động theo dõi lịch thực tập, nhận nhiệm vụ, nộp báo cáo tuần và xem phản hồi từ người hướng dẫn (Mentor).
4. **Cầu nối Doanh nghiệp - Nhà trường:** Cung cấp số liệu thống kê chính xác về chất lượng đào tạo và sinh viên theo từng trường/ngành.
5. **Dữ liệu phân tích chiến lược:** Đo lường tỷ lệ hoàn thành kỳ thực tập và tỷ lệ chuyển đổi thành nhân viên chính thức.

---

### 2. Các vai trò trong hệ thống (Actors & Permissions)

| Vai trò (Role) | Mô tả trách nhiệm chính | Phạm vi quyền hạn |
| :--- | :--- | :--- |
| 🛡️ **Admin** | Quản trị tài khoản, phân quyền, tích hợp hệ thống ngoài (HRM, thiết bị quét thẻ/QR), xem audit logs | Toàn quyền hệ thống |
| 👔 **HR (Nhân sự)** | Quản lý hồ sơ, xét duyệt ứng viên, tạo chương trình thực tập, thiết lập ca làm việc, duyệt nghỉ phép, tổng kết đánh giá | Quản lý nghiệp vụ chung |
| 🧑‍🏫 **Mentor** | Hướng dẫn chuyên môn, giao nhiệm vụ, nhận xét báo cáo tuần, chấm điểm kỹ năng và thái độ | Quản lý nhóm thực tập sinh |
| 🎓 **Thực tập sinh (Intern)** | Nộp hồ sơ/CV, check-in/check-out hằng ngày, nộp tiến độ công việc, nộp báo cáo tuần, gửi đơn nghỉ phép | Cá nhân thực tập sinh |
| ⚙️ **Hệ thống (System)** | Tác vụ ngầm tự động: Gửi email nhắc nhở/lịch họp, đẩy thông báo in-app, sao lưu dữ liệu | Tự động hóa nền |

---

### 3. Danh mục 10 Module chức năng cốt lõi (Core Business Modules)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         HỆ THỐNG QUẢN LÝ INTERNHUB                                │
├─────────────────────────┬────────────────────────────┬───────────────────────────┤
│ Module 1: Hồ sơ SV      │ Module 2: Tiếp nhận/Duyệt  │ Module 3: Chương trình TT │
│ Module 4: Công việc/Task│ Module 5: Chấm công/Nghỉ   │ Module 6: Phụ cấp/Hỗ trợ  │
│ Module 7: Mentor/Phòng  │ Module 8: Báo cáo/Thống kê │ Module 9: Tích hợp/Thông  │
│ Module 10: Quản trị/Auth│                            │     báo & Email           │
└─────────────────────────┴────────────────────────────┴───────────────────────────┘
```

1. **Module 1: Quản lý hồ sơ thực tập sinh:** Thêm, sửa, tìm kiếm, lọc theo trường/ngành, quản lý CV và tài liệu tiếp nhận ([TM-1](docs/specs/TM-1-create-intern-profile-spec.md), [TM-2](docs/specs/TM-2-update-intern-profile-spec.md), [TM-3](docs/specs/TM-3-search-filter-interns-spec.md)).
2. **Module 2: Tiếp nhận và xét duyệt:** Đăng ký trực tuyến, quy trình HR phê duyệt hồ sơ, xác nhận hợp đồng thực tập.
3. **Module 3: Quản lý chương trình thực tập:** Thiết lập khung chương trình theo phòng ban, thời gian bắt đầu/kết thúc, lịch cá nhân.
4. **Module 4: Quản lý công việc và đánh giá:** Mentor giao task, Intern nộp kết quả & báo cáo tuần, Mentor phản hồi & đánh giá định kỳ, HR tổng kết cuối khóa.
5. **Module 5: Quản lý chấm công và thời gian:** Check-in / Check-out hằng ngày, thiết lập ca làm việc linh hoạt, nộp và duyệt đơn xin nghỉ phép, báo cáo chuyên cần ([Chi tiết đặc tả](docs/specs/time_and_attendance.md)).
6. **Module 6: Quản lý hỗ trợ và quyền lợi:** Thiết lập phụ cấp, lịch sử nhận trợ cấp, gửi yêu cầu hỗ trợ (giấy xác nhận thực tập).
7. **Module 7: Quản lý mentor và phòng ban:** Danh mục phòng ban, gán mentor hướng dẫn cho thực tập sinh, theo dõi số lượng tiếp nhận.
8. **Module 8: Báo cáo và thống kê:** Thống kê theo trường/ngành, tỷ lệ hoàn thành chương trình, xuất báo cáo ra Excel/PDF.
9. **Module 9: Tích hợp và thông báo:** In-app Notification, gửi email tự động khi có lịch họp/sự kiện, tích hợp chấm công QR Code/thẻ RFID, tích hợp HRM.
10. **Module 10: Quản trị hệ thống & Xác thực:** Đăng nhập JWT, quản lý tài khoản theo Role, bảo mật dữ liệu, audit log.

---

## 📚 Tài liệu kỹ thuật liên quan (Documentation Links)
- 📖 **Hướng dẫn cài đặt & Quy chuẩn làm việc nhóm:** Xem chi tiết tại [`tutorial.md`](tutorial.md) (bao gồm Git Workflow, Branching Model, Commit Convention và Troubleshooting).
- 🔴 **Quy chuẩn lập trình & Kiến trúc:** Xem chi tiết tại [`.antigravity/rules.md`](.antigravity/rules.md).
- 📋 **Quy chuẩn viết Đặc tả nghiệp vụ:** Xem chi tiết tại [`.antigravity/spec-rules.md`](.antigravity/spec-rules.md).
- 📑 **Bộ đặc tả tính năng đã hoàn thiện:** Xem tại thư mục [`docs/specs/`](docs/specs/).


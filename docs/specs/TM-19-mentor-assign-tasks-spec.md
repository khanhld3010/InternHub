# Specification: Quản Lý Bảng Nhiệm Vụ (MissionBoard) & Giao Việc Cho Thực Tập Sinh (TM-19)

> **Trạng thái:** DRAFT / PENDING_APPROVAL  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md`  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-19](https://robluccibn9935.atlassian.net/browse/TM-19)  
> **Tiêu đề Jira:** *Mentor - Giao nhiệm vụ cho thực tập sinh (MissionBoard)*  
> **Nhánh Git dự kiến:** `feature/TM-19/mentor-missionboard-tasks`  
> **Cấp độ thay đổi (Change Level):** **L3** (Mô hình quan hệ N-N giữa Program và Mentor, Domain Model `MissionBoard` & `MissionItem`, giao việc cho 1 hoặc nhiều TTS, 3 trạng thái Kanban "chưa làm" / "đang làm" / "hoàn thiện", phân quyền RBAC và tích hợp Gateway).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất.

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-02 | AI Senior Pair-Programmer | `TM-19` | Tạo mới | Thiết kế đặc tả ban đầu theo mô hình Task đơn lẻ cá nhân. |
| **v2.0.0** | 2026-10-02 | AI Senior Pair-Programmer & User | `TM-19` | Tái cấu trúc toàn diện kiến trúc | Chuyển đổi sang mô hình **MissionBoard**: Program - Mentor quan hệ N-N; Mentor tạo MissionBoard trong Program; mỗi công việc chi tiết hỗ trợ gán 1 hoặc nhiều TTS; chuẩn hóa 3 trạng thái: "chưa làm" (`TODO`), "đang làm" (`IN_PROGRESS`), "hoàn thiện" (`COMPLETED`) theo thỏa thuận thiết kế. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Mentor Quản Lý Bảng Nhiệm Vụ (MissionBoard) & Giao Việc Cho Thực Tập Sinh (Mentor MissionBoard & Task Assignment)
- **Jira Ticket:** [TM-19](https://robluccibn9935.atlassian.net/browse/TM-19)
- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082):
     - Mở rộng quan hệ N-N giữa `InternshipProgram` và `Mentor` thông qua thực thể `ProgramMentor`.
     - Xây dựng module `org.example.internservice.mission`: Quản lý thực thể `MissionBoard` và `MissionItem`.
     - Hỗ trợ gán một mục công việc cho 1 hoặc nhiều thực tập sinh cùng tham gia (`mission_item_assignees`).
  2. `api-gateway` (Port 8080): Định tuyến `/api/mission-boards/**` và `/api/mission-items/**` về `intern-and-program-service`.
- **Target Users & Roles:**
  - `ROLE_MENTOR`: Người hướng dẫn được phân công vào Program, có toàn quyền tạo MissionBoard, tạo/sửa/xóa các mục công việc, chọn 1 hoặc nhiều TTS trong Program tham gia, và điều phối 3 trạng thái công việc.
  - `ROLE_HR` & `ROLE_ADMIN`: Phân công Mentor vào Program (quan hệ N-N) và giám sát toàn bộ MissionBoard của các chương trình thực tập.
- **Change Level:** **L3** (Tạo mới bảng quan hệ N-N `program_mentors`, bảng `mission_boards`, `mission_items`, `mission_item_assignees`, thiết kế REST API và phân quyền sở hữu).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Quản lý đào tạo theo dự án/chương trình (Cohort/Program-Based Training):** Trong một chương trình thực tập, một nhóm TTS thường cùng thực hiện các giai đoạn đào tạo (ví dụ: *Giai đoạn 1: Onboarding*, *Giai đoạn 2: Phát triển tính năng*). Việc gom các công việc vào **MissionBoard** giúp Mentor điều phối một cách có hệ thống, không bị phân mảnh.
2. **Hợp tác linh hoạt giữa các Mentor (Many-to-Many Mentor - Program):** Một chương trình lớn có thể có nhiều Mentor cùng hướng dẫn chuyên môn (ví dụ Mentor Backend, Mentor Frontend, Mentor DevOps); đồng thời một Mentor có thể tham gia hướng dẫn ở nhiều chương trình khác nhau.
3. **Phân công đa dạng (1 hoặc nhiều TTS cho 1 đầu việc):** Cho phép Mentor giao một mục công việc chi tiết cho một cá nhân phụ trách hoặc một nhóm TTS cùng phối hợp hoàn thành (Pair Programming, Team Project).
4. **Mô hình Kanban 3 trạng thái trực quan:** Chuẩn hóa quy trình theo 3 trạng thái:
   - **`"chưa làm"` (`TODO`)**: Công việc mới giao, chưa tiến hành.
   - **`"đang làm"` (`IN_PROGRESS`)**: Đang trong quá trình thực hiện.
   - **`"hoàn thiện"` (`COMPLETED`)**: Công việc đã được kiểm tra, nghiệm thu và hoàn thành.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- **Mối quan hệ N-N giữa Program và Mentor:**
  - Tạo bảng `program_mentors` liên kết `program_id` và `mentor_id`.
  - API lấy danh sách các Program mà Mentor đăng nhập đang phụ trách: `GET /api/programs/my-mentored-programs`.
  - API lấy danh sách các Thực tập sinh thuộc Program để Mentor lựa chọn khi giao việc: `GET /api/programs/{programId}/interns`.
- **Quản lý Bảng Nhiệm Vụ (MissionBoard):**
  - Mentor tạo MissionBoard gắn với Program mà mình phụ trách: `POST /api/programs/{programId}/mission-boards`.
  - Lấy danh sách MissionBoard của Program: `GET /api/programs/{programId}/mission-boards`.
  - Lấy chi tiết MissionBoard kèm toàn bộ các mục công việc phân theo 3 trạng thái: `GET /api/mission-boards/{boardId}`.
  - Cập nhật MissionBoard: `PUT /api/mission-boards/{boardId}` (tiêu đề, mô tả, đóng/mở board).
  - Xóa/Lưu trữ MissionBoard: `DELETE /api/mission-boards/{boardId}`.
- **Quản lý Mục Công Việc Chi Tiết (MissionItem):**
  - Mentor tạo mục công việc chi tiết trong Board, chọn 1 hoặc nhiều `internIds` trong Program tham gia: `POST /api/mission-boards/{boardId}/items`.
  - Mentor cập nhật mục công việc (tiêu đề, mô tả, mức độ ưu tiên, hạn chót, danh sách `internIds`): `PUT /api/mission-items/{itemId}`.
  - Mentor cập nhật trạng thái mục công việc: `PATCH /api/mission-items/{itemId}/status` (chọn `TODO`, `IN_PROGRESS`, hoặc `COMPLETED`).
  - Mentor xóa mục công việc: `DELETE /api/mission-items/{itemId}`.

### 3.2. Ngoài phạm vi (Out of Scope)
- Không can thiệp sửa đổi giao diện Frontend trong task Backend này (Rule 7 - Boundary Isolation).
- Không xây dựng tính năng WebSocket kéo thả Kanban realtime (Backend cung cấp API chuẩn RESTful cho thao tác đổi trạng thái).

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Mentor tạo Board hoặc giao việc cho Program mà mình KHÔNG phụ trách
- **Vấn đề:** Mentor A gọi API tạo Board hoặc thêm task vào Program mà Mentor A không có trong danh sách `program_mentors`.
- **Giải pháp:** Service kiểm tra bắt buộc quyền sở hữu:
  ```java
  boolean isMentorOfProgram = programMentorRepository.existsByProgramIdAndMentorId(programId, userDetails.getUserId());
  if (!isMentorOfProgram && !isHrOrAdmin(userDetails)) {
      throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
  }
  ```

### 4.2. Edge Case 2: Giao việc cho Thực tập sinh KHÔNG thuộc Program đó
- **Vấn đề:** Khi tạo MissionItem, Mentor truyền `internIds` chứa ID của một TTS thuộc Program khác hoặc TTS không tồn tại.
- **Giải pháp:** Service kiểm tra tính hợp lệ của toàn bộ `internIds`:
  - Tất cả các `internIds` phải tồn tại trong cơ sở dữ liệu.
  - Tất cả các TTS được chọn phải có `intern.getProgram().getId().equals(board.getProgram().getId())`.
  - Nếu có bất kỳ TTS nào không thuộc Program, ném `BadRequestException("Thực tập sinh [ID: ...] không thuộc chương trình đào tạo của bảng nhiệm vụ này")`.

### 4.3. Edge Case 3: Trạng thái không hợp lệ của Thực tập sinh khi giao việc
- **Vấn đề:** TTS trong Program đã bị đình chỉ (`TERMINATED`), từ chối (`REJECTED`) hoặc bảo lưu (`ON_HOLD`).
- **Giải pháp:** Chỉ cho phép gán mục công việc cho TTS có trạng thái đang học tập: `INTERNING` hoặc `APPROVED` (nếu chương trình sắp khởi chạy). Loại bỏ và cảnh báo nếu chọn TTS `TERMINATED` / `REJECTED`.

### 4.4. Edge Case 4: Lỗi N+1 Query khi tải danh sách MissionItem kèm danh sách Assignees
- **Vấn đề:** Một MissionBoard có 20 mục công việc, mỗi mục có quan hệ Many-to-Many với 3-5 TTS. Nếu query thông thường bằng JPA Lazy Loading sẽ sinh ra 21 câu SELECT gây chậm hệ thống.
- **Giải pháp:** 
  - Sử dụng `@EntityGraph(attributePaths = {"assignees"})` hoặc câu truy vấn `JOIN FETCH mi.assignees` trong Repository để nạp toàn bộ công việc và danh sách TTS tham gia chỉ trong 1 truy vấn duy nhất.

### 4.5. Edge Case 5: Hạn chót mục công việc vượt quá thời gian kết thúc của Program
- **Vấn đề:** Program kết thúc ngày `2026-11-30`, nhưng Mentor đặt hạn chót mục công việc là `2026-12-15`.
- **Giải pháp:** Validate `item.dueDate <= program.getEndDate()`. Nếu vượt quá, trả về lỗi `400 BAD_REQUEST`: *"Hạn chót công việc không được vượt quá ngày kết thúc chương trình thực tập"*.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tra cứu Program của Mentor):** Hệ thống cung cấp API cho Mentor xem danh sách các chương trình mà mình đang phụ trách, kèm số lượng TTS hiện có trong chương trình.
- **FR-2 (Tạo MissionBoard):** Mentor có thể tạo nhiều MissionBoard trong một Program (ví dụ: chia theo tuần, theo sprint hoặc theo giai đoạn đào tạo).
- **FR-3 (Tạo MissionItem đa Assignee):** Mentor có thể tạo mục công việc chi tiết với:
  - Tiêu đề (`title`): 3 - 200 ký tự.
  - Mô tả (`description`): Tùy chọn.
  - Mức độ ưu tiên (`priority`): `LOW`, `MEDIUM`, `HIGH`.
  - Hạn chót (`dueDate`): Tùy chọn hoặc ngày trong tương lai.
  - Danh sách người tham gia (`internIds`): Mảng chứa 1 hoặc nhiều ID của TTS trong Program.
- **FR-4 (Cập nhật 3 Trạng thái):** Mentor có thể cập nhật trạng thái mục công việc giữa 3 trạng thái:
  - `TODO` ("chưa làm")
  - `IN_PROGRESS` ("đang làm")
  - `COMPLETED` ("hoàn thiện")
- **FR-5 (Chỉnh sửa & Xóa):** Mentor có thể sửa nội dung, thêm/bớt TTS tham gia, hoặc xóa mục công việc / bảng nhiệm vụ.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Ràng buộc Mentor - Program N-N):** Một Program có thể có nhiều Mentor phụ trách; một Mentor có thể phụ trách nhiều Program. Chỉ Mentor thuộc danh sách `program_mentors` của Program đó mới có quyền tạo và chỉnh sửa MissionBoard trong Program.
- **BR-2 (Ràng buộc Assignee - Program):** Mọi TTS được gán vào `MissionItem` bắt buộc phải có hồ sơ thuộc cùng `program_id` của MissionBoard đó.
- **BR-3 (Ràng buộc số lượng Assignee):** Danh sách `internIds` khi tạo mục công việc không được rỗng (tối thiểu 1 TTS tham gia).
- **BR-4 (Chuẩn hóa 3 Trạng thái duy nhất):**
  - Chỉ chấp nhận 3 giá trị Enum: `TODO`, `IN_PROGRESS`, `COMPLETED`.
  - Không có các trạng thái trung gian gây phức tạp luồng nghiệp vụ.
- **BR-5 (Ràng buộc Hạn chót):** `dueDate` (nếu có) phải lớn hơn hoặc bằng ngày tạo và không được vượt quá `program.endDate`.

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Cấu Trúc Bảng MySQL

#### 1. Bảng liên kết `program_mentors` (N-N giữa Program và Mentor)
```sql
CREATE TABLE program_mentors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(100) NOT NULL,
    mentor_email VARCHAR(100) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_pm_program FOREIGN KEY (program_id) REFERENCES internship_programs (id) ON DELETE CASCADE,
    UNIQUE KEY uk_program_mentor (program_id, mentor_id),
    INDEX idx_pm_mentor (mentor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 2. Bảng `mission_boards`
```sql
CREATE TABLE mission_boards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_board_program FOREIGN KEY (program_id) REFERENCES internship_programs (id) ON DELETE CASCADE,
    INDEX idx_board_program (program_id),
    INDEX idx_board_mentor (mentor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 3. Bảng `mission_items`
```sql
CREATE TABLE mission_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    due_date DATE NULL,
    order_index INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_item_board FOREIGN KEY (board_id) REFERENCES mission_boards (id) ON DELETE CASCADE,
    INDEX idx_item_board_status (board_id, status),
    INDEX idx_item_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 4. Bảng liên kết `mission_item_assignees` (N-N giữa MissionItem và InternProfile)
```sql
CREATE TABLE mission_item_assignees (
    item_id BIGINT NOT NULL,
    intern_id BIGINT NOT NULL,
    PRIMARY KEY (item_id, intern_id),
    CONSTRAINT fk_mia_item FOREIGN KEY (item_id) REFERENCES mission_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_mia_intern FOREIGN KEY (intern_id) REFERENCES intern_profiles (id) ON DELETE CASCADE,
    INDEX idx_mia_intern (intern_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 7.2. JPA Entity Mapping
- Toàn bộ Entity kế thừa `org.example.internservice.common.entity.BaseEntity`
- Package:
  - `org.example.internservice.program.entity.ProgramMentor`
  - `org.example.internservice.mission.entity.MissionBoard`
  - `org.example.internservice.mission.entity.MissionItem`
- Enums:
  - `MissionItemStatus`: `TODO` (*"chưa làm"*), `IN_PROGRESS` (*"đang làm"*), `COMPLETED` (*"hoàn thiện"*)
  - `MissionPriority`: `LOW`, `MEDIUM`, `HIGH`
  - `BoardStatus`: `ACTIVE`, `ARCHIVED`

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### 8.1. Danh mục Endpoints Cho Mentor

| STT | HTTP Method | Endpoint | Quyền | Mục đích |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `GET` | `/api/mentor/programs` | `ROLE_MENTOR` | Lấy danh sách Program mà Mentor đăng nhập phụ trách |
| **2** | `GET` | `/api/programs/{programId}/interns` | `ROLE_MENTOR`, `ROLE_HR` | Lấy danh sách TTS thuộc Program để chọn khi giao việc |
| **3** | `POST` | `/api/programs/{programId}/mission-boards` | `ROLE_MENTOR` | Mentor tạo MissionBoard trong Program |
| **4** | `GET` | `/api/programs/{programId}/mission-boards` | `ROLE_MENTOR`, `ROLE_HR` | Lấy danh sách MissionBoard của Program |
| **5** | `GET` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR`, `ROLE_HR` | Xem chi tiết MissionBoard (bao gồm danh sách items theo 3 cột) |
| **6** | `PUT` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR` | Cập nhật thông tin MissionBoard |
| **7** | `DELETE` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR` | Xóa/Lưu trữ MissionBoard |
| **8** | `POST` | `/api/mission-boards/{boardId}/items` | `ROLE_MENTOR` | Tạo mục công việc chi tiết (chọn 1 hoặc nhiều TTS) |
| **9** | `PUT` | `/api/mission-items/{itemId}` | `ROLE_MENTOR` | Cập nhật mục công việc (tiêu đề, hạn chót, danh sách TTS) |
| **10** | `PATCH` | `/api/mission-items/{itemId}/status` | `ROLE_MENTOR` | Cập nhật trạng thái (`TODO`, `IN_PROGRESS`, `COMPLETED`) |
| **11** | `DELETE` | `/api/mission-items/{itemId}` | `ROLE_MENTOR` | Xóa mục công việc chi tiết |

---

### 8.2. Chi tiết Payloads Mẫu

#### 1. Tạo MissionBoard: `POST /api/programs/{programId}/mission-boards`
**Request Body:**
```json
{
  "title": "Giai đoạn 1: Onboarding & Core Training",
  "description": "Bảng theo dõi các đầu việc thiết lập môi trường, học quy chuẩn code và kiến trúc microservices trong 2 tuần đầu."
}
```

**Response Success (201 Created):**
```json
{
  "code": 201,
  "success": true,
  "message": "Tạo bảng nhiệm vụ thành công",
  "data": {
    "id": 1,
    "programId": 5,
    "programName": "Chương trình Java Backend K28",
    "mentorId": 2,
    "mentorName": "Vũ Thị Thu Hà",
    "title": "Giai đoạn 1: Onboarding & Core Training",
    "description": "Bảng theo dõi các đầu việc thiết lập môi trường, học quy chuẩn code và kiến trúc microservices trong 2 tuần đầu.",
    "status": "ACTIVE",
    "totalItems": 0,
    "todoCount": 0,
    "inProgressCount": 0,
    "completedCount": 0,
    "createdAt": "2026-10-02T10:30:00"
  },
  "timestamp": "2026-10-02T10:30:00"
}
```

#### 2. Tạo mục công việc chi tiết (Gán 1 hoặc nhiều TTS): `POST /api/mission-boards/{boardId}/items`
**Request Body:**
```json
{
  "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
  "description": "Fork repository, cấu hình application.yml và kiểm tra Eureka dashboard hiển thị đúng service.",
  "priority": "HIGH",
  "dueDate": "2026-10-10",
  "internIds": [12, 15, 18]
}
```

**Response Success (201 Created):**
```json
{
  "code": 201,
  "success": true,
  "message": "Tạo mục công việc thành công",
  "data": {
    "id": 101,
    "boardId": 1,
    "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
    "description": "Fork repository, cấu hình application.yml và kiểm tra Eureka dashboard hiển thị đúng service.",
    "priority": "HIGH",
    "status": "TODO",
    "dueDate": "2026-10-10",
    "assignees": [
      {
        "id": 12,
        "fullName": "Nguyễn Văn An",
        "internCode": "INT-2026-0012",
        "email": "an.nguyen@example.com"
      },
      {
        "id": 15,
        "fullName": "Trần Thị Mai",
        "internCode": "INT-2026-0015",
        "email": "mai.tran@example.com"
      },
      {
        "id": 18,
        "fullName": "Lê Hoàng Phúc",
        "internCode": "INT-2026-0018",
        "email": "phuc.le@example.com"
      }
    ],
    "createdAt": "2026-10-02T10:35:00"
  },
  "timestamp": "2026-10-02T10:35:00"
}
```

#### 3. Mentor cập nhật trạng thái mục công việc: `PATCH /api/mission-items/{itemId}/status`
**Request Body:**
```json
{
  "status": "IN_PROGRESS"
}
```
*(Hoặc `"status": "COMPLETED"` khi đã hoàn thiện)*

**Response Success (200 OK):**
```json
{
  "code": 200,
  "success": true,
  "message": "Cập nhật trạng thái công việc thành công",
  "data": {
    "id": 101,
    "status": "IN_PROGRESS",
    "updatedAt": "2026-10-02T11:00:00"
  },
  "timestamp": "2026-10-02T11:00:00"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

```text
[Mentor]
   │
   ▼
[API Gateway: 8080]
   │
   ▼
[intern-and-program-service: 8082]
   │
   ├─► 1. Tra cứu Program của Mentor (GET /api/mentor/programs)
   │      - Query bảng `program_mentors` theo `mentor_id = userDetails.userId`.
   │
   ├─► 2. Lấy danh sách TTS trong Program (GET /api/programs/{id}/interns)
   │      - Query `intern_profiles` theo `program_id = :id` và status IN (APPROVED, INTERNING).
   │
   ├─► 3. Tạo MissionBoard (POST /api/programs/{id}/mission-boards)
   │      - Kiểm tra Mentor có thuộc Program không (`programMentorRepository`).
   │      - Tạo bản ghi trong `mission_boards` với status = ACTIVE.
   │
   ├─► 4. Thêm mục công việc chi tiết (POST /api/mission-boards/{id}/items)
   │      - Kiểm tra toàn bộ `internIds` có thuộc cùng `program_id` không.
   │      - Lưu `mission_items` với status = TODO.
   │      - Gán liên kết N-N vào `mission_item_assignees`.
   │
   └─► 5. Điều phối trạng thái (PATCH /api/mission-items/{id}/status)
          - Chuyển đổi giữa `TODO` -> `IN_PROGRESS` -> `COMPLETED`.
          - Kiểm tra quyền Mentor của Program trước khi cập nhật.
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Chống N+1 Query triệt để (Rule 22):** Khi lấy chi tiết MissionBoard gồm toàn bộ items và assignees, bắt buộc sử dụng `JOIN FETCH` hoặc `@EntityGraph` trên quan hệ `item.assignees`.
2. **Tuân thủ Package-by-Feature (Rule 14):**
   - Các thực thể MissionBoard và MissionItem nằm trong `org.example.internservice.mission.*`.
   - Thực thể `ProgramMentor` nằm trong `org.example.internservice.program.entity.ProgramMentor`.
3. **Tiêu chuẩn Anti-God-Class (Rule 17):** Các class Service không vượt quá 250 dòng. Tách riêng `MissionItemService` và `MissionBoardService` nếu nghiệp vụ mở rộng.
4. **An toàn kiểm soát lỗi (Rule 19):** Bọc 100% response trong `ApiResponse<T>`.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1:** Mentor chỉ xem được và chỉ tạo được MissionBoard trong các Program mà mình được phân công (`program_mentors`).
- [ ] **AC-2:** Tạo được MissionBoard gắn với Program cụ thể, lưu đúng `mentor_id` và `program_id`.
- [ ] **AC-3:** Mentor có thể tạo mục công việc và chọn 1 hoặc nhiều TTS trong Program làm assignees.
- [ ] **AC-4:** Chặn ngay lập tức nếu Mentor chọn TTS không thuộc Program của Board đó (trả về lỗi `400 BAD_REQUEST`).
- [ ] **AC-5:** Mentor có thể cập nhật trạng thái mục công việc thành công giữa 3 trạng thái: `TODO` ("chưa làm"), `IN_PROGRESS` ("đang làm"), `COMPLETED` ("hoàn thiện").
- [ ] **AC-6:** API lấy chi tiết Board trả về đầy đủ các công việc phân nhóm hoặc đính kèm danh sách `assignees` chính xác, không bị lỗi N+1 Query.

---

## 12. Unit & Integration Test Cases Checklist

- [ ] **UT-BE-01:** `createBoard_Success`: Mentor tạo board thành công cho Program mà mình phụ trách.
- [ ] **UT-BE-02:** `createBoard_Fail_NotMentorOfProgram`: Ném `AccessDeniedException` khi Mentor không thuộc Program.
- [ ] **UT-BE-03:** `createItem_Success_SingleIntern`: Tạo mục công việc gán cho 1 TTS thành công.
- [ ] **UT-BE-04:** `createItem_Success_MultipleInterns`: Tạo mục công việc gán cho nhiều TTS cùng lúc thành công.
- [ ] **UT-BE-05:** `createItem_Fail_InternNotInProgram`: Ném `BadRequestException` khi có TTS không thuộc Program.
- [ ] **UT-BE-06:** `updateStatus_Success`: Mentor đổi trạng thái mục công việc từ `TODO` sang `IN_PROGRESS` và `COMPLETED`.
- [ ] **UT-BE-07:** `updateStatus_Fail_InvalidStatus`: Ném lỗi khi truyền trạng thái ngoài 3 trạng thái chuẩn.
- [ ] **UT-BE-08:** `deleteItem_Success`: Mentor xóa mục công việc, tự động dọn sạch quan hệ bảng `mission_item_assignees`.

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

### 13.1. Entities & Enums
- [ ] `program/entity/ProgramMentor.java` (kế thừa `BaseEntity`)
- [ ] `mission/entity/MissionBoard.java` (kế thừa `BaseEntity`)
- [ ] `mission/entity/MissionItem.java` (kế thừa `BaseEntity`)
- [ ] `mission/entity/enums/MissionItemStatus.java` (`TODO`, `IN_PROGRESS`, `COMPLETED`)
- [ ] `mission/entity/enums/MissionPriority.java` (`LOW`, `MEDIUM`, `HIGH`)
- [ ] `mission/entity/enums/BoardStatus.java` (`ACTIVE`, `ARCHIVED`)

### 13.2. DTOs
- [ ] `mission/dto/request/CreateMissionBoardRequest.java`
- [ ] `mission/dto/request/UpdateMissionBoardRequest.java`
- [ ] `mission/dto/request/CreateMissionItemRequest.java`
- [ ] `mission/dto/request/UpdateMissionItemRequest.java`
- [ ] `mission/dto/request/UpdateItemStatusRequest.java`
- [ ] `mission/dto/response/MissionBoardResponse.java`
- [ ] `mission/dto/response/MissionBoardDetailResponse.java`
- [ ] `mission/dto/response/MissionItemResponse.java`
- [ ] `mission/dto/response/AssigneeResponse.java`

### 13.3. Repositories
- [ ] `program/repository/ProgramMentorRepository.java`
- [ ] `mission/repository/MissionBoardRepository.java`
- [ ] `mission/repository/MissionItemRepository.java`

### 13.4. Services
- [ ] `mission/service/MissionBoardService.java` & `MissionBoardServiceImpl.java`
- [ ] `mission/service/MissionItemService.java` & `MissionItemServiceImpl.java`

### 13.5. Controllers
- [ ] `mission/controller/MissionBoardController.java`
- [ ] `mission/controller/MissionItemController.java`
- [ ] `program/controller/MentorProgramController.java` (hoặc mở rộng `ProgramController`)

### 13.6. Gateway Route
- [ ] Cập nhật `config-repo-local/api-gateway.yml`: Route cho `/api/mission-boards/**`, `/api/mission-items/**`.

### 13.7. Unit Tests
- [ ] `src/test/java/.../mission/service/MissionBoardServiceTest.java`

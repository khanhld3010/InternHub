# Specification: Trung Tâm Điều Phối Phòng Ban & Cân Bằng Tải Mentor (Department Capacity & Workload Balancing Hub)

> **Trạng thái:** DRAFT / PROPOSED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-department-capacity-hub-spec.md`  
> **Áp dụng quy tắc:** [Persistent Spec & Change Rationale](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/04-development-guide.md)  
> **Tham chiếu kiến trúc liên quan:**
> - [`TM-15-18-manage-internship-programs-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-15-18-manage-internship-programs-spec.md) (Nguyên tắc kế thừa `department_id` từ Chương trình thực tập)
> - [`TM-16-assign-mentor-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-16-assign-mentor-spec.md) (Quy tắc ngưỡng cảnh báo tải Mentor `activeInternCount` và không hard-block)
> - [`TM-weekly-assessment-hub-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-weekly-assessment-hub-spec.md) (Dữ liệu đánh giá tuần để đo lường sức khỏe đào tạo)
> - [`TM-final-evaluation-summary-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-final-evaluation-summary-spec.md) (Dữ liệu đề xuất tuyển dụng `HIRE_FULLTIME`)

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-03 | Lead Engineer & Antigravity | `TM-DEPT-HUB` | Tạo mới | Thiết kế đặc tả chuyển đổi giao diện Phòng ban từ bảng tĩnh sang **Bento Grid điều phối tải**, giải quyết triệt để 6 phản biện kiến trúc: (1) Khóa toàn vẹn dữ liệu kế thừa phòng ban qua luồng Reassign Program; (2) Tách bạch Event Notification không gửi cho Mentor mới khi chưa gán; (3) Thống nhất biến `activeInternCount`; (4) Giữ ngưỡng cảnh báo mềm 5 TTS/Mentor; (5) `plannedCapacityQuota` chỉ là Soft Display Metric ngăn ngừa Deadlock; (6) Bổ sung công thức Health Matrix. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Trung Tâm Điều Phối Phòng Ban & Cân Bằng Tải Mentor (Department Capacity & Workload Balancing Hub)
- **Jira Ticket:** `TM-DEPT-HUB`
- **Target Microservices:**
  - `intern-and-program-service` (Port 8082 - Logic Aggregation, Department Entities & Endpoints)
  - `api-gateway` (Port 8080 - Định tuyến `/api/departments/**`)
  - `notification-service` (Port 8085 - Realtime Event chuyển chương trình/phòng ban)
- **Target Frontend:** `InternHub-Frontend` (React 19, Vite, Tailwind CSS v4 / CSS Modules)
- **Target Users & Roles:** `HR`, `ADMIN`, `MENTOR` (Chế độ xem thu gọn)
- **Change Level:** **L3** (Mở rộng schema bảng `departments`, thêm Aggregate REST API, xây dựng trang điều phối Bento UI mới)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Xóa bỏ giao diện bảng danh mục tĩnh, biến Phòng ban thành Trung tâm điều phối:** Thay vì chỉ là Dropdown `<select>` đơn điệu, HR có một Dashboard Bento Grid trực quan thể hiện bức tranh nhân sự thực tập: Chỉ tiêu kế hoạch, mức độ lấp đầy, đội ngũ Mentor phụ trách.
2. **Cân bằng tải kèm cặp, chống kiệt sức cho Mentor:** Hiển thị trực quan chỉ số `activeInternCount` ngay trong Modal phân công Mentor. Tự động nhóm Mentor theo cùng phòng ban với Thực tập sinh và đưa ra gợi ý thông minh (Smart Triage), tránh tình trạng phân công lệch chuyên môn hoặc dồn quá nhiều TTS vào 1 Mentor.
3. **Bảo toàn toàn vẹn dữ liệu kiến trúc:** Nghiệp vụ "Luân chuyển phòng ban" cho Thực tập sinh được thiết kế chuẩn xác là **Luồng Đổi Chương Trình Đào Tạo (Reassign Program)**, đảm bảo phòng ban của TTS luôn được kế thừa tự nhiên từ Chương trình thực tập tiếp nhận theo đúng chuẩn của `TM-15/18`, không tạo lỗ hổng dữ liệu lệch chéo.
4. **Không phát sinh rủi ro Concurrency / Deadlock:** Chỉ tiêu phòng ban (`plannedCapacityQuota`) được định nghĩa là **Chỉ tiêu kế hoạch tham khảo (Soft Quota)**, không tạo tầng khóa bi quan (Pessimistic Lock) phức tạp trên nhiều dòng Program, bảo vệ tuyệt đối hiệu năng hệ thống.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)

1. **Backend Database & Model (`intern-and-program-service`):**
   - Mở rộng bảng `departments`: Bổ sung cột `planned_capacity_quota INT DEFAULT 10`, `description TEXT`, `lead_mentor_name VARCHAR(150)`.
   - Xây dựng DTO và Endpoint tổng hợp dữ liệu thời gian thực: `GET /api/departments/capacity-overview` (trả về danh sách phòng ban kèm `activeInternCount`, `activeMentorCount`, `plannedCapacityQuota`, `utilizationRate`, `qualityScoreAvg`).
   - Xây dựng Endpoint cập nhật chỉ tiêu kế hoạch phòng ban: `PUT /api/departments/{id}/quota`.
   - Tối ưu Endpoint lấy danh sách Mentor theo phòng ban: `GET /api/interns/mentors?departmentId={deptId}` trả về kèm `activeInternCount`.

2. **Frontend UI/UX (`InternHub-Frontend`):**
   - Tạo trang mới: [`HrDepartmentHubPage.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/departments/HrDepartmentHubPage.tsx) gắn vào menu Sidebar của HR/Admin.
   - Xây dựng Component **DepartmentBentoCard**:
     - Visual Progress Bar thể hiện tỷ lệ lấp đầy: `< 70%` (Xanh lục), `70% - 90%` (Vàng), `> 90%` (Cam/Tím).
     - Avatar Stack danh sách Mentor của phòng ban kèm số lượng TTS đang kèm.
     - Badge chất lượng đào tạo (Điểm trung bình Weekly Assessment của phòng ban).
   - Nâng cấp [`AssignMentorModal.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/AssignMentorModal.tsx):
     - Phân tab: Tab 1 "Mentor cùng phòng ban" (Khuyên chọn) và Tab 2 "Toàn bộ Mentor".
     - Badge tải màu theo chuẩn `TM-16`: `< 3` (🟢 Rảnh), `3 - 5` (🟡 Tiêu chuẩn), `> 5` (🟠 Cảnh báo tải cao, không hard-block).
     - Nút "Gợi ý Mentor tối ưu": Tự động highlight Mentor cùng phòng ban có `activeInternCount` thấp nhất.

3. **Nghiệp vụ Luân chuyển Chương trình / Phòng ban (Program Reassignment):**
   - Tái sử dụng luồng đổi chương trình: HR chọn Phòng ban đích -> Hệ thống hiển thị các Chương trình đang mở (`OPEN`) của phòng ban đó -> Gán sang Chương trình mới.
   - Thu hồi Mentor cũ (`status = REVOKED`) và đặt cờ `needsMentorAssignment = true`.
   - Bắn thông báo chính xác: Chỉ thông báo cho **Mentor cũ**, **Thực tập sinh**, và **HR Team**. Không gửi thông báo cho Mentor mới (vì chưa tồn tại tại thời điểm này).

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn ngừa suy diễn sai*)

- **Tuyệt đối KHÔNG cập nhật trực tiếp `departmentId` trên `intern_profiles`** mà không thông qua Program. Phòng ban của TTS luôn là giá trị dẫn xuất `intern.program.department`.
- **Tuyệt đối KHÔNG áp dụng Pessimistic Lock ở cấp Department** khi duyệt TTS. Quản lý chỗ cứng (Hard Seat Capacity) vẫn nằm trọn vẹn ở cấp Program (`program.maxInterns`).
- **Không tự động gán Mentor ngẫu nhiên khi luân chuyển**: Luồng gán Mentor mới luôn do con người (HR) quyết định sau khi luân chuyển thành công.

---

## 4. Potential Logic Loopholes & Mitigations (Tối thiểu 6 Edge Cases Cốt Lõi)

### 4.1. Case 1: Lệch dữ liệu chéo giữa Thực tập sinh và Phòng ban
- **Vấn đề:** Nếu cho phép API cập nhật trực tiếp `departmentId` của Intern sang phòng QA trong khi `programId` vẫn là Chương trình Backend (thuộc phòng Software), hồ sơ TTS sẽ rơi vào trạng thái "râu ông nọ cắm cằm bà kia".
- **Giải pháp:** API từ chối việc sửa lẻ `departmentId`. Luồng chuyển phòng ban bắt buộc phải gửi `targetProgramId`. `departmentId` hiển thị của Intern luôn là `intern.getProgram().getDepartment().getId()`.

### 4.2. Case 2: Race Condition & Deadlock khi duyệt hồ sơ đồng thời
- **Vấn đề:** 2 HR cùng duyệt 2 ứng viên vào 2 chương trình khác nhau nhưng cùng thuộc 1 phòng ban. Nếu cố enforce chỉ tiêu phòng ban bằng Database Lock trên Department, việc tranh chấp lock chéo giữa Program và Department sẽ gây treo hệ thống (Deadlock).
- **Giải pháp:** `plannedCapacityQuota` cấp Phòng ban là **Soft Target Metric** phục vụ giám sát và cảnh báo trực quan. Hệ thống giữ nguyên cơ chế khóa `PESSIMISTIC_WRITE` trên 1 dòng đơn lẻ `internship_programs` (`TM-15/18`). Không khóa bảng `departments` khi duyệt hồ sơ.

### 4.3. Case 3: Bắn Event thông báo sai đối tượng
- **Vấn đề:** Khi luân chuyển TTS, hệ thống bắn thông báo tới "Mentor mới" trong khi TTS chưa được gán ai phụ trách.
- **Giải pháp:** Tách rõ 2 pha Event:
  - Pha 1 (Luân chuyển): Bắn event `INTERN_PROGRAM_REASSIGNED` cho Mentor cũ và TTS.
  - Pha 2 (Gán Mentor mới): Bắn event `MENTOR_ASSIGNED` cho Mentor mới theo chuẩn `TM-16`.

### 4.4. Case 4: Nhầm lẫn thuật ngữ tải giữa Program và Mentor
- **Vấn đề:** Dùng chữ `current_interns` cho Mentor gây xung đột với cột lưu trữ lịch sử của bảng `internship_programs`.
- **Giải pháp:** Toàn bộ code và API contract sử dụng thống nhất thuộc tính **`activeInternCount`** cho Mentor, được đếm động qua truy vấn `COUNT(ima.id) WHERE ima.mentorId = :mId AND ima.status = 'ACTIVE'`.

### 4.5. Case 5: Xung đột ngữ nghĩa ngưỡng tải Mentor (Cap vs Soft Warning)
- **Vấn đề:** Dùng từ "Cap" khiến người dùng hiểu lầm là hệ thống sẽ chặn không cho gán khi đạt 3 hoặc 5 TTS.
- **Giải pháp:** Giữ đúng chuẩn của `TM-16`: Ngưỡng cảnh báo mềm là `> 5 TTS`. Khi Mentor có `> 5 TTS`, giao diện hiển thị hộp cảnh báo màu cam: *"Mentor này đang hướng dẫn [X] thực tập sinh, vượt ngưỡng khuyến nghị (5). Bạn vẫn có thể tiếp tục phân công nếu cần thiết."* Nút Xác nhận vẫn cho phép bấm (Human Override).

### 4.6. Case 6: Phòng ban mới chưa có dữ liệu đánh giá tuần
- **Vấn đề:** Phòng ban mới thành lập chưa có đánh giá tuần nào sẽ gây lỗi chia cho 0 (`Divide by Zero`) khi tính `qualityScoreAvg`.
- **Giải pháp:** Dùng SQL `COALESCE(AVG(score), 0.0)` và trên giao diện nếu số lượt đánh giá = 0 thì hiển thị badge `"Chưa có dữ liệu đánh giá"` thay vì điểm 0.0 gây hiểu lầm.

---

## 5. Architectural Contract & Data Model

### 5.1. Database Migration: Mở rộng bảng `departments`

```sql
-- Thêm các cột phục vụ điều phối và chỉ tiêu kế hoạch
ALTER TABLE departments
ADD COLUMN IF NOT EXISTS planned_capacity_quota INT DEFAULT 10,
ADD COLUMN IF NOT EXISTS description TEXT,
ADD COLUMN IF NOT EXISTS lead_mentor_id BIGINT,
ADD COLUMN IF NOT EXISTS lead_mentor_name VARCHAR(150);

COMMENT ON COLUMN departments.planned_capacity_quota IS 'Chỉ tiêu kế hoạch thực tập sinh dự kiến (Soft metric, không chặn cứng)';
```

### 5.2. API Data Contracts

#### A. API Lấy Bức Tranh Điều Phối Phòng Ban
* **Endpoint:** `GET /api/departments/capacity-overview`
* **Quyền hạn:** `HR`, `ADMIN`
* **Response:**
```json
{
  "code": 200,
  "message": "Lấy dữ liệu điều phối phòng ban thành công",
  "data": {
    "companySummary": {
      "totalDepartments": 5,
      "totalActiveInterns": 42,
      "totalPlannedQuota": 55,
      "totalActiveMentors": 18,
      "overallUtilizationRate": 76.4
    },
    "departments": [
      {
        "departmentId": 1,
        "departmentCode": "DEPT-SE",
        "departmentName": "Khối Kỹ thuật Phần mềm",
        "description": "Nghiên cứu và phát triển phần mềm lõi",
        "leadMentorName": "Trần Văn Bách",
        "plannedCapacityQuota": 20,
        "activeInternCount": 16,
        "activeMentorCount": 6,
        "utilizationRate": 80.0,
        "qualityScoreAvg": 4.4,
        "activeProgramsCount": 3,
        "mentors": [
          {
            "mentorId": 101,
            "mentorName": "Nguyễn Hoàng Long",
            "email": "longnh@company.com",
            "avatarUrl": null,
            "activeInternCount": 2,
            "workloadStatus": "AVAILABLE"
          },
          {
            "mentorId": 102,
            "mentorName": "Vũ Đình Trọng",
            "email": "trongvd@company.com",
            "avatarUrl": null,
            "activeInternCount": 5,
            "workloadStatus": "STANDARD"
          }
        ]
      }
    ]
  }
}
```

#### B. API Cập Nhật Chỉ Tiêu Kế Hoạch Phòng Ban
* **Endpoint:** `PUT /api/departments/{departmentId}/quota`
* **Quyền hạn:** `HR`, `ADMIN`
* **Request Body:**
```json
{
  "plannedCapacityQuota": 25
}
```

#### C. API Lấy Danh Sách Mentor Cho Modal Phân Công (Đã bổ sung Filter & Workload)
* **Endpoint:** `GET /api/mentors/assignment-candidates?internId={internId}`
* **Response:**
```json
{
  "code": 200,
  "data": {
    "internDepartmentId": 1,
    "internDepartmentName": "Khối Kỹ thuật Phần mềm",
    "sameDepartmentMentors": [
      {
        "mentorId": 101,
        "mentorName": "Nguyễn Hoàng Long",
        "activeInternCount": 2,
        "isRecommended": true,
        "workloadLevel": "LIGHT"
      }
    ],
    "otherDepartmentMentors": [
      {
        "mentorId": 201,
        "mentorName": "Phạm Quốc Tuấn",
        "departmentName": "Đảm bảo chất lượng QA",
        "activeInternCount": 1,
        "isRecommended": false,
        "workloadLevel": "LIGHT"
      }
    ]
  }
}
```

---

## 6. Frontend UI/UX Wireframe & Layout

### 6.1. Department Bento Hub (`/hr/departments`)

```
+----------------------------------------------------------------------------------------------------+
| 🏢 TRUNG TÂM ĐIỀU PHỐI PHÒNG BAN & CHỈ TIÊU ĐÀO TẠO (DEPARTMENT HUB)                              |
| [Tổng TTS: 42/55 (76%)]  [Tổng Mentor: 18 người]  [Điểm chất lượng toàn cty: ⭐ 4.3/5.0]           |
+----------------------------------------------------------------------------------------------------+
| [ 🔍 Tìm phòng ban... ]                                                 [ + Thiết lập chỉ tiêu ]   |
+----------------------------------------------------------------------------------------------------+
| +-----------------------------------------+  +-----------------------------------------+          |
| | 💻 KHỐI KỸ THUẬT PHẦN MỀM               |  | 🧪 KHỐI ĐẢM BẢO CHẤT LƯỢNG (QA)        |          |
| | Mã: DEPT-SE • Trưởng bộ phận: TV Bách   |  | Mã: DEPT-QA • Trưởng bộ phận: ĐM Hùng   |          |
| |                                         |  |                                         |          |
| | NĂNG LỰC TIẾP NHẬN:                     |  | NĂNG LỰC TIẾP NHẬN:                     |          |
| | [██████████████████░░░░] 16/20 (80%)    |  | [██████████████████████] 10/10 (100%)   |          |
| | Trạng thái: 🟡 Gần đầy chỉ tiêu         |  | Trạng thái: 🔴 Đã đạt trần kế hoạch     |          |
| |                                         |  |                                         |          |
| | ĐỘI NGŨ MENTOR (6 người):               |  | ĐỘI NGŨ MENTOR (3 người):               |          |
| | (A) (B) (C) (D) +2 khác                 |  | (E) (F) (G)                             |          |
| | Tải TB: 2.6 TTS / Mentor                |  | Tải TB: 3.3 TTS / Mentor                |          |
| |                                         |  |                                         |          |
| | CHẤT LƯỢNG ĐÀO TẠO: ⭐ 4.4 / 5.0        |  | CHẤT LƯỢNG ĐÀO TẠO: ⭐ 4.6 / 5.0        |          |
| |                                         |  |                                         |          |
| | [ Xem 16 Thực tập sinh ]  [ Quản lý ]   |  | [ Xem 10 Thực tập sinh ]  [ Quản lý ]   |          |
| +-----------------------------------------+  +-----------------------------------------+          |
+----------------------------------------------------------------------------------------------------+
```

### 6.2. Modal Gán Mentor Thông Minh ([`AssignMentorModal.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/AssignMentorModal.tsx))

```
+----------------------------------------------------------------------------------+
| PHÂN CÔNG MENTOR CHO THỰC TẬP SINH: NGUYỄN VĂN AN (INT-202609-0007)              |
| Chương trình: Kỹ sư Phần mềm Java • Phòng ban: Khối Kỹ thuật Phần mềm            |
+----------------------------------------------------------------------------------+
|  [Tab: Cùng phòng ban (Khuyên chọn - 6)]  |  [Tab: Toàn bộ công ty (18)]         |
+----------------------------------------------------------------------------------+
| 💡 Gợi ý hệ thống: Các Mentor dưới đây thuộc cùng chuyên môn và có tải phù hợp:  |
|                                                                                  |
| (•) Nguyễn Hoàng Long                                                            |
|     Khối Kỹ thuật Phần mềm • Email: longnh@company.com                           |
|     Hiện đang kèm: 1 TTS  [ 🟢 Rảnh - Khuyên chọn ]                             |
|                                                                                  |
| ( ) Vũ Đình Trọng                                                                |
|     Khối Kỹ thuật Phần mềm • Email: trongvd@company.com                          |
|     Hiện đang kèm: 4 TTS  [ 🟡 Tiêu chuẩn ]                                      |
|                                                                                  |
| ( ) Lê Minh Tuấn                                                                 |
|     Khối Kỹ thuật Phần mềm • Email: tuanlm@company.com                           |
|     Hiện đang kèm: 6 TTS  [ 🟠 Tải cao (>5) - Cảnh báo ]                         |
|     ⚠️ Mentor này đã kèm 6 bạn, có thể ảnh hưởng chất lượng hướng dẫn.          |
+----------------------------------------------------------------------------------+
| [ Hủy bỏ ]                                                  [ Xác nhận phân công ]|
+----------------------------------------------------------------------------------+
```

---

## 7. Phân Rã Giai Đoạn Triển Khai (Phased Rollout)

1. **Giai đoạn 1 (Backend Aggregation):** 
   - Migration bổ sung cột `planned_capacity_quota` vào `departments`.
   - Viết Repository Query tổng hợp số lượng TTS và Mentor theo phòng ban.
   - Viết Controller & Unit Tests cho `GET /api/departments/capacity-overview`.
2. **Giai đoạn 2 (Frontend Bento Grid):**
   - Thiết kế giao diện `HrDepartmentHubPage.tsx` và module CSS phong cách Bento Card.
   - Tích hợp gọi API và hiển thị các thanh đo % lấp đầy, số liệu Mentor.
3. **Giai đoạn 3 (Smart Mentor Assignment Modal):**
   - Nâng cấp `AssignMentorModal.tsx` phân tách 2 Tab phòng ban và hiển thị màu tải theo `TM-16`.
4. **Giai đoạn 4 (E2E & Tích hợp Luân chuyển):**
   - Kiểm thử liên kết luồng chuyển đổi chương trình (`ProgramReassignmentModal`).
   - Kiểm thử thông báo WebSocket khi thay đổi trạng thái.

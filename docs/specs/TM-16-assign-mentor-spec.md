# Specification: Phân Công & Quản Lý Người Hướng Dẫn Thực Tập Sinh (TM-16)

> **Tài liệu Đặc Tả Kỹ Thuật Toàn Diện (Comprehensive Feature Specification)**  
> **Dự án:** [InternHub](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub) (Backend Microservices) & [InternHub-Frontend](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend) (React + Vite)  
> **Mã Jira Ticket:** [TM-16](https://robluccibn9935.atlassian.net/browse/TM-16)  
> **Tiêu đề Jira:** *Phân công thực tập sinh cho mentor*  
> **Nhánh Git:** `feature/TM-16/assign-mentor-to-intern` (Backend & Frontend)  
> **Mức độ thay đổi (Change Level):** **L3** (Mở rộng Domain Model `InternProfile` & Bảng `intern_mentor_assignments`, field `replaceReason`, chuẩn hóa departmentId/departmentName cho Mentor, tách bạch 2 cờ điều phối, xóa bỏ blink animation, API phân công/thu hồi Mentor, phân tầng thông báo Email 3 chiều, Cascade khi tài khoản Mentor bị vô hiệu hóa, điều kiện kép tự động chuyển `APPROVED -> INTERNING`, giao diện HR với modal phân công/đổi mentor động).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/).

---

## Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History)

| Phiên bản | Ngày | Người thực hiện | Mã Task Jira | Nội dung thay đổi | Lý do kỹ thuật / Nghiệp vụ (Rationale) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **v1.0.0** | 25/09/2026 | AI Agent & Lập trình viên | `TM-16` | Khởi tạo tài liệu đặc tả kỹ thuật tính năng Phân công thực tập sinh cho Mentor | Cung cấp luồng nghiệp vụ gán Mentor cho TTS sau khi được duyệt hồ sơ / ký hợp đồng. |
| **v1.1.0** | 25/09/2026 | AI Agent & Lập trình viên | `TM-16` | Cập nhật quy tắc chuyển trạng thái điều kiện kép (`mentorId != null && program.status == ONGOING`), cơ chế mở rộng Cron Job `ProgramLifecycleJob`, gợi ý phòng ban và kiểm soát tải công việc tối đa 5 TTS/mentor | Đạt sự đồng thuận qua Cổng làm rõ quyết định (Decision Gate). Tận dụng hạ tầng Cron Job có sẵn, hỗ trợ cảnh báo trực quan cho HR. |
| **v1.2.0** | 25/09/2026 | AI Agent & Lập trình viên | `TM-16` | Chuẩn hóa Enum `MentorAssignmentStatus` (`ACTIVE, REPLACED, REVOKED`), hợp nhất 7 trạng thái `InternStatus`, bổ sung cơ chế gỡ Mentor (`DELETE /mentor`), Cascade khi tài khoản Mentor bị khóa, tách biệt 2 cờ điều phối và bổ sung email thông báo 3 chiều | Khắc phục các blind spots về tính toàn vẹn thông báo, loại bỏ mâu thuẫn trạng thái với TM-15/18 và đảm bảo an toàn nghiệp vụ khi thay đổi nhân sự hướng dẫn. |
| **v1.3.0** | 25/09/2026 | AI Agent & Lập trình viên | `TM-16` | Bổ sung trường `replaceReason` khi thay thế mentor, chuẩn hóa `departmentId`/`departmentName`/`departmentCode` cho Mentor Response, loại bỏ blink animation tuân thủ WCAG | Khắc phục thiếu sót dữ liệu audit revocation_reason và lỗi regression department free-text, đảm bảo trải nghiệm tiếp cận người dùng. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Mục tiêu cốt lõi:**  
  Cung cấp cho Ban Nhân sự (HR) và Ban Quản trị (Admin) chức năng phân công, thay đổi hoặc thu hồi Mentor phụ trách Thực tập sinh (Intern). Thiết lập quy tắc thứ tự phụ thuộc chặt chẽ giữa **Chương trình thực tập** (Khung đào tạo) và **Mentor** (Người hướng dẫn), kiểm soát định mức tải công việc của Mentor, tự động chuyển đổi trạng thái vòng đời TTS sang `INTERNING` theo **Điều kiện kép**, đồng thời kích hoạt luồng thông báo Email tự động cho cả Intern, Mentor mới và Mentor cũ.
- **Phân hệ chịu trách nhiệm:**
  1. **Backend (`identity-and-access-service` - Port 8081):**
     - Cung cấp API lấy danh sách tài khoản `MENTOR` đang hoạt động (`status = ACTIVE`) kèm thông tin phòng ban chuẩn hóa (`departmentId`, `departmentName`, `departmentCode`).
     - Kích hoạt cơ chế thông báo/callback nội bộ khi một tài khoản Mentor bị vô hiệu hóa (`status = INACTIVE`) để cascade thu hồi phân công ở các TTS liên quan.
  2. **Backend (`intern-and-program-service` - Port 8082):**
     - Module: `org.example.internservice.intern`
     - Domain Entities:
       + Bổ sung trường `mentorId`, `mentorName`, `mentorEmail`, `needsMentorReassignment`, `mentorReassignmentReason` vào `InternProfile`.
       + Đổi tên/tách biệt cờ `needsReassignment` thành `needsProgramReassignment` (và alias tương thích ngược).
       + Tạo mới Entity `InternMentorAssignment` (kế thừa `BaseEntity`, status gồm `ACTIVE`, `REPLACED`, `REVOKED`) lưu lịch sử phân công.
     - REST API:
       + `POST /api/interns/{id}/assign-mentor`: Phân công hoặc đổi Mentor cho thực tập sinh (nhận `mentorId`, `notes`, và bắt buộc `replaceReason` nếu đổi mentor).
       + `DELETE /api/interns/{id}/mentor`: Thu hồi (gỡ trắng) Mentor phụ trách khi chưa có người thay thế (bắt buộc `reason`).
       + `GET /api/interns/{id}/mentor-history`: Lấy lịch sử các lần phân công Mentor của thực tập sinh.
       + `GET /api/interns/mentors`: Lấy danh sách Mentor khả dụng kèm khối lượng công việc hiện tại (`activeInternCount`) và phòng ban chuẩn hóa.
     - Tự động hóa vòng đời & Cascade:
       + **Điều kiện kép**: Chuyển `APPROVED` ➔ `INTERNING` khi: `(mentorId != null) AND (program.status == 'ONGOING')`.
       + **Cascade khi Mentor bị khóa**: Thu hồi gán mentor, đặt `mentorId = null`, bật cờ `needsMentorReassignment = true`.
       + **Thứ tự ưu tiên**: Chặn phân công Mentor nếu `needsProgramReassignment == true` (phải ổn định Chương trình trước).
  3. **Backend (`reporting-and-integration-service` - Port 8083):**
     - Lắng nghe event `InternMentorAssignedEvent` / `InternMentorRevokedEvent`.
     - Xây dựng template và gửi Email thông báo tự động cho:
       + **Intern**: Biết thông tin Mentor hướng dẫn (tên, email, phòng ban).
       + **Mentor mới**: Biết thông tin TTS được giao phụ trách và chương trình thực tập.
       + **Mentor cũ**: Thông báo kết thúc phụ trách / bàn giao TTS để không tiếp tục chấm điểm.
  4. **Frontend (`InternHub-Frontend`):**
     - Bảng TTS (`HrInternTable.tsx`): Cột "Mentor phụ trách", badge tĩnh **"Chưa có Mentor"** (cam) hoặc **"Cần đổi Mentor"** (đỏ, không nhấp nháy).
     - Modal Phân công: `AssignMentorModal.tsx` tự động chuyển đổi giữa "Phân Công" và "Thay Đổi Người Hướng Dẫn" (kèm field `replaceReason` bắt buộc khi thay đổi), so khớp ID phòng ban chính xác `mentor.departmentId === intern.program?.departmentId` để đưa ra cảnh báo.
     - Modal Chi tiết: `DetailInternModal.tsx` tích hợp khối Mentor, nút "Đổi Mentor", nút "Gỡ Mentor" và tab xem Lịch sử phân công.

---

## 2. Business Rules & State Transitions (Quy Tắc Nghiệp Vụ & Chuyển Đổi Trạng Thái)

### 2.1. Thứ tự Ưu tiên: Chương trình Thực tập (Program) ➔ Mentor
- Chương trình thực tập là khung năng lực và thời gian tổ chức; Mentor là người trực tiếp hướng dẫn trong khung đó.
- **Ràng buộc cứng (Hard Constraint)**: Nếu thực tập sinh đang có cờ `needsProgramReassignment == true` (chương trình cũ bị hủy hoặc chưa có chương trình mới hợp lệ), API `assign-mentor` **BẮT BUỘC BỊ TỪ CHỐI** với mã lỗi `400 BAD_REQUEST`:
  > *"Không thể phân công Mentor cho thực tập sinh đang chờ điều phối lại chương trình thực tập. Vui lòng xếp chương trình mới trước."*

### 2.2. Điều kiện kép chuyển đổi trạng thái `APPROVED` ➔ `INTERNING`
$$\text{Transition to INTERNING} \iff (\text{mentorId} \neq \text{null}) \land (\text{program.status} == \text{'ONGOING'})$$

1. **Khi gán Mentor thành công:**
   - Nếu `program.status == ONGOING` ➔ Tự động chuyển `intern.status = INTERNING`.
   - Nếu `program.status == PLANNING` hoặc `OPEN` ➔ Giữ `intern.status = APPROVED`, lưu thông tin Mentor sẵn sàng.
2. **Khi Cron Job `ProgramLifecycleJob` chạy:**
   - Khi một chương trình chuyển sang `ONGOING`, tự động quét toàn bộ TTS thuộc chương trình đó đang ở `APPROVED` mà đã có `mentorId != null` để chuyển hàng loạt sang `INTERNING`.
3. **Khi TTS chưa có Mentor dù chương trình đã `ONGOING`:**
   - TTS giữ `APPROVED`, hiển thị badge cam nổi bật trên UI: **"Đã duyệt — Chưa có Mentor"**.

### 2.3. Quy tắc Thay thế (`REPLACED`) & Thu hồi Gỡ trắng Mentor (`REVOKED`)
- **Trường hợp 1: Phân công lần đầu (`intern.mentorId == null`)**:
  - `replaceReason` không bắt buộc.
  - Tạo bản ghi mới trong `intern_mentor_assignments` với `status = ACTIVE`.
- **Trường hợp 2: Thay đổi Mentor (`intern.mentorId != null`)**:
  - `replaceReason` là **bắt buộc (Required)**. Nếu để trống hoặc chỉ có khoảng trắng, trả về `400 BAD_REQUEST`: *"Vui lòng nhập lý do thay đổi người hướng dẫn"*.
  - Bản ghi phân công cũ được cập nhật: `status = REPLACED`, `revokedAt = now()`, `revocationReason = replaceReason`.
  - Tạo bản ghi mới cho Mentor mới với `status = ACTIVE`.
- **Trường hợp 3: Thu hồi (gỡ trắng) không người thay thế (`DELETE /api/interns/{id}/mentor`)**:
  - Bắt buộc cung cấp `reason` trong body.
  - Bản ghi hiện tại trong `intern_mentor_assignments` chuyển `status = REVOKED`, `revokedAt = now()`, `revocationReason = reason`.
  - Trên `intern_profiles`: Đặt `mentorId = null, mentorName = null, mentorEmail = null`.
  - Bật cờ cảnh báo: `needsMentorReassignment = true`, `mentorReassignmentReason = reason`.
  - Giữ nguyên trạng thái `status` chính của TTS (`INTERNING` nếu chương trình đang chạy). UI hiển thị badge cảnh báo đỏ tĩnh: **"Cần đổi Mentor"**.

### 2.4. Cascade khi Tài Khoản Mentor bị Khóa / Vô Hiệu Hóa
- Khi Admin vô hiệu hóa tài khoản User có role `MENTOR` bên Identity Service:
  - Hệ thống tự động thu hồi phân công ở tất cả TTS mà Mentor đó đang phụ trách (`status = REVOKED`, `revocationReason = "Tài khoản Mentor đã bị khóa/nghỉ việc"`).
  - Tự động bật cờ `needsMentorReassignment = true` trên các hồ sơ TTS tương ứng để HR lập tức nắm được danh sách "mồ côi Mentor" cần phân công lại.

### 2.5. Cơ chế Thông báo Email 3 Chiều
- **Khi Gán Mới (`ASSIGNED`):**
  - Gửi email cho Intern: Thông báo họ tên, email Mentor hướng dẫn.
  - Gửi email cho Mentor mới: Thông báo tiếp nhận TTS, lớp/chương trình đào tạo.
- **Khi Thay Đổi (`REPLACED`):**
  - Gửi email cho Intern: Thông báo thay đổi Mentor phụ trách.
  - Gửi email cho Mentor mới: Thông báo tiếp nhận hướng dẫn.
  - Gửi email cho Mentor cũ: Thông báo bàn giao/kết thúc phụ trách TTS đó kèm lý do.
- **Khi Thu Hồi Trắng (`REVOKED`):**
  - Gửi email cho Intern: Thông báo Mentor hiện tại kết thúc phụ trách, ban nhân sự đang thu xếp người hướng dẫn mới.
  - Gửi email cho Mentor cũ: Thông báo đã gỡ phụ trách TTS.

---

## 3. Database & Entity Schema (Mô Hình Dữ Liệu)

### 3.1. Cập nhật bảng `intern_profiles`
```sql
ALTER TABLE intern_profiles
    ADD COLUMN mentor_id BIGINT NULL AFTER program_id,
    ADD COLUMN mentor_name VARCHAR(100) NULL AFTER mentor_id,
    ADD COLUMN mentor_email VARCHAR(100) NULL AFTER mentor_name,
    ADD COLUMN needs_mentor_reassignment BOOLEAN NOT NULL DEFAULT FALSE AFTER needs_reassignment,
    ADD COLUMN mentor_reassignment_reason VARCHAR(255) NULL AFTER needs_mentor_reassignment;

CREATE INDEX idx_intern_profiles_mentor_id ON intern_profiles (mentor_id);
CREATE INDEX idx_intern_profiles_needs_mentor ON intern_profiles (needs_mentor_reassignment);
```

### 3.2. Bảng mới `intern_mentor_assignments`
```sql
CREATE TABLE intern_mentor_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(100) NOT NULL,
    mentor_email VARCHAR(100) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REPLACED, REVOKED
    notes TEXT NULL,
    revoked_at DATETIME NULL,
    revocation_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_intern FOREIGN KEY (intern_id) REFERENCES intern_profiles (id) ON DELETE CASCADE,
    INDEX idx_assignment_intern_id (intern_id),
    INDEX idx_assignment_mentor_id (mentor_id),
    INDEX idx_assignment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. REST API Contract (Hợp Đồng Giao Diện API)

### 4.1. Lấy danh sách Mentor có thể phân công
- **Method & Endpoint:** `GET /api/interns/mentors`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Lấy danh sách Mentor thành công",
  "data": [
    {
      "id": 3,
      "fullName": "Lê Hoàng Nam",
      "email": "mentor@internhub.com",
      "phone": "0988776655",
      "departmentId": 1,
      "departmentName": "Phòng Phát Triển Phần Mềm",
      "departmentCode": "DEV",
      "status": "ACTIVE",
      "activeInternCount": 2
    }
  ]
}
```

### 4.2. Phân công hoặc Đổi Mentor cho Thực tập sinh
- **Method & Endpoint:** `POST /api/interns/{id}/assign-mentor`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Request Body:**
```json
{
  "mentorId": 3,
  "notes": "Phân công hướng dẫn chuyên môn Spring Boot & Cloud Architecture",
  "replaceReason": "Mentor cũ chuyển cơ sở công tác" // Bắt buộc khi TTS ĐÃ CÓ mentor
}
```
- **Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Phân công Mentor thành công",
  "data": {
    "id": 1,
    "internCode": "INT-2026-0001",
    "fullName": "Phạm Đức Minh",
    "status": "INTERNING",
    "mentorId": 3,
    "mentorName": "Lê Hoàng Nam",
    "mentorEmail": "mentor@internhub.com",
    "needsMentorReassignment": false,
    "programId": 2,
    "programName": "Chương Trình Kỹ Sư Phần Mềm Java 2026"
  }
}
```

### 4.3. Thu hồi (Gỡ trắng) Mentor phụ trách
- **Method & Endpoint:** `DELETE /api/interns/{id}/mentor`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Request Body:**
```json
{
  "reason": "Mentor chuyển dự án khẩn cấp, tạm thu hồi chờ sắp xếp người thay thế"
}
```
- **Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Thu hồi phân công Mentor thành công",
  "data": {
    "id": 1,
    "internCode": "INT-2026-0001",
    "fullName": "Phạm Đức Minh",
    "status": "INTERNING",
    "mentorId": null,
    "mentorName": null,
    "mentorEmail": null,
    "needsMentorReassignment": true,
    "mentorReassignmentReason": "Mentor chuyển dự án khẩn cấp, tạm thu hồi chờ sắp xếp người thay thế"
  }
}
```

### 4.4. Xem lịch sử phân công Mentor
- **Method & Endpoint:** `GET /api/interns/{id}/mentor-history`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`
- **Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Lấy lịch sử phân công Mentor thành công",
  "data": [
    {
      "id": 12,
      "internId": 1,
      "mentorId": 3,
      "mentorName": "Lê Hoàng Nam",
      "mentorEmail": "mentor@internhub.com",
      "assignedBy": "hr",
      "assignedAt": "2026-09-25T14:15:00",
      "status": "ACTIVE",
      "notes": "Phân công hướng dẫn mảng Backend"
    },
    {
      "id": 5,
      "internId": 1,
      "mentorId": 8,
      "mentorName": "Nguyễn Văn Tuấn",
      "mentorEmail": "tuan.nv@internhub.com",
      "assignedBy": "hr",
      "assignedAt": "2026-09-01T08:00:00",
      "status": "REPLACED",
      "revokedAt": "2026-09-25T14:15:00",
      "revocationReason": "Mentor cũ chuyển cơ sở công tác",
      "notes": "Hướng dẫn giai đoạn thử việc"
    }
  ]
}
```

---

## 5. UI/UX Frontend Design Specifications (Thiết Kế Giao Diện)

1. **Hiển thị trên Bảng TTS (`HrInternTable.tsx`):**
   - Cột **"Mentor phụ trách"**:
     - Đã có Mentor: Avatar nhỏ + Tên Mentor (`mentorName`).
     - Có cờ `needsMentorReassignment`: Badge đỏ tĩnh (không nhấp nháy, dot + label) **"Cần đổi Mentor"**.
     - Đang `APPROVED` & Program `ONGOING` mà chưa có Mentor: Badge cam tĩnh **"Chưa có Mentor"**.
   - Hành động nhanh: Nút "Phân công" (icon `UserPlus`), "Đổi Mentor" (icon `UserCheck`) hoặc menu hành động có tùy chọn "Thu hồi Mentor".
2. **Modal Phân Công Mentor (`AssignMentorModal.tsx`):**
   - Tiêu đề động:
     - Chưa có mentor: *"Phân Công Người Hướng Dẫn (Mentor)"*.
     - Đã có mentor: *"Thay Đổi Người Hướng Dẫn (Mentor)"* (hiển thị thông tin mentor sắp bị thay thế).
   - Thẻ tóm tắt thông tin TTS: Mã TTS, Họ tên, Vị trí, Chương trình, Trạng thái chương trình.
   - Disable nút submit nếu TTS đang có cờ `needsProgramReassignment = true` kèm thông báo chặn màu đỏ.
   - Dropdown chọn Mentor:
     - Hiển thị tên, email, phòng ban chuẩn hóa và số lượng TTS đang kèm cặp.
     - Cảnh báo vàng nếu Mentor đang có `>= 5 TTS` (vượt định mức khuyến nghị).
     - Nhắc nhở nếu `mentor.departmentId !== intern.program?.departmentId`: *"Lưu ý: Mentor thuộc [tên phòng ban mentor], khác với phòng ban [tên phòng ban chương trình]"*.
   - Ô nhập ghi chú phân công (`notes`).
   - Ô nhập lý do thay đổi (`replaceReason`): Chỉ hiển thị và bắt buộc khi đang đổi mentor (`intern.mentorId != null`).
3. **Modal Chi Tiết Thực Tập Sinh (`DetailInternModal.tsx`):**
   - Card hiển thị Mentor đương nhiệm.
   - Nút hành động: `Đổi Mentor` và `Thu Hồi Mentor` (mở confirm dialog nhập lý do thu hồi).
   - Khối **Lịch Sử Phân Công**: Timeline hiển thị rõ từng giai đoạn, ai phân công, thời gian bắt đầu, thời gian kết thúc và lý do thay đổi/thu hồi.

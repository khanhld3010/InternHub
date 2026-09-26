# Specification Phụ: Hoàn Thiện TM-16 — Lịch Sử Phân Công & Email Thông Báo 3 Chiều

> **Mã tính năng:** [TM-16](https://robluccibn9935.atlassian.net/browse/TM-16) (Sub-spec: History UI & 3-way Email Notification)  
> **Dự án:** [InternHub](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub) & [InternHub-Frontend](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend)  
> **Mục tiêu:** 
> 1. Xây dựng giao diện Tab xem Lịch sử phân công Mentor trên `DetailInternModal.tsx` gọi API `GET /api/interns/{id}/mentor-history`.
> 2. Xây dựng cơ chế phát Event và gửi Email thông báo tự động (Intern, Mentor mới, Mentor cũ) khi HR phân công, đổi mentor hoặc thu hồi mentor.

---

## 1. Giao Diện Frontend: Tab Lịch Sử Phân Công Mentor (`DetailInternModal.tsx`)

### 1.1. Mục tiêu UX/UI
- Trên [DetailInternModal.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/DetailInternModal.tsx), chuyển đổi cấu trúc hiển thị sang hệ thống Tab trực quan:
  - **Tab 1: Thông tin hồ sơ & Tài liệu** (`profile_docs`): Giữ nguyên thông tin chi tiết ứng viên và danh sách tài liệu hiện có.
  - **Tab 2: Lịch sử Người hướng dẫn** (`mentor_history`): Hiển thị dòng thời gian (Timeline) tất cả các đợt phân công, điều chuyển hoặc thu hồi mentor của thực tập sinh.

### 1.2. Dữ liệu & Tương tác
- Endpoint: `GET /api/interns/{id}/mentor-history` (qua `internService.getMentorHistory(id)`).
- Kiểu dữ liệu `MentorAssignmentResponse`:
  - `id`: Mã bản ghi phân công
  - `mentorId`, `mentorName`, `mentorEmail`: Thông tin người hướng dẫn
  - `assignedBy`, `assignedAt`: Người thực hiện và thời điểm gán
  - `status`: `ACTIVE` (Đang phụ trách), `REPLACED` (Đã thay thế), `REVOKED` (Đã thu hồi)
  - `revokedAt`, `revocationReason`: Thời điểm và lý do thu hồi/thay thế
  - `notes`: Ghi chú phân công từ HR
- **Quy tắc hiển thị Badge trạng thái:**
  - `ACTIVE`: Badge xanh lá (`badge-success`) — *"Đang phụ trách"*
  - `REPLACED`: Badge xanh dương/xám (`badge-info`) — *"Đã bàn giao/thay thế"*
  - `REVOKED`: Badge đỏ/cam (`badge-danger`) — *"Đã thu hồi"*
- Hiển thị rõ:
  - Lý do thay đổi/thu hồi (`revocationReason`) nếu có.
  - Người điều phối (`assignedBy`) và ngày giờ cụ thể (`formatDateTime(assignedAt)`).

---

## 2. Backend: Thông Báo Email 3 Chiều Khi Gán / Đổi / Thu Hồi Mentor

### 2.1. Ma Trận Gửi Email Theo Nghiệp Vụ

| Kịch bản HR thực hiện | Email Thực tập sinh (Intern) | Email Mentor Mới | Email Mentor Cũ |
| :--- | :--- | :--- | :--- |
| **1. Phân công lần đầu** (`intern.mentorId == null`) |  Thông báo đã có Mentor phụ trách (Tên, Email Mentor) |  Thông báo tiếp nhận TTS (Họ tên, Vị trí, Chương trình) | ➖ Không áp dụng |
| **2. Thay đổi Mentor** (`intern.mentorId != null`) |  Thông báo thay đổi Mentor phụ trách |  Thông báo tiếp nhận TTS mới |  Thông báo kết thúc phụ trách & lý do bàn giao |
| **3. Thu hồi Mentor** (`DELETE /mentor`) |  Thông báo tạm thời kết thúc phụ trách, chờ xếp mới | ➖ Không áp dụng |  Thông báo đã thu hồi phân công TTS kèm lý do |

### 2.2. Kiến Trúc Phát Sự Kiện (Event-Driven Integration)

1. **`intern-and-program-service`**:
   - Định nghĩa Event: `InternMentorAssignmentEvent`
     - `eventType`: `ASSIGNED` | `REPLACED` | `REVOKED`
     - `internId`, `internCode`, `internName`, `internEmail`, `programName`
     - `newMentorName`, `newMentorEmail` (nếu có)
     - `oldMentorName`, `oldMentorEmail` (nếu có)
     - `reason`, `notes`, `actorUsername`
   - Listener: `InternMentorAssignmentEventListener` bắt event bất đồng bộ (`@Async`), gọi RestTemplate sang `reporting-and-integration-service`.
2. **`reporting-and-integration-service`**:
   - Controller: `POST /api/integration/emails/mentor-assignment`
   - Service: `MentorAssignmentEmailService` tạo email HTML responsive và gửi tới các bên liên quan:
     - Gửi email cho Intern: Lời chào mừng và thông tin liên hệ của Mentor.
     - Gửi email cho Mentor mới: Hồ sơ TTS, chương trình đào tạo và hướng dẫn công việc.
     - Gửi email cho Mentor cũ: Lý do bàn giao/thu hồi công việc.

# Specification: Duyệt Hoặc Từ Chối Hồ Sơ Thực Tập Sinh (Approve / Reject Intern Application)

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Duyệt hoặc từ chối hồ sơ thực tập sinh (Approve or Reject Intern Application)
- **Jira Ticket:** [TM-11](https://robluccibn9935.atlassian.net/browse/TM-11)
- **Target Subsystems:** `intern-and-program-service` (Backend Microservice), `api-gateway` (Routing & Security Verification), `InternHub-Frontend` (HR Management Portal)
- **Target Users:** HR (Nhân sự), Admin (Quản trị viên)
- **Phân loại thay đổi (Change Level):** **L3** (REST API cập nhật trạng thái quyết định hồ sơ, kiểm tra điều kiện chuyển đổi trạng thái State Machine, ghi nhận lý do từ chối, phân quyền bảo mật vai trò `ROLE_HR`/`ROLE_ADMIN`, tích hợp Audit Log và chuẩn bị hook thông báo cho [TM-12](https://robluccibn9935.atlassian.net/browse/TM-12)).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
Tiếp nối chuỗi quy trình tiếp nhận hồ sơ từ [TM-1](https://robluccibn9935.atlassian.net/browse/TM-1), [TM-4](https://robluccibn9935.atlassian.net/browse/TM-4), [TM-5](https://robluccibn9935.atlassian.net/browse/TM-5) và [TM-10](https://robluccibn9935.atlassian.net/browse/TM-10), tính năng **TM-11** cung cấp cơ chế chuẩn hóa cho bộ phận Tuyển dụng/Nhân sự (HR) và Quản trị viên đưa ra quyết định tiếp nhận hoặc từ chối ứng viên thực tập:

1. **Chuẩn hóa quy trình ra quyết định (Decision Workflow):**
   - Đảm bảo hồ sơ được chuyển đổi trạng thái một cách tường minh: Từ `PENDING` sang `APPROVED` (Tiếp nhận thực tập) hoặc `REJECTED` (Từ chối hồ sơ).
   - Cho phép xem xét lại trường hợp đã từ chối (`REJECTED` $\rightarrow$ `PENDING`) nếu ứng viên cập nhật hồ sơ hoặc khiếu nại được chấp thuận.
2. **Minh bạch thông tin từ chối (Rejection Transparency):**
   - Bắt buộc HR phải nhập **lý do từ chối** cụ thể (`rejectionReason`) khi từ chối hồ sơ, làm căn cứ phản hồi rõ ràng cho ứng viên (phục vụ trực tiếp cho ticket [TM-12](https://robluccibn9935.atlassian.net/browse/TM-12): Gửi email thông báo kết quả).
3. **Tính toàn vẹn của dữ liệu hồ sơ (Data Integrity):**
   - Chỉ cho phép duyệt/từ chối đối với hồ sơ đang ở trạng thái hợp lệ theo State Machine. Hồ sơ đã hoàn thành (`COMPLETED`) hoặc đang thực tập (`INTERNING`) không được duyệt/từ chối theo API này mà phải qua quy trình quản lý thực tập riêng.
   - Ghi vết người duyệt (`reviewedBy`) và thời điểm ra quyết định (`reviewedAt`).
4. **Phân quyền và bảo mật (Role-Based Access Control - RBAC):**
   - Thao tác duyệt/từ chối hồ sơ mang tính quyết định nghiệp vụ cao, bắt buộc phải được bảo vệ chặt chẽ bằng JWT Token với vai trò `ROLE_HR` hoặc `ROLE_ADMIN`.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
1. **API Phê duyệt hoặc Từ chối hồ sơ:**
   - `PATCH /api/interns/{id}/decision` (hoặc `POST /api/interns/{id}/decision`) tiếp nhận quyết định duyệt/từ chối hồ sơ thực tập sinh.
2. **Hỗ trợ tra cứu theo ID và theo mã thực tập sinh:**
   - Cung cấp khả năng thao tác qua `id` (Long) hoặc `internCode` (String).
3. **Validation & State Machine:**
   - Trạng thái yêu cầu hợp lệ: `APPROVED` hoặc `REJECTED`.
   - Nếu `decision == REJECTED`: Bắt buộc cung cấp `rejectionReason` (độ dài tối thiểu 5 ký tự).
   - Nếu `decision == APPROVED`: Xóa bỏ `rejectionReason` cũ (nếu có), cập nhật `status = APPROVED`.
   - Kiểm tra trạng thái hiện tại của hồ sơ: Không cho phép thao tác trên hồ sơ `COMPLETED` hoặc `INTERNING`.
4. **Audit Logging & Tracing:**
   - Gắn annotation `@Auditable(action = AuditAction.CHANGE_INTERN_STATUS, module = AuditModule.INTERN)` để lưu vết ai là người duyệt/từ chối, lúc nào và lý do gì.
5. **Chuẩn bị dữ liệu tích hợp cho TM-12 (Email Notification):**
   - Trả về đầy đủ thông tin: Email ứng viên, Họ tên, Trạng thái mới, Lý do từ chối, Thời gian phê duyệt để sẵn sàng cho service TM-12 bắt sự kiện hoặc gọi gửi mail.

### 3.2. Ngoài phạm vi (Out of Scope)
- **Không trực tiếp gửi email:** Việc gửi email kích hoạt tài khoản hoặc thư từ chối thuộc về ticket kế tiếp [TM-12](https://robluccibn9935.atlassian.net/browse/TM-12).
- **Không phân công Mentor:** Việc gán Mentor phụ trách thuộc ticket riêng [TM-16](https://robluccibn9935.atlassian.net/browse/TM-16).
- **Không ký hợp đồng thực tập:** Thuộc ticket [TM-13] & [TM-14].

---

## 4. State Machine & Luồng Chuyển Đổi Trạng Thái (Status Transition Flow)

### 4.1. Sơ đồ State Machine
```mermaid
stateDiagram-v2
    [*] --> PENDING: Ứng viên nộp hồ sơ (TM-1 / TM-10)
    
    PENDING --> APPROVED: HR Phê duyệt (TM-11)
    PENDING --> REJECTED: HR Từ chối + Lý do (TM-11)
    
    REJECTED --> PENDING: Cho phép nộp lại / Mở lại hồ sơ
    
    APPROVED --> INTERNING: Bắt đầu kỳ thực tập (Phân mentor TM-16)
    APPROVED --> REJECTED: Hủy tiếp nhận nếu có vi phạm trước ngày onboard
    
    INTERNING --> COMPLETED: Hoàn thành kỳ thực tập
    INTERNING --> REJECTED: Buộc thôi việc / Chấm dứt thực tập
    
    COMPLETED --> [*]: Kết thúc chu trình (Terminal State)
```

### 4.2. Bảng Ma Trận Chuyển Đổi Trạng Thái trong TM-11

| Trạng thái hiện tại | Hành động: Duyệt (`APPROVED`) | Hành động: Từ chối (`REJECTED`) | Ghi chú |
| :--- | :--- | :--- | :--- |
| `PENDING` | ✅ Cho phép $\rightarrow$ `APPROVED` | ✅ Cho phép $\rightarrow$ `REJECTED` (Cần lý do) | Luồng chính của TM-11 |
| `REJECTED` | ❌ Không trực tiếp (Phải qua `PENDING` trước) | ⚠️ Giữ nguyên (Có thể cập nhật lại lý do) | Cập nhật ghi chú từ chối |
| `APPROVED` | ⚠️ Giữ nguyên trạng thái | ✅ Cho phép hủy tiếp nhận $\rightarrow$ `REJECTED` | Trường hợp ứng viên hủy bỏ hoặc phát hiện gian lận |
| `INTERNING` | ❌ Từ chối qua API TM-11 | ❌ Phải qua quy trình kết thúc thực tập | Tránh xung đột dữ liệu |
| `COMPLETED` | ❌ Bị chặn | ❌ Bị chặn | Trạng thái kết thúc (Terminal) |

---

## 5. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Giải Pháp)

### 5.1. Case 1: Từ chối hồ sơ nhưng không cung cấp lý do từ chối (Empty Reason)
- **Vấn đề:** HR click "Từ chối" nhưng bỏ trống trường lý do, dẫn đến ticket gửi mail TM-12 không có nội dung giải thích cho ứng viên.
- **Khắc phục:** Validation cấp DTO và Service: Khi `decision == REJECTED`, `rejectionReason` bắt buộc không được `null`, độ dài tối thiểu 5 ký tự sau khi `trim()`. Trả về `400 Bad Request` nếu vi phạm.

### 5.2. Case 2: Phê duyệt hồ sơ khi chưa có tài liệu bắt buộc (Missing Documents)
- **Vấn đề:** Ứng viên chỉ mới tạo thông tin cơ bản mà chưa nộp CV/Đơn xin thực tập (TM-4) hoặc tài liệu còn đang bị từ chối (`REJECTED` ở TM-5).
- **Khắc phục (Warning / Option):** Service cung cấp cờ kiểm tra (hoặc cảnh báo). Ít nhất có thể cho phép HR duyệt nhưng có log ghi chú rõ, hoặc khuyến nghị kiểm tra tài liệu đã `APPROVED` trước khi duyệt hồ sơ.

### 5.3. Case 3: Race Condition khi 2 HR cùng duyệt một hồ sơ cùng lúc
- **Vấn đề:** 2 HR cùng mở màn hình xét duyệt hồ sơ, một người bấm Duyệt, một người bấm Từ chối gần như đồng thời.
- **Khắc phục:** Sử dụng cơ chế Versioning `@Version` trên entity `InternProfile` (Optimistic Locking) hoặc kiểm tra trạng thái tại thời điểm thực thi giao dịch `@Transactional`, ngăn chặn ghi đè sai lệch dữ liệu.

### 5.4. Case 4: Cố tình duyệt hồ sơ đã kết thúc (Terminal State Tampering)
- **Vấn đề:** Gọi API trực tiếp gửi ID của một thực tập sinh đã `COMPLETED` để đổi về `APPROVED`.
- **Khắc phục:** `InternStatus.canTransitionTo` đã chặn chuyển đổi từ `COMPLETED`. Service ném `IllegalStateException` và trả về `400 Bad Request`.

---

## 6. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Application Decision API):** Cung cấp endpoint cho phép HR/Admin đưa ra quyết định tiếp nhận (`APPROVED`) hoặc từ chối (`REJECTED`) hồ sơ thực tập sinh.
- **FR-2 (Reason Requirement on Rejection):** Bắt buộc cung cấp `rejectionReason` khi đưa ra quyết định `REJECTED`.
- **FR-3 (Review Metadata Recording):** Lưu vết thông tin người duyệt (`reviewedBy` lấy từ JWT `sub` hoặc `preferred_username`), lý do từ chối (`rejectionReason`), và thời gian xét duyệt (`reviewedAt`).
- **FR-4 (Role-Based Access Control):** Chỉ người dùng có vai trò `ROLE_HR` hoặc `ROLE_ADMIN` mới có quyền thực thi thao tác duyệt/từ chối hồ sơ.
- **FR-5 (Response Data Completeness):** DTO phản hồi trả về đầy đủ trạng thái cập nhật, thời gian và thông tin ứng viên để Frontend hiển thị cập nhật tức thì (Optimistic UI / Real-time update).

---

## 7. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1:** Chỉ hồ sơ ở trạng thái `PENDING` mới được duyệt sang `APPROVED`.
- **BR-2:** Khi từ chối hồ sơ (`REJECTED`), `rejectionReason` phải có độ dài từ 5 đến 1000 ký tự.
- **BR-3:** Khi phê duyệt hồ sơ (`APPROVED`), trường `rejectionReason` sẽ được đặt về `null` hoặc làm rỗng.
- **BR-4:** Tất cả các hành động duyệt/từ chối đều phải được ghi lại trong bảng `audit_logs` với action `CHANGE_INTERN_STATUS`.
- **BR-5:** Endpoint không chấp nhận người dùng chưa xác thực (`401 Unauthorized`) hoặc không có quyền HR/Admin (`403 Forbidden`).

---

## 8. Data Model (Mô Hình Dữ Liệu)

### Bảng `intern_profiles` (Bổ sung các trường phục vụ xét duyệt)

```sql
ALTER TABLE intern_profiles
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
```

### Entity Fields (`InternProfile.java`)
```java
@Column(name = "rejection_reason", columnDefinition = "TEXT")
private String rejectionReason;

@Column(name = "reviewed_by", length = 100)
private String reviewedBy;

@Column(name = "reviewed_at")
private LocalDateTime reviewedAt;
```

### DTO Yêu Cầu Ra Quyết Định: `InternDecisionRequest`
```java
package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.InternStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternDecisionRequest {

    @NotNull(message = "Quyết định xét duyệt không được để trống")
    private InternStatus decision; // APPROVED hoặc REJECTED

    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
    private String rejectionReason; // Bắt buộc nếu decision == REJECTED
}
```

---

## 9. API Contract (Đặc Tả Giao Tiếp REST API)

### 9.1. API Ra Quyết Định Duyệt / Từ Chối Hồ Sơ
- **HTTP Method:** `PATCH`
- **Endpoint URL:** `/api/interns/{id}/decision`
- **Authorization:** `Bearer <JWT_TOKEN>` (Roles: `HR`, `ADMIN`)

#### Request Header:
```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
Content-Type: application/json
```

#### Request Body - Trường hợp Phê duyệt (Approve):
```json
{
  "decision": "APPROVED"
}
```

#### Request Body - Trường hợp Từ chối (Reject):
```json
{
  "decision": "REJECTED",
  "rejectionReason": "Hồ sơ chưa đạt yêu cầu về chứng chỉ ngoại ngữ và thời gian thực tập cam kết tối thiểu dưới 3 tháng."
}
```

#### Response 200 OK (Thành công):
```json
{
  "code": 200,
  "message": "Duyệt hồ sơ thực tập sinh thành công",
  "data": {
    "id": 1,
    "internCode": "INT-202609-0001",
    "fullName": "Nguyễn Văn A",
    "email": "nguyenvana@gmail.com",
    "phone": "0987654321",
    "status": "APPROVED",
    "rejectionReason": null,
    "reviewedBy": "hr_manager",
    "reviewedAt": "2026-09-23T10:30:00",
    "createdAt": "2026-09-20T08:00:00",
    "updatedAt": "2026-09-23T10:30:00"
  }
}
```

#### Error Responses:
- **HTTP 400 Bad Request:** Thiếu lý do từ chối hoặc trạng thái không thể chuyển đổi.
```json
{
  "code": 400,
  "message": "Lý do từ chối là bắt buộc và phải có ít nhất 5 ký tự khi từ chối hồ sơ",
  "errors": null
}
```
- **HTTP 401 Unauthorized:** Thiếu JWT hoặc token hết hạn.
- **HTTP 403 Forbidden:** Người dùng không có vai trò `ROLE_HR` hoặc `ROLE_ADMIN`.
- **HTTP 404 Not Found:** Không tìm thấy hồ sơ với `id` được chỉ định.
```json
{
  "code": 404,
  "message": "Không tìm thấy hồ sơ thực tập sinh với ID: 999",
  "errors": null
}
```

---

## 10. Security & Permissions Matrix

| Endpoint | Method | Public | ROLE_INTERN | ROLE_MENTOR | ROLE_HR | ROLE_ADMIN |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `/api/interns/{id}/decision` | `PATCH` | ❌ | ❌ | ❌ | ✅ | ✅ |
| `/api/interns/{id}` | `GET` | ❌ | ❌ (Chỉ xem của mình) | ✅ | ✅ | ✅ |

---

## 11. Acceptance Criteria (Tiêu Chí Nghiệm Thu)

- [ ] **AC-1:** HR/Admin có thể phê duyệt thành công một hồ sơ đang ở trạng thái `PENDING` sang `APPROVED`.
- [ ] **AC-2:** HR/Admin có thể từ chối một hồ sơ đang ở trạng thái `PENDING` sang `REJECTED` khi cung cấp lý do từ chối hợp lệ ($\ge 5$ ký tự).
- [ ] **AC-3:** Hệ thống chặn và trả về `400 Bad Request` nếu HR chọn `REJECTED` nhưng để trống hoặc nhập lý do từ chối dưới 5 ký tự.
- [ ] **AC-4:** Khi phê duyệt (`APPROVED`), trường `rejectionReason` được reset về `null` và `status` chuyển thành `APPROVED`.
- [ ] **AC-5:** Hệ thống ghi nhận chính xác username của người xét duyệt (`reviewedBy`) và thời gian xét duyệt (`reviewedAt`).
- [ ] **AC-6:** Thao tác xét duyệt sinh ra bản ghi Audit Log với hành động `CHANGE_INTERN_STATUS`.
- [ ] **AC-7:** Chặn người dùng không có quyền (Chưa đăng nhập $\rightarrow$ 401, Sinh viên/Mentor $\rightarrow$ 403).
- [ ] **AC-8:** Chặn chuyển đổi trạng thái không hợp lệ (ví dụ hồ sơ đã `COMPLETED` không được chuyển sang `APPROVED` hay `REJECTED`).
- [ ] **AC-9:** Có đầy đủ Unit Tests và Controller Tests bao phủ toàn bộ các kịch bản thành công và ngoại lệ.

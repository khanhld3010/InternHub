# Đặc Tả Kỹ Thuật: Hệ Thống Bắn Thông Báo Toàn Diện Giữa 4 Vai Trò (Notification Trigger Matrix Spec)

> **Mã đặc tả**: `SPEC-TM-NOTIFICATION-TRIGGERS`  
> **Dự án**: InternHub Management Platform  
> **Trạng thái**: Draft / In-Review  
> **Các bên liên quan**: `ADMIN`, `HR`, `MENTOR`, `INTERN`, `SYSTEM`  
> **Services liên quan**: `identity-and-access-service`, `intern-and-program-service`, `notification-service`, `InternHub-Frontend`  

---

## 1. Mục Tiêu (Objective)
Xây dựng và tích hợp toàn bộ các điểm kích hoạt (Trigger Points) từ các service nghiệp vụ để đẩy thông báo thời gian thực và lưu trữ lịch sử tương tác giữa 4 vai trò cốt lõi trong hệ thống InternHub:
1. **Minh bạch tiến độ thực tập**: TTS và Mentor nhận được cập nhật tức thời khi đơn ứng tuyển, nhiệm vụ, báo cáo hoặc hợp đồng có biến động.
2. **Tối ưu vận hành doanh nghiệp**: HR và Quản trị viên nắm bắt ngay các hành động của TTS/Mentor (nộp hồ sơ, ký hợp đồng, nộp báo cáo) để xử lý nhanh chóng mà không cần liên tục kiểm tra thủ công.
3. **Bảo mật phiên nghiêm ngặt**: Admin có thể gửi lệnh khẩn cấp để khóa tài khoản hoặc ép đăng xuất ngay trên phiên kết nối socket của user.

---

## 2. Capability Map & Ranh Giới Service (Capability Map)

| Module ID | Trách nhiệm chính | Dependencies | Phương thức giao tiếp |
|---|---|---|---|
| `notification-core` | Quản lý kết nối STOMP, lưu DB `notification_db`, Redis fan-out | Redis, MySQL | STOMP `/ws`, REST `/api/notifications/**` |
| `identity-triggers` | Kích hoạt sự kiện tài khoản, khóa phiên, phân quyền | `notification-core` | Redis Channel `pubsub:security-commands` |
| `intern-triggers` | Kích hoạt sự kiện hồ sơ, tuyển dụng, hợp đồng, phân công | `notification-core` | FeignClient `POST /api/notifications/internal` |
| `frontend-consumer`| Hiển thị chuông, badge đếm, toast throttle, điều hướng `action_url` | `notification-core` | STOMP `/user/queue/**`, REST API |

---

## 3. Ma Trận Đầy Đủ Các Điểm Kích Hoạt (Full Notification Matrix)

### Nhóm 1: Tuyển Dụng & Phê Duyệt Hồ Sơ (Onboarding & Applications)
- **`APPLICATION_SUBMITTED`**:
  - *Actor*: `INTERN` (khi nộp form ứng tuyển).
  - *Recipient*: Toàn bộ user có role `HR` hoặc `ADMIN`.
  - *Kênh*: `/user/queue/notifications` + `/topic/live/applications`.
  - *Tiêu đề*: *"Đơn ứng tuyển mới"*
  - *Nội dung*: *"Ứng viên {fullName} vừa nộp hồ sơ vào vị trí {position}."*
  - *Action URL*: `/hr/interns/{internId}`
- **`APPLICATION_APPROVED`**:
  - *Actor*: `HR` hoặc `ADMIN` (khi đổi trạng thái hồ sơ sang `APPROVED`).
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Hồ sơ thực tập được phê duyệt"*
  - *Nội dung*: *"Chúc mừng! Hồ sơ ứng tuyển của bạn đã được phê duyệt. Vui lòng kiểm tra email để nhận thông tin tài khoản."*
  - *Action URL*: `/profile`
- **`APPLICATION_REJECTED`**:
  - *Actor*: `HR` hoặc `ADMIN`.
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Kết quả xét duyệt hồ sơ"*
  - *Nội dung*: *"Hồ sơ của bạn chưa phù hợp ở thời điểm này. Lý do: {rejectionReason}."*
  - *Action URL*: `/profile`

---

### Nhóm 2: Vòng Đời Hợp Đồng Thực Tập (Contract Lifecycle)
- **`CONTRACT_CREATED`**:
  - *Actor*: `HR` / `ADMIN`.
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Hợp đồng thực tập mới"*
  - *Nội dung*: *"HR đã gửi dự thảo hợp đồng {contractCode}. Vui lòng đọc kỹ và ký xác nhận trực tuyến."*
  - *Action URL*: `/profile?tab=contract`
- **`CONTRACT_SIGNED_BY_INTERN`**:
  - *Actor*: `INTERN` (khi ký điện tử OTP/Token).
  - *Recipient*: `HR` (người tạo hợp đồng hoặc phòng HR).
  - *Kênh*: `/user/queue/notifications` + `/topic/live/contracts`.
  - *Tiêu đề*: *"Thực tập sinh đã ký hợp đồng"*
  - *Nội dung*: *"TTS {internName} đã ký xác nhận hợp đồng {contractCode}."*
  - *Action URL*: `/hr/contracts/{contractId}`
- **`CONTRACT_APPROVED_BY_HR`**:
  - *Actor*: `HR` / `ADMIN` (khi đại diện doanh nghiệp ký hoàn tất).
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Hợp đồng thực tập đã có hiệu lực"*
  - *Nội dung*: *"Hợp đồng thực tập {contractCode} của bạn đã được doanh nghiệp phê duyệt chính thức."*
  - *Action URL*: `/profile?tab=contract`
- **`CONTRACT_TERMINATED`**:
  - *Actor*: `HR` / `ADMIN`.
  - *Recipient*: `INTERN` & `MENTOR` phụ trách.
  - *Tiêu đề*: *"Thông báo chấm dứt hợp đồng thực tập"*
  - *Nội dung*: *"Hợp đồng {contractCode} đã chấm dứt trước thời hạn. Lý do: {terminationReason}."*
  - *Action URL*: `/profile?tab=contract`

---

### Nhóm 3: Phân Công Hướng Dẫn & Chương Trình (Mentorship & Program)
- **`MENTOR_ASSIGNED`**:
  - *Actor*: `HR` / `ADMIN`.
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Phân công người hướng dẫn"*
  - *Nội dung*: *"Bạn được phân công Mentor {mentorName} ({mentorEmail}) hướng dẫn trong chương trình thực tập."*
  - *Action URL*: `/profile`
- **`INTERN_ASSIGNED_TO_MENTOR`**:
  - *Actor*: `HR` / `ADMIN`.
  - *Recipient*: `MENTOR`.
  - *Tiêu đề*: *"Tiếp nhận thực tập sinh mới"*
  - *Nội dung*: *"Bạn được phân công phụ trách hướng dẫn TTS {internName} ({internEmail}) cho vị trí {position}."*
  - *Action URL*: `/mentor/interns/{internId}`
- **`MENTOR_HANDOVER`**:
  - *Actor*: `HR` / `ADMIN`.
  - *Recipient*: `MENTOR` cũ & `MENTOR` mới.
  - *Tiêu đề*: *"Bàn giao thực tập sinh"*
  - *Nội dung*: *"TTS {internName} đã được bàn giao từ Mentor {oldMentorName} sang Mentor {newMentorName}."*
  - *Action URL*: `/mentor/interns`

---

### Nhóm 4: Nhiệm Vụ, Báo Cáo & Đánh Giá (Tasks & Evaluation)
- **`TASK_ASSIGNED`**:
  - *Actor*: `MENTOR`.
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Giao nhiệm vụ mới"*
  - *Nội dung*: *"Mentor đã giao cho bạn nhiệm vụ: {taskTitle}. Hạn nộp: {dueDate}."*
  - *Action URL*: `/tasks/{taskId}`
- **`REPORT_SUBMITTED`**:
  - *Actor*: `INTERN`.
  - *Recipient*: `MENTOR`.
  - *Tiêu đề*: *"Nộp báo cáo thực tập"*
  - *Nội dung*: *"TTS {internName} đã nộp báo cáo tuần {weekNo} cho nhiệm vụ {taskTitle}."*
  - *Action URL*: `/mentor/evaluations/{reportId}`
- **`REPORT_FEEDBACK_ADDED`**:
  - *Actor*: `MENTOR`.
  - *Recipient*: `INTERN`.
  - *Tiêu đề*: *"Nhận phản hồi từ Mentor"*
  - *Nội dung*: *"Mentor {mentorName} đã gửi đánh giá & nhận xét về báo cáo tuần của bạn."*
  - *Action URL*: `/tasks/{taskId}`
- **`EVALUATION_COMPLETED`**:
  - *Actor*: `MENTOR`.
  - *Recipient*: `INTERN` & `HR`.
  - *Tiêu đề*: *"Hoàn tất đánh giá kỳ thực tập"*
  - *Nội dung*: *"Mentor đã hoàn tất phiếu đánh giá tổng kết thực tập cho TTS {internName} (Điểm: {finalScore}/10)."*
  - *Action URL*: `/evaluations/{evaluationId}`

---

### Nhóm 5: Tín Hiệu Bảo Mật Khẩn Cấp (Security Commands - Ephemeral)
- **`FORCE_LOGOUT`**:
  - *Actor*: `ADMIN` / `SYSTEM`.
  - *Recipient*: Target User ID bất kỳ.
  - *Kênh*: `/user/queue/security`.
  - *Hành vi Client*: Lập tức ngắt socket, xóa sạch token, popup thông báo và redirect về `/login`.
- **`ACCOUNT_LOCKED`**:
  - *Actor*: `ADMIN` (qua API `toggleUserStatus`).
  - *Recipient*: User bị khóa.
  - *Kênh*: `/user/queue/security`.
  - *Hành vi Client*: Hiển thị popup: *"Tài khoản của bạn đã bị khóa bởi Quản trị viên"*, redirect về `/login`.
- **`TOKEN_EXPIRED`**:
  - *Actor*: `notification-service` Scheduler (quét định kỳ).
  - *Recipient*: User có token hết hạn.
  - *Hành vi Client*: Tự động thử `refreshToken`; nếu refresh thất bại $\rightarrow$ Đăng xuất an toàn.

---

## 4. API & Interface Design (Contract-First)

### 4.1. DTO Chuẩn Service-to-Service (`POST /api/notifications/internal`)
```java
package org.example.internservice.intern.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationInternalRequest {
    private Long recipientId;       // ID người nhận thông báo
    private Long actorId;           // ID người thực hiện (nếu có)
    private String title;           // Tiêu đề ngắn gọn
    private String content;         // Nội dung chi tiết
    private String type;            // Enum mã sự kiện (ví dụ: CONTRACT_SIGNED_BY_INTERN)
    private String referenceType;   // Thực thể liên quan: INTERN, CONTRACT, TASK...
    private String referenceId;     // ID thực thể
    private String actionUrl;       // URL đích phía Frontend điều hướng sẵn
}
```

### 4.2. FeignClient Interface (`intern-and-program-service`)
```java
package org.example.internservice.intern.client;

import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/internal")
    void sendNotification(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody CreateNotificationInternalRequest request
    );
}
```

### 4.3. Async Notification Dispatcher (Chống nghẽn Transaction chính)
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventDispatcher {

    private final NotificationServiceClient notificationClient;

    @Value("${internal.service-token:internhub-internal-secret-token-2026}")
    private String internalServiceToken;

    @Async
    public void dispatch(CreateNotificationInternalRequest request) {
        try {
            notificationClient.sendNotification(internalServiceToken, request);
            log.info("Dispatched notification '{}' to recipientId={}", request.getType(), request.getRecipientId());
        } catch (Exception e) {
            log.error("Failed to send internal notification to recipient {}: {}", request.getRecipientId(), e.getMessage());
        }
    }
}
```

### 4.4. Security Command DTO (`identity-and-access-service`)
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityCommandPayload {
    private String action;   // FORCE_LOGOUT, ACCOUNT_LOCKED
    private Long userId;     // ID user bị tác động
    private String reason;   // Lý do khóa
    private String message;  // Thông điệp hiển thị cho người dùng
}
```

---

## 5. Ranh Giới An Toàn & Quy Tắc Bất Biến (Boundaries)

- **Always (Luôn luôn)**:
  - Bọc lời gọi FeignClient trong `@Async` hoặc sau khi Transaction DB chính commit thành công (`TransactionSynchronizationAdapter.afterCommit`), đảm bảo lỗi gửi thông báo không bao giờ rollback dữ liệu nghiệp vụ chính.
  - Verify quyền sở hữu tại `PATCH /api/notifications/{id}/read` (`recipientId == currentUserId`) để chống IDOR.
  - Phân quyền tại STOMP Frame `SUBSCRIBE`: Chặn Intern subscribe vào `/topic/live/applications` hoặc `/topic/live/contracts`.
- **Ask First (Hỏi trước)**:
  - Bổ sung thêm loại sự kiện mới nằm ngoài 5 nhóm trên.
  - Sửa đổi cấu trúc bảng `notifications` trong `notification_db`.
- **Never (Tuyệt đối cấm)**:
  - Không lưu lệnh bảo mật (`FORCE_LOGOUT`, `ACCOUNT_LOCKED`) vào bảng `notifications`.
  - Không mở public endpoint `/api/notifications/internal/**` ra ngoài Gateway.

---

## 6. Tiêu Chí Nghiệm Thu (Success Criteria)

- [ ] **Acceptance 1**: Khi HR duyệt hồ sơ TTS, TTS đang online nhận được Toast và biểu tượng chuông nhảy số ngay lập tức mà không cần F5.
- [ ] **Acceptance 2**: Khi TTS hoàn tất ký hợp đồng, HR nhận được thông báo kèm nút *"Chi tiết"* click mở thẳng trang `/hr/contracts/{id}`.
- [ ] **Acceptance 3**: Khi Mentor giao task hoặc chấm điểm, TTS nhận được thông báo tương ứng.
- [ ] **Acceptance 4**: Khi Admin bấm toggle khóa tài khoản bên Identity Service, tài khoản đó lập tức nhận thông báo bị khóa và redirect về màn hình login trong vòng < 1 giây.
- [ ] **Acceptance 5**: Không có hiện tượng rò rỉ socket khi user logout hoặc chuyển đổi tài khoản trên cùng một trình duyệt.

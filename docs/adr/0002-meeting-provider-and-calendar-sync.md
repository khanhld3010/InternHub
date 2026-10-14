# ADR 0002: Kiến Trúc Điều Phối Lịch Họp (MeetingProvider), Đồng Bộ Lịch (.ics) và Cảnh Báo Đa Kênh

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-08
- **Tác giả / Người quyết định:** Luong Anh Huy & Antigravity Pair-Programming
- **Phạm vi:** `intern-and-program-service`, `reporting-and-integration-service`, `notification-service` và `InternHub-Frontend`.

---

## 1. Ngữ cảnh & Vấn đề (Context & Problem Statement)

1. **Từ bản ghi thụ động đến Cuộc họp Khả dụng (Actionable Meeting):**
   - Trước đây, lịch họp chỉ đơn thuần là các dòng dữ liệu tĩnh trong cơ sở dữ liệu (`meeting_schedules`), thiếu liên kết phòng họp trực tuyến và không thể đồng bộ sang ứng dụng lịch cá nhân của người dùng.
   - Khi Mentor hoặc HR tạo lịch hẹn, thực tập sinh chỉ thấy thông tin mà không có link video call để vào họp ngay.

2. **Bài toán đồng bộ lịch & nhắc giờ tự động (Calendar Sync & Reminders):**
   - Người dùng thường quên lịch nếu chỉ có thông báo một lần trong ứng dụng.
   - Cần cơ chế tự động đưa sự kiện vào **Google Calendar / Microsoft Outlook / Apple Calendar** để thiết bị di động của thực tập sinh tự rung chuông nhắc hẹn.

3. **Ràng buộc tích hợp bên thứ ba (Third-party Integration Constraints):**
   - **Google Meet qua Google Calendar API:** Đòi hỏi cấu hình OAuth2 Client, cấp quyền người dùng hoặc Google Workspace Domain-wide Delegation. Trong môi trường Development/Testing, refresh token của Google OAuth2 ở chế độ *Testing* bị Google giới hạn **hết hạn sau 7 ngày**, gây gián đoạn luồng vận hành tự động nếu không có cơ chế fallback.
   - Không được phép gắn cứng (hardcode) hệ thống vào duy nhất một nhà cung cấp phòng họp trực tuyến.

---

## 2. Quyết định Kiến trúc (Decision)

Hệ thống quyết định áp dụng **Mô hình Kiến trúc 3 Tầng** với mẫu thiết kế **Strategy Pattern (`MeetingProvider`)**, đồng bộ lịch kép và cơ chế kiểm soát phân tán an toàn:

### A. Tầng 1: Đính Kèm Tệp Lịch Chuẩn iCalendar (.ics) Vào Email (Có Điều Kiện)
- Sử dụng thư viện `ical4j` để tạo file lịch chuẩn MIME `text/calendar; method=REQUEST`.
- **Nguyên tắc phân định nhà cung cấp (Tránh xung đột trùng lịch với Google):**
  - **Với Provider `GOOGLE_MEET`:** Khi tạo qua Google Calendar API với `sendUpdates=all`, Google đã tự động gửi lời mời chính thức kèm sự kiện lịch tới hộp thư người tham gia. Do đó, email từ hệ thống InternHub **KHÔNG đính kèm file `.ics`** (chỉ chứa đường link phòng họp + `actionUrl` dẫn tới chi tiết cuộc họp trên portal) để tránh thực tập sinh bị trùng lặp 2 sự kiện trên lịch.
  - **Với Provider `JITSI` hoặc `CUSTOM_MANUAL`:** Email hệ thống **BẮT BUỘC đính kèm file `.ics`** để thiết bị của thực tập sinh tự động đồng bộ vào Calendar.
- **Nguyên tắc định danh duy nhất (Idempotency & Lifecycle):**
  - Mỗi cuộc họp gắn với một mã `UID` bất biến (ví dụ: `meeting-{id}@internhub.vn`).
  - **Khi tạo mới:** `SEQUENCE = 0`, `STATUS = CONFIRMED`, `METHOD = REQUEST`.
  - **Khi chỉnh sửa (Dời lịch / Đổi link):** Giữ nguyên `UID`, tăng `SEQUENCE = SEQUENCE + 1`. Các ứng dụng Google Calendar / Outlook khi nhận file sẽ tự động cập nhật lại sự kiện cũ trên lịch của thực tập sinh mà không bị trùng lặp.
  - **Khi hủy lịch:** Giữ nguyên `UID`, tăng `SEQUENCE`, đặt `STATUS = CANCELLED`, `METHOD = CANCEL`. Sự kiện trên lịch người dùng sẽ tự động gỡ bỏ hoặc chuyển sang trạng thái đã hủy.

### B. Tầng 2: Thiết Kế Trừu Tượng Hóa `MeetingProvider` & Thứ Tự Ưu Tiên Tuyệt Đối
Thiết kế interface trừu tượng hóa nhà cung cấp phòng họp trực tuyến:

```java
public interface MeetingProvider {
    MeetingRoomInfo createMeeting(MeetingSchedule meeting);
    void updateMeeting(MeetingSchedule meeting);
    void cancelMeeting(MeetingSchedule meeting);
    boolean supports(MeetingProviderType type);
}
```

**Thứ tự ưu tiên sinh link phòng họp (Link tay luôn thắng tất cả):**
1. **Ưu tiên 1 - Nhập link thủ công (`CUSTOM_MANUAL` - Ưu tiên cao nhất):**
   - Nếu Mentor/HR chủ động nhập link phòng họp (Zoom, Google Meet cá nhân, MS Teams, Skype), hệ thống **luôn tôn trọng và sử dụng link này**, bỏ qua toàn bộ logic tự sinh.
2. **Ưu tiên 2 - Ô link để trống + Đã kết nối Google (`GOOGLE_MEET`):**
   - Nếu người tạo để trống link và đã liên kết tài khoản Google Calendar hợp lệ, hệ thống gọi Google Calendar API (`conferenceData.createRequest` loại `hangoutsMeet`) để tự sinh phòng Meet và link tham gia.
3. **Ưu tiên 3 - Ô link để trống + Chưa kết nối Google hoặc Google lỗi (`JITSI` - Fallback mặc định):**
   - Nếu không có link tay và tài khoản chưa kết nối Google (hoặc Google token hết hạn / API lỗi), hệ thống tự động fallback về Jitsi Meet: sinh phòng họp an toàn tức thì dạng `https://meet.jit.si/internhub-meeting-{uuid}` (Zero-Setup, không cần tài khoản hay API key).

### C. Tầng 3: Vòng Đời Sự Kiện Toàn Diện & Cronjob Nhắc Lịch Đa Instance An Toàn
1. **Vòng đời phát sự kiện (Tạo mới, Cập nhật, Hủy bỏ):**
   - Cả 3 hành động **Tạo mới**, **Cập nhật dời lịch**, và **Hủy lịch** đều phát sự kiện bất đồng bộ kích hoạt:
     - Với Google Meet: Gọi Google Calendar API (`events.patch` khi sửa, `events.delete` khi hủy).
     - Gửi Email cập nhật / hủy (đính kèm `.ics` có `SEQUENCE` mới nếu là Jitsi/link tay).
     - Bắn WebSocket STOMP tới kênh `/user/{intern_username}/queue/notifications` kèm `actionUrl` dẫn tới chi tiết lịch.
2. **Cơ chế Quét & Nhắc Lịch Tự Động Trước 15 Phút:**
   - Cột `reminder_sent (BOOLEAN DEFAULT FALSE)` trong bảng `meeting_schedules`.
   - `@Scheduled(cron = "0 * * * * ?")` tại `intern-and-program-service` quét các cuộc họp sắp diễn ra trong khoảng `[now + 14m, now + 16m]` mà `reminder_sent = FALSE`.
   - **Chiến lược thông báo:**
     - Lịch Google Meet: Chỉ bắn **WebSocket STOMP** (vì Google Calendar đã tự push notification và gửi mail điện thoại).
     - Lịch Jitsi / Link tay: Bắn **cả Email nhắc nhở + WebSocket STOMP**.
3. **Chống trùng lặp khi chạy nhiều Instance (Distributed Lock / Optimistic Race):**
   - Để tránh việc chạy nhiều replica của `intern-and-program-service` dẫn tới nhắc lịch bị gửi lặp nhiều lần:
     - Sử dụng **ShedLock** (khóa phân tán qua Redis/MySQL) để đảm bảo tại mỗi phút chỉ có duy nhất 1 node thực thi cronjob.
     - Đồng thời áp dụng câu lệnh cập nhật nguyên tử:
       ```sql
       UPDATE meeting_schedules SET reminder_sent = TRUE 
       WHERE id = :id AND reminder_sent = FALSE;
       ```
       Chỉ khi số dòng bị ảnh hưởng (`rowsUpdated > 0`), node đó mới được phép phát lệnh gửi cảnh báo ra ngoài.

### D. Kiến Trúc Lưu Trữ Bảo Mật & Giao Tiếp Giữa Các Service
1. **Lưu trữ Google Refresh Token:**
   - Refresh Token của người dùng được **mã hóa an toàn (AES-256-GCM)** và lưu trong bảng `user_calendar_integrations` tại cơ sở dữ liệu của `intern-and-program-service`.
   - Tuyệt đối **không lưu trữ hay phân tán refresh token** sang các microservices khác (`notification-service`, `reporting-service`, `gateway`).
2. **Giao tiếp liên dịch vụ & Đảm bảo Tính toàn vẹn (Transactional Outbox & Idempotency):**
   - `intern-and-program-service` phát event thông báo và email sang `notification-service` và `reporting-and-integration-service` thông qua **Redis Message Broker** (hoặc REST Client bất đồng bộ kèm cơ chế Retry).
   - Áp dụng mẫu hình **Transactional Outbox Pattern**: Bản ghi cuộc họp và Event thông báo được lưu trong cùng một Database Transaction, triệt tiêu rủi ro DB đã commit nhưng mạng lỗi làm mất event.
   - Mỗi event mang một `eventId` (UUID). Các consumer (`notification-service`, `reporting-service`) kiểm tra khóa idempotency trên Redis/DB trước khi xử lý, đảm bảo email và chuông thông báo không bao giờ bị gửi trùng lặp.

---

## 3. Các Phương Án Đã Cân Nhắc (Alternatives Considered)

1. **Phương án 1: Chỉ dùng Jitsi Meet, loại bỏ hoàn toàn Google Meet**
   - *Đánh giá:* Rất đơn giản, không phụ thuộc bên thứ ba.
   - *Lý do loại bỏ:* Thiếu tính chuyên nghiệp trong mắt doanh nghiệp lớn, không hỗ trợ đồng bộ lịch 2 chiều trực tiếp vào tài khoản Google Workspace của Mentor/HR.
2. **Phương án 2: Chỉ dùng Google Meet, bắt buộc mọi người dùng phải OAuth2**
   - *Đánh giá:* Đồng bộ hai chiều hoàn hảo.
   - *Lý do loại bỏ:* Gặp rào cản lớn về trải nghiệm và môi trường Testing (Google Refresh token hết hạn sau 7 ngày nếu app chưa Verify). Nếu người dùng không có tài khoản Google hoặc chưa cấp quyền thì toàn bộ chức năng lên lịch họp sẽ bị tê liệt.
3. **Phương án 3: Tách riêng một Microservice độc lập `meeting-service`**
   - *Đánh giá:* Tách biệt nghiệp vụ họp ra khỏi quản lý thực tập sinh.
   - *Lý do loại bỏ:* Quá tải kiến trúc không cần thiết (Over-engineering). Cuộc họp thực tập sinh nằm chặt chẽ trong cùng Bounded Context với Thực tập sinh & Chương trình (`intern-and-program-service`). Việc tách riêng sẽ gây ra quá nhiều lời gọi chéo mạng giữa các service chỉ để lấy thông tin hồ sơ và người hướng dẫn.

---

## 4. Hệ quả & Đánh giá (Consequences)

### Tích cực:
- **Nguyên tắc "Link tay thắng tất cả" rõ ràng:** Mentor hoàn toàn linh hoạt dán link quen thuộc, hệ thống chỉ can thiệp tự sinh khi cần thiết.
- **Trải nghiệm lịch không trùng lặp:** Loại bỏ hoàn toàn nguy cơ nhận 2 sự kiện lịch đè lên nhau giữa Google Calendar và file `.ics`.
- **An toàn trong môi trường phân tán:** Nhờ Shedlock, Optimistic Update và Transactional Outbox, hệ thống có thể mở rộng nhiều replica mà không sợ spam thông báo trùng lặp.
- **Bảo mật cao:** Google Refresh Token được mã hóa tập trung tại đúng một service sở hữu nghiệp vụ.

### Thách thức & Cân nhắc:
- Cần cấu hình khóa mã hóa AES (`ENCRYPTION_SECRET_KEY`) trong biến môi trường an toàn khi lưu trữ Refresh Token.
- Cần chuẩn hóa múi giờ `VTIMEZONE` trong file `.ics` theo chuẩn `Asia/Ho_Chi_Minh` (UTC+7) để các thiết bị iOS/Android/Windows nhận diện chính xác giờ họp.

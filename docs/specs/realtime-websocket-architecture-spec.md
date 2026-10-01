# Đặc Tả Kiến Trúc Hệ Thống Real-time WebSocket & Notification Service

> **Dự án**: InternHub Management System  
> **Tài liệu**: Technical Architecture & Design Specification  
> **Trạng thái**: Approved (Khóa hiểu biết & hoàn tất Brainstorming)  
> **Phiên bản**: 1.0  
> **Ngày phê duyệt**: 01/10/2026  

---

## 1. Mục Tiêu & Phạm Vi (Goals & Scope)

### 1.1. Mục tiêu cốt lõi
Hệ thống Real-time WebSocket được thiết kế để giải quyết 2 bài toán lớn trong kiến trúc Microservices của InternHub:
1. **Live Notification & State Synchronization (Nghiệp vụ thời gian thực)**:
   - Đẩy thông báo có lưu lịch sử (Persistent notifications), hiển thị badge/counter unread trên thanh điều hướng.
   - Phát tán tín hiệu thay đổi trạng thái (State Sync) để Frontend tự làm mới dữ liệu (refetch ngầm hoặc cập nhật state cục bộ) mà người dùng không cần bấm F5 (ví dụ: Intern nộp đơn $\rightarrow$ HR thấy danh sách nhảy mới; HR duyệt hồ sơ / Mentor chấm bài $\rightarrow$ màn hình Intern cập nhật kết quả tức thì).
2. **Security & Session Commands (Bảo mật & Giám sát phiên)**:
   - Đẩy lệnh hệ thống khẩn cấp (Ephemeral system commands) xuống client cá nhân qua kênh an toàn: Ép đăng xuất ngay lập tức (`FORCE_LOGOUT`), khóa tài khoản tức thời (`ACCOUNT_LOCKED`), hoặc thông báo hết hạn phiên (`TOKEN_EXPIRED`).

### 1.2. Ranh giới & Non-Goals
- **Non-Goals giai đoạn này**: Không xây dựng hệ thống chat tin nhắn 2 chiều hoặc chat nhóm phức tạp (tin nhắn văn bản chat, chỉ báo typing, tệp đính kèm chat).
- **Ranh giới dữ liệu**: Các tín hiệu bảo mật (Security Commands) tuyệt đối không được ghi vào CSDL thông báo hay hiển thị trên biểu tượng chuông (bell icon) của người dùng.

---

## 2. Kiến Trúc Hạ Tầng & Topo Mạng (Infrastructure & Topology)

### 2.1. Các thành phần mới bổ sung
1. **Container `redis:alpine`**:
   - Chạy trên port nội bộ `6379`.
   - Đóng vai trò làm High-Speed Message Broker cho Pub/Sub các sự kiện real-time và broadcast lệnh bảo mật liên service.
2. **Microservice độc lập `notification-service`**:
   - Chạy trên port `8085`.
   - Kết nối và sở hữu độc quyền database **`notification_db`** (tuân thủ nghiêm ngặt nguyên tắc **Database-per-Service**, không dùng chung schema với bất kỳ service nào).
   - Đăng ký vào Eureka (`discovery-server:8761`) và nhận cấu hình tập trung từ `config-server:8888`.
3. **Định tuyến tại API Gateway (`api-gateway`)**:
   - **HTTP REST**: Định tuyến `/api/notifications/**` $\rightarrow$ `lb://notification-service`.
   - **WebSocket STOMP**: Định tuyến `/ws/**` $\rightarrow$ `lb:ws://notification-service` bằng bộ định tuyến WebFlux của Spring Cloud Gateway.
   - **Chặn truy cập trái phép**: Gateway cấu hình chặn triệt để mọi request từ public internet gọi trực tiếp vào pattern `/api/notifications/internal/**`.

```
                        [ Frontend Client ]
                               │
                               ▼ (HTTP REST & ws://)
                        [ API Gateway: 8080 ]
                               │
        ┌──────────────────────┴──────────────────────┐
        ▼ (HTTP REST)                                 ▼ (WebSocket Handshake)
  /api/notifications/**                             /ws/**
        │                                             │
        └──────────────────────┬──────────────────────┘
                               ▼
               [ notification-service: 8085 ] ──> MySQL (notification_db)
                               ▲
                               │ (Redis Pub/Sub: events & security)
                       [ Redis: 6379 ]
                        ▲             ▲
      (Service Token)   │             │   (Security Signal)
  [ intern-and-program-service ]     [ identity-and-access-service ]
```

---

## 3. Thiết Kế Cơ Sở Dữ Liệu & REST API (`notification_db`)

### 3.1. Schema bảng `notifications`
```sql
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    actor_id BIGINT NULL COMMENT 'ID người thực hiện hành động (HR, Mentor...) để hiện avatar/audit',
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL COMMENT 'Mã sự kiện: APPLICATION_SUBMITTED, CONTRACT_STATUS_CHANGED...',
    reference_type VARCHAR(50) NULL COMMENT 'Loại thực thể: CONTRACT, INTERN, PROGRAM...',
    reference_id VARCHAR(100) NULL COMMENT 'ID thực thể đích',
    action_url VARCHAR(255) NULL COMMENT 'URL FE điều hướng sẵn (ví dụ: /hr/contracts/12?tab=preview)',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recipient_created (recipient_id, created_at DESC),
    INDEX idx_recipient_unread (recipient_id, is_read)
);
```

### 3.2. REST Endpoints & Kiểm Soát An Toàn
1. **Client Endpoints (Yêu cầu JWT Bearer của người dùng)**:
   - `GET /api/notifications`: Lấy danh sách thông báo. Hỗ trợ **Keyset/Cursor-based pagination** (`created_at`, `id`) kèm `limit` để tối ưu hiệu năng khi dữ liệu tăng cao (tránh quét offset lớn).
   - `GET /api/notifications/unread-count`: Đếm nhanh số thông báo chưa đọc.
   - `PATCH /api/notifications/{id}/read`: Đánh dấu đã đọc 1 thông báo.
     - **Bắt buộc phòng chống IDOR**: Phải truy vấn thông báo và kiểm tra chặt chẽ:
       ```java
       Notification notification = notificationRepository.findById(id)
           .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
       if (!notification.getRecipientId().equals(currentUserId)) {
           throw new ForbiddenException("Không có quyền thao tác trên thông báo này");
       }
       notification.setRead(true);
       notificationRepository.save(notification);
       ```
   - `PATCH /api/notifications/read-all`: Đánh dấu đã đọc toàn bộ thông báo của `currentUserId`.

2. **Internal Service-to-Service Endpoint**:
   - `POST /api/notifications/internal`: Tiếp nhận thông báo nghiệp vụ từ các service khác (`intern-service`).
   - **Cơ chế xác thực**: Dùng **Service Token nội bộ (Client Credentials / Shared Internal Secret)**, không dựa vào JWT của người dùng cá nhân (vì nghiệp vụ do backend tự kích hoạt sau transaction).

---

## 4. Kiến Trúc WebSocket/STOMP & Mô Hình Phân Phối Tin

### 4.1. Cấu trúc STOMP Destinations
- `/user/queue/notifications`: Nhận thông báo nghiệp vụ cá nhân (bell icon, badge, toast).
- `/user/queue/security`: Nhận lệnh bảo mật từ hệ thống (`FORCE_LOGOUT`, `ACCOUNT_LOCKED`, `TOKEN_EXPIRED`).
- `/topic/live/{entity}`: Kênh broadcast theo dõi dữ liệu chung (ví dụ: `/topic/live/applications`, `/topic/live/contracts`).

### 4.2. Bảo vệ kênh SUBSCRIBE (Chống IDOR qua STOMP Topic)
- Trong `ChannelInterceptor.preSend()`: Can thiệp cả lệnh `CONNECT` lẫn `SUBSCRIBE`.
- **Phân quyền tại Frame SUBSCRIBE**:
  - Trích xuất `Principal` và `Role` từ phiên kết nối.
  - Áp dụng kiểm tra quyền: Ví dụ `/topic/live/applications/**` chỉ chấp nhận `ROLE_ADMIN` hoặc `ROLE_HR`.
  - Nếu Intern hoặc người dùng không đủ quyền cố tình subscribe: **Từ chối ngay lập tức (ném AccessDeniedException / ngắt frame subscribe)**.

### 4.3. Hai Luồng Dữ Liệu Tách Biệt (Data Flows)

#### Kịch bản A: Nghiệp vụ thông thường (Persistent Notification & State Sync)
1. Service nghiệp vụ (ví dụ: `intern-service`) hoàn tất transaction DB.
2. Gọi FeignClient `POST /api/notifications/internal` (kèm Service Token).
3. `notification-service` lưu vào `notification_db.notifications`.
4. `notification-service` publish thông điệp lên Redis channel `pubsub:notifications`.
5. Mọi instance của `notification-service` nhận tin từ Redis $\rightarrow$ đẩy frame STOMP tới user đang online qua `/user/queue/notifications`.

#### Kịch bản B: Tín hiệu bảo mật khẩn cấp (Security Commands)
1. `identity-and-access-service` khi khóa tài khoản hoặc thu hồi phiên:
   - Ghi key thu hồi `security:revoked:{userId}` vào Redis với TTL bằng thời hạn tối đa của Refresh Token.
   - Bắn trực tiếp payload `{ "action": "FORCE_LOGOUT", "userId": 123, "reason": "ACCOUNT_LOCKED", "message": "Tài khoản của bạn đã bị khóa." }` lên Redis channel `pubsub:security-commands`.
2. `notification-service` nhận được:
   - **Bước 1**: Gửi frame STOMP tới user đó qua `/user/queue/security`.
   - **Bước 2**: Đợi frame được flush (delay bất đồng bộ ~500ms).
   - **Bước 3**: Gọi lệnh đóng WebSocket session vật lý (`session.close(CloseStatus.POLICY_VIOLATION)`).

#### Thừa nhận đánh đổi về Redis Pub/Sub (At-most-once delivery)
- Redis Pub/Sub là fire-and-forget. Nếu `notification-service` đang restart đúng lúc tin bảo mật phát ra, tin nhắn có thể bị rớt.
- **Safety Net**: Được bảo vệ bởi tầng kiểm tra DB ở mọi REST API nghiệp vụ và cơ chế **Periodic Re-validation** của WebSocket.

---

## 5. Quản Lý Vòng Đời Socket & Re-validation Định Kỳ (Defense-in-depth)

### 5.1. Định vị vai trò 2 cơ chế
- **Cơ chế chính (Primary Enforce)**: Redis Pub/Sub (phát hiện và đẩy lệnh đóng ngay tức thời 0ms).
- **Cơ chế phụ (Secondary Defense-in-depth Polling)**: Scheduler quét định kỳ đóng vai trò "lưới an toàn vét đáy" khi service restart hoặc token hết hạn tự nhiên.

### 5.2. Quản lý Session sạch (Clean Session Lifecycle)
- `notification-service` duy trì in-memory `ConcurrentHashMap<String, WebSocketSessionMeta> activeSessions` (Key là `sessionId`).
- Bắt buộc lắng nghe `SessionDisconnectEvent` của Spring: Xóa ngay metadata khỏi map khi socket đóng $\rightarrow$ Triệt tiêu hoàn toàn **session "ma"**.
- Khi client reconnect: Là kết nối TCP mới $\rightarrow$ `sessionId` mới, metadata mới.

### 5.3. Scheduler Batch Check tối ưu
- Scheduled task chạy mỗi 60 giây trên từng instance.
- Gom toàn bộ `userId` của các session đang active trên instance đó.
- Dùng **Redis Pipelining / MGET** kiểm tra toàn bộ key `security:revoked:{userId}` trong **1 network round-trip duy nhất** (tránh N lần gọi riêng biệt).
- Kiểm tra `tokenExpirationTime < now()`: Nếu hết hạn, gửi `TOKEN_EXPIRED` và đóng socket.

### 5.4. TTL của Revocation Key
- Key `security:revoked:{userId}` trong Redis **bắt buộc có TTL bằng thời hạn tối đa của Refresh Token** (ví dụ: 7 ngày), **tuyệt đối không để TTL theo Access Token**, ngăn chặn việc dùng Refresh Token cũ để "hồi sinh" phiên truy cập.

---

## 6. Kiến Trúc Frontend (React / Vite / TypeScript)

### 6.1. Quản lý kết nối & Subscription theo Role
- Khởi tạo `@stomp/stompjs` + `sockjs-client` trong `NotificationProvider`.
- **Role-based Subscription**: Frontend kiểm tra `user.role` trước khi subscribe:
  - `ROLE_HR` / `ROLE_ADMIN`: Subscribe `/topic/live/applications`, `/topic/live/contracts`.
  - `ROLE_MENTOR`: Subscribe `/topic/live/mentor-tasks`.
  - `ROLE_INTERN`: Chỉ lắng nghe kênh cá nhân `/user/queue/**`, không subscribe các topic nghiệp vụ quản trị.

### 6.2. Vòng đời Socket & Cleanup sạch sẽ
- Quản lý qua `useEffect` theo `isAuthenticated` và `token`:
  - Khi user chủ động bấm "Đăng xuất" hoặc `isAuthenticated: false`: Gọi ngay `stompClient.deactivate()`.
  - Đóng socket vật lý lập tức, dọn dẹp sạch state cache thông báo, ngăn chặn rò rỉ dữ liệu giữa user cũ và user mới khi dùng chung máy.

### 6.3. Xử lý Token Expired & Fallback
- Khi nhận frame `TOKEN_EXPIRED`:
  - Gọi API `refreshToken`.
  - **Nếu thành công**: Cập nhật token trong store, thiết lập kết nối STOMP mới với header mới.
  - **Nếu thất bại** (refresh token đã hết hạn/bị thu hồi): Hủy ngay vòng lặp retry, xóa sạch auth storage, hiển thị thông báo phiên hết hạn và redirect về `/login`.
- **Dự phòng khi mất kết nối WebSocket hoàn toàn**: Rơi về cơ chế **Polling REST nhẹ** (`GET /api/notifications/unread-count` mỗi 60s - 120s) để giữ badge số đếm không bị tê liệt.

### 6.4. UX: Chống Spam Toast & Invalidate Cache đúng phạm vi
- **Toast Throttle & Batching (500ms)**: Nếu nhận dồn dập > 3 thông báo trong vòng 1 giây (ví dụ khi có thao tác cascade), tự động gom thành 1 Toast: *"Bạn có {N} thông báo mới"* kèm nút *"Xem tất cả"*.
- **Granular Query Invalidation**: Khi nhận event từ `/topic/live/{entity}`, chỉ gọi `queryClient.invalidateQueries` đúng phạm vi `['entity', id]`, tuyệt đối không invalidate toàn bộ cache gây bão request.

---

## 7. Nhật Ký Quyết Định (Decision Log)

| Quyết định | Lựa chọn chốt | Lý do & Thay thế đã loại bỏ |
|---|---|---|
| **Vị trí WebSocket Server** | Tách riêng `notification-service` | Cô lập tải bộ nhớ và file descriptors của kết nối socket; không làm chậm các service nghiệp vụ `intern` hay `identity`. |
| **Quyền sở hữu Database** | Database riêng `notification_db` | Tuân thủ triệt để Database-per-Service, chống coupling ngầm qua database. |
| **Message Broker** | Thêm `redis:alpine` Pub/Sub | Siêu nhẹ (~30MB RAM), tốc độ cao, hỗ trợ fan-out mượt mà khi scale nhiều instance. Loại bỏ RabbitMQ/Kafka vì quá nặng cho giai đoạn này. |
| **Phòng chống IDOR** | Verify quyền sở hữu tại `PATCH /read` và kiểm tra role tại STOMP `SUBSCRIBE` | Ngăn chặn việc đọc trộm thông báo của người khác hoặc Intern nghe lén số liệu quản trị của HR. |
| **Bảo mật phiên** | Push tức thời (chính) + Batch Polling (phụ) | Đảm bảo tính real-time tuyệt đối mà vẫn có lớp lưới an toàn khi service restart. |

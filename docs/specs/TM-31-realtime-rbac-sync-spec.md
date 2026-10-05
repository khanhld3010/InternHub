# Đặc Tả Kỹ Thuật: Đồng Bộ Phân Quyền Động Thời Gian Thực (Real-Time RBAC Permissions Synchronization)

**Mã tài liệu:** `TM-31-EXT-REALTIME-RBAC-SYNC`  
**Trạng thái:** `APPROVED / READY FOR IMPLEMENTATION`  
**Tác giả:** Software Architecture Team  
**Ngày phê duyệt:** 2026-10-05  

---

## 1. Bối Cảnh & Mục Tiêu (Context & Objectives)

### 1.1. Vấn đề thực tế
Trong kiến trúc phân tán của InternHub, khi **Quản trị viên (Admin)** thực hiện bật/tắt quyền hạn của một Vai trò (ví dụ: thu hồi quyền `DOCUMENT_REVIEW` của vai trò `HR`), các tài khoản người dùng thuộc vai trò đó đang đăng nhập trực tuyến (Online) vẫn giữ nguyên JWT Access Token và trạng thái giao diện cũ.
- Người dùng vẫn thấy các menu, nút bấm của chức năng bị tước quyền.
- Khi người dùng click thao tác, yêu cầu gửi lên Backend mới bị chặn (403 Forbidden), gây trải nghiệm kém và tồn tại khoảng hở bảo mật dữ liệu trên màn hình (Data Stale Window).

### 1.2. Mục tiêu kỹ thuật
Thiết lập cơ chế **Real-Time Permissions Synchronization** tức thời (độ trễ dưới 500ms):
1. **Phân tách mức độ nghiêm trọng (Severity Separation):**
   - Khóa tài khoản $\rightarrow$ `FORCE_LOGOUT` (Hủy session, ép đăng xuất ngay).
   - Thu hồi/cấp thêm quyền $\rightarrow$ `PERMISSION_UPDATED` (Làm mới ngầm + Chuyển hướng có điều kiện).
2. **Nguyên tắc "Một nguồn sự thật" (Single Source of Truth):** WebSocket chỉ đóng vai trò Trigger kích hoạt. Token và thông tin phân quyền mới chỉ được cập nhật từ API Backend chính thống (`/api/auth/refresh`), tuyệt đối không tự chắp vá (patch) thủ công từ nội dung frame socket.
3. **Bảo mật dứt khoát (Zero-Trust Data Protection):** Nếu người dùng đang đứng trực tiếp tại trang bị thu hồi quyền, chuyển hướng ngay lập tức về Dashboard kèm thông báo rõ ràng về dữ liệu chưa lưu.

---

## 2. Kiến Trúc Luồng Dữ Liệu Toàn Cục (End-to-End Data Flow)

```
┌─────────────────────────────────┐
│ Admin cập nhật Role trên UI     │
└────────────────┬────────────────┘
                 │ PUT /api/system/roles/{id}
                 ▼
┌────────────────────────────────────────────────────────┐
│ identity-and-access-service                            │
│ 1. Cập nhật bảng roles & role_permissions trong DB     │
│ 2. Publish event lên Redis pubsub:security-commands    │
│    {                                                   │
│      "action": "PERMISSION_UPDATED",                   │
│      "role": "HR",                                     │
│      "roleId": 2,                                      │
│      "timestamp": "2026-10-05T00:30:00Z"               │
│    }                                                   │
└────────────────┬───────────────────────────────────────┘
                 │ Redis Pub/Sub (channel: pubsub:security-commands)
                 ▼
┌────────────────────────────────────────────────────────┐
│ notification-service (WebSocket Broker)                │
│ 1. Lắng nghe pubsub:security-commands                  │
│ 2. Duyệt in-memory activeSessions                      │
│ 3. Lọc các session có WebSocketSessionMeta.role == 'HR'│
│ 4. Đẩy STOMP frame riêng tới từng user:                │
│    Topic: /user/queue/security                         │
└────────────────┬───────────────────────────────────────┘
                 │ STOMP over WebSocket
                 ▼
┌────────────────────────────────────────────────────────┐
│ Trình duyệt người dùng HR (Client App)                 │
│ 1. useSecuritySocket lắng nghe /user/queue/security    │
│ 2. Nhận { action: "PERMISSION_UPDATED" }               │
│ 3. Kích hoạt authService.refreshToken()                │
│ 4. Nhận Access Token mới -> Cập nhật RAM (AuthContext) │
│ 5. Đánh giá URL hiện tại (Current Route):              │
│    - NẾU đang ở trang yêu cầu quyền bị tước:           │
│      -> Redirect ngay về /hr/dashboard                 │
│      -> Toast: "Quyền truy cập trang đã bị thu hồi"    │
│    - NẾU ở trang khác:                                 │
│      -> Sidebar tự ẩn menu, nút bấm tự biến mất        │
│      -> Toast nhẹ: "Đặc quyền đã được cập nhật"        │
└────────────────────────────────────────────────────────┘
```

---

## 3. Đặc Tả Chi Tiết Từng Thành Phần

### 3.1. Backend: `identity-and-access-service`
* **Vị trí phát sinh:** `RoleManagementServiceImpl.updateRole(id, request)`
* **Thao tác:** Sau khi `roleRepository.save(role)` thành công:
  ```java
  SecurityCommandEvent event = SecurityCommandEvent.builder()
          .action("PERMISSION_UPDATED")
          .role(role.getName().toUpperCase())
          .roleId(role.getId())
          .timestamp(Instant.now())
          .build();
  redisTemplate.convertAndSend("pubsub:security-commands", objectMapper.writeValueAsString(event));
  ```

### 3.2. Trung Gian: `notification-service`
* **Mở rộng `WebSocketSessionMeta`:**
  Đảm bảo `WebSocketSessionMeta` đã lưu trữ `role` của user khi bắt tay STOMP (trích xuất từ JWT Claims lúc `CONNECT`).
* **Định tuyến tin nhắn trong `RedisMessageSubscriber`:**
  ```java
  if ("PERMISSION_UPDATED".equals(command.getAction())) {
      String targetRole = command.getRole();
      // Duyệt danh sách session online
      for (WebSocketSessionMeta meta : sessionRegistry.getAllSessions().values()) {
          if (targetRole.equalsIgnoreCase(meta.getRole())) {
              messagingTemplate.convertAndSendToUser(
                  meta.getUsername(),
                  "/queue/security",
                  command
              );
          }
      }
  }
  ```
* **Lợi ích kiến trúc:** Không mở thêm topic broadcast `/topic/roles/**`, tránh hoàn toàn lỗ hổng IDOR trên kênh SUBSCRIBE STOMP.

### 3.3. Frontend: `AuthContext` & `useSecuritySocket`
* **Lắng nghe và Điều phối:**
  Khi nhận được payload `PERMISSION_UPDATED`:
  1. **Bước 1 (Silent Refresh):** Gọi `authService.refreshToken()`.
  2. **Bước 2 (Cập nhật State):** Lấy profile và permission codes mới từ token vừa giải mã, cập nhật lại `user.permissions` trong `AuthContext`.
  3. **Bước 3 (Kiểm tra Route Bảo mật dứt khoát):**
     - So khớp route hiện tại với danh sách `ALL_NAV_ITEMS` (hoặc Route Permission Guard).
     - Nếu route hiện tại đòi hỏi quyền mà `hasPermission(requiredPermission)` trả về `false`:
       - Thực hiện `navigate(getFallbackDashboard(role), { replace: true })`.
       - Hiển thị Toast cảnh báo:
         `"Quyền truy cập vào tính năng này của bạn vừa bị thu hồi bởi Quản trị viên. Các dữ liệu chưa lưu trên trang đã bị hủy."`
     - Nếu không vi phạm route hiện tại:
       - Hiển thị Toast thông tin: `"Đặc quyền vai trò của bạn vừa được cập nhật lại."`
       - Sidebar và các component tự động re-render theo reactive state của `hasPermission`.

---

## 4. Bảng Ma Trận Phản Ứng (Action Matrix)

| Loại Tín Hiệu | Mức Độ | Trách Nhiệm Backend | Phản Ứng WebSocket Client |
|---|---|---|---|
| `FORCE_LOGOUT` | Nghiêm trọng | Ghi Redis blacklist, đóng kết nối socket | Xóa token RAM & LocalStorage, đẩy ra `/login`, báo *"Tài khoản đã bị khóa"* |
| `PERMISSION_UPDATED` (Ở trang an toàn) | Nghiệp vụ | Publish event Role qua Redis Pub/Sub | Gọi ngầm `/api/auth/refresh`, đổi RAM, ẩn menu trên Sidebar, Toast nhẹ |
| `PERMISSION_UPDATED` (Đang ở trang bị tước quyền) | Bảo mật dữ liệu | Publish event Role qua Redis Pub/Sub | Gọi ngầm refresh token, **redirect ngay về Dashboard**, Toast cảnh báo mất dữ liệu dở |

---

## 5. Kế Hoạch Kiểm Thử (Acceptance Test Criteria)

1. **Test Case 1 (Admin thu hồi quyền khi HR đang ở trang khác):**
   - Admin gạt tắt `PROGRAM_MANAGE` của HR $\rightarrow$ Bấm Lưu.
   - Trình duyệt HR đang ở Dashboard nhận tín hiệu $\rightarrow$ Menu "Chương trình thực tập" trên Sidebar biến mất trong <500ms, không bị reload trang, không gián đoạn thao tác.
2. **Test Case 2 (Admin thu hồi quyền khi HR đang đứng trong trang):**
   - HR đang mở `/hr/programs` và đang nhập text.
   - Admin tắt `PROGRAM_VIEW` của HR $\rightarrow$ Bấm Lưu.
   - HR lập tức bị redirect về `/hr/dashboard` trong 0ms, Toast cảnh báo quyền hạn xuất hiện rõ ràng.
3. **Test Case 3 (Single Source of Truth):**
   - Giả mạo frame WebSocket gửi tới client $\rightarrow$ Client gọi refresh token nhưng backend trả về quyền cũ $\rightarrow$ Client giữ nguyên quyền cũ, không bị hack quyền trên UI.

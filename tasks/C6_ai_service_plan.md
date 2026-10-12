# KẾ HOẠCH TRIỂN KHAI CHI TIẾT (IMPLEMENTATION PLAN)
## Module C6: Microservice AI Service & Chatbot Trợ Lý Ảo TTS

- **Tham chiếu Spec:** [`docs/specs/C6-AI-SERVICE-AND-INTERN-CHATBOT-SPEC.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/C6-AI-SERVICE-AND-INTERN-CHATBOT-SPEC.md)
- **Mục tiêu:** Xây dựng hoàn chỉnh microservice `ai-service`, cấu hình gateway/docker-compose, màn hình Cấu hình AI (Admin) và Floating Chatbot Widget (Intern).

---

## Giai Đoạn 1: Khởi Tạo Microservice `ai-service` & Cơ Sở Dữ Liệu
1. **Tạo cấu trúc dự án Spring Boot `ai-service`:**
   - Tạo thư mục `ai-service/` với `build.gradle`, `src/main/java/...`, `Dockerfile`.
   - Cấu hình kết nối Eureka (`discovery-server:8761`), Config Client (`8888`), MySQL `internhub_db`.
   - Tạo file cấu hình `config-repo-local/ai-service.yml` trên Config Server.
2. **Database Migration & Entities:**
   - Tạo bảng `ai_system_configs` trong MySQL với giá trị mặc định cho `gemini-1.5-flash`, System Instruction quy chế chuẩn tiếng Việt và danh sách câu hỏi gợi ý.
   - Thêm quyền `AI_CONFIG_MANAGE` vào bảng `permissions` và gán cho role `ADMIN`.
3. **Module Encryption & Provider Integration:**
   - Xây dựng tiện ích mã hóa AES-256 (`AesEncryptionUtil.java`) để mã hóa API Key.
   - Xây dựng `GeminiApiClient.java` (gọi REST API `v1beta/models/{model}:generateContent` của Google Gemini).

---

## Giai Đoạn 2: API Endpoints & Gateway Routing
1. **API Endpoints:**
   - `POST /api/ai/chat`: Nhận tin nhắn + lịch sử, thực hiện Context Injection thông tin TTS, gọi Gemini API, trả lời tin nhắn.
   - `GET /api/ai/prompts`: Trả về Quick Prompts và cấu hình bot cho TTS.
   - `GET /api/ai/admin/config`: Trả về thông tin cấu hình (API Key đã được che `••••`).
   - `PUT /api/ai/admin/config`: Cập nhật cấu hình, mã hóa lưu key.
2. **Cấu hình Gateway & Docker Compose:**
   - Cập nhật `config-repo-local/api-gateway.yml`: Bổ sung route `- id: ai-service, uri: lb://ai-service, predicates: Path=/api/ai/**`.
   - Cập nhật `docker-compose.yml`: Bổ sung container `ai-service` (Port 8086) kèm healthcheck.

---

## Giai Đoạn 3: Giao Diện Người Dùng (Frontend)
1. **Trang Cấu Hình AI cho Admin (`/admin/ai-settings`):**
   - Form chỉnh sửa Provider, Model, API Key (input type password/text toggle), Temperature slider, Max tokens.
   - Textarea lớn chỉnh sửa System Instruction (Nội quy & quy chế công ty).
   - Danh sách thẻ tag Quick Prompts (cho phép thêm/xóa câu hỏi mẫu gợi ý).
   - Nút "Kiểm tra kết nối AI" (Test Connection) để Admin test ngay key vừa nhập có hoạt động không.
2. **Floating Chatbot Widget cho Thực Tập Sinh (`InternChatWidget.tsx`):**
   - Button tròn icon Robot ở góc dưới bên phải màn hình trên tất cả layout Intern.
   - Modal popup trượt lên mượt mà (smooth animation), không ảnh hưởng đến form bên dưới.
   - Header hiển thị "Trợ lý ảo InternHub", nút Thu nhỏ, nút Xóa đoạn chat.
   - Danh sách tin nhắn dạng bong bóng chat đẹp mắt, hỗ trợ Markdown (in đậm, danh sách dấu chấm).
   - Thanh gợi ý Quick Prompts lướt ngang (click vào là gửi câu hỏi ngay).
   - Lưu trữ lịch sử tạm bằng `sessionStorage`.
3. **Menu & Route Permissions:**
   - Thêm route `/admin/ai-settings` được bảo vệ bởi quyền `AI_CONFIG_MANAGE`.
   - Thêm tab "Cấu hình AI" trong menu Admin hoặc cài đặt hệ thống.

---

## Giai Đoạn 4: Kiểm Thử & Nghiệm Thu
1. Kiểm tra gửi tin nhắn từ TTS: Hỏi về quy chế điểm danh, xin nghỉ, phụ cấp.
2. Kiểm tra tính năng từ chối khi hỏi câu không liên quan.
3. Kiểm tra Admin cập nhật API Key và System Instruction.
4. Kiểm tra rate-limit và xử lý lỗi khi mất kết nối mạng / hết quota API.

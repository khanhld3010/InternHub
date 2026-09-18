# 📋 Bộ Quy Chuẩn Viết Đặc Tả (Spec-Driven Development Guidelines)

> **Mục tiêu:** Đưa đặc tả (Spec) làm nguồn tham chiếu duy nhất trước khi sinh code. Ngăn chặn AI Agent suy diễn sai phạm vi, tự ý thêm dependency hoặc phá vỡ kiến trúc hệ thống.

---

## 🧭 1. Triết lý Cốt lõi: Spec → Plan → Tasks → Implement

1. **Spec (Cần đạt điều gì):** Góc nhìn người dùng & nghiệp vụ. Tuyệt đối không chốt sớm thư viện, công nghệ hay framework.
2. **Plan (Làm như thế nào):** Bản đồ kỹ thuật kết nối Spec vào codebase hiện có. Xác định rõ file sửa, luồng dữ liệu, rủi ro và lệnh kiểm thử.
3. **Tasks (Từng bước cụ thể):** Chia nhỏ thành các đầu mục có thể quan sát, kiểm thử độc lập và review trong một lần diff.
4. **Implement & Converge (Thực thi & Đối chiếu):** Sau khi viết code, **bắt buộc đối chiếu từng tiêu chí chấp nhận** với bằng chứng kiểm tra, không được tự ý mở rộng phạm vi.

---

## ⚖️ 2. Phân Loại Mức Độ Thay Đổi (Tài Liệu Tối Thiểu & Cổng Kiểm Soát)

Không biến mọi việc thành thủ tục rườm rà. Áp dụng theo ma trận 4 cấp độ:

| Cấp độ | Loại thay đổi | Tài liệu tối thiểu | Cổng kiểm soát (Gateways) |
| :---: | :--- | :--- | :--- |
| **L1** | Sửa lỗi nhỏ, đổi nhãn, CSS cục bộ, field có sẵn pattern | Prompt rõ mục tiêu, ràng buộc, bước tái hiện | Chạy test hoặc kiểm tra thủ công |
| **L2** | Tính năng mới trong module quen thuộc | `spec.md` ngắn + `tasks.md` | Review Spec và Diff |
| **L3** | Tính năng ảnh hưởng Database, UX, API, Role/Permission | `spec.md` + `plan.md` + `tasks.md` | Cổng làm rõ câu hỏi, checklist, test, review |
| **L4** | Thay đổi kiến trúc, DB Migration, Auth toàn hệ thống | Bộ tài liệu đầy đủ + `rollback-plan.md` | Lead/Mentor phê duyệt trước khi implement |

---

## 📝 3. Cấu Trúc Chi Tiết Của Một `spec.md` Chuẩn

Mỗi tính năng mới (từ mức L2 trở lên) bắt buộc phải có tài liệu đặc tả gồm 5 phần:

### 3.1. Mục tiêu (Objective)
- Trả lời ngắn gọn: Tính năng này giải quyết vấn đề gì cho ai?

### 3.2. Trong phạm vi (In Scope)
- Liệt kê chính xác các hành vi, màn hình, endpoint mà tính năng **sẽ thực hiện**.

### 3.3. Ngoài phạm vi (Out of Scope) - *Cực kỳ quan trọng*
- Nêu rõ những gì **KHÔNG LÀM** trong task này để ngăn AI suy diễn thêm hệ thống đăng nhập, cache, cron job, bảng mới...

### 3.4. Các quy tắc hành vi & Điều kiện biên (Behavior Rules & Edge Cases)
- Hành vi khi input rỗng / sai định dạng?
- Xử lý khi trùng lặp dữ liệu?
- Quyền hạn (Role nào được gọi, Role nào bị 403)?
- Empty state hiển thị ra sao?

### 3.5. Tiêu chí chấp nhận (Acceptance Criteria - AC)
- Các tiêu chí phải **đo lường và quan sát được** (testable):
  - Ví dụ: *"API trả về 201 Created cùng ID mới khi dữ liệu hợp lệ trong dưới 300ms."*
  - Ví dụ: *"Khi email đã tồn tại, trả về HTTP 409 Conflict kèm message tiếng Việt rõ ràng."*

---

## 🔍 4. Quy Trình 5 Bước Cho AI Coding Agent

### Bước 1: Tiếp nhận yêu cầu & Soạn thảo `spec.md`
- Đọc Jira Ticket và ngữ cảnh dự án.
- Soạn thảo bản phác thảo `spec.md` với đầy đủ 5 mục ở phần 3.

### Bước 2: Cổng làm rõ quyết định (Clarification Gate)
- AI **không được tự đoán** các quyết định kiến trúc/nghiệp vụ.
- Phải đặt các câu hỏi trọng tâm ảnh hưởng trực tiếp đến code hoặc trải nghiệm:
  - *"Số điện thoại có bắt buộc định dạng 10 số đầu 0x không?"*
  - *"Khi thêm mới Intern, trạng thái ban đầu mặc định là PENDING hay ACTIVE?"*
  - *"Có cần xóa mềm (soft delete) không hay xóa hẳn?"*
- Nếu chưa có câu trả lời ngay, phải ghi rõ **Giả định tạm thời** và xin xác nhận trước khi code.

### Bước 3: Lập kế hoạch kỹ thuật (`plan.md`)
- Xác định các file cụ thể sẽ tạo/sửa:
  - Entity: Kế thừa `BaseEntity`, quan hệ bảng.
  - DTO: Tách riêng `dto/request/` và `dto/response/`.
  - Repository & Service: Các hàm nghiệp vụ, `@Transactional`.
  - Controller: Endpoint, HTTP Method, `@Valid`.
- Nêu rõ các rủi ro (N+1 query, xung đột port, ảnh hưởng dữ liệu cũ).
- **Yêu cầu phê duyệt Plan** trước khi viết bất kỳ dòng code nào!

### Bước 4: Tách danh sách việc (`tasks.md`)
- Tách thành các task nhỏ độc lập có thể kiểm tra được:
  - [ ] Tạo Migration SQL / Entity `InternProfile`
  - [ ] Tạo Request/Response DTO kèm Validation annotations
  - [ ] Viết Repository và Service logic
  - [ ] Viết REST Controller
  - [ ] Viết Unit Test cho Service & Controller
  - [ ] Chạy `./gradlew compileJava` và kiểm tra API qua Postman/Curl

### Bước 5: Thực thi & Đối chiếu (Implement & Converge)
- Sau khi code xong, AI phải tự động đối chiếu:
  1. Từng Tiêu chí chấp nhận (AC) đã đạt được bằng chứng gì? (Log test, kết quả curl).
  2. Có file nào bị sửa ngoài phạm vi Plan không?
  3. Lệnh build/test nào đã chạy và kết quả ra sao?

---

## 🚫 5. Ba Lỗi Chết Người Cần Tránh (Anti-Patterns)

1. **Spec viết lại tiêu đề ticket mà không có điều kiện biên:**
   - *Sai:* "Người dùng có thể thêm mới thực tập sinh."
   - *Đúng:* "Thêm mới thực tập sinh với mã SV duy nhất, email đúng định dạng @..., số điện thoại 10 số; nếu trùng trả lỗi 409."
2. **Chốt thư viện/công nghệ trước khi hiểu nghiệp vụ:**
   - Đòi cài thêm Redis, MongoDB hay thư viện lạ vào `build.gradle` khi MySQL và Spring Data JPA hiện tại hoàn toàn đáp ứng tốt.
3. **Checklist chỉ được tick sau khi đã code xong:**
   - Biến spec và checklist thành hình thức đối phó. Checklist phải là rào chắn ngăn việc bắt đầu code khi yêu cầu còn mơ hồ.

---

## 💬 6. Prompt Mẫu Chuẩn Cho Lập Trình Viên Khi Giao Việc Cho AI

Khi muốn AI bắt đầu một tính năng mới theo quy chuẩn này, hãy dùng prompt mẫu sau:

```text
Mục tiêu: Triển khai tính năng [Tên tính năng] theo Jira Ticket [Mã-Ticket].
Nguồn tham chiếu: AGENTS.md và .antigravity/rules.md.

Trước khi viết bất kỳ code nào, hãy thực hiện:
1. Đọc các file liên quan trong service hiện tại và tóm tắt luồng hoạt động.
2. Soạn thảo tài liệu spec.md (Mục tiêu, Trong phạm vi, Ngoài phạm vi, Điều kiện biên, Tiêu chí chấp nhận).
3. Đặt các câu hỏi làm rõ nếu có điểm mơ hồ hoặc nêu các giả định kỹ thuật.
4. Lập plan.md và tasks.md chi tiết (danh sách file tạo/sửa, lệnh test).
5. DỪNG LẠI và chờ tôi xác nhận trước khi implement code!

Quy tắc thực thi:
- Không tự ý thêm dependency mới vào build.gradle.
- Không sửa file ngoài phạm vi plan.
- Sau khi code xong, phải chạy .\gradlew compileJava và đối chiếu từng tiêu chí chấp nhận.
```

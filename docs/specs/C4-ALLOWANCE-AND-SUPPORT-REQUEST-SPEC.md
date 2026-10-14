# THIẾT KẾ CHI TIẾT & ĐẶC TẢ KỸ THUẬT: QUẢN LÝ HỖ TRỢ & QUYỀN LỢI (C4)
*Cơ chế: Tự động hóa tính toán phụ cấp có đối soát audit trail, kiểm soát 4 mắt (Maker-Checker), bảo mật tài liệu nhạy cảm, phát sự kiện giao dịch tin cậy và luồng yêu cầu hỗ trợ chuẩn hóa.*

**Trạng thái tài liệu:** ĐÃ ĐỒNG BỘ 100% VÀ XÁC NHẬN CHÍNH THỨC VỚI PRODUCT OWNER (`/interview-me`, `/idea-refine`, `/brain-to-docs`, `/brainstorming`)  
**Ngày cập nhật:** 2026-10-09  
**Module:** `intern-and-program-service` & `reporting-and-integration-service`  
**Liên kết kiến trúc:** Tuân thủ chuẩn phát sự kiện và giao dịch theo [ADR 0002: Meeting Provider and Calendar Sync](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/adr/0002-meeting-provider-and-calendar-sync.md).

---

## 📌 BẢN TUYÊN BỐ Ý ĐỊNH ĐÃ XÁC NHẬN (CONFIRMED STATEMENT OF INTENT)

> Tài liệu này được chuẩn hóa thông qua kỹ thuật phỏng vấn chuyên sâu `/interview-me` và sàng lọc ý tưởng `/idea-refine`, đã được xác nhận **"Yes"** chính thức từ Product Owner.

- **Outcome:** Xây dựng trọn vẹn luồng Phụ cấp hàng tháng (HR tính/chỉnh/chốt/duyệt hàng loạt, TTS tra cứu) và Yêu cầu hỗ trợ giấy tờ (TTS gửi ticket, HR phê duyệt & upload file đính kèm phản hồi qua kênh bảo mật).
- **User:** HR Chuyên viên (tính toán, lập bảng, sửa đổi có lý do), HR Manager/Admin (duyệt chi, mở khóa, xác nhận chuyển tiền), và Thực tập sinh (theo dõi thu nhập minh bạch, nộp yêu cầu xin giấy tờ/thắc mắc).
- **Why now:** Hoàn thành nhiệm vụ C4 (Dòng 25, 26, 27, 28) theo đúng bảng phân rã tính năng master roadmap của dự án.
- **Success:**
  1. Phụ cấp được tính tự động theo công thức tỷ lệ ngày công chuẩn cả tháng; trường hợp TTS vào hoặc kết thúc giữa tháng được tự động prorate chính xác dựa trên lịch làm việc thực tế.
  2. Nguồn dữ liệu ngày công lấy từ `intern_attendance_records` (có check-in và check-out hợp lệ trong khoảng hạn hợp đồng, hoặc đơn giải trình được Mentor duyệt, tối đa 1 công/ngày; nếu DB chỉ có check-in thì tạm chấp nhận 1 lượt là hạn chế đã biết).
  3. Cơ chế đổi hợp đồng giữa tháng: Tách dòng theo hợp đồng `UNIQUE(intern_user_id, period_month, contract_id)`, snapshot mức phụ cấp, gộp hiển thị trên một Payslip duy nhất cho TTS.
  4. HR có quyền sửa trực tiếp ngày công / số tiền; bắt buộc nhập lý do; lưu cả số ngày tự tính và số ngày sau chỉnh; ràng buộc không vượt quá ngày công chuẩn; khi đã chỉnh tay (`is_manually_adjusted = TRUE`) thì cronjob tự động tuyệt đối không ghi đè; lưu lịch sử append-only vào bảng `intern_allowance_audit_logs`.
  5. Vòng đời trạng thái một chiều: `DRAFT` $\rightarrow$ `APPROVED` $\rightarrow$ `PAID`. Kỳ ở `APPROVED` chỉ HR Manager/Admin được mở khóa về `DRAFT` kèm lý do và thông báo TTS; Kỳ ở `PAID` đóng băng tuyệt đối không được xóa, sai sót xử lý bằng khoản bù (truy lĩnh/truy thu) ở kỳ kế tiếp có tham chiếu kỳ gốc và hiển thị thành dòng riêng trong Payslip.
  6. Duyệt hàng loạt (`batch-approve`): Hỗ trợ xử lý duyệt cả kỳ hoặc danh sách chọn lọc, hỗ trợ partial failure (báo cáo rõ các bản ghi thành công vs thất bại kèm nguyên nhân, không rollback toàn bộ).
  7. Bảo mật file giấy tờ nhạy cảm: Lưu trữ ngoài thư mục public, chỉ tải qua endpoint backend kiểm tra JWT và quyền sở hữu (chính chủ TTS hoặc HR), stream file kèm security headers (`Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`), upload kiểm tra whitelist định dạng, dung lượng (tối đa 5MB) và magic bytes. Sẵn sàng tích hợp Presigned URL sau này.
  8. Phát sự kiện đáng tin cậy: Áp dụng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, gán `eventId` định danh duy nhất (Idempotent), retry tối đa 3 lần cho email delivery. Email thông báo chỉ gửi link dẫn về web portal, tuyệt đối không đính kèm file nhạy cảm.
- **Constraint:**
  - Không vượt quá số ngày công chuẩn của tháng khi sửa tay; không sửa khi đã khóa kỳ (`status != 'DRAFT'`).
  - Hạn hợp đồng ràng buộc phạm vi tính ngày nghỉ phép/lễ (`contract.startDate <= leave_date <= contract.endDate`).
  - Quy tắc 4 mắt (Maker-Checker): Người lập bảng nháp khác người duyệt chi.
  - Làm tròn tiền đến 1.000 VNĐ (`ROUND(amount, -3)`). Thuế TNCN & BHXH: Chưa áp dụng cho đối tượng TTS theo quy chế hiện hành.
- **Out of scope (Explicitly excluded in this phase):**
  - Tự động sinh PDF chứng nhận từ HTML template mã nguồn (giai đoạn này HR upload file scan/ký thủ công ngoài).
  - Tích hợp cổng thanh toán trực tiếp ngân hàng (chỉ quản lý số liệu đối soát và xuất phiếu chi).

---

## I. MỤC TIÊU VÀ ĐỐI TƯỢNG NGHIỆP VỤ

Module này giải quyết trọn vẹn 4 User Stories thuộc nhóm **Quản lý hỗ trợ & quyền lợi**:
1. **Dòng 25 (HR):** HR lập bảng và duyệt phụ cấp hàng tháng cho TTS: tự động tính toán dựa trên mức phụ cấp trong hợp đồng nhân tỷ lệ ngày công thực tế (lấy từ dữ liệu check-in/chuyên cần + ngày nghỉ phép/lễ hợp lệ), có quyền điều chỉnh thủ công trực tiếp kèm audit trail trước khi khóa kỳ.
2. **Dòng 26 (Thực tập sinh):** TTS theo dõi lịch sử thu nhập minh bạch theo từng tháng; khi HR bấm Duyệt chi trả, hệ thống tự động bắn thông báo Realtime WebSocket STOMP và gửi Email phiếu chi (Payslip chi tiết) cho TTS.
3. **Dòng 27 (Thực tập sinh):** TTS gửi yêu cầu xin giấy chứng nhận thực tập, thư giới thiệu, xác nhận nhà trường hoặc thắc mắc quyền lợi theo 6 loại danh mục chuẩn hóa. Hỗ trợ cả TTS đang thực tập (`ACTIVE`/`SIGNED`) và TTS đã hoàn thành thực tập (`COMPLETED` cho các loại giấy chứng nhận hoàn thành, thư giới thiệu, xác nhận nhà trường).
4. **Dòng 28 (HR):** HR xem hàng đợi yêu cầu, duyệt/từ chối và phản hồi: HR đính kèm file scan/chứng nhận đã ký đóng dấu gửi lại cho TTS qua kênh lưu trữ bảo mật kèm thông báo đa kênh.

---

## II. NGUYÊN TẮC BẮT BUỘC KHI TÍNH TOÁN TỰ ĐỘNG & QUY TẮC ĐÃ CHỐT

### 1. Công thức tính toán linh hoạt & Làm tròn số tiền
- **Theo tháng (Mặc định):**  
  $$\text{Lương ngày công} = \frac{\text{base\_allowance}}{\text{standard\_working\_days}} \times (\text{actual\_working\_days} + \text{paid\_leave\_days})$$  
  $$\text{actual\_amount} = \text{ROUND}(\text{Lương ngày công} + \text{bonus\_amount} - \text{deduction\_amount} + \text{retro\_adjustment\_amount},\, -3)$$
  - *Ngày công chuẩn (`standard_working_days`):* Luôn tính theo lịch thực tế của tháng (tổng số ngày trong tháng trừ Thứ 7, Chủ Nhật và ngày lễ công ty), **không hardcode cố định 22 ngày**.
  - *Trường hợp vào hoặc kết thúc giữa tháng:* Mẫu số luôn là số ngày công chuẩn của cả tháng; tử số là số ngày làm việc thực tế nằm trong khoảng hợp đồng có hiệu lực.
  - *Giới hạn cận trên:* Tổng số ngày tính phụ cấp không được vượt quá ngày công chuẩn:
    $$\text{actual\_working\_days} + \text{paid\_leave\_days} \le \text{standard\_working\_days}$$
  - *Quy tắc làm tròn:* Làm tròn đến **1.000 VNĐ** (`ROUND(x, -3)`) để chuẩn hóa đối soát ngân hàng.
  - *Thuế TNCN & BHXH:* Ghi rõ **"Chưa áp dụng cho đối tượng thực tập sinh theo quy chế hiện hành của doanh nghiệp"**.
- **Theo ngày (Nếu hợp đồng có cấu hình đơn giá theo ngày):**  
  $$\text{Lương ngày công} = \text{daily\_rate} \times (\text{actual\_working\_days} + \text{paid\_leave\_days})$$
  $$\text{actual\_amount} = \text{ROUND}(\text{Lương ngày công} + \text{bonus\_amount} - \text{deduction\_amount} + \text{retro\_adjustment\_amount},\, -3)$$

### 2. Nguồn dữ liệu ngày công thực tế & Ngày nghỉ phép
- **Ngày check-in hợp lệ (`actual_working_days`):**
  - Nguồn dữ liệu: Quét từ bảng `intern_attendance_records` trong `intern-and-program-service`.
  - Điều kiện hợp lệ: Ngày làm việc nằm trong khoảng hiệu lực của hợp đồng, có đủ `check_in_time` và `check_out_time` hợp lệ (hoặc đơn check-in bổ sung đã được Mentor duyệt `APPROVED`). Mỗi ngày làm việc tính tối đa **1 công**.
  - *Hạn chế đã biết (Known Limitation):* Trường hợp hệ thống cũ hoặc ca làm chỉ ghi nhận 1 lượt check-in thì tạm thời chấp nhận 1 lượt check-in hợp lệ làm 1 ngày công.
- **Ngày nghỉ phép có hưởng phụ cấp (`paid_leave_days`):**
  - Đơn xin nghỉ phép hợp lệ được Mentor/HR duyệt (`InternLeaveRequest` trạng thái `APPROVED` / `TM-28`) và Ngày nghỉ lễ theo lịch công ty.
  - **Ràng buộc hạn hợp đồng:** Ngày nghỉ phép và ngày lễ chỉ được tính nếu nằm trong khoảng thời gian hợp đồng có hiệu lực (`contract.startDate <= leave_date <= contract.endDate`).

### 3. Cơ chế đổi mức phụ cấp giữa tháng (Nhiều hợp đồng)
- Khi TTS ký hợp đồng mới hoặc điều chỉnh mức phụ cấp giữa tháng:
  - Hệ thống tách thành nhiều dòng bản ghi trong cùng tháng, định danh bằng khóa duy nhất `UNIQUE(intern_user_id, period_month, contract_id)` với `contract_id NOT NULL`.
  - Mẫu số mỗi dòng vẫn là `standard_working_days` của toàn bộ tháng.
  - Dữ liệu lưu snapshot mức phụ cấp gốc tại thời điểm tính toán.
  - Trên giao diện HR và trên Payslip gửi TTS, hệ thống tự động **gộp các dòng** của cùng 1 tháng thành 1 phiếu chi duy nhất hiển thị chi tiết từng giai đoạn hợp đồng.

### 4. Dấu vết kiểm toán (Audit Trail) khi điều chỉnh thủ công & Quyền HR
- **Cơ chế kết hợp (Ưu tiên HR):** TTS vẫn có thể tạo đơn check-in bổ sung để hệ thống tự cộng, nhưng HR khi duyệt bảng phụ cấp có toàn quyền sửa trực tiếp số ngày công thực tế, thưởng, phạt hoặc số tiền cuối cùng.
- **Ghi log từng lần sửa (Append-Only Audit Log):** Tuyệt đối không ghi đè mất dấu vết các lần sửa trước. Mỗi lần HR bấm lưu điều chỉnh, hệ thống bắt buộc ghi thêm một bản ghi mới vào bảng `intern_allowance_audit_logs`:
  - Lưu đầy đủ trạng thái trước khi sửa (`previous_state`: ngày công, thưởng, phạt, tổng tiền cũ).
  - Lưu đầy đủ trạng thái sau khi sửa (`new_state`: ngày công, thưởng, phạt, tổng tiền mới).
  - Lý do điều chỉnh (`adjustment_reason`) — Bắt buộc nhập, không được để trống.
  - Người sửa (`adjusted_by_id`, `adjusted_by_name`), thời điểm sửa (`adjusted_at`).
- **Đánh dấu cờ:** `is_manually_adjusted = TRUE` trên bản ghi chính.
- **Quy tắc bảo vệ dữ liệu:** Khi `is_manually_adjusted = TRUE`, các job tính toán tự động định kỳ tuyệt đối **KHÔNG ĐƯỢC TỰ Ý TÍNH LẠI ĐÈ LÊN** số liệu mà HR đã điều chỉnh.
- Chỉ được chỉnh sửa khi kỳ lương ở trạng thái `DRAFT`.

### 5. Vòng đời trạng thái một chiều & Cơ chế Mở khóa kỳ / Điều chỉnh bù (Phương án C)
- **Vòng đời trạng thái một chiều:** `DRAFT` $\rightarrow$ `APPROVED` $\rightarrow$ `PAID`.
  - Cờ `is_locked` được suy trực tiếp từ điều kiện `status != 'DRAFT'`.
  - `payment_date` chỉ được ghi nhận chính xác tại thời điểm chuyển trạng thái sang `PAID`.
- **Kỳ ở trạng thái `APPROVED` (Đã duyệt nhưng chưa chuyển tiền `PAID`):**
  - Cho phép **HR Manager / Admin** mở khóa kỳ (Unlock Period).
  - Bắt buộc nhập lý do mở khóa, lưu log kiểm toán.
  - Kỳ chuyển ngược về trạng thái `DRAFT` để HR chuyên viên điều chỉnh và phải trình duyệt lại từ đầu.
  - Hệ thống tự động gửi thông báo cập nhật cho TTS nếu trước đó đã gửi thông báo duyệt.
- **Kỳ ở trạng thái `PAID` (Đã chi trả thực tế):**
  - **Đóng băng vĩnh viễn, tuyệt đối KHÔNG cho phép mở khóa và KHÔNG cho phép xóa kỳ đã PAID.**
  - Mọi sai sót phát hiện sau thời điểm chi trả được xử lý bằng **khoản điều chỉnh bù (Retroactive Adjustment - truy lĩnh hoặc truy thu, có thể mang giá trị âm)** ở kỳ kế tiếp:
    - Lưu vào trường `retro_adjustment_amount`.
    - Tham chiếu kỳ gốc `retro_ref_period_month` (ví dụ: *"Truy lĩnh phụ cấp tháng 09/2026"*).
    - Hiển thị thành một dòng diễn giải độc lập, minh bạch trên Payslip của tháng kế tiếp.

### 6. Duyệt hàng loạt & Xử lý lỗi một phần (Batch Approve & Partial Failure)
- Cho phép HR Manager duyệt chi hàng loạt theo kỳ tháng hoặc theo danh sách ID.
- Xử lý phân tách từng bản ghi (Item-level error isolation):
  - Bản ghi hợp lệ: Chuyển `APPROVED`, ghi nhận người duyệt.
  - Bản ghi bị lỗi dữ liệu (ví dụ hợp đồng thiếu hiệu lực, số ngày công âm, vượt ngày chuẩn): Ghi nhận mã lỗi cụ thể vào danh sách `errors`.
  - Trả về response chi tiết: `{ totalProcessed: 50, successCount: 48, failedCount: 2, errors: [...] }`.
  - Không rollback toàn bộ các bản ghi hợp lệ khác nếu gặp lỗi đơn lẻ.

### 7. Quy tắc chuẩn hóa Yêu cầu hỗ trợ (Support Requests) & SLA
- **Danh mục 6 loại yêu cầu (`request_type`):**
  1. `INTERNSHIP_CONFIRMATION` (Xác nhận đang thực tập)
  2. `COMPLETION_CERTIFICATE` (Chứng nhận hoàn thành thực tập)
  3. `RECOMMENDATION_LETTER` (Thư giới thiệu)
  4. `UNIVERSITY_CONFIRMATION` (Giấy xác nhận nộp cho trường đại học)
  5. `ALLOWANCE_INQUIRY` (Thắc mắc/khiếu nại về phụ cấp)
  6. `OTHER` (Yêu cầu khác - bắt buộc mô tả chi tiết nội dung)
- **Ràng buộc chống spam:** Mỗi TTS chỉ được phép có tối đa 1 yêu cầu ở trạng thái đang xử lý (`PENDING` hoặc `PROCESSING`) cho cùng một loại `request_type`.
- **Ràng buộc trạng thái TTS (Validation Rule):**
  - `INTERNSHIP_CONFIRMATION`: Dành cho TTS có hợp đồng `ACTIVE` hoặc `SIGNED`.
  - `COMPLETION_CERTIFICATE` & `RECOMMENDATION_LETTER`: Cho phép TTS có trạng thái kết thúc là `COMPLETED` hoặc hợp đồng `COMPLETED`.
  - `UNIVERSITY_CONFIRMATION`: Cho phép cả TTS đang thực tập (`ACTIVE`, `SIGNED`) và TTS đã hoàn thành (`COMPLETED`).
  - **Ngoại lệ HR (Override):** HR có quyền duyệt ngoại lệ nếu thấy hợp lý nhưng bắt buộc nhập lý do ngoại lệ và lưu log.
- **Quyền hủy của TTS:** TTS chỉ được phép HỦY yêu cầu (`status = CANCELLED`) khi ticket còn ở trạng thái `PENDING`. Khi HR đã tiếp nhận (`PROCESSING`), TTS không thể tự hủy.
- **Cam kết thời gian xử lý (SLA):** Thời hạn xử lý tiêu chuẩn là **03 ngày làm việc** kể từ khi tạo ticket. Bảng điều khiển HR hiển thị cảnh báo màu vàng khi quá 48h và màu đỏ khi quá 72h (Overdue SLA).
- **Lịch sử trạng thái ticket:** Lưu bảng `support_request_status_logs` mỗi khi ticket đổi trạng thái (`PENDING` $\rightarrow$ `PROCESSING` $\rightarrow$ `RESOLVED` / `REJECTED` / `CANCELLED`).

### 8. Bảo mật tài liệu nhạy cảm (Secure Document Storage & Streaming)
- Tuyệt đối **KHÔNG lưu trữ file trong thư mục web công khai (public)**.
- Trong Database: Chỉ lưu đường dẫn logic / storage key nội bộ (ví dụ: `support-attachments/2026/10/uuid.pdf`), không lưu public URL.
- **Endpoint tải file bảo mật:**
  - `GET /api/v1/support-requests/{id}/attachment` (File đính kèm của TTS).
  - `GET /api/v1/support-requests/{id}/response-attachment` (File kết quả scan/PDF của HR).
- **Kiểm soát phân quyền (Anti-IDOR):**
  - Chỉ HR (có quyền quản lý) hoặc chính TTS sở hữu ticket (`intern_user_id == currentUserId` từ JWT token) mới được cấp quyền tải. Người dùng khác nhận HTTP `403 Forbidden`.
  - Ghi nhận Audit Log mỗi lượt tải file (`user_id`, `file_id`, `downloaded_at`, `ip_address`).
- **Response Headers an toàn:**
  - `Content-Disposition: attachment; filename="..."` (bắt buộc tải về, không render trực tiếp để tránh stored XSS).
  - `X-Content-Type-Options: nosniff`.
  - `Cache-Control: no-cache, no-store, must-revalidate`.
- **Kiểm tra khi tải lên (Upload Validation):**
  - Whitelist định dạng: Chỉ chấp nhận `.pdf`, `.jpg`, `.jpeg`, `.png`.
  - Kích thước tối đa: **5 MB**.
  - Kiểm tra MIME type thực tế và Magic Bytes ở tầng backend (không chỉ tin tưởng phần mở rộng đuôi file).
- **Thiết kế mở rộng:** Sau này khi chuyển sang Cloud S3 / MinIO, controller chỉ cần chuyển hướng HTTP 302 sang Presigned URL có hạn 15 phút mà không cần thay đổi API contract phía Frontend.

### 9. Phát sự kiện sau Commit & Idempotency (Event Dispatching)
- Tuân thủ thiết kế [ADR 0002](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/adr/0002-meeting-provider-and-calendar-sync.md):
  - Sử dụng Spring `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` để bảo đảm dữ liệu đã ghi xuống database thành công trước khi gửi event.
  - Cấu trúc EventId duy nhất chống trùng lặp (Idempotent): `EVT-ALLOWANCE-{recordId}-{status}-{timestamp}`.
  - Cơ chế retry khi gửi email thất bại: Sử dụng Resilience4j retry tối đa 3 lần với exponential backoff.
  - Nội dung Email: Chỉ chứa tóm tắt thông tin và link an toàn dẫn người dùng đăng nhập vào hệ thống để xem Payslip hoặc tải giấy tờ, **tuyệt đối không đính kèm file nhạy cảm trong email**.

---

## III. THIẾT KẾ MÔ HÌNH DỮ LIỆU (DATABASE SCHEMA)

Cơ sở dữ liệu: `internhub_db` (trong `intern-and-program-service`).

### 1. Bảng `intern_allowance_records` (Phụ cấp hàng tháng)
```sql
CREATE TABLE intern_allowance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_user_id BIGINT NOT NULL,
    intern_profile_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL, -- NOT NULL để hỗ trợ đổi hợp đồng giữa tháng
    period_month VARCHAR(7) NOT NULL, -- Định dạng YYYY-MM (ví dụ: '2026-10')
    
    -- Định mức gốc & Ngày công
    calculation_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY', -- MONTHLY, DAILY
    base_allowance DECIMAL(15, 2) NOT NULL, -- Mức phụ cấp gốc từ hợp đồng (VNĐ) - snapshot
    standard_working_days INT NOT NULL, -- Số ngày công chuẩn của tháng (tính theo lịch thực tế)
    
    -- Số liệu tự tính ban đầu của hệ thống
    auto_calculated_days INT NOT NULL DEFAULT 0,
    auto_calculated_amount DECIMAL(15, 2) NOT NULL,
    
    -- Số liệu thực tế áp dụng (sau khi HR chỉnh sửa hoặc giữ nguyên)
    actual_working_days INT NOT NULL DEFAULT 0, -- Số ngày check-in thực tế
    paid_leave_days INT NOT NULL DEFAULT 0, -- Số ngày nghỉ phép có lương/ngày lễ trong hạn hợp đồng
    bonus_amount DECIMAL(15, 2) NOT NULL DEFAULT 0, -- Thưởng hiệu suất
    deduction_amount DECIMAL(15, 2) NOT NULL DEFAULT 0, -- Khấu trừ phạt
    
    -- Khoản điều chỉnh bù kỳ trước (Truy lĩnh / Truy thu)
    retro_adjustment_amount DECIMAL(15, 2) NOT NULL DEFAULT 0, -- Có thể dương hoặc âm
    retro_ref_period_month VARCHAR(7) NULL, -- Kỳ gốc phát sinh sai sót (ví dụ: '2026-09')
    retro_reason TEXT NULL, -- Diễn giải lý do truy lĩnh/truy thu
    
    -- Tổng tiền thực nhận (đã làm tròn đến 1.000 VNĐ)
    actual_amount DECIMAL(15, 2) NOT NULL,
    
    -- Trạng thái điều chỉnh & Vòng đời trạng thái một chiều
    is_manually_adjusted BOOLEAN NOT NULL DEFAULT FALSE, -- Cờ đánh dấu đã từng có HR can thiệp chỉnh tay
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, APPROVED, PAID
    is_locked BOOLEAN GENERATED ALWAYS AS (status != 'DRAFT') STORED, -- Đóng băng tự động khi duyệt
    payment_date DATE NULL, -- Ngày giải ngân thực tế (chỉ có khi status = PAID)
    notes TEXT NULL,
    
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_allowance_intern (intern_user_id),
    INDEX idx_allowance_period (period_month),
    INDEX idx_allowance_status (status),
    UNIQUE KEY uq_intern_period_contract (intern_user_id, period_month, contract_id)
);
```

### 2. Bảng `intern_allowance_audit_logs` (Nhật ký kiểm toán lưu vết từng lần chỉnh sửa - Append-Only)
```sql
CREATE TABLE intern_allowance_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    allowance_record_id BIGINT NOT NULL, -- FK trỏ tới intern_allowance_records
    
    -- Dấu vết trước khi sửa (Previous State)
    previous_working_days INT NOT NULL,
    previous_paid_leave_days INT NOT NULL,
    previous_bonus_amount DECIMAL(15, 2) NOT NULL,
    previous_deduction_amount DECIMAL(15, 2) NOT NULL,
    previous_amount DECIMAL(15, 2) NOT NULL,
    
    -- Dấu vết sau khi sửa (New State)
    new_working_days INT NOT NULL,
    new_paid_leave_days INT NOT NULL,
    new_bonus_amount DECIMAL(15, 2) NOT NULL,
    new_deduction_amount DECIMAL(15, 2) NOT NULL,
    new_amount DECIMAL(15, 2) NOT NULL,
    
    -- Thông tin kiểm toán bắt buộc
    adjustment_reason TEXT NOT NULL, -- Lý do sửa (BẮT BUỘC)
    adjusted_by_id BIGINT NOT NULL, -- ID của người sửa
    adjusted_by_name VARCHAR(100) NOT NULL, -- Tên người sửa
    adjusted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm sửa
    
    INDEX idx_audit_allowance (allowance_record_id),
    INDEX idx_audit_adjuster (adjusted_by_id),
    CONSTRAINT fk_audit_allowance FOREIGN KEY (allowance_record_id) REFERENCES intern_allowance_records(id) ON DELETE CASCADE
);
```

### 3. Bảng `support_requests` (Yêu cầu hỗ trợ & Giấy tờ)
```sql
CREATE TABLE support_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_user_id BIGINT NOT NULL,
    intern_profile_id BIGINT NOT NULL,
    request_type VARCHAR(50) NOT NULL, -- INTERNSHIP_CONFIRMATION, COMPLETION_CERTIFICATE, RECOMMENDATION_LETTER, UNIVERSITY_CONFIRMATION, ALLOWANCE_INQUIRY, OTHER
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    attachment_storage_key VARCHAR(500) NULL, -- Storage key nội bộ file đính kèm của TTS (không public)
    attachment_filename VARCHAR(255) NULL, -- Tên file gốc người dùng tải lên
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, RESOLVED, REJECTED, CANCELLED
    
    -- Phản hồi từ HR
    hr_response TEXT NULL,
    response_storage_key VARCHAR(500) NULL, -- Storage key nội bộ file kết quả của HR
    response_filename VARCHAR(255) NULL,
    handler_id BIGINT NULL,
    handler_name VARCHAR(100) NULL,
    override_reason TEXT NULL, -- Ghi chú lý do nếu duyệt ngoại lệ cho TTS
    sla_due_date DATETIME NOT NULL, -- Hạn xử lý cam kết (SLA: +3 ngày làm việc)
    resolved_at DATETIME NULL,
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_support_intern (intern_user_id),
    INDEX idx_support_status (status),
    INDEX idx_support_type (request_type),
    INDEX idx_support_sla (sla_due_date)
);
```

### 4. Bảng `support_request_status_logs` (Nhật ký trạng thái xử lý ticket)
```sql
CREATE TABLE support_request_status_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    changed_by_id BIGINT NOT NULL,
    changed_by_name VARCHAR(100) NOT NULL,
    comment TEXT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_request (request_id),
    CONSTRAINT fk_log_support_request FOREIGN KEY (request_id) REFERENCES support_requests(id) ON DELETE CASCADE
);
```

### 5. Bảng `document_access_logs` (Kiểm toán bảo mật truy cập file nhạy cảm)
```sql
CREATE TABLE document_access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    document_type VARCHAR(50) NOT NULL, -- SUPPORT_ATTACHMENT, SUPPORT_RESPONSE, PAYSLIP_EXCEL
    target_id BIGINT NOT NULL, -- request_id hoặc allowance_id
    storage_key VARCHAR(500) NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    accessed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_user (user_id),
    INDEX idx_doc_time (accessed_at)
);
```

---

## IV. MA TRẬN PHÂN QUYỀN & NGUYÊN TẮC KIỂM SOÁT 4 MẮT (MAKER-CHECKER)

Để bảo đảm an toàn kiểm soát tài chính, hệ thống phân định rõ ranh giới giữa người lập (Maker) và người duyệt (Checker):

| Hành động nghiệp vụ | Vai trò cho phép | Nguyên tắc kiểm soát & Ghi chú |
| :--- | :--- | :--- |
| **Tính toán xem trước (Preview)** | `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Quét dữ liệu attendance và hợp đồng, sinh bảng nháp |
| **Lưu bảng tính phụ cấp (Batch Save)** | `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Lưu nháp ở trạng thái `DRAFT` |
| **Chỉnh sửa ngày công, thưởng, phạt** | `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Chỉ sửa khi `status = DRAFT`, bắt buộc nhập lý do, ghi audit log |
| **Phê duyệt kỳ lương (`APPROVED`)** | `HR_MANAGER`, `ADMIN` | **Maker $\neq$ Checker**: Người bấm duyệt không được là người lập/sửa nháp nếu cùng cấp |
| **Duyệt hàng loạt (`batch-approve`)** | `HR_MANAGER`, `ADMIN` | Xử lý từng bản ghi, trả về danh sách thành công và thất bại |
| **Mở khóa kỳ lương (Unlock Period)** | `HR_MANAGER`, `ADMIN` | Chỉ mở khi kỳ ở `APPROVED`, chuyển về `DRAFT`, nhập lý do, thông báo TTS |
| **Xác nhận đã chi trả (`PAID`)** | `HR_MANAGER`, `ADMIN` | Ghi nhận `payment_date`, đóng băng vĩnh viễn, gửi Payslip & WebSocket |
| **Tạo khoản truy lĩnh/truy thu** | `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Tạo cho kỳ tiếp theo, tham chiếu kỳ gốc đã `PAID` |
| **Xuất Excel danh sách phụ cấp** | `HR_MANAGER`, `ACCOUNTANT`, `ADMIN` | Chỉ xuất các bản ghi đã ở trạng thái `APPROVED` hoặc `PAID` |
| **Xem lịch sử phiếu của mình** | `INTERN` | Chỉ xem được bản ghi của chính mình (chống IDOR) |
| **Nộp yêu cầu hỗ trợ (Support Ticket)** | `INTERN` | Kiểm tra điều kiện trạng thái TTS, tối đa 1 pending/loại |
| **Hủy yêu cầu hỗ trợ** | `INTERN` | Chỉ hủy được khi ticket còn ở trạng thái `PENDING` |
| **Xử lý & phản hồi ticket hỗ trợ** | `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Upload file scan đính kèm, nhập lý do nếu override |
| **Tải file giấy tờ nhạy cảm** | `INTERN`, `HR_STAFF`, `HR_MANAGER`, `ADMIN` | Kiểm quyền chính chủ/HR, stream file kèm security headers |

---

## V. THIẾT KẾ HỆ THỐNG RESTful API

### 1. Phân hệ Phụ cấp (`AllowanceController` - `/api/v1/allowances`)
- `POST /api/v1/allowances/calculate-preview?month=YYYY-MM`: 
  - Quét danh sách TTS có hợp đồng `ACTIVE`, lấy `allowanceAmount` trong hợp đồng, đếm số ngày check-in hợp lệ trong tháng để sinh trước bảng tính nháp (Preview).
  - Tự động bỏ qua không ghi đè lên các bản ghi đã có `is_manually_adjusted = TRUE` hoặc `status != 'DRAFT'`.
- `POST /api/v1/allowances/batch-save`:
  - HR lưu bảng tính phụ cấp cho toàn bộ hoặc danh sách TTS trong tháng ở trạng thái `DRAFT`.
- `PUT /api/v1/allowances/{id}/adjust`:
  - HR chỉnh sửa thủ công số ngày công, thưởng, phạt, số tiền hoặc ghi chú cho từng cá nhân.
  - **Ràng buộc:** Bắt buộc nhập lý do (`adjustment_reason`), chặn nếu `status != 'DRAFT'`, chặn nếu ngày công sau sửa vượt ngày chuẩn. Tự động ghi 1 dòng log mới vào bảng `intern_allowance_audit_logs`.
- `PATCH /api/v1/allowances/period/{month}/approve`:
  - **HR Manager/Admin** phê duyệt kỳ lương (`status = APPROVED`).
  - Áp dụng nguyên tắc kiểm soát 4 mắt (Maker-Checker).
- `PATCH /api/v1/allowances/batch-approve`:
  - **HR Manager/Admin** duyệt hàng loạt danh sách phụ cấp.
  - Request: `{ periodMonth: "2026-10", recordIds: [1, 2, 3] }` (nếu `recordIds` rỗng thì duyệt toàn bộ kỳ).
  - Response: `{ totalProcessed: 50, successCount: 48, failedCount: 2, errors: [{ recordId: 10, internName: "Nguyen Van A", reason: "Ngày công vượt chuẩn" }] }`.
- `PATCH /api/v1/allowances/period/{month}/unlock`:
  - **HR Manager/Admin** mở khóa kỳ lương (`status` từ `APPROVED` quay về `DRAFT`). Bắt buộc nhập lý do mở khóa, gửi thông báo cập nhật cho TTS. Chặn tuyệt đối nếu kỳ đã `PAID`.
- `PATCH /api/v1/allowances/{id}/mark-paid`:
  - **HR Manager/Admin** xác nhận chi trả cá nhân hoặc cả kỳ (`status = PAID`).
  - Tự động cập nhật `payment_date = CURRENT_DATE`.
  - **Sự kiện kích hoạt:** Gửi Email thông báo phiếu chi qua `reporting-and-integration-service` + Đẩy WebSocket STOMP tới TTS: *"Phụ cấp tháng 10/2026 của bạn đã được chi trả: 3.500.000 VNĐ."*
- `POST /api/v1/allowances/retro-adjustment`:
  - HR tạo khoản điều chỉnh bù (truy lĩnh / truy thu) cho một TTS vào kỳ kế tiếp, tham chiếu kỳ gốc đã thanh toán và lý do cụ thể.
- `GET /api/v1/allowances/my-history`:
  - TTS xem danh sách phiếu phụ cấp của chính mình qua các tháng (tự động gộp dòng nếu có nhiều hợp đồng trong tháng).
- `GET /api/v1/allowances/intern/{internId}`:
  - HR xem lịch sử phụ cấp của một TTS cụ thể.
- `GET /api/v1/allowances?month=YYYY-MM`:
  - HR tra cứu bảng phụ cấp toàn bộ TTS trong tháng.
- `GET /api/v1/allowances/export?month=YYYY-MM`:
  - Xuất bảng chi trả phụ cấp toàn công ty ra file Excel.
  - **Quyền hạn:** `HR_MANAGER`, `ACCOUNTANT`, `ADMIN`.
  - **Điều kiện:** Chỉ xuất các bản ghi có trạng thái `APPROVED` hoặc `PAID`.
  - **Cấu trúc 14 cột tiêu chuẩn:**
    1. STT | 2. Mã TTS | 3. Họ và Tên | 4. Vị trí thực tập | 5. Kỳ phụ cấp | 6. Mức phụ cấp hợp đồng (VNĐ) | 7. Ngày công chuẩn | 8. Ngày làm thực tế | 9. Ngày nghỉ có phép/lễ | 10. Thưởng (VNĐ) | 11. Khấu trừ (VNĐ) | 12. Truy lĩnh/Truy thu (VNĐ) | 13. Thực nhận (VNĐ) | 14. Trạng thái & Ngày chi trả.
- `GET /api/v1/allowances/{id}/audit-logs`:
  - HR xem toàn bộ lịch sử các lần điều chỉnh tay của phiếu phụ cấp (ai sửa, lúc nào, từ bao nhiêu sang bao nhiêu, lý do gì).

### 2. Phân hệ Yêu cầu Hỗ trợ (`SupportRequestController` - `/api/v1/support-requests`)
- `POST /api/v1/support-requests`:
  - TTS gửi ticket yêu cầu hỗ trợ mới (hỗ trợ multipart upload file đính kèm).
  - **Ràng buộc:** Cho phép TTS có hợp đồng `ACTIVE`/`SIGNED` hoặc đã `COMPLETED` (đối với giấy chứng nhận hoàn thành/thư giới thiệu/xác nhận trường). Kiểm tra chống spam (tối đa 1 yêu cầu pending/loại). Validate định dạng file (.pdf, .jpg, .png, tối đa 5MB, kiểm tra magic bytes).
  - Tự động tính hạn xử lý: `sla_due_date = CURRENT_TIMESTAMP + 3 business days`.
- `GET /api/v1/support-requests/my-requests`:
  - TTS xem danh sách các yêu cầu của bản thân, tiến độ SLA và trạng thái xử lý.
- `GET /api/v1/support-requests`:
  - HR xem danh sách toàn bộ ticket cần xử lý (Lọc: `status`, `requestType`, `isOverdueSla`, tìm kiếm theo tên TTS).
- `PATCH /api/v1/support-requests/{id}/cancel`:
  - TTS chủ động hủy yêu cầu. Chỉ hợp lệ khi `status = PENDING`.
- `PATCH /api/v1/support-requests/{id}/process`:
  - HR tiếp nhận xử lý (`status = PROCESSING`). Ghi nhận handler và cập nhật status log.
- `POST /api/v1/support-requests/{id}/generate-certificate`:
  - HR bấm nút tự động sinh Giấy Chứng Nhận / Xác Nhận Thực Tập dạng PDF (tích hợp OpenPDF / Flying Saucer nhúng logo và mộc điện tử - phần mở rộng giai đoạn sau).
- `PATCH /api/v1/support-requests/{id}/respond`:
  - HR duyệt (`RESOLVED`) hoặc từ chối (`REJECTED`), upload file kết quả scan/đã ký thủ công, nhập lý do ngoại lệ (`override_reason` nếu có).
  - **Sự kiện kích hoạt:** Bắn thông báo Realtime WebSocket + Email phản hồi cho TTS (Email chỉ dẫn link xem, không đính kèm file).
- `GET /api/v1/support-requests/{id}/attachment`:
  - Tải file đính kèm do TTS gửi lên. Kiểm quyền: Chính chủ TTS hoặc HR. Stream file an toàn.
- `GET /api/v1/support-requests/{id}/response-attachment`:
  - Tải file kết quả do HR cấp phát. Kiểm quyền: Chính chủ TTS hoặc HR. Stream file an toàn.
- `GET /api/v1/support-requests/{id}/status-logs`:
  - Xem dòng thời gian xử lý ticket (ai đổi trạng thái, lúc nào, ghi chú gì).

---

## VI. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS - NFR)

1. **Hiệu năng (Performance):**
   - Tác vụ tính toán xem trước phụ cấp (`calculate-preview`) cho **500 thực tập sinh** phải hoàn tất trong vòng $\le \mathbf{2.0\text{ giây}}$.
   - Bắt buộc tối ưu câu lệnh Bulk Query / Batch Fetching ngày công từ `intern_attendance_records` và đơn nghỉ phép từ `intern_leave_requests`, triệt để loại bỏ vấn đề $N+1$ query.
2. **Múi giờ chuẩn hóa (Timezone Consistency):**
   - Toàn bộ thời gian tính toán ngày công, thời điểm check-in/out, hạn SLA và mốc khóa kỳ đều được neo chuẩn theo múi giờ Việt Nam: `Asia/Ho_Chi_Minh` (UTC+7).
3. **An toàn bảo mật (Security & Privacy):**
   - Áp dụng kiểm soát IDOR nghiêm ngặt: Mọi truy vấn từ phía Intern đều ép lọc theo `intern_user_id` trích xuất trực tiếp từ JWT Principal của Security Context.
   - Các file giấy tờ nhạy cảm được bảo vệ tuyệt đối: không có directory listing, không có static file mapping, kiểm tra magic bytes chống upload webshell/malware.
4. **Tính nhất quán dữ liệu & Idempotency:**
   - Việc phát email và WebSocket STOMP bắt buộc chạy sau khi Database đã commit xong giao dịch (`TransactionPhase.AFTER_COMMIT`).
   - Consumer nhận sự kiện phải xử lý theo `eventId` để chống duplicate notification khi mạng chập chờn.

---

## VII. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA)

### AC 1: Tính phụ cấp tự động & Prorate giữa tháng
- **Given:** TTS A có mức phụ cấp hợp đồng 4.400.000 VNĐ/tháng, tháng 10/2026 có 22 ngày công chuẩn (trừ T7, CN).
- **When:** TTS A onboarding ngày 15/10/2026 và có 10 ngày check-in hợp lệ trong khoảng hiệu lực của hợp đồng (không có ngày phép, không thưởng/phạt).
- **Then:** Hệ thống tự động tính lương ngày công = $(4.400.000 / 22) \times 10 = 2.000.000\text{ VNĐ}$. Làm tròn đến 1.000 VNĐ.

### AC 2: HR điều chỉnh tay & Audit Trail
- **Given:** Phiếu phụ cấp tháng 10/2026 của TTS B đang ở trạng thái `DRAFT` với số tiền tự tính 3.000.000 VNĐ.
- **When:** HR chuyên viên sửa `actual_working_days` từ 15 lên 17 ngày (tổng tiền thành 3.400.000 VNĐ) và nhập lý do *"Xác nhận check-in bù do lỗi máy quét vân tay"*.
- **Then:** Hệ thống cập nhật số tiền mới, gắn cờ `is_manually_adjusted = TRUE`, thêm 1 dòng log mới vào `intern_allowance_audit_logs`. Khi cronjob tự động chạy lại vào ban đêm, bản ghi này không bị ghi đè.

### AC 3: Kiểm soát 4 mắt (Maker-Checker) & Khóa kỳ
- **Given:** Bảng phụ cấp tháng 10/2026 đã được HR Staff lập và lưu nháp.
- **When:** HR Staff tự bấm nút Approve kỳ lương.
- **Then:** Hệ thống từ chối (HTTP 403) vì chỉ `HR_MANAGER` hoặc `ADMIN` mới có quyền phê duyệt.
- **When:** HR Manager bấm Approve kỳ lương.
- **Then:** Trạng thái chuyển thành `APPROVED`, hệ thống khóa không cho phép sửa đổi số tiền trừ khi HR Manager thực hiện thao tác mở khóa (Unlock Period).

### AC 4: Duyệt chi trả `PAID` & Xử lý sai sót bằng truy lĩnh kỳ sau
- **Given:** Kỳ lương tháng 09/2026 đã chuyển trạng thái `PAID`.
- **When:** Người dùng cố gắng gọi API sửa đổi hoặc mở khóa kỳ tháng 09/2026.
- **Then:** Hệ thống từ chối tuyệt đối.
- **When:** HR phát hiện tháng 09/2026 thiếu 1 ngày công của TTS C (tương đương 200.000 VNĐ) và gọi API tạo `retro-adjustment` vào kỳ tháng 10/2026.
- **Then:** Kỳ tháng 10/2026 ghi nhận `retro_adjustment_amount = +200.000 VNĐ`, `retro_ref_period_month = '2026-09'`. Payslip tháng 10 hiển thị riêng dòng *"Truy lĩnh phụ cấp tháng 09/2026: +200.000 VNĐ"*.

### AC 5: Ticket hỗ trợ & Tải file bảo mật
- **Given:** TTS D đã hoàn thành thực tập (hợp đồng `COMPLETED`).
- **When:** TTS D nộp ticket loại `COMPLETION_CERTIFICATE` kèm file xác nhận điểm.
- **Then:** Hệ thống chấp nhận nộp thành công và gán SLA +3 ngày làm việc. Nếu TTS D cố tình nộp thêm 1 ticket cùng loại khi ticket cũ đang `PENDING`, hệ thống sẽ chặn báo lỗi trùng lặp.
- **When:** TTS khác E (hoặc người dùng vãng lai không có token) cố gắng truy cập link `/api/v1/support-requests/{id}/attachment` của TTS D.
- **Then:** Hệ thống trả về HTTP 403 Forbidden.

---

## VIII. DANH SÁCH TEST CASE CHÍNH (TEST MATRIX)

| Mã Test | Mô tả Test Case | Dữ liệu đầu vào | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-ALW-01** | Tính phụ cấp chuẩn đủ tháng | Hợp đồng 5.000.000đ, 22/22 công | Thực nhận 5.000.000đ, status `DRAFT` |
| **TC-ALW-02** | Prorate hợp đồng bắt đầu giữa tháng | Bắt đầu ngày 15, đi làm 10 ngày, công chuẩn 22 | Phụ cấp = $5.000.000 \times (10/22)$, làm tròn đến 1.000đ |
| **TC-ALW-03** | Đổi mức phụ cấp giữa tháng | Hợp đồng 1: 3tr (10 ngày), Hợp đồng 2: 5tr (12 ngày) | Tách 2 dòng DB `UNIQUE`, gộp hiển thị trên 1 Payslip |
| **TC-ALW-04** | Ngày nghỉ ngoài hạn hợp đồng | Nghỉ phép ngày 02 nhưng hợp đồng bắt đầu ngày 05 | Không cộng ngày nghỉ ngày 02 vào `paid_leave_days` |
| **TC-ALW-05** | HR sửa tay thiếu lý do | `adjustment_reason = ""` | Chặn HTTP 400 Bad Request |
| **TC-ALW-06** | HR sửa tay ngày công vượt công chuẩn | Sửa thành 25 ngày trong khi tháng có 22 ngày | Chặn HTTP 400, báo lỗi ngày công không hợp lệ |
| **TC-ALW-07** | Cronjob chạy lại bản ghi đã sửa tay | Bản ghi có `is_manually_adjusted = TRUE` | Số liệu giữ nguyên, không bị cron ghi đè |
| **TC-ALW-08** | Maker-Checker duyệt kỳ lương | HR Staff gọi `/period/{month}/approve` | Chặn HTTP 403 Forbidden |
| **TC-ALW-09** | Batch Approve có 1 bản ghi lỗi | Danh sách 10 TTS, 1 TTS có dữ liệu âm | 9 TTS thành công `APPROVED`, 1 TTS báo lỗi trong list `errors` |
| **TC-ALW-10** | Mở khóa kỳ `APPROVED` | HR Manager gọi `/unlock` kèm lý do | Kỳ chuyển về `DRAFT`, ghi log kiểm toán, bắn notify |
| **TC-ALW-11** | Cố tình mở khóa kỳ `PAID` | Kỳ đã `status = PAID` | Chặn tuyệt đối HTTP 400/403 |
| **TC-ALW-12** | Truy lĩnh bù kỳ sau | `retro_adjustment_amount = -150.000` | Kỳ mới trừ 150.000đ, hiển thị dòng truy thu trên Payslip |
| **TC-ALW-13** | Xuất Excel khi kỳ chưa duyệt | Xuất kỳ đang ở `DRAFT` | Chỉ xuất danh sách trống hoặc chặn báo lỗi |
| **TC-SUP-01** | TTS hợp đồng `COMPLETED` xin chứng nhận | Loại `COMPLETION_CERTIFICATE` | Nộp thành công, gán SLA +3 ngày |
| **TC-SUP-02** | TTS nộp trùng loại ticket đang xử lý | Đang có 1 ticket `PENDING`, nộp tiếp ticket cùng loại | Chặn HTTP 400 Conflict |
| **TC-SUP-03** | TTS tự hủy ticket khi `PROCESSING` | Ticket đã được HR tiếp nhận | Chặn HTTP 400, chỉ cho hủy khi còn `PENDING` |
| **TC-SUP-04** | Upload file sai định dạng hoặc quá 5MB | File `.exe` hoặc file dung lượng 6MB | Chặn HTTP 400 Bad Request ngay tại validation |
| **TC-SUP-05** | IDOR Attack tải file tài liệu | User B cố tải file attachment của User A | Chặn HTTP 403 Forbidden, ghi log bảo mật |

---

## IX. KẾ HOẠCH TRIỂN KHAI THEO LÁT CẮT MỎNG (INCREMENTAL SLICES)

- **Slice 1: Backend Domain Models & Repositories:**  
  Tạo enum (`AllowanceStatus`: DRAFT, APPROVED, PAID; `SupportRequestType`), Entity `InternAllowanceRecord`, `InternAllowanceAuditLog`, `SupportRequest`, `SupportRequestStatusLog`, `DocumentAccessLog` và các Repository với custom query.
- **Slice 2: DTOs, Service Logic & Security:**  
  Cài đặt logic tính toán công chuẩn/công thực tế từ attendance, cơ chế kiểm tra prorate theo hạn hợp đồng, tách/gộp hợp đồng giữa tháng, quy tắc làm tròn 1.000đ, audit log append-only khi HR điều chỉnh, cơ chế Maker-Checker và mở khóa kỳ / truy lĩnh bù, validate điều kiện nộp ticket hỗ trợ và gửi notification STOMP/Mail qua `@TransactionalEventListener(AFTER_COMMIT)`.
- **Slice 3: REST Controllers, Secure File Streaming & Route Gateway:**  
  Xây dựng endpoint chuẩn REST, batch-approve với partial failure, stream file an toàn với security headers, export Excel, phân quyền chi tiết HR Staff vs HR Manager vs Intern.
- **Slice 4: Frontend UI Components:**  
  Giao diện HR quản lý bảng phụ cấp (tính nháp, sửa có lý do, phê duyệt Maker-Checker, mở khóa, batch approve, export Excel) & bàn làm việc hỗ trợ ticket (cảnh báo SLA, tải file bảo mật); Giao diện TTS xem lịch sử thu nhập (gộp phiếu, hiển thị dòng truy lĩnh) & nộp/theo dõi ticket.
- **Slice 5: Verification & Docker Deployment:**  
  Kiểm thử end-to-end theo đúng Acceptance Criteria & Test Matrix; build JAR và cập nhật Docker container.

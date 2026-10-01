# ĐẶC TẢ KỸ THUẬT NÂNG CẤP PHÂN HỆ QUẢN LÝ HỢP ĐỒNG THỰC TẬP (INTERN CONTRACT ENHANCEMENT)

- **Nhánh phát triển:** `feature/contract-enhancement` (Backend & Frontend)
- **Ngày lập:** 30/09/2026
- **Trạng thái:** Chờ User phê duyệt (RFC)

---

## 1. TỔNG QUAN YÊU CẦU & BỐI CẢNH (OVERVIEW)

Phân hệ quản lý Hợp đồng hiện tại đang gặp một số hạn chế về mặt kiến trúc và vận hành:
1. **Lưu trữ:** Đang upload tệp qua Multipart form trực tiếp vào local disk của backend (`intern-and-program-service`), chưa tận dụng kiến trúc `file-service` & `adobe/s3mock:9090` (nguy cơ tốn băng thông backend, lỗi 403 Forbidden khi `window.open` thiếu Bearer Token).
2. **Giao diện HR:** HR chỉ xem được hợp đồng lẻ tẻ trong popup của từng thực tập sinh; thiếu màn hình trung tâm quản lý toàn bộ hợp đồng trong công ty.
3. **Tự động hóa thông báo:** Chưa bắn email mời ký khi tải hợp đồng lên, chưa có phản hồi tự động khi TTS xác nhận/từ chối hoặc phản hồi thắc mắc về hợp đồng.
4. **Cơ chế gia hạn:** Chưa có luồng nghiệp vụ chuẩn hóa khi gia hạn thêm 1-3 tháng thực tập.
5. **Ràng buộc pháp lý:** Hợp đồng là tài liệu có tính pháp lý cao, không thể cho phép hard-delete tùy tiện (kể cả bởi Admin).

---

## 2. NGUYÊN TẮC THIẾT KẾ VÀ QUYẾT ĐỊNH ĐÃ THỐNG NHẤT

### 2.1. Kiến trúc Lưu trữ Direct-to-S3 Pre-signed URL
- **Upload Flow (3 bước chuẩn):**
  1. Frontend gọi `POST /api/contracts/upload-url` (hoặc qua `intern-and-program-service` kết nối `file-service`) để lấy `presignedUrl` và `tempKey`.
  2. Frontend thực hiện `PUT` tệp PDF trực tiếp lên S3/MinIO (`internhub-s3-storage:9090` / bucket `internhub-documents`).
  3. Frontend gọi `POST /api/contracts/confirm-upload` gửi `tempKey`, thông tin hợp đồng (mã HĐ, thời hạn, trợ cấp, role...) $\rightarrow$ Backend di chuyển tệp vào vị trí chính thức `contracts/{internCode}/{contractCode}.pdf` và lưu bản ghi vào MySQL.
- **View / Download Flow:**
  - Tuyệt đối không dùng URL tĩnh của backend cần Header Authorization.
  - Endpoint `GET /api/contracts/{id}/view-url` sẽ sinh Pre-signed View URL (hạn dùng 15-30 phút) để frontend mở an toàn bằng `window.open` hoặc `iframe` mà không bị 403 Forbidden.

### 2.2. Cơ chế Gia hạn Hợp đồng (Contract Extension Model - Phương án A)
- **Liên kết phả hệ (Parent-Child Tracking):**
  - Thực thể `InternContract` bổ sung cột `parent_contract_id` (tự tham chiếu tới Hợp đồng gốc).
  - Bổ sung loại hợp đồng `contract_type`: `OFFICIAL_INTERNSHIP` (Hợp đồng thực tập ban đầu) và `EXTENSION_APPENDIX` (Phụ lục gia hạn).
  - Khi Hợp đồng gia hạn mới được TTS xác nhận (ACTIVE), Hợp đồng cũ sẽ được tự động chuyển trạng thái thành `SUPERSEDED` (Đã được thay thế / gia hạn).
  - `InternProfile` sẽ được tự động đồng bộ ngày kết thúc thực tập (`endDate`) theo ngày kết thúc mới nhất của hợp đồng gia hạn.

### 2.3. Luồng "Có thắc mắc, liên hệ HR" & Phản hồi Hợp đồng
- Bổ sung trạng thái `PENDING_INTERN_FEEDBACK` (TTS gửi thắc mắc) vào vòng đời hợp đồng.
- Bổ sung trường `feedback_notes` và `feedback_at` trong `InternContract`.
- Khi TTS gửi thắc mắc:
  - Trạng thái hợp đồng chuyển thành `PENDING_INTERN_FEEDBACK`.
  - Hệ thống tự động gửi Email kèm nội dung chi tiết thắc mắc tới HR phụ trách.
  - HR có thể điều chỉnh lại hợp đồng hoặc tải bản scan rõ nét hơn để thay thế.

### 2.4. Màn hình Contract Management Hub dành cho HR (`/hr/contracts`)
- **Tổ chức giao diện:**
  - Tab lọc trạng thái: `Tất cả` | `Chờ ký (PENDING_SIGNATURE)` | `Đang hiệu lực (ACTIVE)` | `Sắp hết hạn (EXPIRING_SOON)` | `Lịch sử (EXPIRED / TERMINATED / SUPERSEDED)`.
  - Thanh tìm kiếm: Theo Số/Mã hợp đồng, Tên thực tập sinh, Mã thực tập sinh (`internCode`).
  - Badge cảnh báo thời hạn:
    - 🟡 **Vàng (Warning):** Còn $\le$ 30 ngày.
    - 🔴 **Đỏ (Urgent):** Còn $\le$ 15 ngày.
  - Hành động nhanh: Xem trước (Preview PDF), Gửi nhắc nhở ký qua email (Send Reminder), Gia hạn hợp đồng (Tạo phụ lục), Chấm dứt trước hạn (Terminate).

### 2.5. Cơ chế Bảo vệ Pháp lý (Non-Deletable & Audit Trail)
- **Quy tắc bất biến:** Không cung cấp API Hard-Delete hợp đồng cho bất kỳ role nào (kể cả ADMIN).
- Hợp đồng chỉ có thể chuyển sang:
  - `TERMINATED`: Bị chấm dứt trước thời hạn (yêu cầu HR/Admin nhập lý do chấm dứt).
  - `SUPERSEDED`: Được kế thừa bởi hợp đồng gia hạn mới.
  - `ARCHIVED`: Đưa vào kho lưu trữ hồ sơ lịch sử.
- Toàn bộ thao tác đều được ghi lại trong `Audit Trail` (Người tạo, ngày ký, IP/User-Agent xác nhận, ngày hủy, lý do).

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU & SCHEMA

### Bảng `intern_contracts` (Cập nhật)
```sql
ALTER TABLE intern_contracts 
ADD COLUMN parent_contract_id BIGINT NULL,
ADD COLUMN contract_type VARCHAR(50) DEFAULT 'OFFICIAL_INTERNSHIP',
ADD COLUMN feedback_notes TEXT NULL,
ADD COLUMN feedback_at DATETIME NULL,
ADD COLUMN termination_reason TEXT NULL,
ADD COLUMN terminated_at DATETIME NULL,
ADD COLUMN terminated_by VARCHAR(100) NULL,
ADD CONSTRAINT fk_contract_parent FOREIGN KEY (parent_contract_id) REFERENCES intern_contracts(id);
```

### Các trạng thái hợp đồng (`ContractStatus` Enum)
1. `DRAFT`: HR đang soạn thảo, chưa phát hành.
2. `PENDING_SIGNATURE`: Đã phát hành, đang chờ TTS ký/xác nhận.
3. `PENDING_INTERN_FEEDBACK`: TTS phản hồi thắc mắc (bản scan mờ, sai điều khoản).
4. `ACTIVE`: Hợp đồng đang có hiệu lực.
5. `EXPIRING_SOON`: (Computed / Filtered) Còn $\le$ 30 ngày trước ngày `end_date`.
6. `EXPIRED`: Đã hết thời hạn thực tập.
7. `SUPERSEDED`: Đã được gia hạn / thay thế bởi hợp đồng tiếp nối.
8. `TERMINATED`: Chấm dứt trước hạn.
9. `REJECTED`: TTS từ chối ký.

---

## 4. KẾ HOẠCH TRIỂN KHAI CHI TIẾT (IMPLEMENTATION ROADMAP)

### Giai đoạn 1: Backend Architecture & Direct-to-S3
- [ ] Chuyển đổi `InternContractController` & `InternContractService` sang quy trình Direct-to-S3 thông qua `file-service`.
- [ ] Bổ sung endpoint `POST /upload-url`, `POST /confirm-upload`, `GET /{id}/view-url`.
- [ ] Cập nhật Entity `InternContract` và Migration database.
- [ ] Bổ sung các service: Gia hạn hợp đồng (`extendContract`), Phản hồi thắc mắc (`submitFeedback`), Chấm dứt hợp đồng (`terminateContract`).

### Giai đoạn 2: Tự động hóa Thông báo & Email Notification
- [ ] Tích hợp gửi email trong `notification-service`:
  - Email gửi TTS: Mời ký hợp đồng kèm link trực tiếp.
  - Email gửi HR: Thông báo TTS đã xác nhận / từ chối / gửi thắc mắc.
  - Email nhắc nhở (Contract Reminder) cho các hợp đồng `PENDING_SIGNATURE` quá hạn.

### Giai đoạn 3: Frontend - Contract Management Hub cho HR
- [ ] Tạo Route `/hr/contracts` và trang `ContractHubPage`.
- [ ] Xây dựng bảng quản lý danh sách hợp đồng với Tab lọc thông minh, Badge cảnh báo 15/30 ngày.
- [ ] Xây dựng Modal tạo hợp đồng gia hạn (Contract Extension Modal).
- [ ] Tích hợp trình xem trước tài liệu PDF trực tiếp bằng Presigned URL.

### Giai đoạn 4: Frontend - Nâng cấp trải nghiệm TTS
- [ ] Cập nhật giao diện `InternContractSection`:
  - Thêm nút "Có thắc mắc, liên hệ HR" $\rightarrow$ Modal ghi chú phản hồi.
  - Hiển thị trạng thái phản hồi `PENDING_INTERN_FEEDBACK` rõ ràng, tránh gây hoang mang cho TTS.
- [ ] Tích hợp tải và mở hợp đồng an toàn qua Presigned View URL.

### Giai đoạn 5: Testing & Kiểm định toàn diện
- [ ] Test end-to-end chu trình ký, phản hồi thắc mắc, gia hạn và kiểm tra tệp trên `internhub-s3-storage`.
- [ ] Kiểm thử quyền và đảm bảo không có lỗ hổng xóa dữ liệu trái phép.

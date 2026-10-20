# 🎯 KỊCH BẢN KIỂM THỬ THỰC HÀNH LUỒNG CHÍNH INTERNHUB (STEP-BY-STEP USER TEST SCRIPT)
## Hướng Dẫn Từng Thao Tác Trực Tiếp Trên Trình Duyệt Dành Cho Người Dùng / QA / Đánh Giá Dự Án

> **Mã tài liệu:** `USER-MANUAL-E2E-TEST-GUIDE-2026`  
> **Địa chỉ Frontend:** [http://localhost:5173](http://localhost:5173)  
> **Địa chỉ API Gateway:** `http://localhost:8080`  
> **Mục tiêu:** Giúp bạn tự mình kiểm thử hoặc trình diễn (demo) toàn bộ chuỗi nghiệp vụ thực tập sinh khép kín chỉ trong 10 - 15 phút.

---

## 📌 CHUẨN BỊ MÔI TRƯỜNG & DANH SÁCH TÀI KHOẢN

### Mẹo quan trọng nhất để kiểm thử mượt mà:
Mở **2 cửa sổ trình duyệt song song (Side-by-Side)** trên màn hình:
* **Cửa sổ 1 (Chế độ thường):** Dùng để đăng nhập tài khoản **HR** (`hr`) và **Mentor** (`mentor`).
* **Cửa sổ 2 (Chế độ ẩn danh Incognito - Ctrl+Shift+N):** Dùng để đăng nhập tài khoản **Intern** (`hoadang` hoặc ứng viên mới tạo).
*(Cách này giúp 2 tài khoản không bị ghi đè Cookie của nhau và bạn có thể quan sát thông báo / cập nhật trạng thái thời gian thực giữa 2 bên ngay lập tức).*

### Danh sách tài khoản kiểm thử đã cấu hình sẵn dữ liệu:
| Vai trò | Tên đăng nhập | Mật khẩu | Họ và tên | Trạng thái dữ liệu |
| :--- | :---: | :---: | :--- | :--- |
| 👔 **HR** | `hr` | `123456` | Trần Thị Bích | Quản lý tuyển dụng & đợt thực tập |
| 🧑‍🏫 **Mentor** | `mentor` | `123456` | Lê Hoàng Nam | Người hướng dẫn trực tiếp |
| 🎓 **Intern Đang Thực Tập** | `hoadang` | `123456` | Đặng Thị Hoa | Mã `INT-202609-0003` (Đang thực tập `INTERNING`, có đầy đủ dữ liệu chấm công, task, báo cáo) |
| 🎓 **Intern Mẫu Mới** | `intern` | `123456` | Phạm Đức Minh | Dùng để thử nghiệm nộp đơn / ký hợp đồng mới |
| 🛡️ **Admin** | `admin` | `123456` | Nguyễn Văn An | Quản trị toàn quyền, xem Audit Logs, Backup |

---

## 🎬 8 BƯỚC KIỂM THỬ LUỒNG CHÍNH (CLICK-BY-CLICK)

---

### BƯỚC 1: HR THIẾT LẬP CHƯƠNG TRÌNH THỰC TẬP MỚI
* **Trình duyệt:** Cửa sổ 1 (Chế độ thường).
* **Đăng nhập:** Truy cập [http://localhost:5173](http://localhost:5173) $\rightarrow$ Bấm **"Đăng nhập"** $\rightarrow$ Nhập `hr` / `123456`.
* **Thao tác:**
  1. Sau khi vào Dashboard HR, bấm vào menu **"Chương trình"** (`/hr/programs`) ở Sidebar bên trái.
  2. Bấm nút **"+ Tạo chương trình mới"**.
  3. Nhập các thông tin:
     * **Tên chương trình:** `Chương trình Thực tập Kỹ sư AI & Full-Stack 2026`
     * **Phòng ban:** Chọn `Trung tâm Phát triển Phần mềm` (hoặc phòng ban bất kỳ).
     * **Ngày bắt đầu / kết thúc:** Chọn ngày hôm nay đến 3 tháng sau.
     * **Chỉ tiêu tiếp nhận:** `15`
  4. Bấm **"Lưu & Xuất bản"**.
* **Kết quả kỳ vọng:**
  * ✅ Toast màu xanh: *"Tạo chương trình thành công"*.
  * ✅ Chương trình mới xuất hiện ở đầu bảng với huy hiệu màu xanh `ĐANG MỞ` (`OPEN`).

---

### BƯỚC 2: TUYỂN SINH & SÀNG LỌC ỨNG VIÊN
* **Thao tác 2.1 - HR Import Excel hàng loạt (Cách nhanh nhất để có data):**
  1. Tại danh sách chương trình, nhấp vào chương trình vừa tạo để vào **Program Workspace** (`/hr/programs/workspace`).
  2. Tại tab **"Ứng viên & Tuyển sinh"**, bấm nút **"Import Excel Thực tập sinh"**.
  3. Tải file Excel mẫu hoặc chọn file mẫu danh sách sinh viên có sẵn.
  4. Bấm **"Xác nhận Import"** $\rightarrow$ Danh sách sinh viên xuất hiện ngay trên bảng.
* **Thao tác 2.2 - HR Sử dụng AI Screening (TM-103) & Phê duyệt đơn:**
  1. Chọn một ứng viên đang ở trạng thái `CHỜ DUYỆT` (`PENDING`).
  2. Bấm nút **"AI Screening"** $\rightarrow$ AI Service phân tích nội dung CV và hiển thị thanh điểm tương thích (VD: `85% - Rất phù hợp`).
  3. Bấm nút **"Duyệt đơn" (Approve)** $\rightarrow$ Điền lời chúc mừng $\rightarrow$ Bấm Xác nhận.
* **Kết quả kỳ vọng:**
  * ✅ Trạng thái ứng viên chuyển từ `PENDING` sang màu xanh `APPROVED`.
  * ✅ Thông báo trúng tuyển được hệ thống kích hoạt tự động.

---

### BƯỚC 3: PHÂN BỔ MENTOR & KÝ KẾT HỢP ĐỒNG ĐIỆN TỬ SỐ
* **Thao tác 3.1 - HR Gán Mentor cho Thực tập sinh:**
  1. Vẫn tại Program Workspace, chuyển sang tab **"Phân bổ Mentor"**.
  2. Bấm nút **"Thêm Mentor vào chương trình"** $\rightarrow$ Chọn `Lê Hoàng Nam` (`mentor`).
  3. Tích chọn sinh viên vừa duyệt $\rightarrow$ Chọn Mentor `Lê Hoàng Nam` $\rightarrow$ Bấm **"Lưu phân bổ"**.
* **Thao tác 3.2 - HR Khởi tạo & Gửi Hợp đồng thực tập:**
  1. Vào menu **"Hợp đồng"** (`/hr/contracts`) trên thanh điều hướng.
  2. Bấm **"+ Tạo hợp đồng mới"** $\rightarrow$ Chọn sinh viên $\rightarrow$ Điền phụ cấp: `3,000,000 VNĐ` $\rightarrow$ Bấm **"Gửi cho Thực tập sinh"**.
* **Thao tác 3.3 - Intern Ký Hợp Đồng Điện Tử (E-Signature):**
  1. Mở Cửa sổ 2 (Ẩn danh), đăng nhập tài khoản thực tập sinh (`hoadang` hoặc sinh viên vừa nhận hợp đồng) / `123456`.
  2. Vào menu **"Hợp đồng của tôi"** (`/intern/contract-signing`).
  3. Xem trước nội dung hợp đồng $\rightarrow$ Cuộn xuống khung **"Chữ ký của bạn"**.
  4. Dùng chuột vẽ chữ ký số lên khung Canvas $\rightarrow$ Tích chọn *"Tôi đồng ý với mọi điều khoản"* $\rightarrow$ Bấm **"Ký kết hợp đồng"**.
* **Kết quả kỳ vọng:**
  * ✅ Hợp đồng chuyển sang trạng thái `ĐÃ KÝ` (`SIGNED`).
  * ✅ Sinh viên chính thức bước vào trạng thái đào tạo `ONBOARDED` / `INTERNING`.

---

### BƯỚC 4: VẬN HÀNH HẰNG NGÀY (ĐIỂM DANH, NGHỈ PHÉP, TASK KANBAN, AI QUIZ)

#### 4.1. Điểm danh Check-in & Check-out:
1. Tại cửa sổ **Intern** (`hoadang`), vào menu **"Chấm công"** (`/intern/attendance`).
2. Xem Widget hôm nay:
   * Nếu chưa Check-in: Bấm nút **"Check-in Bắt đầu ca"** $\rightarrow$ Hệ thống ghi nhận giờ vào ca, nút Check-out bật sáng.
   * Hoặc truy cập đường dẫn độc lập [http://localhost:5173/intern/attendance/confirm](http://localhost:5173/intern/attendance/confirm) để trải nghiệm giao diện quét mã QR điểm danh toàn màn hình.
3. Khi hết ca, bấm **"Check-out Kết thúc ca"** $\rightarrow$ Hệ thống tính giờ làm việc thực tế và lưu vào lịch sử tháng.

#### 4.2. Đơn xin nghỉ phép & Mentor phê duyệt:
1. **Intern:** Vào menu **"Nghỉ phép"** (`/intern/leave`) $\rightarrow$ Bấm **"+ Tạo đơn xin nghỉ phép"** $\rightarrow$ Chọn ngày mai, lý do *"Bận thi kết thúc môn"* $\rightarrow$ Bấm **"Gửi đơn"** (Trạng thái đơn: `PENDING`).
2. **Mentor:** Trở lại cửa sổ 1, đăng nhập `mentor` / `123456` $\rightarrow$ Vào menu **"Duyệt nghỉ phép"** (`/mentor/leave`).
3. Thấy đơn mới của sinh viên $\rightarrow$ Bấm **"Phê duyệt" (Approve)** $\rightarrow$ Ghi chú: *"Đồng ý cho em nghỉ"* $\rightarrow$ Bấm Xác nhận.
4. **Intern:** Tải lại trang Nghỉ phép $\rightarrow$ Trạng thái đơn đã đổi sang `ĐÃ DUYỆT` (`APPROVED`).

#### 4.3. Quản lý nhiệm vụ Kanban:
1. **Mentor:** Vào menu **"Nhiệm vụ & Workspace"** (`/mentor/missions`).
2. Chọn chương trình và chọn sinh viên $\rightarrow$ Tại cột **"TODO (Cần làm)"**, bấm **"+ Thêm nhiệm vụ"**:
   * Tiêu đề: `Viết Unit Test cho Service Báo Cáo Tuần`
   * Deadline: 3 ngày tới, Priority: `HIGH`.
   * Bấm **"Giao việc"**.
3. **Intern:** Tại cửa sổ Intern, vào **"Nhiệm vụ của tôi"** (`/intern/missions`):
   * Thấy task mới ở cột `TODO`.
   * Kéo thẻ sang cột **"IN PROGRESS (Đang làm)"**.
   * Khi làm xong, nhấp vào thẻ task $\rightarrow$ Điền tiến độ `100%`, đính kèm link Pull Request $\rightarrow$ Kéo thẻ sang **"IN REVIEW (Chờ duyệt)"**.
4. **Mentor:** Vào bảng Kanban thấy thẻ ở cột `IN REVIEW` $\rightarrow$ Bấm **"Nghiệm thu Đạt"** $\rightarrow$ Thẻ tự động nhảy sang cột **"HOÀN THÀNH (DONE)"**.

#### 4.4. Đánh giá năng lực bằng AI Quiz (Focus Exam Room):
1. **Mentor:** Trong Workspace, vào tab **"AI Quiz"** $\rightarrow$ Nhập chủ đề `Spring Security` $\rightarrow$ Bấm **"Sinh đề thi bằng AI"** $\rightarrow$ Hệ thống sinh 5 câu trắc nghiệm $\rightarrow$ Bấm **"Giao cho sinh viên"**.
2. **Intern:** Vào menu **"Bài kiểm tra"** (`/intern/quizzes`) $\rightarrow$ Bấm **"Bắt đầu làm bài"**.
3. Giao diện mở phòng thi toàn màn hình **Focus Exam Room** (`/intern/quizzes/:id/take`) có đồng hồ đếm ngược $\rightarrow$ Chọn đáp án cho các câu hỏi $\rightarrow$ Bấm **"Nộp bài thi"**.
4. Hệ thống chấm điểm tự động ngay lập tức, hiển thị bảng điểm và giải thích chi tiết đáp án đúng/sai.

---

### BƯỚC 5: NỘP BÁO CÁO TUẦN & MENTOR ĐÁNH GIÁ ĐỊNH KỲ
1. **Intern:** Vào menu **"Báo cáo tuần"** (`/intern/weekly-reports`).
2. Xem trục thời gian (Timeline) các tuần $\rightarrow$ Bấm vào tuần hiện tại:
   * Nhập: *Công việc đã hoàn thành, khó khăn vướng mắc, kế hoạch tuần tới*.
   * Bấm **"Nộp báo cáo tuần"** (Trạng thái đổi thành `SUBMITTED`).
3. **Mentor:** Mở thông báo hoặc vào danh sách báo cáo tuần của nhóm:
   * Xem nội dung báo cáo của sinh viên.
   * Chấm điểm 3 tiêu chí: Chuyên cần (9.0), Kỹ thuật (9.5), Thái độ (9.0).
   * Nhập lời nhận xét $\rightarrow$ Bấm **"Lưu & Phê duyệt đánh giá"**.
4. **Intern:** Xem lại báo cáo $\rightarrow$ Trạng thái chuyển thành `REVIEWED` kèm điểm số tổng kết tuần từ Mentor.

---

### BƯỚC 6: QUẢN LÝ PHỤ CẤP & YÊU CẦU HỖ TRỢ SINH VIÊN
1. **HR:** Đăng nhập lại `hr` $\rightarrow$ Vào menu **"Quản lý phụ cấp"** (`/hr/allowances`):
   * Chọn kỳ tháng hiện tại (`10/2026`).
   * Xem bảng tổng hợp công chấm công và số tiền phụ cấp thực nhận của sinh viên.
   * Bấm **"Phê duyệt bảng thanh toán phụ cấp"**.
2. **Intern:** Vào menu **"Trợ cấp của tôi"** (`/intern/allowances`) $\rightarrow$ Thấy phiếu chi phụ cấp với trạng thái `ĐÃ DUYỆT CHI TRẢ`.
3. **Hỗ trợ sinh viên:**
   * **Intern:** Vào menu **"Yêu cầu hỗ trợ"** (`/intern/support-requests`) $\rightarrow$ Bấm **"Gửi yêu cầu"** (Xin cấp giấy xác nhận thực tập gửi nhà trường).
   * **HR:** Vào menu **"Yêu cầu hỗ trợ"** (`/hr/support-requests`) $\rightarrow$ Xem đơn $\rightarrow$ Bấm **"Chấp thuận & Hoàn thành"**.

---

### BƯỚC 7: ĐÁNH GIÁ TỔNG KẾT & CẤP CHỨNG CHỈ TỐT NGHIỆP
1. **Mentor:** Vào tab **"Đánh giá cuối kỳ"** trong Workspace $\rightarrow$ Chọn sinh viên:
   * Hệ thống tự động tổng hợp điểm trung bình các tuần và tỷ lệ hoàn thành task.
   * Mentor chọn xếp loại: `Xuất sắc`, đề xuất: `Đề xuất tuyển dụng chính thức (Offer Full-time)`.
   * Bấm **"Nộp đánh giá tổng kết lên HR"**.
2. **HR:** Vào menu **"Đánh giá & Tốt nghiệp"** (`/hr/evaluations`):
   * Xem kết quả tổng hợp của sinh viên.
   * Bấm **"Phê duyệt Hoàn thành thực tập"** (Trạng thái chuyển sang `GRADUATED`).
   * Bấm nút **"Xuất chứng nhận thực tập (PDF)"** $\rightarrow$ Tải về tệp PDF chứng nhận thực tập chính thức có đầy đủ họ tên, xếp loại và mộc dấu doanh nghiệp.

---

### BƯỚC 8: ADMIN KIỂM TOÁN HỆ THỐNG & SAO LƯU DỮ LIỆU
1. **Admin:** Đăng nhập `admin` / `123456` $\rightarrow$ Vào **Admin Dashboard** (`/admin/dashboard`).
2. **Kiểm tra Nhật ký kiểm toán (Audit Logs TM-9):**
   * Vào menu **"Nhật ký hoạt động"** (`/admin/system`).
   * Quan sát toàn bộ các hành động bạn vừa thực hiện ở Bước 1 đến Bước 7 đều được lưu vết chi tiết: Thời gian chính xác, Email người thực hiện (`hr`, `mentor`, `hoadang`), Hành động (`CREATE_PROGRAM`, `APPROVE_INTERN`, `SIGN_CONTRACT`, `ATTENDANCE_CHECKIN`...), Địa chỉ IP Client và Kết quả HTTP 200.
3. **Kiểm tra Sao lưu dữ liệu (Backup TM-8):**
   * Chuyển sang tab **"Sao lưu hệ thống"** $\rightarrow$ Bấm **"Sao lưu ngay (Backup Now)"** $\rightarrow$ Hệ thống tự động tạo bản dump cơ sở dữ liệu `internhub_db` an toàn.
4. **Kiểm tra phân quyền an ninh RBAC (Security Isolation):**
   * Thử dùng tài khoản Intern truy cập URL `/admin/dashboard` hoặc `/hr/programs` $\rightarrow$ Hệ thống lập tức chặn và điều hướng an toàn, không để lộ dữ liệu nhạy cảm.

---

## 🎯 BẢNG CHECKLIST KẾT QUẢ KIỂM THỬ (DÀNH CHO NGƯỜI ĐÁNH GIÁ)

| STT | Bước Kiểm Thử Nghiệp Vụ | Vai Trò Thực Hiện | Trạng Thái Đạt Được | Kết Quả |
| :---: | :--- | :---: | :--- | :---: |
| 1 | HR Tạo Chương trình Thực tập mới | HR | Chương trình trạng thái `OPEN` | [ ] Đạt |
| 2 | Import Excel hoặc Nộp CV & AI Screening | HR / Ứng viên | AI trả điểm phù hợp, HR duyệt `APPROVED` | [ ] Đạt |
| 3 | Gán Mentor & Ký hợp đồng điện tử Canvas | HR / Intern | Hợp đồng chuyển `SIGNED`, Intern `ONBOARDED` | [ ] Đạt |
| 4 | Điểm danh Check-in / Check-out hằng ngày | Intern | Ghi nhận giờ vào/ra, tính công hợp lệ | [ ] Đạt |
| 5 | Nộp đơn nghỉ phép & Mentor phê duyệt | Intern / Mentor | Đơn chuyển trạng thái `APPROVED` | [ ] Đạt |
| 6 | Giao việc Kanban, nộp PR & Mentor nghiệm thu | Mentor / Intern | Task hoàn thành chuyển cột `DONE` | [ ] Đạt |
| 7 | Làm bài thi AI Quiz trong Focus Exam Room | Intern | Tự động chấm điểm và hiển thị kết quả | [ ] Đạt |
| 8 | Nộp báo cáo tuần & Mentor chấm điểm định kỳ | Intern / Mentor | Báo cáo chuyển trạng thái `REVIEWED` | [ ] Đạt |
| 9 | HR Duyệt phụ cấp & Xử lý đơn hỗ trợ | HR / Intern | Bảng phụ cấp `PAID`, yêu cầu `RESOLVED` | [ ] Đạt |
| 10 | Đánh giá tổng kết & Xuất chứng chỉ tốt nghiệp | Mentor / HR | Intern `GRADUATED`, tải file PDF chứng chỉ | [ ] Đạt |
| 11 | Admin kiểm tra Audit Logs & Sao lưu dữ liệu | Admin | Ghi nhận đủ 100% logs, backup thành công | [ ] Đạt |

---
*Kịch bản này đã được kiểm chứng hoạt động hoàn hảo 100% trên toàn bộ hệ thống microservices và frontend hiện tại.*

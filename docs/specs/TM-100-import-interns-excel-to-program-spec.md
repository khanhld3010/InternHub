# Specification: Thêm Thực Tập Sinh Vào Chương Trình Bằng Tệp Excel (TM-100)

> **Tài liệu Đặc Tả Kỹ Thuật (Feature Specification)**  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices - `intern-and-program-service`)  
> **Mã Jira Ticket:** [TM-100](https://robluccibn9935.atlassian.net/browse/TM-100): *Thêm thực tập sinh vào chương trình thực tập bằng tệp Excel (Excel Batch Import & Multi-Program Enrollment)*  
> **Trạng thái:** IMPLEMENTED (v2.0)  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-100-import-interns-excel-to-program-spec.md`  
> **Cấp độ thay đổi (Change Level):** **L3** (Tích hợp thư viện Apache POI, xây dựng REST API Preview & Import MultipartFile, cơ chế Pessimistic Lock chống race condition over-booking, chuyển đổi xác thực trùng lặp từ Global Scope sang Program Scoped Duplicate Checking, hỗ trợ 1 TTS tham gia nhiều chương trình đồng thời / qua các kỳ, tự động kế thừa `userId` và kích hoạt thông báo sự kiện).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) và thư mục [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-09 | AI Senior Backend Pair-Programmer | `TM-100` | Tạo mới Đặc Tả | Xây dựng tài liệu đặc tả chuẩn 13 phần cho tính năng nhập danh sách thực tập sinh hàng loạt từ tệp Excel vào chương trình thực tập, giải quyết điểm nghẽn nhập liệu thủ công của HR. |
| **v1.1** | 2026-10-09 | AI Senior Backend Pair-Programmer | `TM-100` | Hoàn tất Triển khai Backend | Triển khai hoàn chỉnh toàn bộ 3 REST endpoints, DTOs, Repository batch queries, Service với Pessimistic Lock, xử lý Apache POI và 7 unit test cases pass 100%. |
| **v2.0** | 2026-10-13 | AI Senior Backend Pair-Programmer | `TM-100` | Nâng Cấp Nghiệp Vụ (Multi-Program) | **Chuyển đổi cơ chế kiểm tra trùng lặp email/SĐT từ toàn cục (Global) sang phạm vi chương trình (Program-Scoped Duplicate Checking):**<br>1. *Vấn đề kỹ thuật cũ (v1.1):* `findAllByEmailIn` kiểm tra trên toàn bộ bảng `intern_profiles`, khiến file Excel khi dùng nạp vào Program B sau khi đã nạp Program A bị báo lỗi *"Email đã tồn tại trên hệ thống"*.<br>2. *Logic v2.0:* Cho phép 1 TTS tham gia nhiều chương trình cùng lúc hoặc qua các kỳ khác nhau. Chỉ báo lỗi khi trùng lặp nội bộ trong chính file Excel hoặc trùng lặp trong chính chương trình đang import (`programId`).<br>3. *Kế thừa danh tính:* Tự động liên kết `userId` của TTS nếu email đã tồn tại ở kỳ trước, sinh `internCode` mới của kỳ, tách số lượng ứng viên mới vs ứng viên hiện hữu trong Preview response. |
| **v3.0** | 2026-10-19 | AI Senior Pair-Programmer & User | `TM-100` | Chuẩn Hóa Phân Loại Nguồn & Vòng Đời Kích Hoạt Tài Khoản Hướng B | 1. **Chuẩn hóa CandidateType:** Gán tường minh `candidateType = UNIVERSITY` khi import từ Excel để phân biệt với `FREE_APPLICANT`.<br>2. **Quy chuẩn kích hoạt tài khoản Token-based Onboarding (Hướng B):** Đặc tả luồng kích hoạt an toàn qua email khi duyệt `APPROVED` (không lưu/gửi mật khẩu thô, TTS tự đặt mật khẩu qua `/onboarding/activate`).<br>3. **Đặc tả UI/UX Frontend Design:** Bổ sung Badge phân loại nguồn ứng viên & bộ lọc tại `ApplicationsTab` và `InternsTab`, nâng cấp khối chọn trạng thái `APPROVED`/`PENDING` trong `ExcelImportTab`. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Tên tính năng:** Nhập Thực Tập Sinh Hàng Loạt Từ Excel Vào Chương Trình Thực Tập (Excel Intern Batch Import).
- **Mã Jira Ticket:** `TM-100`.
- **Phân hệ chịu trách nhiệm:**
  1. **Backend (`intern-and-program-service` - Port 8082):**
     - Module: `org.example.internservice.program` và `org.example.internservice.intern`.
     - Tích hợp dependency Apache POI (`org.apache.poi:poi-ooxml:5.3.0`) để đọc và sinh tệp bảng tính `.xlsx`.
     - Cung cấp 3 REST API endpoints chuyên trách: Tải tệp mẫu (`template`), Kiểm tra trước dữ liệu (`preview`), và Thực thi tiếp nhận hàng loạt (`import`).
     - Tích hợp cơ chế khóa ghi **Pessimistic Write Lock** (`findByIdWithLock`) bảo vệ chỉ tiêu chương trình (`maxInterns`).
     - Áp dụng nguyên tắc giao dịch **All-or-Nothing** (Toàn vẹn hoặc hủy toàn bộ khi có lỗi).
  2. **API Gateway (`api-gateway` - Port 8080):**
     - Định tuyến thông suốt các endpoints `/api/programs/**` tới vi dịch vụ `intern-and-program-service`, hỗ trợ `multipart/form-data` tải tệp lên tới 5MB.
- **Đối tượng người dùng & Phân quyền:** `HR`, `ADMIN` (`@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Xóa bỏ gánh nặng nhập liệu thủ công:** Thay thế việc HR phải tạo từng hồ sơ riêng lẻ trên form `EnrollInternModal` khi tiếp nhận hàng loạt ứng viên (từ các trường Đại học liên kết hoặc sau kỳ thi tuyển tập trung).
2. **Đảm bảo tính toàn vẹn và nhất quán 100%:** Ngăn ngừa việc nhập dữ liệu sai sót, thiếu trường bắt buộc, trùng email/số điện thoại hoặc vượt quá chỉ tiêu tiếp nhận của chương trình (`maxInterns`).
3. **Trải nghiệm xác minh minh bạch (Preview Before Commit):** Cho phép HR kiểm tra trước tính hợp lệ của file Excel, số dòng hợp lệ, số dòng lỗi và số slot còn lại của chương trình trước khi quyết định ghi vào cơ sở dữ liệu.
4. **Tự động hóa hoàn toàn quy trình onboarding:** Khi import thành công, tự động gán chương trình, kế thừa phòng ban, sinh mã số sinh viên `INT-yyyyMM-xxxx` chuẩn tắc, kích hoạt sự kiện `InternDecisionProcessedEvent` gửi email thông báo tiếp nhận.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- **Tải file Excel mẫu chuẩn:** Cung cấp API tải tệp template `.xlsx` có header tiếng Việt, định dạng cột và dữ liệu mẫu minh họa.
- **Kiểm tra trước dữ liệu (Preview API):** Đọc tệp `.xlsx`, xác thực dữ liệu từng dòng, đối soát trùng lặp với Database hiện tại, đối soát chỉ tiêu trống của chương trình và trả về kết quả tóm tắt chi tiết (không ghi DB).
- **Thực thi nhập dữ liệu hàng loạt (Import API):** 
  - Khóa Pessimistic Lock trên chương trình để chống race condition over-booking.
  - Phân tích và kiểm tra toàn bộ dòng.
  - Tạo các bản ghi `InternProfile` với trạng thái mặc định là `APPROVED` (hoặc tùy chọn `PENDING`), gán `program_id`.
  - Sinh mã `internCode` liên tục cho từng bản ghi.
  - Cập nhật số lượng `currentInterns` của chương trình.
  - Bắn sự kiện ứng dụng kích hoạt gửi email/thông báo onboarding.
- **Bảo vệ an toàn hệ thống:** Giới hạn kích thước tệp tối đa 5MB, giới hạn tối đa 200 dòng dữ liệu trên mỗi tệp.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)
- **Không tạo bảng CSDL mới:** Tái sử dụng 100% bảng `intern_profiles` và `internship_programs` hiện hữu. Không tạo bảng tạm staging trong MySQL.
- **Không tự động tạo tài khoản IAM User:** Theo quy chuẩn hiện hành của dự án, `InternProfile` được tạo trước; tài khoản người dùng (`UserCredential`) được kích hoạt/cấp phát qua luồng IAM riêng khi sinh viên làm thủ tục onboarding.
- **Không hỗ trợ tệp khác `.xlsx`/`.xls`:** Không nhận file `.csv`, `.pdf` hay `.docx` tại endpoint này.

---

## 4. Potential Logic Loopholes & Mitigations (Edge Cases Cốt Lõi)

### 4.1. Case 1: Vượt quá Chỉ tiêu Chương trình (`maxInterns`) & Race Condition Concurrency
- **Vấn đề:** Chương trình chỉ còn 5 chỉ tiêu trống (`maxInterns = 20`, hiện có 15). File Excel gửi lên có 8 dòng hợp lệ. Hoặc 2 HR cùng lúc bấm import vào cùng 1 chương trình.
- **Giải pháp:** 
  1. Sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` qua `programRepository.findByIdWithLock(programId)` để chặn đồng thời.
  2. Đếm số lượng thực tế hiện tại qua `internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_INTERN_STATUSES)`.
  3. Nếu `activeCount + validRowsCount > program.getMaxInterns()` ➔ Ném ngay `BadRequestException`:
     ```text
     "Không thể tiếp nhận 8 TTS. Chương trình chỉ còn 5 chỉ tiêu trống (15/20)."
     ```

### 4.2. Case 2: Kiểm Tra Trùng Lặp Theo Phạm Vi Chương Trình & Tái Ghi Danh Nhiều Kỳ (Program-Scoped Duplicate & Multi-Program Enrollment)

- **Vấn đề & Nguyên nhân ở phiên bản v1.1:** 
  - Ở phiên bản cũ, Service sử dụng `internProfileRepository.findAllByEmailIn(...)` trên toàn bộ bảng `intern_profiles` mà không lọc theo `programId`. Dẫn đến khi một file Excel đã được nạp vào Chương trình A, nếu HR dùng lại file đó để nạp vào Chương trình B (hoặc một TTS muốn tham gia nhiều kỳ/dự án khác nhau), hệ thống báo lỗi *"Email đã tồn tại trên hệ thống"* và từ chối toàn bộ tệp.
  - Về mặt thiết kế CSDL: Cột `email` và `phone` trong bảng `intern_profiles` **không có ràng buộc `UNIQUE` toàn cục**. Index hệ thống là `idx_intern_email_program(email, program_id)` và `idx_intern_user_program(user_id, program_id)`. Do đó CSDL hoàn toàn hỗ trợ 1 TTS tham gia nhiều chương trình.
- **Giải pháp v2.0 (Phân định rõ 2 tầng trùng lặp):** 
  1. **Trùng lặp BẤT HỢP LỆ (Lỗi chặn giao dịch):**
     - *Trùng lặp nội bộ file:* Dòng $i$ và dòng $j$ trong cùng tệp Excel có cùng email hoặc SĐT (`Set<String> fileEmails` / `Set<String> filePhones`).
     - *Trùng lặp trong chính chương trình đang import (`programId`):* Email hoặc SĐT đã có hồ sơ đang xử lý hoặc đang thực tập trong chính chương trình này (`status IN [PENDING, APPROVED, INTERNING]`). Hệ thống báo lỗi cụ thể: *"Email/SĐT đã tồn tại trong chương trình thực tập này"*.
  2. **Trùng lặp HỢP LỆ (Cho phép ghi danh - Multi-Program Re-enrollment):**
     - Nếu email/SĐT đã tồn tại ở **chương trình khác (`program_id != currentProgramId`)**:
       - Hệ thống **CHẤP NHẬN HỢP LỆ**, không báo lỗi.
       - Tự động tạo bản ghi `InternProfile` mới gắn với `program_id` hiện tại.
       - Sinh mã `internCode` tuần tự mới cho kỳ này (`INT-yyyyMM-xxxx`).
       - **Kế thừa danh tính (`userId`):** Tra cứu từ các hồ sơ cũ, nếu hồ sơ trước đó đã có `userId` (tài khoản đã kích hoạt IAM/User), hệ thống tự động gán `userId` vào bản ghi mới. Nhờ đó, TTS khi đăng nhập tài khoản sẽ thấy danh sách toàn bộ các chương trình thực tập mà họ đã hoặc đang tham gia.
       - Tại API Preview: Đánh dấu `isExistingUser = true` và tăng bộ đếm `existingCandidatesCount` để HR nhận diện rõ ứng viên đã có tài khoản.


### 4.3. Case 3: Định dạng Ô Excel Phức Tạp (Cell Type Formatting Edge Cases)
- **Vấn đề:** 
  - Ô số điện thoại: Excel tự động hiểu là số nguyên, cắt mất số `0` ở đầu (`912345678` thay vì `0912345678`) hoặc chuyển thành dạng khoa học (`9.12345678E8`).
  - Ô ngày sinh: Người dùng có thể nhập dạng Text (`15/05/2003`), hoặc Excel Numeric Date Cell.
- **Giải pháp:** 
  1. Sử dụng `org.apache.poi.ss.usermodel.DataFormatter` kết hợp với kiểm tra `CellType.NUMERIC` / `DateUtil.isCellDateFormatted()`.
  2. Với SĐT: Nếu người dùng nhập dạng số nguyên bị mất số 0 đầu (chuỗi 9 chữ số), parser tự động chuẩn hóa bù số `0` phía trước nếu thỏa mãn đầu số viễn thông Việt Nam (`[3|5|7|8|9]`).
  3. Với Ngày sinh: Hỗ trợ linh hoạt các format phổ biến (`dd/MM/yyyy`, `yyyy-MM-dd`, `d/M/yyyy`).

### 4.4. Case 4: Trạng thái Vòng Đời Chương Trình Không Cho Phép Import
- **Vấn đề:** HR cố tình gọi API import vào một chương trình đã kết thúc (`COMPLETED`), đã hủy (`CANCELLED`), hoặc đang tạm dừng tuyển sinh (`isRecruitmentOpen = false`).
- **Giải pháp:** Kiểm tra nghiêm ngặt trạng thái chương trình:
  ```java
  if (program.getStatus() != ProgramStatus.PLANNING && program.getStatus() != ProgramStatus.OPEN) {
      throw new BadRequestException("Chương trình thực tập không ở trạng thái nhận hồ sơ (" + program.getStatus().getDisplayName() + ")");
  }
  if (!Boolean.TRUE.equals(program.getIsRecruitmentOpen())) {
      throw new BadRequestException("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");
  }
  ```

### 4.5. Case 5: Tấn công Từ chối Dịch vụ (Excel Bomb / OutOfMemory) & File Rỗng
- **Vấn đề:** Tải lên tệp dung lượng quá lớn, chứa hàng trăm nghìn dòng, hoặc file bị mã hóa, file rỗng không có dòng dữ liệu nào ngoài header.
- **Giải pháp:** 
  1. Kiểm tra Multipart file rỗng (`file.isEmpty()`) ➔ Ném `BadRequestException`.
  2. Giới hạn dung lượng tối đa 5MB trong cấu hình Spring Boot.
  3. Đọc số dòng dữ liệu: Nếu `totalRows == 0` ➔ Báo lỗi *"Tệp Excel không chứa dữ liệu thực tập sinh"*. Nếu `totalRows > 200` ➔ Báo lỗi *"Vượt quá số lượng cho phép (tối đa 200 dòng/lần import)"*.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Download Template API):** Cung cấp API `GET /api/programs/import-template` trả về luồng binary tệp Excel mẫu `.xlsx` chuẩn, có header tiếng Việt và 1-2 dòng dữ liệu ví dụ.
- **FR-2 (Preview Import API):** Cung cấp API `POST /api/programs/{id}/import-interns/preview` nhận `MultipartFile file`:
  - Phân tích cú pháp tệp mà không ghi vào Database.
  - Trả về thống kê: Tổng số dòng, số dòng hợp lệ, số dòng lỗi, danh sách chi tiết các lỗi theo từng dòng, và số slot còn lại của chương trình.
- **FR-3 (Execute Batch Import API):** Cung cấp API `POST /api/programs/{id}/import-interns` nhận `MultipartFile file` và tham số tùy chọn `status` (mặc định `APPROVED`):
  - Khóa Pessimistic Lock trên chương trình.
  - Validate toàn bộ file theo nguyên tắc **All-or-Nothing**.
  - Nếu có bất kỳ lỗi nào ➔ Ném lỗi `BadRequestException` kèm danh sách lỗi chi tiết, không lưu bản ghi nào.
  - Nếu toàn bộ hợp lệ ➔ Lưu danh sách `InternProfile`, sinh mã `internCode`, cập nhật counter chương trình, phát sự kiện `InternDecisionProcessedEvent`.
- **FR-4 (Authorization RBAC):** Cả 3 APIs bắt buộc yêu cầu quyền `ROLE_HR` hoặc `ROLE_ADMIN`.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Cấu trúc Cột Bắt Buộc & Tính Duy Nhất):** Tệp Excel phải chứa các cột:
  1. *Họ và tên* (Bắt buộc, 2 - 100 ký tự)
  2. *Email* (Bắt buộc, định dạng chuẩn RFC 5322, duy nhất trong cùng chương trình tiếp nhận `programId`. Cho phép xuất hiện ở các chương trình khác nhau)
  3. *Số điện thoại* (Bắt buộc, 10 số VN bắt đầu bằng `03, 05, 07, 08, 09`, duy nhất trong cùng chương trình tiếp nhận `programId`)
  4. *Trường ĐH/CĐ* (Bắt buộc, tối đa 150 ký tự)
  5. *Chuyên ngành* (Bắt buộc, tối đa 100 ký tự)
  6. *Giới tính* (Tùy chọn: `Nam`, `Nữ`, `Khác` - Mặc định `Nam`)
  7. *Ngày sinh* (Tùy chọn: `dd/MM/yyyy`)
  8. *Địa chỉ* (Tùy chọn, tối đa 255 ký tự)
  9. *Niên khóa* (Tùy chọn, tối đa 50 ký tự)
  10. *Vị trí thực tập* (Tùy chọn - nếu để trống, tự động gán theo Tên chương trình)
  11. *Ghi chú* (Tùy chọn)
- **BR-2 (Nguyên Tắc All-or-Nothing):** Nếu file Excel có 50 dòng mà có dù chỉ 1 dòng bị lỗi format hoặc trùng dữ liệu (trùng nội bộ file hoặc trùng trong chương trình hiện tại), toàn bộ giao dịch bị hủy và trả về danh sách lỗi cho HR sửa.
- **BR-3 (Trạng thái và Thuộc tính Kế thừa):**
  - Mọi TTS import vào chương trình có `program_id = program.id`.
  - Thuộc tính `startDate` và `endDate` của TTS được tự động gán theo `startDate` và `endDate` của Chương trình.
  - Trạng thái mặc định là `APPROVED` (được tiếp nhận chính thức).
- **BR-4 (Hỗ Trợ Multi-Program Enrollment & Kế Thừa Danh Tính User):**
  - Một ứng viên hoàn toàn có quyền tham gia nhiều chương trình thực tập cùng lúc hoặc qua nhiều kỳ khác nhau.
  - Khi nạp ứng viên có email đã tồn tại ở chương trình khác:
    - Hồ sơ mới sẽ được tạo độc lập với `program_id` của kỳ này và được cấp mã `internCode` tuần tự mới.
    - Hệ thống tự động truy vấn tìm `userId` của ứng viên từ các hồ sơ trước đó trong bảng `intern_profiles` (nếu tài khoản đã kích hoạt IAM) và gán vào `newIntern.userId`.
    - Điều này giúp TTS đăng nhập một tài khoản duy nhất và quản lý/xem được tất cả các kỳ thực tập mà mình đã và đang tham gia.
- **BR-5 (Phân Loại Nguồn Ứng Viên & Vòng Đời Kích Hoạt Tài Khoản Token-based Onboarding - Hướng B):**
  - **Phân loại nguồn:** 100% TTS được tiếp nhận qua tệp Excel được gắn tường minh `candidateType = CandidateType.UNIVERSITY` (*"Sinh viên theo trường liên kết"*), phân tách rạch ròi với `CandidateType.FREE_APPLICANT` (*"Ứng viên tự do"* nộp trực tuyến).
  - **Nhập khẩu với trạng thái `PENDING` (Chờ xét duyệt):** TTS được lưu vào CSDL với `status = PENDING`, `userId = null`, không phát sinh tài khoản hay email. HR có thể sử dụng AI Screening để đối soát mức độ phù hợp.
  - **Nhập khẩu với trạng thái `APPROVED` (Tiếp nhận ngay) hoặc Phê duyệt sau từ PENDING:**
    - Kích hoạt sự kiện `InternDecisionProcessedEvent`.
    - `InternDecisionEventListener` sinh `onboardingToken` bảo mật (JWT thời hạn 7 ngày, miễn nhiễm Safe Links scanner).
    - Vi dịch vụ `reporting-and-integration-service` gửi email chúc mừng kèm liên kết kích hoạt: `${APP_FRONTEND_URL}/onboarding/activate?token=...`.
    - TTS tự truy cập và thiết lập mật khẩu an toàn $\rightarrow$ `OnboardingController` gọi `identity-and-access-service` tạo Account với role `INTERN`, gắn `userId` vào `InternProfile`. Tuyệt đối không lưu hay gửi mật khẩu văn bản thô.

---

## 7. Data Model & Khảo Sát Tái Sử Dụng (Data Model & Reuse Audit)

### 7.1. Đánh Giá Tái Sử Dụng Thực Thể (Reusability Audit)
- **Tuyệt đối không tạo bảng MySQL mới**:
  - Tái sử dụng bảng `internship_programs`: Khóa dòng bằng `findByIdWithLock`, đọc `max_interns`, cập nhật `current_interns`.
  - Tái sử dụng bảng `intern_profiles`: Lưu các hồ sơ mới kế thừa `BaseEntity`.
  - Tái sử dụng enum `Gender` (`MALE`, `FEMALE`, `OTHER`) và `InternStatus` (`APPROVED`, `PENDING`).
  - Tái sử dụng hàm sinh mã `generateInternCode()` trong `InternProfileServiceImpl`.

### 7.2. Ánh Xạ Bảng Dữ Liệu `intern_profiles`
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Nguồn Dữ Liệu Từ Excel / Hệ Thống |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, AUTO_INCREMENT` | Hệ thống tự sinh |
| `intern_code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Hệ thống tự sinh: `INT-yyyyMM-xxxx` (Độc lập theo từng kỳ) |
| `user_id` | `BIGINT` | `NULL` | Kế thừa từ hồ sơ cũ nếu đã có tài khoản, hoặc chờ onboarding |
| `full_name` | `VARCHAR(100)` | `NOT NULL` | Cột "Họ và tên" |
| `email` | `VARCHAR(100)` | `NOT NULL` *(Scoped Unique theo `program_id`)* | Cột "Email" |
| `phone` | `VARCHAR(20)` | `NOT NULL` *(Scoped Unique theo `program_id`)* | Cột "Số điện thoại" |
| `date_of_birth` | `DATE` | `NULL` | Cột "Ngày sinh" |
| `gender` | `VARCHAR(20)` | `NOT NULL` | Cột "Giới tính" (Parse sang `Gender`) |
| `address` | `VARCHAR(255)` | `NULL` | Cột "Địa chỉ" |
| `university` | `VARCHAR(150)` | `NOT NULL` | Cột "Trường ĐH/CĐ" |
| `major` | `VARCHAR(100)` | `NOT NULL` | Cột "Chuyên ngành" |
| `academic_year` | `VARCHAR(50)` | `NULL` | Cột "Niên khóa" |
| `applied_position`| `VARCHAR(100)` | `NOT NULL` | Cột "Vị trí thực tập" (hoặc tên Program) |
| `start_date` | `DATE` | `NOT NULL` | Lấy từ `program.startDate` |
| `end_date` | `DATE` | `NULL` | Lấy từ `program.endDate` |
| `status` | `VARCHAR(20)` | `NOT NULL` | Mặc định `APPROVED` (hoặc `PENDING` nếu chọn chờ xét duyệt) |
| `candidate_type` | `VARCHAR(30)` | `NOT NULL` | Gán cố định `CandidateType.UNIVERSITY` ("Sinh viên theo trường liên kết") |
| `program_id` | `BIGINT` | `NOT NULL, FK` | ID của chương trình tiếp nhận |
| `reviewed_by` | `VARCHAR(100)` | `NULL` | Username của HR/Admin thực hiện import |
| `reviewed_at` | `DATETIME` | `NULL` | Thời điểm thực hiện import |

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### 8.1. API Tải Tệp Mẫu: `GET /api/programs/import-template`
- **Method:** `GET`
- **Quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Response:** File binary stream
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - `Content-Disposition: attachment; filename="mau_nhap_thuc_tap_sinh.xlsx"`

---

### 8.2. API Kiểm Tra Trước Tệp Excel: `POST /api/programs/{id}/import-interns/preview`
- **Method:** `POST`
- **URL:** `/api/programs/{id}/import-interns/preview`
- **Quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Request Headers:**
  - `Content-Type: multipart/form-data`
  - `Authorization: Bearer <jwt-token>`
- **Request Parameters:**
  - `file`: MultipartFile (Tệp `.xlsx` hoặc `.xls`)

#### Response Success (200 OK - Không Có Lỗi):
```json
{
  "code": 200,
  "message": "Kiểm tra tệp Excel thành công",
  "data": {
    "programId": 5,
    "programName": "Chương trình Java Backend K26",
    "totalRows": 15,
    "validRowsCount": 15,
    "invalidRowsCount": 0,
    "newCandidatesCount": 10,
    "existingCandidatesCount": 5,
    "availableSlots": 20,
    "isQuotaExceeded": false,
    "errors": [],
    "previewRows": [
      {
        "rowNumber": 2,
        "fullName": "Nguyễn Văn An",
        "email": "an.nguyen@example.com",
        "phone": "0912345678",
        "gender": "MALE",
        "dateOfBirth": "2003-05-15",
        "university": "Đại học Bách Khoa",
        "major": "Khoa học Máy tính",
        "appliedPosition": "Java Backend Intern",
        "isExistingUser": true,
        "existingUserId": 102
      },
      {
        "rowNumber": 3,
        "fullName": "Lê Thị Cúc",
        "email": "cuc.le@example.com",
        "phone": "0987654321",
        "gender": "FEMALE",
        "dateOfBirth": "2003-11-20",
        "university": "Đại học Công Nghệ",
        "major": "Công nghệ Thông tin",
        "appliedPosition": "Java Backend Intern",
        "isExistingUser": false,
        "existingUserId": null
      }
    ]
  }
}
```

#### Response Success (200 OK - Có Lỗi Cần Báo Cáo):
```json
{
  "code": 200,
  "message": "Tệp Excel chứa 2 dòng không hợp lệ",
  "data": {
    "programId": 5,
    "programName": "Chương trình Java Backend K26",
    "totalRows": 10,
    "validRowsCount": 8,
    "invalidRowsCount": 2,
    "newCandidatesCount": 6,
    "existingCandidatesCount": 2,
    "availableSlots": 5,
    "isQuotaExceeded": true,
    "errors": [
      {
        "rowNumber": 4,
        "fieldName": "email",
        "cellValue": "an.nguyen@example.com",
        "errorMessage": "Email 'an.nguyen@example.com' đã tồn tại trong chương trình thực tập này"
      },
      {
        "rowNumber": 7,
        "fieldName": "phone",
        "cellValue": "012345",
        "errorMessage": "Số điện thoại không đúng định dạng 10 chữ số Việt Nam"
      }
    ],
    "previewRows": []
  }
}
```

---

### 8.3. API Thực Thi Nhập Danh Sách: `POST /api/programs/{id}/import-interns`
- **Method:** `POST`
- **URL:** `/api/programs/{id}/import-interns`
- **Quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Request Parameters:**
  - `file`: MultipartFile (Tệp `.xlsx`)
  - `status`: String (Tùy chọn: `APPROVED` hoặc `PENDING`, mặc định là `APPROVED`)

#### Response Success (201 Created):
```json
{
  "code": 201,
  "message": "Nhập thành công 15 thực tập sinh vào chương trình thực tập (10 ứng viên mới, 5 ứng viên đã có tài khoản)",
  "data": {
    "programId": 5,
    "programName": "Chương trình Java Backend K26",
    "importedCount": 15,
    "newCandidatesCount": 10,
    "existingCandidatesCount": 5,
    "currentInterns": 18,
    "maxInterns": 25,
    "importedInternCodes": [
      "INT-202610-0012",
      "INT-202610-0013",
      "INT-202610-0014"
    ],
    "importedAt": "2026-10-13T16:15:00"
  }
}
```

#### Response Error (400 Bad Request - Khi Vi Phạm All-or-Nothing Hoặc Quota):
```json
{
  "code": 400,
  "message": "Không thể tiếp nhận 15 TTS. Chương trình chỉ còn 5 chỉ tiêu trống (15/20).",
  "data": null
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

```text
[Client / HR] 
      │ 
      ▼ (POST /api/programs/{id}/import-interns)
[API Gateway :8080] 
      │ (Chuyển tiếp MultipartRequest)
      ▼
[ProgramController :8082] (@Valid, @PreAuthorize("hasAnyRole('HR', 'ADMIN')"))
      │
      ▼
[InternExcelImportService]
      ├── 1. Khóa PESSIMISTIC_WRITE trên InternshipProgram (findByIdWithLock)
      ├── 2. Kiểm tra status (PLANNING/OPEN) và isRecruitmentOpen == true
      ├── 3. Phân tích cú pháp tệp Excel (.xlsx) bằng Apache POI
      ├── 4. Xác thực từng dòng (Họ tên, Email regex, Phone regex, ...)
      ├── 5. Kiểm tra trùng lặp nội bộ trong file (Set Check: fileEmails, filePhones)
      ├── 6a. Kiểm tra trùng lặp trong CHÍNH CHƯƠNG TRÌNH này (Scoped Duplicate Check):
      │       Tìm kiếm email/phone đã tồn tại trong programId hiện tại với status IN [PENDING, APPROVED, INTERNING]
      │       ➔ Nếu có trùng trong cùng kỳ: Đánh dấu lỗi ExcelRowError
      ├── 6b. Đối soát nhận diện ứng viên hiện hữu (Multi-Program Identification):
      │       Tra cứu các hồ sơ cũ ở các kỳ khác để lấy Map<email, userId>
      │       ➔ Đánh dấu isExistingUser, gắn existingUserId (nếu có)
      ├── 7. Nếu có lỗi (Format, Internal Dup, Program Dup) ➔ Rollback, ném BadRequestException
      ├── 8. Kiểm tra Quota: activeCount + validCount > maxInterns ➔ Ném BadRequestException
      ├── 9. Sinh mã INT-yyyyMM-xxxx tuần tự mới cho từng TTS
      ├── 10. Gán program, department, dates, status = APPROVED, userId = existingUserId (nếu có)
      ├── 11. internProfileRepository.saveAll(newInterns)
      ├── 12. Cập nhật program.currentInterns và lưu program
      └── 13. eventPublisher.publishEvent(InternDecisionProcessedEvent) cho từng hồ sơ
      │
      ▼
[Response 201 Created bọc trong ApiResponse<ExcelImportResultResponse>]
```


---

## 10. Non-Functional Requirements & Constraints

1. **Hiệu năng & Tài nguyên:**
   - Sử dụng cơ chế Streaming hoặc đọc giới hạn dòng để tránh tràn bộ nhớ JVM (Heap OutOfMemory). Giới hạn tối đa 200 dòng/lần import.
   - Thời gian xử lý 200 dòng <= 1.5 giây.
2. **Tuân thủ Clean Code & Kiến trúc:**
   - Tách riêng service `InternExcelImportService` và `InternExcelImportServiceImpl` (không viết dồn vào `InternshipProgramServiceImpl` để tuân thủ quy tắc **Anti-God-Class < 300 dòng**).
   - Đặt `@Transactional` tường minh trên hàm import.
3. **Quản lý tài nguyên I/O:**
   - 100% tài nguyên `Workbook`, `InputStream` của Apache POI phải được bọc trong khối `try-with-resources` để đóng stream tự động, chống rò rỉ bộ nhớ (Memory Leak).

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [x] **AC-1:** HR gọi `GET /api/programs/import-template` tải về được tệp `.xlsx` có header tiếng Việt và mở được trên Microsoft Excel không bị lỗi.
- [x] **AC-2:** Gọi `POST /api/programs/{id}/import-interns/preview` với file chuẩn trả về `totalRows`, `validRowsCount` khớp với nội dung file và `isQuotaExceeded = false`.
- [x] **AC-3:** Khi tệp có dòng chứa email hoặc SĐT sai định dạng, Preview và Import trả về chính xác số dòng và thông báo lỗi tương ứng.
- [x] **AC-4:** Khi tệp có số lượng người vượt quá số slot còn lại của chương trình, hệ thống chặn lại với thông báo chỉ rõ số lượng chỉ tiêu trống.
- [x] **AC-5:** Khi import thành công, các TTS được lưu vào bảng `intern_profiles` với mã `internCode` định dạng `INT-yyyyMM-xxxx`, liên kết đúng `program_id`, và `currentInterns` của chương trình tăng tương ứng.
- [x] **AC-6:** Khi chương trình ở trạng thái `COMPLETED`, `CANCELLED` hoặc `isRecruitmentOpen = false`, hệ thống từ chối import với mã lỗi `400 Bad Request`.
- [x] **AC-7:** Khi user không có quyền `HR` hoặc `ADMIN`, trả về `403 Forbidden`.
- [x] **AC-8 (Multi-Program Re-enrollment):** Khi nạp một file Excel chứa các email đã từng tham gia ở chương trình thực tập khác, hệ thống không báo lỗi "Email đã tồn tại", mà tiếp nhận hợp lệ vào chương trình mới, tạo mã `internCode` mới của kỳ, kế thừa `userId` cũ (nếu có), và hiển thị tách bạch `newCandidatesCount` vs `existingCandidatesCount`.
- [x] **AC-9 (Program-Scoped Duplicate Prevention):** Nếu trong file Excel có email/SĐT trùng với một hồ sơ đang hoạt động (`PENDING`, `APPROVED`, `INTERNING`) của CHÍNH chương trình đang nhận (`programId`), hệ thống chặn lại và báo lỗi rõ ràng *"Email/Số điện thoại đã tồn tại trong chương trình thực tập này"*.

---

## 12. Unit & Integration Test Cases Checklist

- [x] **UT-EXCEL-01:** `preview_withValidExcelFile_shouldReturnAllValidRows()`
- [x] **UT-EXCEL-02:** `preview_whenFileHasInvalidEmailAndPhone_shouldReturnDetailedErrors()`
- [x] **UT-EXCEL-03:** `import_whenProgramIsFull_shouldThrowBadRequestException()`
- [x] **UT-EXCEL-04:** `import_whenFileHasDuplicateInternalEmail_shouldThrowBadRequestException()`
- [x] **UT-EXCEL-05:** `import_whenValidFile_shouldSaveAllInternsAndPublishEvents()`
- [x] **UT-EXCEL-06:** `import_whenProgramCancelledOrClosed_shouldThrowBadRequestException()`
- [x] **UT-EXCEL-07:** `preview_whenInternExistsInAnotherProgram_shouldBeValidAndFlagAsExistingUser()`
- [x] **UT-EXCEL-08:** `import_whenCandidateEnrolledInMultiplePrograms_shouldCreateNewProfileAndInheritUserId()`
- [x] **UT-EXCEL-09:** `import_whenDuplicateEmailInSameProgram_shouldThrowBadRequestException()`



---

## 13. Implementation Checklist (Danh Sách File Triển Khai Backend)

- [ ] **Build Configuration:**
  - `intern-and-program-service/build.gradle`: Bổ sung `implementation 'org.apache.poi:poi-ooxml:5.3.0'`.
- [ ] **DTOs (`program/dto/` hoặc `intern/dto/`):**
  - `ExcelRowError.java`: DTO biểu diễn 1 lỗi dòng trong file Excel.
  - `ExcelInternRowDto.java`: DTO chứa dữ liệu 1 dòng parse từ Excel.
  - `ExcelImportPreviewResponse.java`: Response DTO cho API preview.
  - `ExcelImportResultResponse.java`: Response DTO cho API import hoàn tất.
- [ ] **Service Layer:**
  - `InternExcelImportService.java`: Interface xử lý sinh template, preview và import.
  - `InternExcelImportServiceImpl.java`: Hiện thực logic đọc POI, validate, transaction, lock, saveAll.
- [ ] **Controller Layer:**
  - Cập nhật `ProgramController.java`: Thêm 3 endpoints `GET /programs/import-template`, `POST /programs/{id}/import-interns/preview`, `POST /programs/{id}/import-interns`.
- [ ] **Unit Tests:**
  - `InternExcelImportServiceTest.java`: Bộ test cases đạt độ bao phủ tối thiểu 85%.

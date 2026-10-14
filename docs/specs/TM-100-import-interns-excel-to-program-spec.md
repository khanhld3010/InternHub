# Specification: Thêm Thực Tập Sinh Vào Chương Trình Bằng Tệp Excel (TM-100)

> **Tài liệu Đặc Tả Kỹ Thuật (Feature Specification)**  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices - `intern-and-program-service`)  
> **Mã Jira Ticket:** [TM-100](https://robluccibn9935.atlassian.net/browse/TM-100): *Thêm thực tập sinh vào chương trình thực tập bằng tệp Excel (Excel Batch Import)*  
> **Trạng thái:** IMPLEMENTED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-100-import-interns-excel-to-program-spec.md`  
> **Cấp độ thay đổi (Change Level):** **L3** (Tích hợp thư viện Apache POI, xây dựng REST API Preview & Import MultipartFile, cơ chế Pessimistic Lock chống race condition over-booking, bảo vệ toàn vẹn dữ liệu 2 lớp, xử lý định dạng ô bảng tính Excel phức tạp, ghi nhận Audit log và kích hoạt thông báo sự kiện).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) và thư mục [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-09 | AI Senior Backend Pair-Programmer | `TM-100` | Tạo mới Đặc Tả | Xây dựng tài liệu đặc tả chuẩn 13 phần cho tính năng nhập danh sách thực tập sinh hàng loạt từ tệp Excel vào chương trình thực tập, giải quyết điểm nghẽn nhập liệu thủ công của HR. |
| **v1.1** | 2026-10-09 | AI Senior Backend Pair-Programmer | `TM-100` | Hoàn tất Triển khai Backend | Triển khai hoàn chỉnh toàn bộ 3 REST endpoints, DTOs, Repository batch queries, Service với Pessimistic Lock, xử lý Apache POI và 7 unit test cases pass 100%. |

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

### 4.2. Case 2: Trùng lặp Email / Số điện thoại 2 Lớp (Dual-Layer Duplicate)
- **Vấn đề:** 
  - Trùng lặp nội bộ: Dòng 3 và dòng 12 trong cùng một file Excel có cùng email hoặc SĐT.
  - Trùng lặp hệ thống: Email hoặc SĐT trong file Excel đã thuộc về một TTS khác trong CSDL.
- **Giải pháp:** 
  1. Duyệt qua danh sách để gom `Set<String> fileEmails` và `Set<String> filePhones`. Nếu kích thước Set nhỏ hơn kích thước danh sách ➔ Đánh dấu chính xác các dòng bị trùng lặp nội bộ.
  2. Thực hiện truy vấn kiểm tra nhanh: `internProfileRepository.findAllByEmailIn(...)` và `findAllByPhoneIn(...)` để phát hiện dòng nào trùng với CSDL và trả về thông báo lỗi cụ thể cho dòng đó.

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

- **BR-1 (Cấu trúc Cột Bắt Buộc):** Tệp Excel phải chứa các cột:
  1. *Họ và tên* (Bắt buộc, 2 - 100 ký tự)
  2. *Email* (Bắt buộc, định dạng chuẩn RFC 5322, duy nhất)
  3. *Số điện thoại* (Bắt buộc, 10 số VN bắt đầu bằng `03, 05, 07, 08, 09`, duy nhất)
  4. *Trường ĐH/CĐ* (Bắt buộc, tối đa 150 ký tự)
  5. *Chuyên ngành* (Bắt buộc, tối đa 100 ký tự)
  6. *Giới tính* (Tùy chọn: `Nam`, `Nữ`, `Khác` - Mặc định `Nam`)
  7. *Ngày sinh* (Tùy chọn: `dd/MM/yyyy`)
  8. *Địa chỉ* (Tùy chọn, tối đa 255 ký tự)
  9. *Niên khóa* (Tùy chọn, tối đa 50 ký tự)
  10. *Vị trí thực tập* (Tùy chọn - nếu để trống, tự động gán theo Tên chương trình)
  11. *Ghi chú* (Tùy chọn)
- **BR-2 (Nguyên Tắc All-or-Nothing):** Nếu file Excel có 50 dòng mà có dù chỉ 1 dòng bị lỗi format hoặc trùng dữ liệu, toàn bộ giao dịch bị hủy và trả về danh sách lỗi cho HR sửa.
- **BR-3 (Trạng thái và Thuộc tính Kế thừa):**
  - Mọi TTS import vào chương trình có `program_id = program.id`.
  - Thuộc tính `startDate` và `endDate` của TTS được tự động gán theo `startDate` và `endDate` của Chương trình.
  - Trạng thái mặc định là `APPROVED` (được tiếp nhận chính thức).

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
| `intern_code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Hệ thống tự sinh: `INT-yyyyMM-xxxx` |
| `full_name` | `VARCHAR(100)` | `NOT NULL` | Cột "Họ và tên" |
| `email` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | Cột "Email" |
| `phone` | `VARCHAR(20)` | `NOT NULL, UNIQUE` | Cột "Số điện thoại" |
| `date_of_birth` | `DATE` | `NULL` | Cột "Ngày sinh" |
| `gender` | `VARCHAR(20)` | `NOT NULL` | Cột "Giới tính" (Parse sang `Gender`) |
| `address` | `VARCHAR(255)` | `NULL` | Cột "Địa chỉ" |
| `university` | `VARCHAR(150)` | `NOT NULL` | Cột "Trường ĐH/CĐ" |
| `major` | `VARCHAR(100)` | `NOT NULL` | Cột "Chuyên ngành" |
| `academic_year` | `VARCHAR(50)` | `NULL` | Cột "Niên khóa" |
| `applied_position`| `VARCHAR(100)` | `NOT NULL` | Cột "Vị trí thực tập" (hoặc tên Program) |
| `start_date` | `DATE` | `NOT NULL` | Lấy từ `program.startDate` |
| `end_date` | `DATE` | `NULL` | Lấy từ `program.endDate` |
| `status` | `VARCHAR(20)` | `NOT NULL` | Mặc định `APPROVED` |
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
        "appliedPosition": "Java Backend Intern"
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
    "availableSlots": 5,
    "isQuotaExceeded": true,
    "errors": [
      {
        "rowNumber": 4,
        "fieldName": "email",
        "cellValue": "an.nguyen@example.com",
        "errorMessage": "Email 'an.nguyen@example.com' đã tồn tại trong hệ thống"
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
  "message": "Nhập thành công 15 thực tập sinh vào chương trình thực tập",
  "data": {
    "programId": 5,
    "programName": "Chương trình Java Backend K26",
    "importedCount": 15,
    "currentInterns": 18,
    "maxInterns": 25,
    "importedInternCodes": [
      "INT-202610-0012",
      "INT-202610-0013",
      "INT-202610-0014"
    ]
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
      ├── 5. Kiểm tra trùng lặp nội bộ trong file (Set Check)
      ├── 6. Kiểm tra trùng lặp với CSDL (Batch Repository Query)
      ├── 7. Nếu có lỗi ➔ Rollback, ném BadRequestException kèm chi tiết lỗi
      ├── 8. Kiểm tra Quota: activeCount + totalNew > maxInterns ➔ Ném BadRequestException
      ├── 9. Sinh mã INT-yyyyMM-xxxx tuần tự cho từng TTS
      ├── 10. Gán program, department, dates, status = APPROVED
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

- [ ] **AC-1:** HR gọi `GET /api/programs/import-template` tải về được tệp `.xlsx` có header tiếng Việt và mở được trên Microsoft Excel không bị lỗi.
- [ ] **AC-2:** Gọi `POST /api/programs/{id}/import-interns/preview` với file chuẩn trả về `totalRows`, `validRowsCount` khớp với nội dung file và `isQuotaExceeded = false`.
- [ ] **AC-3:** Khi tệp có dòng chứa email hoặc SĐT sai định dạng, Preview và Import trả về chính xác số dòng và thông báo lỗi tương ứng.
- [ ] **AC-4:** Khi tệp có số lượng người vượt quá số slot còn lại của chương trình, hệ thống chặn lại với thông báo chỉ rõ số lượng chỉ tiêu trống.
- [ ] **AC-5:** Khi import thành công, các TTS được lưu vào bảng `intern_profiles` với mã `internCode` định dạng `INT-yyyyMM-xxxx`, liên kết đúng `program_id`, và `currentInterns` của chương trình tăng tương ứng.
- [ ] **AC-6:** Khi chương trình ở trạng thái `COMPLETED`, `CANCELLED` hoặc `isRecruitmentOpen = false`, hệ thống từ chối import với mã lỗi `400 Bad Request`.
- [ ] **AC-7:** Khi user không có quyền `HR` hoặc `ADMIN`, trả về `403 Forbidden`.

---

## 12. Unit & Integration Test Cases Checklist

- [ ] **UT-EXCEL-01:** `preview_withValidExcelFile_shouldReturnAllValidRows()`
- [ ] **UT-EXCEL-02:** `preview_whenFileHasInvalidEmailAndPhone_shouldReturnDetailedErrors()`
- [ ] **UT-EXCEL-03:** `import_whenProgramIsFull_shouldThrowBadRequestException()`
- [ ] **UT-EXCEL-04:** `import_whenFileHasDuplicateInternalEmail_shouldThrowBadRequestException()`
- [ ] **UT-EXCEL-05:** `import_whenValidFile_shouldSaveAllInternsAndPublishEvents()`
- [ ] **UT-EXCEL-06:** `import_whenProgramCancelledOrClosed_shouldThrowBadRequestException()`
- [ ] **IT-EXCEL-01:** Kiểm thử endpoint `/api/programs/{id}/import-interns` qua MockMvc với file `.xlsx` giả lập.

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

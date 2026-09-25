# Specification: Quản Lý Chương Trình Thực Tập & Thiết Lập Thời Gian (TM-15 & TM-18)

> **Tài liệu Đặc Tả Kỹ Thuật (Feature Specification)**  
> **Dự án:** [InternHub](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub) (Backend Microservices) & [InternHub-Frontend](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend) (React + Vite)  
> **Mã Jira Tickets:**  
> - [TM-15](https://robluccibn9935.atlassian.net/browse/TM-15): *Tạo chương trình thực tập theo phòng ban*  
> - [TM-18](https://robluccibn9935.atlassian.net/browse/TM-18): *Thiết lập ngày bắt đầu và kết thúc chương trình thực tập*  
> **Nhánh Git:** `feature/TM-15-18/manage-internship-programs` (cả Backend và Frontend)  
> **Mức độ thay đổi (Change Level):** **L3** (Tạo Domain Entity mới `InternshipProgram`, `Department`, `ProgramCodeSequence`, REST API CRUD đầy đủ, tích hợp API Gateway routing, phân quyền RBAC, kiểm soát Pessimistic Write Lock chống over-booking, Cron Job quản lý vòng đời, giao diện quản trị HR Program Management với modal tạo/sửa).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/).

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Mục tiêu cốt lõi:**  
  Cung cấp cho Ban Nhân sự (HR) và Ban Quản trị (Admin) công cụ quản lý các đợt/chương trình thực tập chuyên nghiệp theo phòng ban (`department`), đồng thời cấu hình chặt chẽ khung thời gian diễn ra (`startDate`, `endDate`), chỉ tiêu tuyển sinh (`maxInterns`), tách biệt cờ dừng tuyển sinh (`isRecruitmentOpen`), liên kết hồ sơ thực tập sinh chặt chẽ và tự động hóa toàn bộ vòng đời chương trình.
- **Phân hệ chịu trách nhiệm:**
  1. **Backend (`intern-and-program-service` - Port 8082):**
     - Module mới: `org.example.internservice.program`
     - Domain Models: `InternshipProgram`, `Department` (kế thừa `BaseEntity`)
     - Cung cấp toàn bộ REST API cho HR/Admin quản lý chương trình và danh mục phòng ban.
     - Cập nhật API quyết định hồ sơ (`PATCH /api/interns/{id}/decision`) **bắt buộc có `programId`** khi `APPROVED` với cơ chế kiểm soát Pessimistic Write Lock chống over-booking.
     - Cron Job định kỳ quản lý vòng đời và xử lý các ca biên (quên mở tuyển, ân hạn 7 ngày, đóng chương trình).
  2. **API Gateway (`api-gateway` - Port 8080):**
     - Định tuyến `/api/programs/**` và `/api/departments/**` về `intern-and-program-service`.
  3. **Frontend (`InternHub-Frontend`):**
     - Menu Sidebar HR: Thêm mục **"Chương Trình Thực Tập"** (`/hr/programs`).
     - Trang Quản trị: `HrProgramManagementPage.tsx` hiển thị lưới/bảng chương trình, bộ lọc theo phòng ban và trạng thái.
     - Modal: `CreateProgramModal.tsx` & `EditProgramModal.tsx` chuẩn 3 khối với validation ngày bắt đầu < ngày kết thúc, toggle nhập dữ liệu lịch sử.
     - Modal duyệt hồ sơ: `ApproveConfirmModal.tsx` bắt buộc chọn chương trình mở còn chỉ tiêu.

---

## 2. Business Requirements & Core Domain Logic (Nghiệp Vụ Chi Tiết)

### 2.1. Quản lý Chương trình theo Phòng ban (TM-15)
- **Chuẩn hóa danh mục Phòng ban (`departments`):**
  - Tạo bảng/Entity `Department` (`id`, `name`, `code`, `description`, `status`) để quản lý danh mục phòng ban chuẩn tắc, quan hệ 1-N với `internship_programs`.
  - Hỗ trợ API seed/lấy danh sách phòng ban: `GET /api/departments` phục vụ các dropdown chọn trên Frontend.
  - Khi tạo chương trình, HR chọn `departmentId` từ danh sách có sẵn.
- **Quy tắc Mã chương trình (`programCode`):**
  - **Tự động sinh 100%** theo định dạng `PRG-yyyyMM-xxxx` (ví dụ: `PRG-202610-0001`), không cho phép người dùng sửa tay.
  - **Kiến trúc Chống Race Condition Bằng Bảng Sequence Độc Lập:**
    - Tuyệt đối **không** dùng `SELECT COUNT(...)` hoặc lock trên bảng chính `internship_programs` (tránh rủi ro phantom read và table lock).
    - Tạo bảng riêng `program_code_sequence` (`year_month` VARCHAR(6) PRIMARY KEY, `current_seq` BIGINT NOT NULL).
    - Khi tạo chương trình mới cho tháng `yyyyMM`:
      1. Khóa theo Khóa chính PK bằng `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE` trên dòng `year_month` cụ thể):
         ```java
         @Lock(LockModeType.PESSIMISTIC_WRITE)
         @Query("SELECT s FROM ProgramCodeSequence s WHERE s.yearMonth = :yearMonth")
         Optional<ProgramCodeSequence> findByYearMonthWithLock(@Param("yearMonth") String yearMonth);
         ```
      2. Tăng `current_seq = current_seq + 1`, format chuỗi 4 chữ số `String.format("%04d", nextSeq)` ➔ sinh ra `PRG-yyyyMM-xxxx`.
      3. Lưu bản ghi sequence.
    - **Lớp phòng thủ phụ (Defense-in-depth):**
      - Cột `program_code` trên bảng `internship_programs` có `UNIQUE CONSTRAINT`.
      - Bọc service method tạo chương trình bằng `@Retryable(retryFor = {DataIntegrityViolationException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2.0))` để tự động retry an toàn nếu xảy ra xung đột tải cao.

### 2.2. Thiết lập Khung thời gian & Cơ chế Dual-mode (TM-18)
Khắc phục rủi ro suy đoán ngầm bằng cơ chế **Gắn quy tắc vào trạng thái khởi tạo (Dual-mode Creation)**:

| Luồng tạo | Trạng thái khởi tạo | Quy tắc ngày áp dụng | Ràng buộc chỉ tiêu |
| :--- | :--- | :--- | :--- |
| **Tạo chương trình mới** (Mặc định: `isHistorical = false`) | `PLANNING` hoặc `OPEN` | `startDate >= hôm nay` (chặn ngày quá khứ), `endDate > startDate`. **Thời lượng tối thiểu:** `ChronoUnit.DAYS.between(startDate, endDate) >= 27` (tương đương bao trọn 4 tuần thực tế). | `maxInterns > 0` (bắt buộc nhập chỉ tiêu dự kiến). |
| **Nhập dữ liệu lịch sử** (HR bật `isHistorical = true`) | `COMPLETED` trực tiếp | Cho phép `startDate` và `endDate` hoàn toàn trong quá khứ (`endDate > startDate`). **Bỏ qua hoàn toàn điều kiện tối thiểu 4 tuần.** | Ẩn `maxInterns`, chỉ cho phép nhập `currentInterns` thực tế đã tốt nghiệp. |

- **Công Thức Tính Toán & Trải Nghiệm Người Dùng (UX Interaction):**
  - **Backend Validation:**
    ```java
    if (!Boolean.TRUE.equals(request.getIsHistorical())) {
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Ngày bắt đầu chương trình không được nằm trong quá khứ");
        }
        long daysBetween = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        if (daysBetween < 27) {
            throw new BadRequestException("Thời lượng chương trình thực tập tối thiểu phải từ 4 tuần trở lên (ít nhất 28 ngày học tập/làm việc)");
        }
    }
    ```
  - **Frontend Realtime Assistant:**
    - Khi HR chọn `startDate`, Frontend tự động gợi ý và điền sẵn `endDate = startDate + 4 tuần` (28 ngày).
    - Hiển thị badge realtime bên cạnh khung chọn ngày: *"Thời lượng: X tuần (Y ngày)"*. Nếu `Y < 27` ngày, hiển thị thông báo lỗi màu đỏ ngay dưới ô `endDate` và vô hiệu hóa nút submit.

- **Tách biệt Tuyển sinh (`isRecruitmentOpen`) và Vòng đời chương trình (`status`):**
  - Bổ sung cờ: `isRecruitmentOpen: boolean` (mặc định `true`).
  - Khi chương trình chạm `maxInterns` hoặc HR chủ động bấm "Dừng nhận hồ sơ": `isRecruitmentOpen = false`.
  - Hệ quả: Ẩn chương trình khỏi dropdown chọn duyệt TTS mới, nhưng chương trình và các TTS đã duyệt vẫn học tập và chuyển trạng thái `ONGOING` bình thường.

### 2.3. Cơ Chế Liên Kết Chương Trình & Thực Tập Sinh (Intern-Program Linkage)
1. **Chuẩn hóa quan hệ dữ liệu:**
   - Bổ sung cột `program_id` (nullable, FK `internship_programs(id)`) vào bảng `intern_profiles`.
   - Một chương trình chứa nhiều thực tập sinh (`1 - N`).
   - Phòng ban của thực tập sinh được **kế thừa trực tiếp** từ `department_id` của Chương trình thực tập tiếp nhận (loại bỏ hoàn toàn rủi ro lệch phòng ban giữa ứng viên và chương trình).
2. **Quy tắc BẮT BUỘC chọn Program khi Duyệt hồ sơ (Mandatory on Approval):**
   - Khi HR duyệt hồ sơ (`decision = 'APPROVED'`): **Bắt buộc phải chọn `programId`** (Backend ném `400 BadRequest` nếu thiếu).
   - **Cơ chế Chống Race Condition Over-booking (Pessimistic Write Lock):**
     - Tuyệt đối **không** dùng `current_interns` như 1 counter sống tăng/giảm thủ công.
     - Trong transaction duyệt hồ sơ, Backend thực hiện khóa ghi đối với chương trình thông qua repository:
       ```java
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM InternshipProgram p WHERE p.id = :id")
       Optional<InternshipProgram> findByIdWithLock(@Param("id") Long id);
       ```
     - Sau khi nhận lock ghi, đếm số lượng thực tế hiện tại qua `intern_profiles`:
       ```java
       long activeCount = internRepository.countByProgramIdAndStatusIn(programId, List.of(APPROVED, INTERNING, COMPLETED));
       if (activeCount >= program.getMaxInterns()) {
           throw new BadRequestException("Chương trình đã đạt giới hạn chỉ tiêu tiếp nhận (" + program.getMaxInterns() + " TTS)");
       }
       ```
     - Khi 2 HR cùng duyệt 2 ứng viên vào slot cuối cùng, giao dịch thứ hai bắt buộc phải đợi (block) giao dịch thứ nhất commit. Khi giao dịch thứ hai vào đọc, `activeCount` đã chạm ngưỡng và nhận ngay lỗi `400 Bad Request` an toàn, triệt tiêu hoàn toàn rủi ro over-booking mà không phụ thuộc vào việc cập nhật entity chương trình.
3. **Cơ chế Duy Nhất của Cột `current_interns`:**
   - **Mục đích duy nhất:** Cột `current_interns` trong bảng `internship_programs` **CHỈ** dùng để lưu giá trị số lượng TTS thực tế đã tốt nghiệp đối với chương trình lịch sử (`is_historical = true`).
   - Đối với tất cả chương trình sống (`is_historical = false`): Mọi nơi hiển thị số lượng (trên bảng quản trị, modal chi tiết, API response) **đều phải tính toán động qua truy vấn COUNT** (trực tiếp theo `programId` hoặc gom nhóm `GROUP BY` khi lấy danh sách phân trang), tuyệt đối không đọc từ cột này và không can thiệp tăng/giảm thủ công.
4. **Tương tác 2 chiều (Click-through Navigation):**
   - Từ bảng Program (`/hr/programs`): Click vào số lượng `current_interns` sẽ điều hướng sang `/hr/interns?programId={id}` để xem ngay danh sách TTS thuộc chương trình đó.
   - Từ Modal chi tiết TTS (`DetailInternModal`): Hiển thị Tên chương trình tiếp nhận (clickable) để mở nhanh thông tin chương trình.

---

## 3. Ma Trận Chuyển Đổi Trạng Thái (State Transition Matrix)

Phần này đặc tả toàn bộ các trạng thái, đường đi hợp lệ, điều kiện chuyển đổi và cơ chế bảo vệ tính toàn vẹn trạng thái của Chương trình thực tập (`ProgramStatus`).

### 3.1. Sơ Đồ Chuyển Đổi Trạng Thái (State Transition Diagram)

```text
       ┌───────────┐         ┌───────────┐         ┌───────────┐         ┌───────────┐
       │ PLANNING  │ ──────> │   OPEN    │ ──────> │  ONGOING  │ ──────> │ COMPLETED │ (Terminal)
       └─────┬─────┘         └─────┬─────┘         └─────┬─────┘         └───────────┘
             │                     │                     │
             │   (Cron auto-chain  │                     │
             │   nếu có TTS)       │                     │
             ├─────────────────────┘                     │
             │                                           │
             └───────────────────────────────────────────┴─────────────> ┌───────────┐
                                (HR hủy hoặc cron ân hạn 7 ngày)         │ CANCELLED │ (Terminal)
                                                                         └───────────┘
```

### 3.2. Bảng Ma Trận Chuyển Đổi Chi Tiết

| Trạng thái hiện tại | Trạng thái đích | Tác nhân | Điều kiện & Nghiệp vụ chi tiết |
| :--- | :--- | :--- | :--- |
| **`PLANNING`** | `OPEN` | HR / Admin | HR chủ động mở tuyển sinh sớm khi cấu hình xong chương trình (khi `today < startDate`). |
| **`PLANNING`** | `ONGOING` | Cron Job tự động | **Xử lý case HR quên mở tuyển:** Tới ngày `startDate` (`today >= startDate`), nếu kiểm tra động thấy chương trình đã có TTS (`internRepository.existsByProgramIdAndStatusIn(programId, List.of(APPROVED, INTERNING, COMPLETED))`), Cron Job tự động chuyển chuỗi `PLANNING ➔ OPEN ➔ ONGOING` trong 1 transaction an toàn, ghi nhận Audit Log. |
| **`PLANNING`** | `CANCELLED` | HR hoặc Cron Job | 1. **HR hủy thủ công:** Bắt buộc nhập lý do hủy `cancellationReason`.<br>2. **Cron Job tự động:** Sau **7 ngày ân hạn** (`today >= startDate + 7 days`) mà chương trình vẫn ở `PLANNING` và không có bất kỳ TTS nào (`!internRepository.existsByProgramIdAndStatusIn(...)`), tự động hủy với lý do hệ thống. |
| **`OPEN`** | `ONGOING` | Cron Job tự động | Tự động kích hoạt khi ngày hiện tại đạt ngày bắt đầu (`today >= startDate` và `today <= endDate`). Không yêu cầu can thiệp thủ công từ HR. |
| **`OPEN`** | `CANCELLED` | HR / Admin | HR chủ động hủy đợt tuyển sinh. Bắt buộc nhập lý do hủy `cancellationReason`. Kích hoạt quy tắc cascade bảo vệ ứng viên (mục 4.2). |
| **`ONGOING`** | `COMPLETED` | Cron Job tự động | Tự động chuyển đổi sau khi ngày làm việc cuối cùng kết thúc (`today > endDate` hay `today >= endDate.plusDays(1)`). Trong suốt ngày `endDate`, chương trình vẫn là `ONGOING`. |
| **`ONGOING`** | `CANCELLED` | HR / Admin | Hủy chương trình khẩn cấp giữa kỳ (do biến động doanh nghiệp). Bắt buộc nhập `cancellationReason`. Kích hoạt cờ `needs_reassignment` cho toàn bộ TTS đang thực tập (mục 4.2). |
| **`COMPLETED`** | *None (Terminal)* | - | **Trạng thái kết thúc:** Không thể chuyển sang bất kỳ trạng thái nào khác. Dữ liệu chỉ đọc. |
| **`CANCELLED`** | *None (Terminal)* | - | **Trạng thái đóng:** Không thể chuyển sang bất kỳ trạng thái nào khác. Dữ liệu chỉ phục vụ tra cứu và kiểm toán. |

### 3.3. Quy Tắc Chặn Chuyển Đổi Không Hợp Lệ (Invalid Transition Guard)
- API `PATCH /api/programs/{id}/status` bắt buộc validate qua bảng ma trận trên.
- Mọi nỗ lực chuyển đổi không nằm trong ma trận (ví dụ: `COMPLETED ➔ OPEN`, `CANCELLED ➔ PLANNING`, `ONGOING ➔ OPEN`) sẽ bị chặn ngay lập tức và trả về mã lỗi `400 Bad Request`:
  ```json
  {
    "statusCode": 400,
    "message": "Không thể chuyển trạng thái chương trình từ {currentStatus} sang {targetStatus}."
  }
  ```

### 3.4. Ma Trận Khóa Dữ Liệu Chỉnh Sửa Theo Trạng Thái (Data Edit Lock Rules)
Gắn liền với vòng đời của State Machine, API `PUT /api/programs/{id}` và giao diện Frontend tuân thủ nghiêm ngặt ma trận khóa quyền sửa đổi dữ liệu sau:

| Trạng thái Chương trình | Sửa `startDate` | Sửa `endDate` | Sửa `name`, `description`, `departmentId` | Sửa `maxInterns` |
| :--- | :---: | :---: | :---: | :---: |
| **`PLANNING`** | ✅ Cho phép | ✅ Cho phép | ✅ Cho phép | ✅ Cho phép |
| **`OPEN`** | ✅ Cho phép (`>= today`) | ✅ Cho phép | ✅ Cho phép | ✅ Cho phép (`>= count active`) |
| **`ONGOING`** | ❌ **KHÓA CỨNG** (Không cho sửa lùi/tiến ngày bắt đầu) | ✅ Cho phép (Gia hạn kỳ thực tập) | ✅ Cho phép | ✅ Cho phép (`>= count active`) |
| **`COMPLETED`** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** |
| **`CANCELLED`** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** | ❌ **KHÓA TOÀN BỘ FORM** |

- **Backend Enforcement:**
  - Nếu `status == ONGOING` và request gửi `startDate` khác với `startDate` hiện tại trong DB ➔ Ném `400 Bad Request`: *"Không thể thay đổi ngày bắt đầu khi chương trình thực tập đang diễn ra (ONGOING)"*.
  - Nếu `status IN (COMPLETED, CANCELLED)` và nhận request `PUT` ➔ Ném `400 Bad Request`: *"Không thể chỉnh sửa thông tin chương trình đã kết thúc hoặc đã hủy"*.
- **Frontend UI Enforcement:**
  - Tại `EditProgramModal.tsx`: Field `startDate` bị `disabled = true` kèm tooltip giải thích nếu chương trình đang `ONGOING`.
  - Nút `[Chỉnh sửa]` trên `ProgramTable.tsx` bị ẩn hoặc disabled khi trạng thái là `COMPLETED` hoặc `CANCELLED` (chỉ hiển thị nút `[Xem chi tiết]`).

---

## 4. Cơ Chế Xử Lý Tự Động & Ràng Buộc Dữ Liệu (Automation & Data Integrity)

### 4.1. Hành Vi Tự Động Của Cron Job Vòng Đời (`ProgramLifecycleJob`)
Chạy định kỳ mỗi ngày (ví dụ `0 0 1 * * ?` - lúc 01:00 AM):
1. **Chuyển `ONGOING` tự động:**
   - Quét các chương trình `status = OPEN` có `today >= startDate` VÀ `today <= endDate` ➔ Chuyển sang `ONGOING`.
   - Quét các chương trình `status = PLANNING` có `today >= startDate` VÀ `today <= endDate`:
     - Kiểm tra động nguồn sự thật duy nhất qua repository:
       ```java
       boolean hasActiveInterns = internRepository.existsByProgramIdAndStatusIn(
           program.getId(), List.of(InternStatus.APPROVED, InternStatus.INTERNING, InternStatus.COMPLETED)
       );
       ```
     - Nếu `hasActiveInterns == true`: Tự động chuyển chuỗi `PLANNING ➔ OPEN ➔ ONGOING` trong 1 transaction an toàn và ghi nhận Audit Log.
2. **Quy tắc Ân Hạn 07 Ngày (Grace Period 7 Days):**
   - Nếu chương trình ở trạng thái `PLANNING` mà đã quá `startDate + 7 ngày` (`today >= startDate.plusDays(7)`):
     - Kiểm tra động: `boolean hasActiveInterns = internRepository.existsByProgramIdAndStatusIn(program.getId(), List.of(APPROVED, INTERNING, COMPLETED));`
     - Nếu `hasActiveInterns == false` (hoàn toàn không có bất kỳ TTS nào tiếp nhận):
       - ➔ Cron job tự động chuyển `status` sang `CANCELLED`.
       - ➔ Ghi nhận `cancellation_reason`: *"Hệ thống tự động hủy chương trình do quá 7 ngày kể từ ngày bắt đầu dự kiến mà không có thực tập sinh tiếp nhận"*.
3. **Chuyển `COMPLETED` tự động (Quy ước `endDate` là Ngày Làm Việc Cuối Cùng - Inclusive):**
   - **Quy ước:** `endDate` là ngày làm việc cuối cùng của thực tập sinh (TTS vẫn đến công ty làm việc, bàn giao, nhận đánh giá trong suốt ngày `endDate`).
   - Do đó, trong suốt ngày `endDate` (từ 00:00 đến 23:59:59 của `endDate`), trạng thái chương trình **vẫn duy trì là `ONGOING`**.
   - Cron Job chạy vào đầu ngày hôm sau (lúc 01:00 AM) quét các chương trình có `today > endDate` (tức `today >= endDate.plusDays(1)`) ➔ Chuyển sang `COMPLETED`. Tuyệt đối không chuyển giữa ngày hoặc sáng sớm ngày `endDate`.

### 4.2. Xử Lý Cascade Khi Hủy Chương Trình (`CANCELLED`)
Khi một chương trình bị HR hủy hoặc đóng sớm giữa chừng:
- **Nguyên tắc bảo toàn lịch sử:** **TUYỆT ĐỐI KHÔNG SET NULL `program_id`** trên bảng `intern_profiles` (đảm bảo tính toàn vẹn kiểm toán và báo cáo).
- **Cơ chế gắn cờ điều phối lại (`needs_reassignment`):**
  - Tất cả các Thực tập sinh thuộc chương trình đó đang mang trạng thái `APPROVED` hoặc `INTERNING`:
    - Giữ nguyên trạng thái `status` chính (`APPROVED` / `INTERNING`).
    - Bật cờ nghiệp vụ trên `intern_profiles`: **`needs_reassignment = true`**, lưu `reassignment_reason = "Chương trình [Mã CT] đã bị hủy: [Lý do hủy]"`.
  - Trên Frontend: Hiển thị tag cảnh báo màu cam nổi bật: **`⚠️ Cần phân bổ lại CT`** trong danh sách thực tập sinh để HR dễ dàng lọc và tái phân bổ vào chương trình mới còn chỉ tiêu.
  - Khi HR phân bổ sang chương trình mới thành công, `program_id` được trỏ sang ID mới và `needs_reassignment` tự động tắt về `false`.

### 4.3. Quy Tắc Khóa Dữ Liệu Khi Cập Nhật (`PUT /api/programs/{id}`)
- Khi `status == ONGOING`: **Khóa cứng trường `startDate`** (chỉ cho phép sửa `endDate` để gia hạn, `description`, `name`, `maxInterns`).
- Khi `status == COMPLETED` hoặc `CANCELLED`: **Khóa toàn bộ form** (chỉ đọc, ném `400 BadRequest` nếu cố tình gửi request PUT).

### 4.4. Cơ Chế Guard Chặt Chẽ Khi DELETE (`DELETE /api/programs/{id}`)
Để bảo vệ toàn vẹn dữ liệu quan hệ và lịch sử hồ sơ ứng viên, thao tác xóa vật lý (`HARD DELETE`) chương trình thực tập được thiết kế với **3 lớp chốt chặn liên hoàn (3-Layer Defense)**:

```text
Request DELETE /api/programs/{id}
       │
       ▼
 [Lớp 1: Phân Quyền] ──(Không phải ADMIN)──> 403 Forbidden
       │ (Hợp lệ: ROLE_ADMIN)
       ▼
 [Lớp 2: Kiểm Tra Trạng Thái] ──(status != PLANNING)──> 400 BadRequest ("Chỉ được xóa khi ở trạng thái PLANNING...")
       │ (status == PLANNING)
       ▼
 [Lớp 3: Kiểm Tra Hồ Sơ TTS] ──(countByProgramId > 0)──> 400 BadRequest ("Đã có hồ sơ gắn kèm. Vui lòng chuyển CANCELLED.")
       │ (countByProgramId == 0)
       ▼
 [Thực Hiện Xóa Vật Lý Khỏi DB] ──> 204 No Content
```

#### Chi tiết triển khai kỹ thuật 3 lớp:
1. **Lớp 1: Phân quyền gắt gao (RBAC):**
   - Chỉ duy nhất tài khoản mang quyền `ROLE_ADMIN` mới có thể gọi endpoint `DELETE /api/programs/{id}`. `ROLE_HR` chỉ có quyền đổi trạng thái sang `CANCELLED`.
2. **Lớp 2: Chặn theo Vòng đời (Lifecycle Guard):**
   - Kiểm tra `program.getStatus() != ProgramStatus.PLANNING`.
   - Nếu chương trình đã từng sang `OPEN`, `ONGOING`, `COMPLETED` hay `CANCELLED` ➔ Ném ngay `BadRequestException`:
     ```text
     "Chỉ có thể xóa chương trình đang ở trạng thái Kế hoạch (PLANNING). Với các chương trình khác, vui lòng chuyển trạng thái sang CANCELLED."
     ```
3. **Lớp 3: Chặn phụ thuộc dữ liệu ngoại lai (Zero Reference Guard):**
   - Truy vấn đếm **tất cả mọi hồ sơ thực tập sinh** có `program_id = :id` (không phân biệt status, bao gồm cả `PENDING`, `APPROVED`, `REJECTED`, `INTERNING`, `COMPLETED`):
     ```java
     long totalLinkedInterns = internRepository.countByProgramId(programId);
     if (totalLinkedInterns > 0) {
         throw new BadRequestException("Không thể xóa chương trình đã có " + totalLinkedInterns + " hồ sơ ứng viên gắn kèm. Hãy chuyển trạng thái sang CANCELLED.");
     }
     ```
   - **Lý do bảo vệ:** Nếu xóa một chương trình mà đã có hồ sơ (dù là hồ sơ bị Rejected hay Pending), dữ liệu của bảng `intern_profiles` sẽ bị mồ côi (`orphan`) hoặc gây vi phạm Foreign Key Constraint ở tầng Database.
4. **Phía Giao diện Frontend (`ProgramTable.tsx`):**
   - Nút `[Xóa]` (icon thùng rác đỏ) **chỉ hiển thị** khi:
     - User hiện tại có role `ADMIN`.
     - Dòng chương trình có `status === 'PLANNING'`.
     - `internCount === 0`.
   - Đối với tất cả các dòng còn lại: Ẩn hoàn toàn nút Xóa hoặc disable kèm Tooltip giải thích: *"Chương trình đã phát sinh dữ liệu hoặc đã qua giai đoạn kế hoạch. Vui lòng chọn Hủy chương trình (CANCELLED)."*
   - Khi bấm Xóa: Bắt buộc mở Modal xác nhận nguy hiểm (Type confirming modal: Nhập đúng mã chương trình ví dụ `PRG-202610-0001` mới cho bấm nút Xác nhận xóa).

---

## 5. Database Schema Design (Thiết Kế CSDL)

### 5.1. Bảng `departments`:
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | Khóa chính |
| `name` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | Tên phòng ban (vd: "Phát triển phần mềm") |
| `code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Mã phòng ban (vd: "DEV", "HR", "QA") |
| `description` | `VARCHAR(255)` | `NULL` | Mô tả chức năng |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'ACTIVE'` | `ACTIVE`, `INACTIVE` |

### 5.2. Bảng `internship_programs`:
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | Khóa chính kế thừa từ `BaseEntity` |
| `program_code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Mã CT: `PRG-yyyyMM-xxxx` |
| `name` | `VARCHAR(150)` | `NOT NULL` | Tên chương trình |
| `department_id` | `BIGINT` | `NOT NULL, FK -> departments(id)` | Phòng ban tiếp nhận |
| `description` | `TEXT` | `NULL` | Mô tả chi tiết / Lộ trình đào tạo |
| `max_interns` | `INT` | `NOT NULL, DEFAULT 10` | Chỉ tiêu tiếp nhận tối đa |
| `current_interns` | `INT` | `NOT NULL, DEFAULT 0` | Số TTS thực tế (CHỈ dùng lưu giá trị tĩnh khi `is_historical = true`) |
| `start_date` | `DATE` | `NOT NULL` | Ngày bắt đầu |
| `end_date` | `DATE` | `NOT NULL` | Ngày kết thúc |
| `is_recruitment_open` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | Cờ mở nhận hồ sơ tuyển dụng |
| `is_historical` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | Flag nhận diện dữ liệu lịch sử |
| `status` | `VARCHAR(20)` | `NOT NULL` | `PLANNING`, `OPEN`, `ONGOING`, `COMPLETED`, `CANCELLED` |
| `cancellation_reason` | `VARCHAR(255)` | `NULL` | Lý do hủy chương trình |
| `created_by` | `VARCHAR(100)` | `NULL` | Username của HR/Admin tạo |
| `created_at` | `DATETIME(6)` | `NOT NULL` | Thời gian tạo (kế thừa `BaseEntity`) |
| `updated_at` | `DATETIME(6)` | `NULL` | Thời gian cập nhật (kế thừa `BaseEntity`) |

### 5.3. Bảng `program_code_sequence`:
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `year_month` | `VARCHAR(6)` | `PRIMARY KEY` | Khóa chính lưu chuỗi tháng năm định dạng `yyyyMM` (vd: "202610") |
| `current_seq` | `BIGINT` | `NOT NULL, DEFAULT 0` | Số thứ tự hiện tại của tháng đó |
| `updated_at` | `DATETIME(6)` | `NOT NULL` | Thời gian cập nhật số thứ tự |

### 5.4. Cập nhật Bảng `intern_profiles`:
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `program_id` | `BIGINT` | `NULL, FK -> internship_programs(id)` | Chương trình thực tập tiếp nhận (Bắt buộc khi APPROVED) |
| `needs_reassignment` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | Cờ cảnh báo cần điều phối lại khi chương trình bị hủy |
| `reassignment_reason` | `VARCHAR(255)` | `NULL` | Lý do cần phân bổ lại |

---

## 6. REST API Contract & DTO Models

### 6.1. Routing tại API Gateway (`config-repo-local/api-gateway.yml`):
```yaml
- id: program-service
  uri: lb://intern-and-program-service
  predicates:
    - Path=/api/programs/**, /api/departments/**
```

### 6.2. Danh sách API Endpoints:

| Method | Endpoint | Quyền (RBAC) | Mô Tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/departments` | `AUTHENTICATED` | Lấy danh sách phòng ban cho dropdown |
| `POST` | `/api/programs` | `ROLE_HR`, `ROLE_ADMIN` | Tạo mới chương trình (chặn quá khứ và thời lượng < 4 tuần trừ khi `isHistorical = true`) |
| `GET` | `/api/programs` | `ROLE_HR`, `ROLE_ADMIN`, `ROLE_MENTOR` | Danh sách & phân trang (bộ lọc: `keyword`, `departmentId`, `status`, tính `currentInterns` động qua COUNT) |
| `GET` | `/api/programs/{id}` | `AUTHENTICATED` | Xem chi tiết: HR/Admin thấy DTO đầy đủ, Intern thấy `ProgramSummaryResponse` rút gọn |
| `PUT` | `/api/programs/{id}` | `ROLE_HR`, `ROLE_ADMIN` | Cập nhật thông tin (khóa `startDate` nếu `ONGOING`, cấm sửa nếu `COMPLETED`/`CANCELLED`) |
| `PATCH` | `/api/programs/{id}/status` | `ROLE_HR`, `ROLE_ADMIN` | Chuyển đổi trạng thái theo State Transition Matrix |
| `PATCH` | `/api/programs/{id}/recruitment-toggle` | `ROLE_HR`, `ROLE_ADMIN` | Đóng/mở cờ nhận hồ sơ `isRecruitmentOpen` |
| `DELETE` | `/api/programs/{id}` | `ROLE_ADMIN` | Xóa chương trình (chỉ khi `PLANNING` và chưa có bất kỳ hồ sơ nào) |
| `PATCH` | `/api/interns/{id}/decision` | `ROLE_HR`, `ROLE_ADMIN` | Mở rộng: bắt buộc `programId` khi `APPROVED`, kiểm tra chỉ tiêu bằng Pessimistic Write Lock |

### 6.3. Chi Tiết Phân Tách DTO Response Theo Vai Trò (RBAC Response Projection)

Nhằm bảo đảm nguyên tắc **Principle of Least Privilege** và an toàn thông tin nội bộ (không để lộ chỉ tiêu tuyển dụng, tiến độ tuyển, danh tính HR tạo chương trình hoặc các cờ quản trị ra phía ứng viên/Intern), hệ thống tách biệt 2 DTO chiếu theo vai trò đăng nhập:

#### A. `ProgramDetailResponse` (Dành cho Quản trị: `ROLE_HR`, `ROLE_ADMIN`, `ROLE_MENTOR`)
Chứa toàn bộ thông số quản trị, chỉ tiêu và trạng thái vòng đời chi tiết:
```json
{
  "id": 1,
  "programCode": "PRG-202610-0001",
  "name": "Chương Trình Thực Tập Kỹ Sư Phần Mềm Mùa Thu 2026",
  "departmentId": 2,
  "departmentName": "Phát triển phần mềm",
  "departmentCode": "DEV",
  "description": "Lộ trình đào tạo Backend Java Spring Boot & Microservices trong 12 tuần.",
  "maxInterns": 15,
  "currentInterns": 8,
  "availableSlots": 7,
  "startDate": "2026-10-01",
  "endDate": "2026-12-24",
  "durationWeeks": 12,
  "status": "OPEN",
  "isRecruitmentOpen": true,
  "isHistorical": false,
  "cancellationReason": null,
  "createdBy": "hr_manager",
  "createdAt": "2026-09-24T10:00:00",
  "updatedAt": "2026-09-24T14:30:00"
}
```

#### B. `ProgramSummaryResponse` (Dành cho Ứng viên / Intern: `ROLE_INTERN` hoặc Public View)
Chỉ cung cấp thông tin học tập và đào tạo cần thiết cho thực tập sinh, **ẩn toàn bộ số liệu nhạy cảm**:
- ❌ Không có `maxInterns`, `currentInterns`, `availableSlots` (tránh tạo áp lực hoặc lộ kế hoạch nhân sự).
- ❌ Không có `isHistorical`, `createdBy`, `isRecruitmentOpen`, `cancellationReason`.
```json
{
  "id": 1,
  "programCode": "PRG-202610-0001",
  "name": "Chương Trình Thực Tập Kỹ Sư Phần Mềm Mùa Thu 2026",
  "departmentName": "Phát triển phần mềm",
  "description": "Lộ trình đào tạo Backend Java Spring Boot & Microservices trong 12 tuần.",
  "startDate": "2026-10-01",
  "endDate": "2026-12-24",
  "durationWeeks": 12,
  "status": "OPEN"
}
```

#### C. Quy tắc Điều Hướng tại Tầng Controller (`ProgramController.java`)
Trong endpoint `GET /api/programs/{id}`:
```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<?>> getProgramDetail(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
    
    boolean isPrivileged = userDetails.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_HR") 
                    || a.getAuthority().equals("ROLE_ADMIN") 
                    || a.getAuthority().equals("ROLE_MENTOR"));

    if (isPrivileged) {
        ProgramDetailResponse detail = programService.getProgramDetailById(id);
        return ResponseEntity.ok(ApiResponse.success(detail));
    } else {
        ProgramSummaryResponse summary = programService.getProgramSummaryById(id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
```

---

## 7. Thiết Kế Giao Diện Frontend (`InternHub-Frontend`)

1. **Điều hướng Sidebar HR:**
   - Thêm item: `Chương Trình Thực Tập` với icon `FolderGit2` hoặc `Layers`, dẫn tới URL `/hr/programs`.
2. **Trang `HrProgramManagementPage.tsx` (`/hr/programs`):**
   - **Header:** Tiêu đề *"Quản Lý Chương Trình Thực Tập"*, nút `[+ Tạo Chương Trình Mới]`.
   - **Thanh lọc (`ProgramFilterBar`):** Tìm kiếm theo tên/mã, chọn phòng ban từ dropdown, lọc theo trạng thái (`Tất cả`, `Đang mở tuyển`, `Đang diễn ra`, `Kế hoạch`, `Đã kết thúc`, `Đã hủy`).
   - **Bảng danh sách (`ProgramTable`):** Cột Mã CT, Tên chương trình, Phòng ban, Thời gian (`dd/MM/yyyy - dd/MM/yyyy` kèm số tuần), Chỉ tiêu (`current/max` - tính qua COUNT), Toggle đóng/mở tuyển sinh trực tiếp, Badge trạng thái màu riêng biệt, Actions (Xem chi tiết, Sửa, Đổi trạng thái).
3. **Modal `CreateProgramModal.tsx` & `EditProgramModal.tsx`:**
   - **Checkbox/Toggle:** *"Đây là chương trình đã kết thúc (Nhập dữ liệu lịch sử)"*.
     - Mặc định: tắt. `startDate` validate `>= today`, `endDate - startDate >= 4 tuần`.
     - Khi bật: cho phép chọn ngày quá khứ, tự động set status = `COMPLETED`, chuyển field `maxInterns` thành `currentInterns` (Số TTS đã tốt nghiệp).
   - Tự động tính toán số tuần và hiển thị realtime (ví dụ: *"Thời lượng: 12 tuần"*).
4. **Cập nhật Modal Duyệt Hồ Sơ (`ApproveConfirmModal.tsx`):**
   - Bắt buộc có Select chọn Chương trình tiếp nhận (chỉ hiển thị các chương trình `OPEN`/`PLANNING` còn chỉ tiêu).
   - Hiển thị badge chỉ tiêu bên cạnh tên chương trình: *(còn 3/15 slot)*.
5. **Cảnh báo Reassignment trên danh sách TTS:**
   - Hiển thị nhãn `⚠️ Cần phân bổ lại CT` khi hồ sơ có `needs_reassignment = true`.
   - Cho phép HR nhấp để mở modal điều phối sang chương trình mới còn chỉ tiêu.

---

## 8. Kế Hoạch Kiểm Thử & Xác Minh (Verification Plan)

1. **Unit Test Backend:**
   - Test tạo chương trình thường thành công khi `startDate >= today` và khoảng cách >= 4 tuần.
   - Test ném `BadRequestException` khi tạo chương trình thường với thời lượng < 4 tuần.
   - Test tạo thành công chương trình lịch sử khi bật `isHistorical = true`.
   - Test chặn chuyển đổi trạng thái không hợp lệ trong State Machine (vd: `COMPLETED` ➔ `OPEN`, `ONGOING` ➔ `OPEN`).
   - Test Cron Job tự động chuyển chuỗi `PLANNING ➔ OPEN ➔ ONGOING` và tự hủy sau 7 ngày không có TTS.
   - Test Pessimistic Write Lock chống over-booking khi 2 thread cùng duyệt vào slot cuối cùng (thread thứ 2 nhận `400 BadRequest`).
   - Test Cascade cờ `needs_reassignment` khi hủy chương trình và không xóa `program_id`.
2. **Biên dịch & Build:**
   - Chạy `./gradlew :intern-and-program-service:compileJava` và `bootJar`.
   - Chạy `npm run build` trên `InternHub-Frontend`.
3. **Kiểm thử Thực tế:**
   - Tạo chương trình thường và chương trình lịch sử trên UI.
   - Duyệt hồ sơ thực tập sinh và xác nhận chỉ tiêu cập nhật tự động, link 2 chiều click-through hoạt động chính xác.

# Đặc Tả Kỹ Thuật (Technical Specification)
## Mã Tính Năng: `TM-HR-FINAL-EVALUATION-HUB`
## Tên Tính Năng: Trung Tâm Phê Duyệt Đánh Giá Cuối Kỳ & Xuất Báo Cáo Sinh Viên (HR Final Evaluation Hub & Transcript Export)

---

### 1. Bối cảnh & Mục tiêu (Context & Objectives)
* **Vấn đề thực tế:** Khi sinh viên chuẩn bị kết thúc kỳ thực tập, Nhà trường bắt buộc Doanh nghiệp phải nộp Phiếu Đánh Giá & Bảng Điểm Thực Tập (có nhận xét, kết quả xếp loại và dấu mộc Doanh nghiệp) để tính tín chỉ tốt nghiệp.
* **Hiện trạng hệ thống:**
  * Mentor đã có thể chấm điểm cuối kỳ qua màn hình Mentor (`intern_evaluations` có `technicalScore`, `attitudeScore`, `softSkillsScore`, `weeklyAssessmentAvgScore`, `finalScore`, `recommendation`).
  * Tuy nhiên, HR hiện chưa có màn hình tập trung để rà soát điểm của Mentor, chưa có bước Ký duyệt chính thức đại diện Doanh nghiệp, và chưa có công cụ In / Xuất PDF phiếu đánh giá chuẩn form để gửi về Nhà trường.
* **Mục tiêu của tính năng:**
  1. Cung cấp một không gian làm việc chuyên trách cho HR tại `/hr/evaluations` (Menu Sidebar: **"Đánh Giá Cuối Kỳ"**).
  2. **Quy tắc Duyệt an toàn (Strict Single Approval):** HR bắt buộc duyệt **từng hồ sơ một**. Khi duyệt, HR nhập ý kiến nhận xét của Phòng Nhân sự, tích xác nhận Hoàn thành và hệ thống chuyển trạng thái đánh giá thành `APPROVED`, đồng thời cập nhật trạng thái TTS thành `COMPLETED`. **Tuyệt đối không cho phép duyệt hàng loạt (No Bulk Approval)**.
  3. **Quy tắc Xuất PDF tiện lợi (Safe Bulk Export):** Cho phép xuất PDF lẻ từng bạn hoặc tích chọn nhiều hồ sơ đã có trạng thái `APPROVED` để **Xuất / Tải trọn bộ Phiếu Đánh Giá (.PDF)** gửi về trường một lượt trước deadline.

---

### 2. Các Ràng Buộc Kỹ Thuật & Chất Lượng (Quality Constraints)
Áp dụng theo kỹ năng `constraint-driven-development`:
* **State Machine Constraint:**
  * Đánh giá chỉ có thể chuyển từ `SUBMITTED` $\rightarrow$ `APPROVED` (bởi HR).
  * Không thể duyệt khi trạng thái vẫn đang là `DRAFT` (Mentor chưa nộp).
  * Hồ sơ chưa được `APPROVED` bởi HR thì không được xuất ra bản in chính thức có chữ ký/mộc.
* **Security & Authorization Constraint:**
  * Endpoint duyệt và xuất báo cáo chỉ cấp quyền cho `ROLE_HR` và `ROLE_ADMIN`.
* **Frontend Performance & UX Constraint:**
  * Render giao diện bảng đánh giá mượt mà, phân trang, bộ lọc không re-render thừa.
  * Form in PDF chuẩn định dạng A4 (Print CSS layout) hiển thị đầy đủ Header công ty, Bảng điểm chi tiết, Nhận xét Mentor, Nhận xét HR và Khuông chữ ký đại diện.
  * Tuân thủ CSS Design Tokens (`var(--bg-card)`, `var(--text-main)`, v.v.).

---

### 3. Thiết Kế Hợp Đồng API & Dữ Liệu (API & Data Contracts)

#### 3.1. Cập nhật Database Entity (`intern_evaluations`)
Bổ sung các trường đại diện cho chữ ký duyệt của HR:
```sql
ALTER TABLE intern_evaluations
  ADD COLUMN hr_comments TEXT NULL COMMENT 'Nhận xét của Phòng Nhân sự',
  ADD COLUMN hr_approved_by VARCHAR(100) NULL COMMENT 'Username hoặc tên HR phê duyệt',
  ADD COLUMN hr_approved_at DATETIME NULL COMMENT 'Thời điểm HR phê duyệt',
  ADD COLUMN internship_result VARCHAR(30) NULL DEFAULT 'PASSED' COMMENT 'Kết luận: PASSED, EXCELLENT, FAILED';
```

#### 3.2. RESTful API Endpoints (`intern-and-program-service`)

##### `GET /api/evaluations/hr/summary`
* **Mục đích:** Lấy danh sách đánh giá của các TTS phục vụ bảng quản lý của HR.
* **Query Params:**
  * `programId` (Long, optional)
  * `university` (String, optional)
  * `status` (String, optional: `ALL`, `PENDING_MENTOR`, `PENDING_HR`, `APPROVED`)
  * `keyword` (String, optional: tìm theo tên, mã TTS)
  * `page`, `size` (phân trang)
* **Response:**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "items": [
      {
        "evaluationId": 12,
        "internCode": "INT2026001",
        "internName": "Nguyễn Văn A",
        "university": "Đại học Bách Khoa",
        "programName": "Chương trình Web Backend K14",
        "departmentName": "Khối Kỹ thuật Phần mềm",
        "mentorName": "Lương Anh Huy",
        "status": "SUBMITTED",
        "technicalScore": 8.5,
        "attitudeScore": 9.0,
        "softSkillsScore": 8.0,
        "weeklyAvgScore": 8.2,
        "finalScore": 8.4,
        "recommendation": "HIRE_FULLTIME",
        "hrApprovedAt": null,
        "internshipResult": null
      }
    ],
    "totalElements": 25,
    "totalPages": 3
  }
}
```

##### `PATCH /api/evaluations/{internCode}/hr-approve`
* **Mục đích:** HR thẩm định và phê duyệt kết quả đánh giá cuối kỳ của từng TTS.
* **Quyền:** `HR`, `ADMIN`
* **Request Body:**
```json
{
  "hrComments": "Sinh viên có thái độ học tập và đóng góp rất tốt trong suốt thời gian thực tập tại doanh nghiệp.",
  "internshipResult": "PASSED" // hoặc EXCELLENT, FAILED
}
```
* **Nghiệp vụ kèm theo (Cascade):**
  * Đổi `evaluation.status` = `APPROVED`.
  * Ghi nhận `hr_approved_by` = current authenticated user, `hr_approved_at` = now().
  * Cập nhật trạng thái `InternProfile` của bạn đó từ `INTERNING` sang `COMPLETED`.

##### `GET /api/evaluations/{internCode}/transcript-data`
* **Mục đích:** Lấy đầy đủ dữ liệu bảng điểm và phiếu đánh giá chuẩn của sinh viên phục vụ xuất bản in / PDF.

---

### 4. Thiết Kế Giao Diện Frontend (Frontend UI Design)

#### 4.1. Cấu trúc trang `/hr/evaluations` ([`HrEvaluationManagementPage.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/evaluations/HrEvaluationManagementPage.tsx))
1. **Header:** 
   * Tiêu đề: *"Trung Tâm Đánh Giá & Báo Cáo Cuối Kỳ"*.
   * Mô tả: *"Rà soát kết quả đánh giá của Mentor, phê duyệt chính thức và xuất bảng điểm gửi Nhà trường."*
2. **Thanh công cụ & Bộ lọc (Toolbar & Filters):**
   * Ô tìm kiếm từ khóa (Tên TTS, Mã TTS).
   * Dropdown lọc theo Chương trình thực tập.
   * Dropdown lọc theo Trường đại học.
   * Tabs lọc theo trạng thái duyệt:
     * *Tất cả*
     * *Chờ Mentor nộp* (chưa có đánh giá hoặc còn DRAFT)
     * *Chờ HR duyệt* (`SUBMITTED` - Cần HR mở xem)
     * *Đã duyệt hoàn tất* (`APPROVED` - Sẵn sàng xuất phiếu)
   * Nút Action chính: **"Xuất PDF Hàng Loạt"** (chỉ enable khi HR tích chọn $\ge 1$ hồ sơ `APPROVED`).
3. **Bảng Danh Sách Hồ Sơ Đánh Giá:**
   * Cột Checkbox (chỉ cho phép tích với hồ sơ `APPROVED`).
   * Cột TTS (Mã TTS, Họ tên, Trường).
   * Cột Chương trình & Mentor phụ trách.
   * Cột Điểm Đánh Giá (Điểm chuyên môn, Thái độ, Điểm tổng kết).
   * Cột Trạng thái (`badge-warning`: Chờ HR duyệt, `badge-success`: Đã duyệt).
   * Cột Thao tác:
     * Nếu `SUBMITTED`: Nút **"Xem & Ký Duyệt"** (Mở Modal).
     * Nếu `APPROVED`: Nút **"In / Xuất PDF"**.
     * Nếu chưa nộp: Badge *"Mentor đang đánh giá"*.

#### 4.2. Modal Phê Duyệt Đánh Giá (`ApproveEvaluationModal.tsx`)
* **Khối thông tin Mentor chấm (Readonly):**
  * Điểm Chuyên môn (`/10`), Thái độ (`/10`), Kỹ năng mềm (`/10`), Điểm TB các tuần (`/10`).
  * **Điểm tổng kết cuối kỳ (Final Score)** được highlight nổi bật.
  * Nhận xét điểm mạnh, điểm cần cải thiện và Đề xuất tuyển dụng của Mentor.
* **Khối nhập liệu của HR:**
  * Textarea: *Ý kiến nhận xét & đánh giá chung của Doanh nghiệp*.
  * Select: *Kết luận kỳ thực tập* (Đạt yêu cầu - Hoàn thành xuất sắc - Không đạt).
* **Nút hành động:**
  * Nút *"Hủy bỏ"*.
  * Nút *"Phê Duyệt & Chốt Kết Quả"* (Kèm xác nhận cảnh báo: Hành động này sẽ chuyển trạng thái TTS sang Hoàn thành kỳ thực tập).

#### 4.3. Phiếu Đánh Giá & Bảng Điểm In Ấn (`InternshipTranscriptPrintView.tsx`)
* Sử dụng CSS `@media print` chuẩn khổ giấy A4:
  * Quốc hiệu / Logo Doanh nghiệp & Thông tin công ty.
  * Tiêu đề: **PHIẾU ĐÁNH GIÁ KẾT QUẢ THỰC TẬP TỐT NGHIỆP**.
  * Thông tin sinh viên: Họ tên, Mã SV, Trường đại học, Khoa/Ngành, Thời gian thực tập.
  * Bảng điểm chi tiết 4 tiêu chí theo khung chuẩn của các trường ĐH.
  * Nhận xét của Người hướng dẫn (Mentor) & Nhận xét của Đại diện Ban Nhân sự (HR).
  * Kết luận chung & Khung chữ ký đóng dấu xác nhận của Công ty.

---

### 5. Kế Hoạch Triển Khai Theo Từng Bước (Implementation Plan)
1. **Bước 1 (Backend Database & Service):**
   * Bổ sung các field `hr_comments`, `hr_approved_by`, `hr_approved_at`, `internship_result` vào `InternEvaluation`.
   * Viết endpoint `PATCH /api/evaluations/{internCode}/hr-approve` và `GET /api/evaluations/hr/summary`.
2. **Bước 2 (Frontend Types & Service):**
   * Bổ sung Types trong `evaluation.types.ts`.
   * Thêm hàm gọi API trong `evaluationService.ts`.
3. **Bước 3 (Frontend UI - Page & Modal):**
   * Xây dựng trang `HrEvaluationManagementPage.tsx` và styles.
   * Xây dựng `ApproveEvaluationModal.tsx`.
   * Xây dựng view in phiếu `InternshipTranscriptPrintView.tsx`.
4. **Bước 4 (Navigation & Menu):**
   * Thêm route `/hr/evaluations` vào `AppRoutes.tsx`.
   * Bổ sung mục "Đánh Giá Cuối Kỳ" trên `Sidebar.tsx`.
5. **Bước 5 (Kiểm thử & Rà soát):**
   * Build kiểm tra TypeScript và test luồng ký duyệt, xuất PDF trên trình duyệt.

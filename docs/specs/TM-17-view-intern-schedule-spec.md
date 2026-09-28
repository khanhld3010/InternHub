# Specification: Xem Lịch Thực Tập Cá Nhân & Theo Dõi Tiến Độ (TM-17)

> **Tài liệu Đặc Tả Kỹ Thuật (Feature Specification)**  
> **Dự án:** [InternHub](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub) & [InternHub-Frontend](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend) (React + Vite + TypeScript)  
> **Mã Jira Ticket:** [TM-17](https://robluccibn9935.atlassian.net/browse/TM-17)  
> **Tiêu đề User Story:** *Là thực tập sinh, tôi muốn xem lịch thực tập cá nhân để biết kế hoạch.*  
> **Nhánh Git:** `feature/TM-17/intern-personal-schedule` (hoặc tích hợp trên nhánh hiện tại)  
> **Mức độ thay đổi (Change Level):** **L2** (Frontend Component & Widget chuyên biệt cho Intern Dashboard, thuật toán tính toán tiến độ tuần realtime, xử lý dữ liệu ngày tháng chuẩn xác, tuân thủ Web Design Guidelines & WCAG).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-09-26 | AI Agent & Developer | `TM-17` | Tạo mới | Thiết kế đặc tả kỹ thuật tính năng Xem Lịch Thực Tập Cá Nhân & Tiến Độ cho TTS theo thỏa thuận Brainstorming. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Xem Lịch Thực Tập Cá Nhân & Theo Dõi Lộ Trình (Personal Internship Schedule & Progress Tracker)
- **Jira Ticket:** [TM-17](https://robluccibn9935.atlassian.net/browse/TM-17)
- **Target Subsystems:**
  - `InternHub-Frontend` (Cổng thông tin Thực tập sinh `/intern/dashboard`)
  - Tái sử dụng dữ liệu từ `intern-and-program-service` (thông qua hồ sơ `InternProfile`: `startDate`, `endDate`, `programName`, `mentorName`, `mentorEmail`, `status`)
- **Target Users & Roles:** Thực tập sinh (`ROLE_INTERN` / `ROLE_USER`)
- **Change Level:** **L2** (Giao diện người dùng & logic tính toán client-side)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Minh bạch hóa kế hoạch thực tập:** Giúp thực tập sinh nắm rõ khung thời gian bắt đầu, kết thúc, số tuần đào tạo và tuần thực tập hiện tại.
2. **Theo dõi tiến độ trực quan (Progress Tracking):** Cung cấp thanh đo tiến độ thời gian thực (% hoàn thành kỳ thực tập) kèm đếm ngược số ngày còn lại (Countdown).
3. **Định hình lộ trình đào tạo chuẩn mực:** Hiển thị 4 cột mốc (Milestones) chuẩn của doanh nghiệp từ khi bắt đầu đến khi bảo vệ báo cáo tốt nghiệp.
4. **Kết nối sinh hoạt với Mentor:** Nắm rõ thời gian làm việc chuẩn tại công ty (Thứ 2 - Thứ 6) và lịch hẹn hướng dẫn/review định kỳ cùng Mentor.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- Tạo component `InternScheduleWidget.tsx` và stylesheet `InternScheduleWidget.module.css`.
- Thuật toán tính toán thời gian:
  - Tổng số ngày: `totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1`
  - Số ngày đã trôi qua: `elapsedDays = today - startDate`
  - Tỷ lệ hoàn thành: `progressPercent = Math.min(100, Math.max(0, Math.round((elapsedDays / totalDays) * 100)))`
  - Tuần thực tập hiện tại: `currentWeek = Math.floor(elapsedDays / 7) + 1` trên `totalWeeks`
- Khối hiển thị Lịch làm việc tuần & Lịch gặp Mentor.
- Khối hiển thị 4 Cột mốc lộ trình đào tạo (Onboarding $\rightarrow$ Chuyên môn $\rightarrow$ Dự án $\rightarrow$ Đánh giá).
- Tích hợp component vào [InternDashboard.tsx](file:///c:/Users/Luong Anh Huy/InternHub-Workspace/InternHub-Frontend/src/pages/intern/InternDashboard.tsx).

### 3.2. Ngoài phạm vi (Out of Scope)
- Không tạo bảng cơ sở dữ liệu mới (tận dụng trường `startDate`, `endDate`, `program` trong `intern_profiles`).
- Không tạo tính năng điểm danh vân tay hoặc chấm công hàng ngày (thuộc module Quản lý công việc sau này).

---

## 4. UI/UX Design & Component Architecture

### 4.1. Kiến Trúc Khối Giao Diện `InternScheduleWidget`

```
+-----------------------------------------------------------------------------------+
|  [Calendar Icon] Kế Hoạch & Lịch Thực Tập Cá Nhân               [Badge: Tuần 3/12] |
+-----------------------------------------------------------------------------------+
|  Tiến độ kỳ thực tập: [===================>---------------] 35% Hoàn thành       |
|  Bắt đầu: 01/09/2026  |  Kết thúc: 25/11/2026  |  Còn lại: 60 ngày làm việc       |
+-----------------------------------------------------------------------------------+
|  Lịch Sinh Hoạt Tuần                    |  Lộ Trình Cột Mốc Đào Tạo              |
|  - Giờ làm việc: Thứ 2 - Thứ 6          |  1. [V] Tuần 1: Onboarding & Hội nhập   |
|    (08:30 - 17:30)                      |  2. [>] Tuần 2-4: Đào tạo kỹ thuật     |
|  - Họp Review tuần: Chiều Thứ 6 (16:00) |  3. [ ] Tuần 5-10: Dự án thực tế       |
|  - Mentor hỗ trợ: Vũ Thị Thu Hà         |  4. [ ] Tuần 11-12: Báo cáo cuối kỳ    |
+-----------------------------------------------------------------------------------+
```

### 4.2. Xử Lý Các Trường Hợp Biên (Edge Cases)
1. **Chưa thiết lập ngày (`startDate == null` hoặc `endDate == null`):**
   - Hiển thị thông báo màu xanh dương: *"Kế hoạch thực tập của bạn đang được bộ phận Nhân sự sắp xếp khung thời gian cụ thể."*
2. **Chưa đến ngày bắt đầu (`today < startDate`):**
   - Hiển thị `progressPercent = 0%`, badge: *"Sắp diễn ra — Bắt đầu sau X ngày nữa"*.
3. **Đã kết thúc hoặc trạng thái `COMPLETED` (`today > endDate`):**
   - Hiển thị `progressPercent = 100%`, badge xanh lá: *"Đã hoàn thành kỳ thực tập"*.
4. **Chưa có Mentor:**
   - Ở mục lịch gặp Mentor: Hiển thị trạng thái chờ phân công người hướng dẫn kỹ thuật.

---

## 5. Verification & Acceptance Criteria (Tiêu Chí Nghiệm Thu)

1. [ ] Khi TTS đăng nhập và vào `/intern/dashboard`, khối **"Lịch & Tiến Độ Thực Tập Cá Nhân"** xuất hiện nổi bật, đẹp mắt.
2. [ ] Tiến độ % và số tuần được tính toán chính xác dựa trên `startDate` và `endDate` của hồ sơ.
3. [ ] Hiển thị đầy đủ thông tin thời gian làm việc chuẩn, lịch họp tuần với Mentor.
4. [ ] 4 mốc lộ trình đào tạo hiển thị trực quan trạng thái (Đã qua, Đang diễn ra, Sắp tới).
5. [ ] Giao diện responsive trên cả máy tính và thiết bị di động, tuân thủ bảng màu chuẩn của dự án.

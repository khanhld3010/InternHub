# Spec: Mentor Dashboard Scale-Up & Mentorship Evaluation Hub

**Status:** Proposed  
**Authors:** Antigravity Team & Lead Engineer  
**Date:** 2026-10-02  
**Branch:** `feature/mentor-evaluation-hub`  
**Target:** Frontend (`InternHub-Frontend`), Backend (`InternHub/intern-and-program-service`)

---

## 1. Objective & Problem Statement

### 1.1 Vấn đề hiện tại (Current Pain Points)
1. **Mentor Dashboard bị nghẽn giao diện (Unscalable 2-column fixed layout):**
   - Danh sách TTS bên trái dùng các card dọc kích thước lớn, không thể mở rộng khi Mentor quản lý từ 10 đến 100+ TTS (cuộn vô tận, không tìm kiếm, không lọc theo chương trình).
   - Panel chi tiết bên phải luôn hiển thị cố định chiếm diện tích, khiến thông tin bị ép chật chội.
   - Không có cơ chế cảnh báo nhanh ("Ai cần chú ý trước?"), Mentor phải click từng người mới biết ai chưa đánh giá tuần hoặc sắp tới hạn.
2. **Trải nghiệm đánh giá sơ sài, tạm bợ (Poor Evaluation UX):**
   - Chỉ có 1 ô `<textarea>` lưu vào `localStorage` cá nhân (`internhub_mentor_notes`), dễ mất dữ liệu khi đổi máy/xóa cache, không có lưu trữ Backend tập trung.
   - Thiếu tiêu chí đánh giá định lượng (Rubrics), thiếu thang sao (1-5 ⭐).
   - Thiếu mục tiêu tuần tới (`nextWeekGoals`) để đồng hành cùng TTS.
   - Thiếu cờ phân biệt giữa lưu nháp (`DRAFT`) và gửi công khai cho TTS (`PUBLISHED`).

### 1.2 Mục tiêu đạt được (Goal)
- **Scale-Ready Mentor Overview:** Chuyển đổi toàn diện trang `/mentor/interns` và `/mentor/dashboard` sang giao diện tổng quan hiện đại:
  - Header Triage KPI: Tổng phụ trách, Cần đánh giá tuần này (có viền cảnh báo), Quá hạn Midterm, Điểm TB nhóm.
  - Search & Smart Filter Pills: `Tất cả` | `Cần chú ý` | `Ổn định` | Lọc theo Chương trình.
  - Toggle chế độ xem: **Bento Card Grid** (thẻ trực quan có thanh tiến độ tuần & badge hành động) và **Data Table List** (bảng danh sách gọn gàng tái sử dụng pattern từ `HrInternTable`).
- **Dedicated Intern Detail Drawer / Full-view:** Click vào TTS sẽ mở Drawer/Panel toàn màn hình, hiển thị 4 tab/khối thông tin rộng rãi.
- **Mentorship Weekly Evaluation Hub (Đánh giá tuần độc lập):**
  - Đủ 4 tiêu chí chuẩn (1-5 ⭐): Kỹ năng chuyên môn, Thái độ & Trách nhiệm, Giao tiếp & Tinh thần đồng đội, Tiến độ hoàn thành công việc.
  - Nhận xét chi tiết + Mục tiêu tuần tới (`nextWeekGoals`).
  - Nút `✨ Gợi ý nhận xét AI` gắn nhãn `Coming Soon` + Bộ Smart Templates tĩnh theo mức điểm.
  - Workflow Draft / Publish: Mentor tự chủ bấm "Gửi Đánh Giá Cho TTS" (không qua HR duyệt đối với Weekly Assessment).
  - Tách bạch hoàn toàn khỏi đánh giá mốc kỳ chính thức (`Midterm/Final Evaluation` vẫn theo luồng duyệt HR).

---

## 2. Tech Stack & Architecture

- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, Lucide React, Sonner (Toast notifications).
- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, Spring Cloud (Gateway, Eureka).
- **Service Phụ trách:** `intern-and-program-service` (Port 8082).

---

## 3. Data Model & Database Schema

### 3.1 Bảng CSDL Mới: `intern_weekly_assessments`
Tách biệt hoàn toàn khỏi `intern_evaluations` (dành cho Midterm/Final):

```sql
CREATE TABLE intern_weekly_assessments (
    id BIGSERIAL PRIMARY KEY,
    intern_code VARCHAR(50) NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(150),
    week_number INT NOT NULL,
    assessment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    
    -- 4 Tiêu chí Rubrics (Thang điểm 1 - 5)
    technical_score INT NOT NULL CHECK (technical_score BETWEEN 1 AND 5),
    attitude_score INT NOT NULL CHECK (attitude_score BETWEEN 1 AND 5),
    teamwork_score INT NOT NULL CHECK (teamwork_score BETWEEN 1 AND 5),
    productivity_score INT NOT NULL CHECK (productivity_score BETWEEN 1 AND 5),
    average_score NUMERIC(3, 1) NOT NULL,
    
    -- Nội dung định tính
    feedback TEXT NOT NULL,
    next_week_goals TEXT,
    
    -- Trạng thái phát hành
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT' | 'PUBLISHED'
    published_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_weekly_assessment_intern FOREIGN KEY (intern_code) 
        REFERENCES intern_profiles(intern_code) ON DELETE CASCADE
);

CREATE INDEX idx_weekly_assess_intern_code ON intern_weekly_assessments(intern_code);
CREATE INDEX idx_weekly_assess_mentor_id ON intern_weekly_assessments(mentor_id);
```

---

## 4. RESTful API Contracts

Base URL: `/api/interns/weekly-assessments` (qua API Gateway)

### 4.1 Tạo / Cập nhật đánh giá tuần (Mentor)
- **POST** `/api/interns/{internCode}/weekly-assessments`
- **Request Body:**
```json
{
  "weekNumber": 8,
  "technicalScore": 5,
  "attitudeScore": 4,
  "teamworkScore": 5,
  "productivityScore": 4,
  "feedback": "Nắm bắt nhanh kiến trúc Microservices, hoàn thành tốt task viết test.",
  "nextWeekGoals": "Tập trung tối ưu hóa truy vấn SQL và xử lý exception tập trung.",
  "isPublish": true
}
```
- **Response (200 OK):**
```json
{
  "code": 200,
  "message": "Lưu đánh giá tuần thành công",
  "data": {
    "id": 101,
    "internCode": "INT-202608-0001",
    "weekNumber": 8,
    "averageScore": 4.5,
    "status": "PUBLISHED",
    "createdAt": "2026-10-02T11:15:00Z"
  }
}
```

### 4.2 Lấy danh sách lịch sử đánh giá tuần của 1 TTS
- **GET** `/api/interns/{internCode}/weekly-assessments`
  - Nếu role là `INTERN`: Backend tự động filter chỉ trả về bản ghi có `status = 'PUBLISHED'`.
  - Nếu role là `MENTOR` hoặc `HR`: Trả về cả `DRAFT` và `PUBLISHED`.

### 4.3 Thống kê nhanh danh sách TTS cho Mentor (Scale Triage)
- **GET** `/api/mentors/my-interns/overview`
- **Response:**
```json
{
  "totalAssigned": 24,
  "needsWeeklyAssessmentCount": 7,
  "overdueMidtermCount": 2,
  "groupAverageScore": 4.5,
  "interns": [
    {
      "internCode": "INT-202608-0001",
      "fullName": "Phạm Anh Thư",
      "programName": "QA Automation 2026",
      "currentWeek": 6,
      "totalWeeks": 13,
      "progressPercent": 46,
      "weeklyStatus": "NEEDS_ASSESSMENT",
      "lastAverageScore": 4.6,
      "overdueMidterm": false
    }
  ]
}
```

---

## 5. UI/UX Component Specifications (Frontend)

### 5.1 Trang `MentorDashboard.tsx` (Scale-Up View)
1. **Header Triage Stats Grid:**
   - 4 Card chỉ số: `Tổng phụ trách`, `Cần đánh giá tuần này` (viền vàng cam nổi bật), `Quá hạn Midterm` (cảnh báo đỏ), `Điểm TB nhóm`.
2. **Search & Action Bar:**
   - Ô tìm kiếm theo tên, mã TTS.
   - Filter Tabs: `Tất cả` | `Cần chú ý` (lọc nhanh các TTS có `weeklyStatus = 'NEEDS_ASSESSMENT'` hoặc `overdueMidterm = true`) | `Ổn định`.
   - Dropdown chọn Chương trình.
   - Nút chuyển View Mode: Grid (Bento cards) / Table (Bảng dữ liệu chuẩn).
3. **Intern Bento Card:**
   - Hiển thị Avatar, Họ tên, Mã TTS, Tên chương trình.
   - Progress bar thanh tiến độ tuần (`Tuần X/Y` - X%).
   - Badge trạng thái nổi bật (`🟡 Chưa đánh giá tuần X`, `🟢 Đã đánh giá tuần X`, `🟠 Quá hạn Midterm`).
   - Điểm đánh giá gần nhất (`4.8 / 5`).
4. **Detail Drawer Component (`MentorInternDetailDrawer.tsx`):**
   - Mở trượt mượt mà từ cạnh phải (hoặc Modal rộng 80vw) khi nhấn vào card/dòng.
   - Tab 1: Thông tin phân công & Thông tin liên hệ.
   - Tab 2: Danh sách tài liệu & Thẩm định tệp tin.
   - Tab 3: **Mentorship Evaluation Hub** (Form chấm điểm 4 tiêu chí + Smart Template + AI Coming Soon + Lịch sử timeline).

### 5.2 Form Đánh Giá Tuần (`WeeklyEvaluationForm.tsx`)
- 4 thanh Star Rating tương tác (1 - 5 sao).
- Quick Suggestion Chips (Template nhận xét theo mức sao: Xuất sắc, Đạt yêu cầu, Cần cải thiện).
- Nút `✨ Gợi ý nhận xét AI (Coming Soon)` disabled với badge đẹp mắt.
- 2 ô Textarea:
  - `Nhận xét tuần này`
  - `Mục tiêu tuần tới (Next-week Goals)`
- Nút `Lưu Nháp (Draft)` & Nút `Gửi Đánh Giá Cho TTS (Publish)`.

---

## 6. Boundaries (Giới Hạn & Nguyên Tắc Thực Hiện)

- **Always Do:**
  - Giữ vững 4 tiêu chí chuẩn (Technical, Attitude, Teamwork, Productivity).
  - Tách bạch 100% CSDL giữa `intern_weekly_assessments` (Weekly, không qua HR) và `intern_evaluations` (Midterm/Final, qua HR).
  - Tái sử dụng thiết kế bảng & filter từ hệ thống Design System hiện tại.
- **Ask First:**
  - Nếu muốn thay đổi thang điểm khác 1-5 sao.
  - Nếu muốn kích hoạt gọi API LLM thực tế thay vì badge `Coming Soon`.
- **Never Do:**
  - Không lưu trữ đánh giá nghiệp vụ vào `localStorage` của trình duyệt nữa.
  - Không cho phép Intern xem các bản ghi ở trạng thái `DRAFT`.
  - Không xoá bỏ luồng duyệt HR đối với đánh giá Midterm/Final chính thức.

---

## 7. Success Criteria & Verification

1. **Scalability:** Màn hình Mentor quản lý từ 1 đến 100+ TTS hiển thị mượt mà, tìm kiếm và lọc theo trạng thái tức thì không giật lag.
2. **Actionability:** Mentor liếc mắt vào Header là biết chính xác có bao nhiêu TTS cần đánh giá tuần này và ai bị quá hạn Midterm.
3. **Data Integrity:** Đánh giá tuần được lưu an toàn vào PostgreSQL qua API, có lịch sử theo tuần, tính điểm trung bình chính xác.
4. **Privacy:** Thực tập sinh chỉ xem được đánh giá khi Mentor đã bấm "Gửi Đánh Giá" (`PUBLISHED`).

# Spec: Final & Milestone Mentorship Evaluation Hub (Tổng Kết Thực Tập)

**Status:** Draft / Proposed  
**Authors:** Antigravity Team & Lead Engineer  
**Date:** 2026-10-03  
**Branch:** `feature/mentor-evaluation-hub`  
**Target:** Frontend (`InternHub-Frontend`), Backend (`InternHub/intern-and-program-service`)

---

## 1. Objective & User Story

### 1.1 User Story
> **"Là mentor, tôi muốn đánh giá kỹ năng và thái độ của thực tập sinh để tổng kết."**

### 1.2 Business Context & Pain Points
- Hiện tại hệ thống đã có **Weekly Assessment Hub** (đánh giá vi mô liên tục tuần 1 - 12 cho việc kèm cặp).
- Tuy nhiên, khi kết thúc kỳ thực tập (hoặc giữa kỳ - Midterm), Mentor và tổ chức **cần một bản Đánh Giá Mốc / Tổng Kết (Final Evaluation Summary)** chính thức để:
  1. Tổng kết toàn diện về **Kỹ năng chuyên môn (Technical Skills)** và **Thái độ nghề nghiệp (Work Attitude & Soft Skills)**.
  2. Đưa ra **Khuyến nghị tuyển dụng / Quyết định nhân sự chính thức (Final Recommendation)**:
     - `HIRE_FULLTIME`: Tuyển dụng chính thức làm nhân viên (Full-time Employee).
     - `EXTEND_INTERNSHIP`: Gia hạn thời gian thực tập thêm dự án/thời gian.
     - `PASS`: Đạt yêu cầu hoàn thành kỳ thực tập (Cấp chứng chỉ thực tập).
     - `FAIL`: Không đạt yêu cầu hoàn thành thực tập.
  3. Tích hợp tổng hợp dữ liệu từ lịch sử đánh giá tuần (Weekly Average Score) để mentor không phải tính toán thủ công.
  4. Trình nộp lên bộ phận HR xét duyệt hoặc lưu hồ sơ nhân sự chính thức (`SUBMITTED_TO_HR`).

---

## 2. Technical Stack & Architecture

- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4 / Vanilla CSS Modules, Lucide React, Sonner.
- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, Spring Cloud (Gateway, Eureka).
- **Service Phụ trách:** `intern-and-program-service` (Port 8082).
- **Gateway Route:** `/api/interns/{internCode}/evaluations/**` đã được định tuyến sẵn qua API Gateway sang `intern-and-program-service`.

---

## 3. Data Model & Database Schema

### 3.1 Bảng CSDL: `intern_evaluations`
Bảng lưu trữ đánh giá mốc chính thức (Midterm / Final):

```sql
CREATE TABLE intern_evaluations (
    id BIGSERIAL PRIMARY KEY,
    intern_code VARCHAR(50) NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(150),
    
    evaluation_type VARCHAR(20) NOT NULL DEFAULT 'FINAL', -- 'MIDTERM' | 'FINAL'
    
    -- Đánh giá Kỹ Năng Chuyên Môn (Thang điểm 1 - 10 hoặc 1 - 5, chuẩn hóa 1.0 - 10.0)
    technical_score NUMERIC(3, 1) NOT NULL CHECK (technical_score BETWEEN 1.0 AND 10.0),
    technical_comments TEXT NOT NULL,
    
    -- Đánh giá Thái Độ & Tác Phong (Thang điểm 1.0 - 10.0)
    attitude_score NUMERIC(3, 1) NOT NULL CHECK (attitude_score BETWEEN 1.0 AND 10.0),
    attitude_comments TEXT NOT NULL,
    
    -- Kỹ năng mềm & Tinh thần hợp tác (Thang điểm 1.0 - 10.0)
    soft_skills_score NUMERIC(3, 1) NOT NULL CHECK (soft_skills_score BETWEEN 1.0 AND 10.0),
    
    -- Điểm tổng kết chung (Trung bình có trọng số hoặc trung bình cộng)
    final_score NUMERIC(3, 1) NOT NULL,
    
    -- Điểm trung bình tích lũy từ các tuần đã đánh giá (Snapshot tham chiếu)
    weekly_assessment_avg_score NUMERIC(3, 1),
    
    -- Nhận xét điểm mạnh & điểm cần phát triển
    strengths TEXT,
    areas_for_improvement TEXT,
    
    -- Khuyến nghị chính thức của Mentor
    recommendation VARCHAR(30) NOT NULL, -- 'HIRE_FULLTIME' | 'EXTEND_INTERNSHIP' | 'PASS' | 'FAIL'
    recommendation_note TEXT,
    
    -- Trạng thái đánh giá
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT' | 'SUBMITTED' | 'APPROVED'
    
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by VARCHAR(150),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_eval_intern FOREIGN KEY (intern_code) 
        REFERENCES intern_profiles(intern_code) ON DELETE CASCADE,
    CONSTRAINT uq_intern_eval_type UNIQUE (intern_code, evaluation_type)
);

CREATE INDEX idx_intern_eval_code ON intern_evaluations(intern_code);
CREATE INDEX idx_intern_eval_mentor ON intern_evaluations(mentor_id);
```

---

## 4. RESTful API Contracts

Base URL: `/api/interns/{internCode}/evaluations`

### 4.1 Lấy thông tin đánh giá mốc / tổng kết
- **GET** `/api/interns/{internCode}/evaluations?type=FINAL`
- **Response (200 OK):**
```json
{
  "code": 200,
  "message": "Lấy thông tin đánh giá thành công",
  "data": {
    "id": 12,
    "internCode": "INT-202608-0001",
    "evaluationType": "FINAL",
    "technicalScore": 9.0,
    "technicalComments": "Nắm vững kiến trúc Microservices, độc lập giải quyết bài toán phức tạp.",
    "attitudeScore": 9.5,
    "attitudeComments": "Thái độ chủ động, luôn đúng deadline và hỗ trợ đồng đội rất nhiệt tình.",
    "softSkillsScore": 8.5,
    "finalScore": 9.0,
    "weeklyAssessmentAvgScore": 4.6,
    "strengths": "Tư duy logic tốt, khả năng tự học công nghệ mới nhanh.",
    "areasForImprovement": "Cần trau dồi thêm kỹ năng thuyết trình tài liệu kiến trúc.",
    "recommendation": "HIRE_FULLTIME",
    "recommendationNote": "Đề xuất nhận chính thức vị trí Junior Backend Engineer.",
    "status": "SUBMITTED",
    "submittedAt": "2026-10-03T02:00:00Z"
  }
}
```

### 4.2 Tạo hoặc Cập nhật đánh giá tổng kết (Mentor)
- **POST** `/api/interns/{internCode}/evaluations`
- **Request Body:**
```json
{
  "evaluationType": "FINAL",
  "technicalScore": 9.0,
  "technicalComments": "Nắm vững kiến trúc Microservices...",
  "attitudeScore": 9.5,
  "attitudeComments": "Thái độ chủ động, cầu thị...",
  "softSkillsScore": 8.5,
  "strengths": "Tư duy hệ thống tốt",
  "areasForImprovement": "Kỹ năng thuyết trình",
  "recommendation": "HIRE_FULLTIME",
  "recommendationNote": "Đề xuất vào team Core Platform",
  "isSubmit": false
}
```
- **Response (200 OK):** Trả về đối tượng `InternEvaluationResponse` đã lưu.

---

## 5. UI/UX Specifications (Frontend)

1. **Tab Mới trong `MentorInternDetailDrawer.tsx`:**
   - Thêm tab thứ 4: **"Tổng Kết & Đánh Giá Kỳ" (Milestone / Final Evaluation)** với biểu tượng `Award` / `CheckCircle`.
2. **Component `MentorFinalEvaluationTab.tsx`:**
   - **Tóm tắt Tiến độ & Dữ liệu tuần:** Hiển thị thẻ thống kê điểm trung bình từ các tuần thực tập (`Weekly Assessment Avg: 4.6/5.0 ⭐`) giúp Mentor có căn cứ tổng kết chuẩn xác.
   - **Phần 1: Kỹ Năng Chuyên Môn (Technical):**
     - Thang điểm 1 - 10 (Interactive Slider hoặc Number Rating).
     - Textarea nhận xét kỹ năng lập trình, tư duy giải thuật, độ chuẩn xác của code.
   - **Phần 2: Thái Độ & Tác Phong (Attitude & Soft Skills):**
     - Thang điểm 1 - 10 cho Thái độ & Tác phong (kỷ luật giờ giấc, tinh thần học hỏi).
     - Thang điểm 1 - 10 cho Kỹ năng giao tiếp & Làm việc nhóm.
     - Textarea nhận xét thái độ làm việc.
   - **Phần 3: Điểm Mạnh & Cần Cải Thiện:**
     - 2 ô text riêng biệt: "Điểm mạnh nổi bật" và "Điểm cần phát triển thêm".
   - **Phần 4: Khuyến Nghị Tuyển Dụng (Recommendation):**
     - Radio Cards / Select: `Tuyển dụng chính thức (Hire Full-time)`, `Gia hạn thực tập (Extend)`, `Đạt (Pass)`, `Không đạt (Fail)`.
     - Ghi chú đề xuất cho HR / Ban quản lý.
   - **Thanh Hành Động (Action Bar):**
     - Nút "Lưu Nháp (Save Draft)"
     - Nút "Nộp Cho HR (Submit Final Evaluation)" với confirm modal trước khi khóa chỉnh sửa.

---

## 6. Boundaries

- **Always:**
  - Tách bạch 2 luồng: Đánh giá tuần (Mentor toàn quyền gửi TTS) và Đánh giá tổng kết (Mentor tổng kết và trình HR xem xét).
  - Tự động lấy điểm trung bình của các tuần (`weeklyAssessmentAvgScore`) làm điểm tham chiếu.
- **Ask first:**
  - Nếu muốn mở rộng thêm thang điểm theo chữ (A, B, C, D) thay vì thang số 10.
- **Never:**
  - Không xóa đè hay trộn lẫn bảng `intern_weekly_assessments` với bảng `intern_evaluations`.

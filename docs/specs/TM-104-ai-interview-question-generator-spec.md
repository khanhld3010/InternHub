# Đặc Tả Kỹ Thuật (PRD / Technical Specification)
# Module AI-104: AI Tự Động Sinh Bộ Câu Hỏi Phỏng Vấn (Kỹ Thuật, Dự Án & Văn Hóa)

- **Mã sáng kiến:** `TM-104-AI-INTERVIEW-QUESTION-GENERATOR`
- **Tác giả:** Antigravity Pairing Assistant & Senior Backend Engineer
- **Ngày lập:** 2026-10-15
- **Trạng thái:** DRAFT v2.1 (Đã bổ sung Cơ Chế Xử Lý Đa Luồng Bất Đồng Bộ - Async Non-blocking Job Processing & Cửa Sổ Tiến Độ Floating Progress tương tự TM-103)
- **Lưu trữ tại:** [`InternHub/docs/specs/TM-104-ai-interview-question-generator-spec.md`](file:///d:/codegym_final_project/InternHub/docs/specs/TM-104-ai-interview-question-generator-spec.md)
- **Change Level:** **L3** (Tích hợp AI Interview Question Generator: Microservice `ai-service` Port 8086, ThreadPool `aiInterviewTaskExecutor`, Redis Job State Cache, Non-blocking Async Polling Contract & FloatingProgressWidget).
- **Tiêu chuẩn tuân thủ:** 31 Nguyên tắc bất biến ([`AGENTS.md`](file:///d:/codegym_final_project/InternHub/AGENTS.md)), Package-by-feature, Zero-access `.env`.

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-15 | AI Senior Backend Pair-Programmer & User | `TM-104` | Tạo mới Đặc Tả | Khởi tạo tài liệu đặc tả kỹ thuật Backend cho tính năng AI sinh bộ câu hỏi phỏng vấn theo JD và CV ứng viên, định nghĩa bảng CSDL `interview_question_sets` và `interview_questions`. |
| **v2.0** | 2026-10-22 | AI Senior Backend Pair-Programmer & User | `TM-104` | Tích hợp AI Screening Dossier & Prompt Structured JSON | Bổ sung cơ chế liên kết dữ liệu từ bảng `ai_screening_results` (missingSkills, redFlags, matchScore) để sinh câu hỏi chất vấn sâu; cấu hình Gemini 3.5 Flash-Lite sinh JSON chuẩn hóa. |
| **v2.1** | 2026-10-26 | AI Senior Backend Pair-Programmer & User | `TM-104` | Kiến Trúc Đa Luồng Chạy Ngầm & Non-blocking Floating Widget tương tự TM-103 | Tách luồng sinh câu hỏi AI sang Thread Pool bất đồng bộ `aiInterviewTaskExecutor` (`@Async`); trả về HTTP 202 Accepted kèm `jobId` trong `< 50ms`; lưu trạng thái tiến độ vào Redis (`ai:interview:job:{jobId}`) và In-memory map; cung cấp endpoint polling `GET /api/ai/interview-questions/jobs/{jobId}`; kích hoạt `FloatingProgressWidget` trên giao diện, triệt tiêu hoàn toàn độ trễ blocking 15-30 giây giúp HR/Mentor không bị gián đoạn công việc. |

---

## 1. Bối cảnh & Vấn đề Cần Giải Quyết (Problem Statement)
Trong quy trình tuyển dụng và đánh giá thực tập sinh:
1. **Thiếu chuẩn hóa:** Người phỏng vấn (HR hoặc Mentor) thường chuẩn bị câu hỏi theo cảm tính, dẫn đến việc đánh giá giữa các ứng viên trong cùng một vị trí thiếu tính nhất quán và công bằng.
2. **Mất thời gian chuẩn bị:** Mentor kỹ thuật bận rộn với dự án nên thường không có thời gian soi kỹ từng dòng trong CV của ứng viên để tìm ra các lỗ hổng năng lực hoặc đặt câu hỏi xác thực kinh nghiệm.
3. **Lãng phí dữ liệu từ bước Lọc CV:** Ở bước Thẩm định hồ sơ trước đó (AI Screening), hệ thống đã chỉ ra được điểm tương đồng (`matchScore`), các kỹ năng ứng viên còn thiếu (`missingSkills`) và các điểm đáng ngờ/bất thường (`redFlags`). Tuy nhiên, dữ liệu này hiện chưa được liên kết tự động sang vòng phỏng vấn để chất vấn ứng viên.
4. **Trải nghiệm gián đoạn (Blocking Latency):** Việc gọi LLM sinh từ 6 - 9 câu hỏi phỏng vấn phân loại sâu có thể kéo dài 10 - 25 giây. Việc xử lý đồng bộ làm đóng băng giao diện người dùng, ngăn cản HR tiếp tục công việc tuyển dụng khác.

**Giải pháp:** Xây dựng tính năng **AI Tự động Sinh Bộ Câu Hỏi Phỏng Vấn** trong `ai-service`, tích hợp liền mạch dữ liệu từ Mô tả kỳ thực tập (JD), CV ứng viên và Hồ sơ thẩm định AI Screening để tự động tạo ra bộ câu hỏi phỏng vấn chuẩn hóa, có thể lưu trữ, chỉnh sửa và xuất file in trực tiếp. Đồng thời áp dụng **mô hình xử lý đa luồng chạy ngầm không chặn (Non-blocking Async Job)** tương tự TM-103, cho phép người dùng tự do chuyển trang trong khi cửa sổ nổi thời gian thực (**FloatingProgressWidget**) hiển thị tiến trình.

---

## 2. Mục Tiêu & Tiêu Chí Thành Công (Acceptance Criteria - AC)

### 2.1. Mục tiêu (Goals)
- Hỗ trợ **2 phạm vi sinh câu hỏi (2-Tier Generation)**:
  - **Tầng 1 - Program Scope:** Sinh bộ câu hỏi khung tiêu chuẩn cho toàn bộ kỳ thực tập dựa trên JD và yêu cầu công nghệ của phòng ban.
  - **Tầng 2 - Candidate Scope:** Sinh bộ câu hỏi cá nhân hóa chuyên sâu cho từng ứng viên cụ thể, đối soát chéo giữa JD, CV và kết quả AI Screening.
- Phân nhóm câu hỏi thành **3 trụ cột cốt lõi**:
  1. `TECHNICAL_FOUNDATION`: Kiến thức nền tảng, tư duy lập trình và cấu trúc dữ liệu / giải thuật theo vị trí.
  2. `PROJECT_DEEP_DIVE`: Câu hỏi xoáy sâu vào các dự án/công nghệ mà ứng viên khai trong CV để kiểm chứng tính trung thực và mức độ đóng góp thực tế.
  3. `BEHAVIORAL_AND_CULTURE_FIT`: Thái độ làm việc, kỹ năng mềm, khả năng thích nghi và giải quyết xung đột nhóm.
- **Workflow hoàn chỉnh & Không chặn tác vụ:** Bấm sinh câu hỏi ➔ Phản hồi ngay `HTTP 202 Accepted` kèm `jobId` (< 50ms) ➔ Luồng ngầm đa luồng `aiInterviewTaskExecutor` gọi Gemini LLM ➔ Lưu DB ở trạng thái `DRAFT` ➔ Thông báo hoàn tất trên cửa sổ tiến trình ➔ Cho phép HR/Mentor sửa/xóa/thêm câu hỏi thủ công ➔ Chốt `FINALIZED` ➔ Xuất file PDF **Phiếu Phỏng Vấn (Printable Interview Evaluation Sheet)**.

### 2.2. Tiêu chí nghiệm thu chi tiết (Acceptance Criteria)
- **AC-1:** Chỉ người dùng có vai trò `HR`, `MENTOR` hoặc `ADMIN` (hoặc có quyền `AI_INTERVIEW_MANAGE`) mới được phép sinh, xem, chỉnh sửa và xuất bộ câu hỏi phỏng vấn.
- **AC-2:** Khi sinh câu hỏi cấp độ ứng viên (`internId` hợp lệ), hệ thống **bắt buộc** phải truy vấn bảng `ai_screening_results` của ứng viên đó để lấy `match_score`, `missing_skills`, `red_flags` và `strengths`. Prompt của AI phải sinh ít nhất 2 câu hỏi nhằm chất vấn / làm rõ các `missing_skills` và `red_flags` này.
- **AC-3:** Mỗi câu hỏi phỏng vấn sinh ra phải có đầy đủ:
  - `questionText`: Nội dung câu hỏi rõ ràng, sư phạm.
  - `category`: Một trong 3 nhóm (`TECHNICAL_FOUNDATION`, `PROJECT_DEEP_DIVE`, `BEHAVIORAL_AND_CULTURE_FIT`).
  - `difficulty`: `EASY`, `MEDIUM`, `HARD`.
  - `expectedAnswer`: Gợi ý câu trả lời kỳ vọng và các từ khóa mấu chốt (Key points) giúp Mentor/HR nắm bắt nhanh câu trả lời đúng.
  - `evaluationCriteria`: Tiêu chí đánh giá cụ thể (VD: Trả lời được A được 5đ, giải thích được B được 8đ, có ví dụ thực tế được 10đ).
  - `suggestedScoreWeight`: Trọng số điểm (mặc định 10 điểm).
- **AC-4:** Bộ câu hỏi được lưu vào bảng `interview_question_sets` và `interview_questions` ở trạng thái `DRAFT`.
- **AC-5:** HR/Mentor có thể cập nhật nội dung câu hỏi, điều chỉnh trọng số điểm hoặc xóa bớt câu hỏi. Sau khi hài lòng, có thể bấm "Chốt Bộ Câu Hỏi" (`FINALIZED`).
- **AC-6:** Tính năng xuất PDF (`GET /api/ai/interview-questions/sets/{id}/export-pdf`) tạo ra file PDF định dạng A4 chuẩn in ấn, có Header chương trình/ứng viên, bảng câu hỏi chi tiết kèm khung ô trống để người phỏng vấn ghi điểm (1-10) và dòng chữ ký xác nhận của phỏng vấn viên.
- **AC-7:** Xử lý ngoại lệ (Fault Tolerance): Nếu Gemini LLM bị timeout hoặc quá tải, hệ thống tự động fallback an toàn sang bộ câu hỏi mẫu tiêu chuẩn hoặc cập nhật trạng thái job thành `FAILED` với thông điệp rõ ràng, tuyệt đối không tạo dữ liệu rác trong database.
- **AC-8:** Cơ chế Tự động Dự phòng (Profile Fallback): Nếu ứng viên chưa có kết quả AI Screening (hoặc file CV bị lỗi), hệ thống không báo lỗi mà tự động fallback lấy thông tin từ Profile ứng viên (kỹ năng, trường học, kinh nghiệm dự án đã khai) kết hợp với JD của kỳ thực tập để tạo bộ câu hỏi tiệm cận nhất.
- **AC-9:** Tính năng "Đổi câu hỏi khác" (Regenerate Single Question): Cung cấp endpoint `POST /api/ai/interview-questions/sets/{setId}/questions/{questionId}/regenerate` cho phép AI tạo ra 1 câu hỏi mới thay thế đúng vị trí, đúng nhóm (`category`) và độ khó (`difficulty`) mà không làm thay đổi các câu hỏi khác trong bộ.
- **AC-10 (Đa luồng Chạy Ngầm & Non-blocking Task Execution tương tự TM-103):** 
  - Khi người dùng bấm tạo (endpoint `POST /api/ai/interview-questions/generate-async`), hệ thống **bắt buộc** phải khởi tạo tiến trình ngầm (`jobId`), phân phối vào Thread Pool `aiInterviewTaskExecutor` và phản hồi HTTP 202 Accepted ngay lập tức trong thời gian `< 100ms`.
  - Luồng HTTP request của người dùng không bị block. Người dùng có thể đóng modal hoặc chuyển trang tùy ý.
  - Trạng thái Job được lưu song song vào Redis (`TTL 24h`) và In-memory map fallback, có trường tiến độ `percent` (0% -> 100%) và mô tả giai đoạn `currentStage`.
  - Cung cấp endpoint `GET /api/ai/interview-questions/jobs/{jobId}` cho phép Client thăm dò (polling) tiến độ thời gian thực.
  - Khi tiến trình hoàn tất, lưu bộ câu hỏi vào database và gắn `questionSetId` vào job để Client mở ngay lập tức.

---

## 3. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (Mandatory Scan & Reuse Assessment)

> [!IMPORTANT]
> Tuân thủ triệt để Chỉ thị cốt lõi của Backend: **"ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"** (Rule 31).

### 3.1. Các thành phần Backend hiện hữu được tái sử dụng 100%
1. **Gemini LLM Client & System Configs:**
   - Tái sử dụng bảng `ai_system_configs` và `AiSystemConfigRepository` để nạp model name (`gemini-3.5-flash-lite`), API key và timeout hiện hữu của `ai-service`.
   - Tái sử dụng service gọi Google Gemini API (`GeminiAiClient` / `RestTemplate` / `WebClient`) đã được kiểm thử kết nối thành công (1340ms, tier normal).
2. **Hạ tầng Redis & In-Memory Job State:**
   - Kế thừa mô hình quản lý Job bất đồng bộ từ TM-103 (`ai:screening:job:{jobId}`), chuẩn hóa sang namespace `ai:interview:job:{jobId}` với TTL 24 giờ.
   - Tái sử dụng `StringRedisTemplate` và `ObjectMapper` đã được cấu hình trong `ai-service`.
3. **Liên kết Dữ liệu từ TM-103 (AI Screening Dossier):**
   - Tái sử dụng bảng `ai_screening_results` và repository `AiScreeningResultRepository` để trích xuất `match_score`, `missing_skills`, `red_flags` và `strengths` của ứng viên phục vụ Prompt cá nhân hóa.
4. **Giao tiếp liên dịch vụ (Feign Client):**
   - Tái sử dụng `InternServiceClient` để lấy thông tin chi tiết chương trình thực tập (JD, yêu cầu kỹ năng, mô tả dự án) từ `intern-and-program-service` (Port 8082).
5. **Common Utilities & Base Classes:**
   - Kế thừa `ApiResponse<T>` cho chuẩn đóng gói response RESTful.
   - Sử dụng thư viện `DocumentTextExtractor` đã có trong `ai-service` để trích xuất CV nếu cần phân tích trực tiếp file PDF/DOCX.

### 3.2. Các thành phần tạo mới theo chuẩn Package-by-Feature
1. `org.example.aiservice.config.AsyncConfig`: Khởi tạo Bean Thread Pool `aiInterviewTaskExecutor` (`corePoolSize=4`, `maxPoolSize=10`, `queueCapacity=100`, prefix `"ai-interview-task-"`).
2. `interview_question_sets` & `interview_questions`: Bảng CSDL lưu trữ bộ câu hỏi và danh sách câu hỏi theo 3 nhóm cốt lõi.
3. Entities & DTOs trong package `org.example.aiservice.modules.interview`:
   - `InterviewQuestionSet`, `InterviewQuestion`, `QuestionCategory`, `QuestionDifficulty`, `InterviewSetStatus`.
   - `GenerateInterviewQuestionsRequest`, `QuestionGenerationJobResponse`, `InterviewQuestionSetResponse`.
4. Service & Controller:
   - `AiInterviewQuestionService` & `AiInterviewQuestionServiceImpl`: Chứa nghiệp vụ sinh câu hỏi, luồng async ngầm `@Async("aiInterviewTaskExecutor")`, và polling job status.
   - `AiInterviewQuestionController`: Cung cấp các endpoints chuẩn RESTful.

---

## 4. Kiến Trúc Kỹ Thuật & Luồng Tích Hợp Đa Luồng (Async Architecture & Data Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as HR / Mentor
    participant FE as Frontend React SPA (Floating Widget)
    participant GW as API Gateway (:8080)
    participant AIS as ai-service (:8086)
    participant Pool as ThreadPool (aiInterviewTaskExecutor)
    participant Redis as Redis / In-Memory Cache
    participant IPS as intern-service (:8082)
    participant DB as MySQL (internhub_db)
    participant LLM as Google Gemini 3.5 Flash-Lite

    User->>FE: Bấm "Sinh Bộ Câu Hỏi Phỏng Vấn AI"
    FE->>GW: POST /api/ai/interview-questions/generate-async
    GW->>AIS: Forward request (kèm JWT Header)
    AIS->>AIS: Xác thực quyền & kiểm tra Rate Limit
    AIS->>Redis: Tạo bản ghi Job (jobId, status=PROCESSING, percent=0%)
    AIS->>Pool: executeAsyncQuestionGeneration(jobId, request) [@Async]
    AIS-->>FE: HTTP 202 Accepted (jobId, status=PROCESSING) [< 50ms]
    Note over FE,User: Frontend tự động mở FloatingProgressWidget ở góc màn hình!
    Note over User: Người dùng hoàn toàn tự do đóng modal hoặc duyệt ứng viên khác!

    par Xử lý đa luồng chạy ngầm trong ThreadPool
        Pool->>Redis: Cập nhật percent=25%, currentStage="Đang phân tích JD & CV"
        alt Sinh theo ứng viên (Candidate Scope)
            Pool->>IPS: Lấy thông tin CV + JD kỳ thực tập
            Pool->>DB: Lấy kết quả thẩm định ai_screening_results (missingSkills, redFlags, matchScore)
        else Sinh theo kỳ thực tập (Program Scope)
            Pool->>IPS: Lấy thông tin JD, công nghệ & mô tả kỳ
        end
        Pool->>Redis: Cập nhật percent=60%, currentStage="Đang kết nối Gemini AI sinh câu hỏi"
        Pool->>LLM: Gửi Structured JSON Prompt (JD + CV + Screening Dossier)
        LLM-->>Pool: Trả về JSON 3 nhóm câu hỏi + rubric
        Pool->>Redis: Cập nhật percent=85%, currentStage="Đang chuẩn hóa & lưu trữ cơ sở dữ liệu"
        Pool->>DB: INSERT interview_question_sets (status: DRAFT)
        Pool->>DB: BATCH INSERT interview_questions
        Pool->>Redis: Cập nhật percent=100%, status=COMPLETED, questionSetId=id
    and Client Polling hiển thị tiến trình
        loop Định kỳ 2 giây / lần
            FE->>AIS: GET /api/ai/interview-questions/jobs/{jobId}
            AIS->>Redis: Lấy trạng thái Job hiện hành
            AIS-->>FE: HTTP 200 OK (percent, currentStage, status)
        end
        Note over FE,User: FloatingProgressWidget thông báo: "AI đã hoàn tất sinh bộ câu hỏi!"
        User->>FE: Bấm nút "Xem Bộ Câu Hỏi" trên Widget
        FE->>AIS: GET /api/ai/interview-questions/sets/{id}
        AIS-->>FE: Trả về bộ câu hỏi hoàn chỉnh để Mentor hiệu chỉnh & Chốt
    end
```

---

## 5. Thiết Kế Cơ Sở Dữ Liệu (Database Schema)

```sql
-- Bảng quản lý Bộ Câu Hỏi Phỏng Vấn
CREATE TABLE IF NOT EXISTS interview_question_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT 'Tiêu đề bộ câu hỏi (VD: Phỏng vấn Backend - Đặng Văn GPA)',
    program_id BIGINT NULL COMMENT 'Mã kỳ thực tập liên kết',
    intern_id BIGINT NULL COMMENT 'Mã ứng viên (nếu sinh cá nhân hóa theo CV)',
    scope VARCHAR(30) NOT NULL DEFAULT 'PROGRAM' COMMENT 'PROGRAM hoặc CANDIDATE',
    target_role VARCHAR(100) NOT NULL COMMENT 'Vị trí chuyên môn (Backend, Frontend, Fullstack...)',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT (cho phép sửa) hoặc FINALIZED (đã chốt)',
    total_questions INT NOT NULL DEFAULT 0 COMMENT 'Tổng số câu hỏi trong bộ',
    screening_result_id BIGINT NULL COMMENT 'Liên kết sang bảng ai_screening_results (nếu có)',
    screening_match_score DECIMAL(5,2) NULL COMMENT 'Điểm match score tham chiếu từ bước screening',
    created_by_user_id VARCHAR(100) NOT NULL COMMENT 'ID người tạo (HR / Mentor)',
    created_by_name VARCHAR(150) NOT NULL COMMENT 'Tên người tạo',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_iqs_program (program_id),
    INDEX idx_iqs_intern (intern_id),
    INDEX idx_iqs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bảng chi tiết từng Câu Hỏi trong Bộ
CREATE TABLE IF NOT EXISTS interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL COMMENT 'Khóa ngoại liên kết interview_question_sets',
    category VARCHAR(50) NOT NULL COMMENT 'TECHNICAL_FOUNDATION, PROJECT_DEEP_DIVE, BEHAVIORAL_AND_CULTURE_FIT',
    difficulty VARCHAR(30) NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY, MEDIUM, HARD',
    question_text TEXT NOT NULL COMMENT 'Nội dung câu hỏi phỏng vấn',
    expected_answer TEXT NULL COMMENT 'Gợi ý câu trả lời kỳ vọng / Điểm mấu chốt',
    evaluation_criteria TEXT NULL COMMENT 'Tiêu chí đánh giá / Thang điểm chi tiết',
    suggested_score_weight INT NOT NULL DEFAULT 10 COMMENT 'Thang điểm tối đa của câu hỏi',
    display_order INT NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị trong bộ câu hỏi',
    focus_area VARCHAR(150) NULL COMMENT 'Điểm mấu chốt nhắm tới (VD: Xử lý Missing Skill Docker, Kiểm chứng Project E-commerce)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_iq_set FOREIGN KEY (set_id) REFERENCES interview_question_sets (id) ON DELETE CASCADE,
    INDEX idx_iq_category (category),
    INDEX idx_iq_order (set_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 6. Thiết Kế Prompt Engineering Cho Gemini AI

### 6.1. System Prompt

```text
Bạn là Trợ lý Chuyên gia Tuyển dụng Kỹ thuật và Phỏng vấn cấp cao của tập đoàn InternHub.
Nhiệm vụ của bạn là xây dựng bộ câu hỏi phỏng vấn thực tập sinh cực kỳ sắc bén, thực tế và khách quan.

BẠN PHẢI PHÂN CHIA BỘ CÂU HỎI THÀNH ĐÚNG 3 NHÓM:
1. TECHNICAL_FOUNDATION: Kiểm tra kiến thức nền tảng vững chắc, tư duy thuật toán/hệ thống theo vị trí.
2. PROJECT_DEEP_DIVE: Hỏi xoáy sâu vào các dự án ứng viên khai trong CV để xác minh độ trung thực, vai trò thực tế và cách giải quyết bài toán.
3. BEHAVIORAL_AND_CULTURE_FIT: Kiểm tra tinh thần học hỏi, khả năng chịu áp lực, giao tiếp và văn hóa làm việc nhóm.

ĐẶC BIỆT KHI CÓ DỮ LIỆU AI SCREENING (missingSkills, redFlags):
- Bạn PHẢI tạo ít nhất 2 câu hỏi nhắm thẳng vào các kỹ năng còn thiếu (missingSkills) để kiểm tra khả năng tự học/học bù.
- Bạn PHẢI tạo ít nhất 1 câu hỏi đối chất với các điểm bất thường (redFlags) trong CV để kiểm chứng tính xác thực.

ĐẦU RA BẮT BUỘC LÀ JSON NGUYÊN BẢN (KHÔNG BỌC TRONG MARKDOWN CODE BLOCK):
{
  "title": "Bộ câu hỏi phỏng vấn ...",
  "questions": [
    {
      "category": "TECHNICAL_FOUNDATION | PROJECT_DEEP_DIVE | BEHAVIORAL_AND_CULTURE_FIT",
      "difficulty": "EASY | MEDIUM | HARD",
      "questionText": "...",
      "expectedAnswer": "...",
      "evaluationCriteria": "...",
      "suggestedScoreWeight": 10,
      "focusArea": "..."
    }
  ]
}
```

---

## 7. Đặc Tả Chi Tiết API Endpoints

### 7.1. Sinh bộ câu hỏi phỏng vấn bằng AI (Synchronous Fallback)

- **Endpoint:** `POST /api/ai/interview-questions/generate`
- **Quyền:** `hasAnyRole('HR', 'MENTOR', 'ADMIN')`
- **Request Body DTO:**
  ```json
  {
    "programId": 1,
    "internId": 4,                       // Optional (nếu có -> sinh cá nhân hóa)
    "targetRole": "Backend Developer",
    "totalQuestions": 8,                 // Số lượng câu hỏi mong muốn (mặc định 6-9 câu)
    "includeScreeningDossier": true      // Có tích hợp missingSkills/redFlags không
  }
  ```
- **Response 201 Created:**
  ```json
  {
    "code": 201,
    "message": "Sinh bộ câu hỏi phỏng vấn thành công",
    "data": {
      "id": 12,
      "title": "Bộ câu hỏi phỏng vấn Backend Developer - Ứng viên Đặng Văn GPA",
      "scope": "CANDIDATE",
      "status": "DRAFT",
      "totalQuestions": 8,
      "screeningMatchScore": 75.5,
      "questions": [
        {
          "id": 45,
          "category": "PROJECT_DEEP_DIVE",
          "difficulty": "MEDIUM",
          "questionText": "Trong dự án E-commerce bạn khai đã tối ưu query MySQL, hãy mô tả cụ thể bạn đã đánh Index như thế nào và đo lường hiệu năng bằng công cụ gì?",
          "expectedAnswer": "Ứng viên nêu được lệnh EXPLAIN, chỉ mục B-Tree, Composite Index và slow query log.",
          "evaluationCriteria": "Hiểu bản chất Index: 7-8đ; có số liệu đo lường cụ thể trước/sau tối ưu: 9-10đ.",
          "suggestedScoreWeight": 10,
          "focusArea": "Xác minh kinh nghiệm tối ưu database trong CV"
        }
      ]
    }
  }
  ```

### 7.2. Kích hoạt sinh câu hỏi phỏng vấn ĐA LUỒNG BẤT ĐỒNG BỘ (Non-blocking Async)

- **Endpoint:** `POST /api/ai/interview-questions/generate-async`
- **Quyền:** `hasAnyRole('HR', 'MENTOR', 'ADMIN')`
- **Mô tả:** Đẩy tác vụ sinh câu hỏi vào thread pool ngầm `aiInterviewTaskExecutor`, trả về ngay lập tức `< 50ms` để Client mở widget tiến trình mà không làm nghẽn giao diện người dùng.
- **Request Body DTO:** Giống mục 7.1 (`GenerateInterviewQuestionsRequest`).
- **Response 202 Accepted:**
  ```json
  {
    "code": 202,
    "message": "Yêu cầu sinh câu hỏi phỏng vấn đã được tiếp nhận và đang xử lý ngầm",
    "data": {
      "jobId": "job-iq-9a8b7c6d",
      "programId": 1,
      "internId": 4,
      "targetRole": "Backend Developer",
      "status": "PROCESSING",
      "percent": 0,
      "currentStage": "Đang khởi tạo tiến trình sinh câu hỏi AI...",
      "questionSetId": null,
      "result": null,
      "errorMessage": null,
      "createdAt": "2026-10-26T10:55:00",
      "updatedAt": "2026-10-26T10:55:00"
    }
  }
  ```

### 7.3. Thăm dò tiến độ sinh câu hỏi thời gian thực (Polling Job Status)

- **Endpoint:** `GET /api/ai/interview-questions/jobs/{jobId}`
- **Quyền:** `hasAnyRole('HR', 'MENTOR', 'ADMIN')`
- **Mô tả:** Client thăm dò định kỳ 2s/lần để cập nhật thanh tiến trình trên `FloatingProgressWidget`.
- **Response 200 OK (Khi đang chạy):**
  ```json
  {
    "code": 200,
    "message": "Lấy trạng thái tiến trình thành công",
    "data": {
      "jobId": "job-iq-9a8b7c6d",
      "status": "PROCESSING",
      "percent": 65,
      "currentStage": "Gemini AI đang sinh 8 câu hỏi tình huống chuyên sâu...",
      "questionSetId": null,
      "result": null
    }
  }
  ```
- **Response 200 OK (Khi hoàn tất - COMPLETED):**
  ```json
  {
    "code": 200,
    "message": "Lấy trạng thái tiến trình thành công",
    "data": {
      "jobId": "job-iq-9a8b7c6d",
      "status": "COMPLETED",
      "percent": 100,
      "currentStage": "Hoàn tất! Bộ câu hỏi đã sẵn sàng",
      "questionSetId": 12,
      "result": {
        "id": 12,
        "title": "Bộ câu hỏi phỏng vấn Backend Developer - Ứng viên Đặng Văn GPA",
        "totalQuestions": 8,
        "status": "DRAFT"
      }
    }
  }
  ```

### 7.4. Lấy chi tiết bộ câu hỏi

- **Endpoint:** `GET /api/ai/interview-questions/sets/{id}`
- **Response 200 OK:** Toàn bộ thông tin bộ câu hỏi và danh sách câu hỏi sắp xếp theo `display_order`.

### 7.5. Cập nhật / Tùy chỉnh bộ câu hỏi (Manual Customization & Finalization)

- **Endpoint:** `PUT /api/ai/interview-questions/sets/{id}`
- **Request Body:**
  ```json
  {
    "title": "Bộ câu hỏi phỏng vấn Backend - Đã hiệu chỉnh",
    "status": "FINALIZED",               // Chuyển sang FINALIZED khi đã duyệt xong
    "questions": [
      {
        "id": 45,
        "questionText": "Nội dung câu hỏi đã được Mentor sửa lại...",
        "expectedAnswer": "...",
        "evaluationCriteria": "...",
        "suggestedScoreWeight": 15
      }
    ]
  }
  ```

### 7.6. Đổi / Tái sinh riêng 1 câu hỏi cụ thể (Regenerate Single Question)

- **Endpoint:** `POST /api/ai/interview-questions/sets/{setId}/questions/{questionId}/regenerate`
- **Mô tả:** Giữ nguyên các câu khác, AI chỉ sinh 1 câu hỏi mới cùng nhóm (`category`) và độ khó (`difficulty`) thay thế cho câu hiện tại.
- **Response 200 OK:** Chi tiết câu hỏi mới được cập nhật trong Database.

### 7.7. Xuất file Phiếu Phỏng Vấn (Printable PDF Sheet)

- **Endpoint:** `GET /api/ai/interview-questions/sets/{id}/export-pdf`
- **Headers:** `Content-Type: application/pdf`, `Content-Disposition: inline; filename="phieu-phong-van-INT4.pdf"`
- **Đặc điểm file PDF:**
  - Tiêu đề trang trọng: TẬP ĐOÀN INTERNHUB - PHIẾU ĐÁNH GIÁ PHỎNG VẤN THỰC TẬP SINH.
  - Thông tin ứng viên / kỳ thực tập, điểm sàng lọc AI Screening.
  - Bảng 3 nhóm câu hỏi với 4 cột: STT & Câu hỏi | Gợi ý trả lời & Tiêu chí | Điểm chấm (1-10) | Ghi chú người phỏng vấn.
  - Phần tổng kết điểm số, đánh giá chung ĐẠT / KHÔNG ĐẠT và chỗ ký tên của người phỏng vấn.

---

## 8. Giao Diện Người Dùng & Cửa Sổ Tiến Độ Nổi (Frontend UI/UX & Floating Widget tương tự TM-103)

1. **Điểm kích hoạt (Entry Points):**
   - **Tại Workspace Kỳ Thực Tập:** Tab "Đội Ngũ Mentors" hoặc "Tổng Quan" có nút *"Sinh Bộ Câu Hỏi Khung Cho Kỳ"* ➔ Tạo bộ câu hỏi tầng Chương trình.
   - **Tại Danh Sách Ứng Viên / Chi Tiết Ứng Viên (ApplicationsTab / CandidateWorkspaceModal):** Nút *"Phỏng Vấn AI"* ➔ Tạo bộ câu hỏi tầng Cá nhân hóa.
2. **Cơ chế Cửa Sổ Tiến Độ Nổi Không Chặn (Non-blocking FloatingProgressWidget tương tự TM-103):**
   - Khi HR/Mentor bấm nút *"Tạo bộ câu hỏi"*, Modal cấu hình đóng lại ngay lập tức (hoặc thu nhỏ), không làm treo màn hình.
   - Một **Cửa sổ nổi ghim góc phải dưới màn hình (`FloatingProgressWidget`)** tự động bung ra ở trạng thái Mở rộng (Expanded Card):
     - **Card Header:** Icon Sparkles xoay animation + Tiêu đề *"Tiến Trình Sinh Câu Hỏi Phỏng Vấn AI"* + Nút Thu nhỏ (-) và Nút Đóng (x).
     - **Card Body:**
       - Tên vị trí tuyển dụng / Ứng viên mục tiêu.
       - Thanh phần trăm tiến độ gradient sinh động (`0% -> 25% -> 60% -> 85% -> 100%`).
       - Dòng thông tin giai đoạn (`currentStage`): *"Đang đọc JD & CV..."* ➔ *"Gemini AI đang sinh bộ câu hỏi..."* ➔ *"Chuẩn hóa dữ liệu..."*.
       - Ghi chú chân card: *"HR/Mentor có thể tự do chuyển trang hoặc thao tác tác vụ khác, tiến trình AI vẫn chạy ngầm liên tục."*
     - **Pill thu nhỏ (Collapsed Pill):** Khi bấm Thu nhỏ, widget co lại thành một viên thuốc bo tròn nhỏ gọn ở góc dưới cùng bên phải màn hình:
       - Hiển thị: Icon Sparkles + Tỷ lệ % + Tên bộ câu hỏi đang sinh.
       - Cho phép bấm vào để phóng to lại bất cứ lúc nào.
     - **Khi hoàn tất (Status = COMPLETED):**
       - Âm thanh/Toast thông báo thành công: *"AI đã hoàn tất sinh bộ câu hỏi phỏng vấn!"*.
       - Nút bấm trực diện: **`[ ✦ Xem Bộ Câu Hỏi Vừa Tạo ]`** (màu `var(--primary)`).
       - Nhấn vào nút này sẽ mở trực tiếp Modal chi tiết bộ câu hỏi kèm toàn bộ các câu hỏi đã được tạo đầy đủ.
3. **Modal Xem & Chỉnh Sửa Bộ Câu Hỏi (`InterviewQuestionModal.tsx`):**
   - Phân chia tab trực quan theo 3 nhóm: `Kiến thức nền tảng`, `Đào sâu dự án`, `Kỹ năng mềm & Văn hóa`.
   - Các câu hỏi nhắm vào `missingSkills` hoặc `redFlags` được gắn badge highlight nổi bật (VD: `⚠️ Đối chất điểm nghi vấn`, `🎯 Kiểm tra kỹ năng thiếu`).
   - Có nút *Chỉnh sửa trực tiếp*, *Thêm câu hỏi mới*, *Lưu nháp (DRAFT)*, *Chốt câu hỏi (FINALIZED)* và *In / Xuất PDF Phiếu Đánh Giá*.

---

## 9. Ma Trận Phân Quyền (RBAC Matrix) & Quyền Hạn

- Thêm permission mới: `AI_INTERVIEW_MANAGE` (Quản lý và sinh bộ câu hỏi phỏng vấn AI).
- Seed tự động cho 2 role: `ROLE_HR` và `ROLE_MENTOR` (cùng `ROLE_ADMIN`).
- **Ma trận quyền hạn chi tiết:**

| Role | Sinh bộ câu hỏi | Xem danh sách | Sửa/Xóa câu hỏi | Đổi câu hỏi (Regenerate) | Chốt FINALIZED | Xuất PDF |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **HR** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **MENTOR** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **ADMIN** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **INTERN** | ❌ (403) | ❌ (403) | ❌ (403) | ❌ (403) | ❌ (403) | ❌ (403) |

---

## 10. Kế Hoạch Kiểm Thử & Kịch Bản Thử Nghiệm (Test Plan)

- **TC-01 (Program Scope Generation):** Sinh bộ câu hỏi theo kỳ thực tập (chỉ truyền `programId`). Kỳ vọng: Sinh đủ 3 nhóm câu hỏi bám sát JD, không có câu hỏi xoáy CV cá nhân.
- **TC-02 (Candidate Scope + Screening Dossier):** Sinh bộ câu hỏi cho ứng viên đã qua AI Screening có `missingSkills` (VD: Docker) và `redFlags`. Kỳ vọng: Tối thiểu 2 câu hỏi kỹ thuật nhắm vào Docker và 1 câu hỏi kiểm chứng red flag.
- **TC-03 (Candidate Scope Profile Fallback):** Sinh bộ câu hỏi cho ứng viên chưa qua AI Screening. Kỳ vọng: Hệ thống tự động fallback đọc kỹ năng từ Profile ứng viên, không văng lỗi 500.
- **TC-04 (Regenerate Single Question):** Bấm "Đổi câu hỏi khác" cho câu hỏi số 3. Kỳ vọng: Chỉ câu số 3 thay đổi nội dung, các câu hỏi 1, 2, 4-8 giữ nguyên 100%.
- **TC-05 (Manual Edit & Finalize):** Mentor sửa nội dung câu hỏi và bấm Chốt. Kỳ vọng: Status chuyển sang `FINALIZED`, lưu DB thành công.
- **TC-06 (Export PDF Evaluation Sheet):** Tải file PDF. Kỳ vọng: File PDF A4 mở được, có font tiếng Việt chuẩn không bị lỗi font, bảng biểu căn chỉnh ngay ngắn kèm ô điểm và chữ ký.
- **TC-07 (Security & RBAC):** Dùng token của Thực tập sinh gọi API. Kỳ vọng: Nhận `403 Forbidden`.


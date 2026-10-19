# ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
# MODULE AI-19: PROMPT MANAGEMENT ENGINE & VERSIONING

> **Mã đặc tả:** `SPEC-AI-19-PROMPT-MANAGEMENT-ENGINE`  
> **Phiên bản:** 1.0 (PRODUCTION READY - SPEC-DRIVEN)  
> **Trạng thái:** DRAFT FOR REVIEW / READY FOR IMPLEMENTATION  
> **Tác giả:** Đội ngũ Kỹ thuật & AI Architecture InternHub  
> **Ngày cập nhật:** 2026-10-19  
> **Mức độ ưu tiên:** 🔥 **P1 - HIGH**  
> **Lưu trữ tại:** `InternHub/docs/specs/SPEC_AI_19_PROMPT_MANAGEMENT_ENGINE.md`  
> **Tuân thủ quy chuẩn:** [CONSTRAINTS.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/CONSTRAINTS.md) | Test-Driven Development (TDD) | Zero suppression | 0 FQCN  

---

## 0. NHẬT KÝ THAY ĐỔI & GIẢI TRÌNH KỸ THUẬT (REVISION HISTORY)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-19 | Antigravity AI | `AI-19` | Khởi tạo Deep Spec | Xóa bỏ nợ kỹ thuật hardcode prompt rải rác, tập trung toàn bộ Prompt vào CSDL có versioning, template engine biến `{{var}}` và UI quản trị. |

---

## 1. MỤC TIÊU & PHẠM VI (GOALS & SCOPE)

### 1.1 Mục tiêu Nghiệp Vụ (Business Goals)
1. **Loại bỏ Nợ Kỹ Thuật Hardcoded Prompt:**
   - Hiện tại các prompt hệ thống và prompt phân tích dữ liệu đang bị hardcode chuỗi Java dài hàng trăm dòng trong `AiScreeningServiceImpl`, `AiInterviewQuestionServiceImpl`, `AiInterviewGraderService`, và `AiWeeklyReportGraderService`.
   - Mỗi lần tinh chỉnh rubric chấm điểm hay thêm bớt tiêu chí đều phải sửa code Java, build lại fat JAR và restart container Docker, gây rủi ro gián đoạn dịch vụ.
2. **Quản lý Tập trung & Động (Dynamic Prompt Management):**
   - Đưa toàn bộ Prompt Templates vào cơ sở dữ liệu MySQL (`ai_prompt_templates`).
   - Cho phép Quản trị viên (Admin) xem trước (Preview), chỉnh sửa câu lệnh, và kích hoạt tức thì thông qua giao diện Web mà không cần can thiệp mã nguồn.
3. **Phiên bản hóa & Rollback an toàn (Prompt Versioning):**
   - Mỗi thay đổi tạo ra một bản ghi phiên bản mới (`version = version + 1`).
   - Duy trì lịch sử phiên bản đầy đủ, cho phép rollback về phiên bản trước bất cứ lúc nào.
4. **Bảo đảm Không Gián Đoạn (Zero-Downtime Fallback Guarantee):**
   - Nếu kết nối database gặp trục trặc hoặc bản ghi bị xóa, hệ thống tự động fallback về bộ prompt mặc định chuẩn có sẵn trong code để bảo đảm các tính năng nghiệp vụ của người dùng không bao giờ bị đứt gãy.

### 1.2 Ngoài phạm vi (Non-Goals)
- Không hỗ trợ cú pháp lập trình điều kiện phức tạp (if/else, while loop) trong template để tránh code injection và logic rườm rà. Chỉ sử dụng cú pháp thay thế biến giá trị thuần túy `{{variable}}` và `{variable}`.

---

## 2. KIẾN TRÚC THÀNH PHẦN (COMPONENT ARCHITECTURE)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FRONTEND: AiSettingsPage.tsx                    │
│   Tab 'Quản lý Prompt Templates' ──► Danh sách Template theo Module   │
│   - Editor Monospace              - Chip chèn biến {{variable}}        │
│   - Nút Preview nội suy           - Nút 'Lưu phiên bản mới'            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ REST API
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               BACKEND: AiPromptTemplateAdminController                │
│   GET /api/v1/ai/admin/prompt-templates?featureGroup=...               │
│   PUT /api/v1/ai/admin/prompt-templates/{code}                         │
│   POST /api/v1/ai/admin/prompt-templates/{code}/preview                │
│   POST /api/v1/ai/admin/prompt-templates/{code}/versions/{v}/activate │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    SERVICE: AiPromptTemplateService                    │
│   - getActiveTemplate(templateCode) -> Cache Redis / DB                │
│   - getResolvedPrompt(code, variables, fallbackDefault)                │
│   - createOrUpdateTemplate(code, request) -> Auto-increment version    │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
                    ▼                                ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     PromptTemplateEngine          │ │   REPOSITORY & CƠ SỞ DỮ LIỆU      │
│  Nội suy biến: compile(...)       │ │   Bảng: ai_prompt_templates       │
│  - Hỗ trợ {{var}} và {var}        │ │   Entity: AiPromptTemplate.java   │
│  - Null-safe & Sanitize an toàn   │ │                                   │
└─────────────────┬─────────────────┘ └───────────────────────────────────┘
                  │
                  ├──► 1. Sàng lọc CV: AiScreeningService
                  ├──► 2. Sinh câu hỏi phỏng vấn: AiInterviewQuestionService
                  ├──► 3. Chấm bài phỏng vấn: AiInterviewGraderService
                  └──► 4. Chấm báo cáo tuần: AiWeeklyReportGraderService
```

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

Tệp Flyway Migration: `V14__create_ai_prompt_templates_table.sql`

```sql
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL COMMENT 'Mã định danh duy nhất của template logic',
    template_name VARCHAR(150) NOT NULL COMMENT 'Tên hiển thị thân thiện trên UI',
    feature_group VARCHAR(50) NOT NULL COMMENT 'SCREENING, INTERVIEW_GEN, INTERVIEW_GRADE, REPORT_GRADE',
    prompt_type VARCHAR(30) NOT NULL DEFAULT 'USER' COMMENT 'SYSTEM hoặc USER',
    version INT NOT NULL DEFAULT 1 COMMENT 'Số phiên bản (1, 2, 3...)',
    template_content MEDIUMTEXT NOT NULL COMMENT 'Nội dung chứa placeholder {{variable}}',
    description VARCHAR(255) NULL COMMENT 'Mô tả mục đích và lưu ý khi tinh chỉnh',
    available_variables_json TEXT NULL COMMENT 'JSON Array các biến cho phép: ["{{cvText}}", "{{internInfo}}"]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Chỉ có duy nhất 1 version is_active=true cho mỗi template_code',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_code_version UNIQUE (template_code, version),
    INDEX idx_template_code_active (template_code, is_active),
    INDEX idx_feature_group (feature_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. BỘ TEMPLATES MẶC ĐỊNH (DEFAULT SEEDED PROMPTS)

Hệ thống tự động seed 6 templates lõi cho 4 nhóm tính năng:

| Mã Template (`template_code`) | Nhóm (`feature_group`) | Loại (`prompt_type`) | Các biến khả dụng (`available_variables`) |
| :--- | :--- | :---: | :--- |
| `SCREENING_USER_PROMPT` | `SCREENING` | `USER` | `{{cvText}}`, `{{candidateInfoJson}}`, `{{programCriteriaJson}}`, `{{rulesJson}}` |
| `INTERVIEW_GEN_SYSTEM` | `INTERVIEW_GEN` | `SYSTEM` | *(Không biến - Định hình vai trò chuyên gia tuyển dụng)* |
| `INTERVIEW_GEN_USER` | `INTERVIEW_GEN` | `USER` | `{{targetRole}}`, `{{totalQuestions}}`, `{{customRequirements}}`, `{{cvText}}`, `{{screeningDossier}}`, `{{programInfo}}`, `{{internInfo}}` |
| `INTERVIEW_GRADE_SYSTEM` | `INTERVIEW_GRADE` | `SYSTEM` | *(Không biến - Định hình chuyên gia khảo thí & 4 trục Rubric)* |
| `INTERVIEW_GRADE_USER` | `INTERVIEW_GRADE` | `USER` | `{{questionListDossier}}`, `{{candidateAnswersDossier}}` |
| `REPORT_GRADE_USER` | `REPORT_GRADE` | `USER` | `{{completedTasks}}`, `{{unfinishedTasks}}`, `{{challenges}}`, `{{learnings}}`, `{{kanbanTasksList}}` |

---

## 5. ĐẶC TẢ HỢP ĐỒNG API (REST API CONTRACTS)

### 5.1. Lấy danh sách Template
- **Method & Path:** `GET /api/v1/ai/admin/prompt-templates`
- **Query Params:** `featureGroup` (Optional: `SCREENING`, `INTERVIEW_GEN`, etc.)
- **Response `200 OK`:**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": [
    {
      "id": 1,
      "templateCode": "SCREENING_USER_PROMPT",
      "templateName": "Prompt Thẩm định CV & Tiêu chuẩn Tuyển dụng",
      "featureGroup": "SCREENING",
      "promptType": "USER",
      "version": 1,
      "templateContent": "Hãy đóng vai trò Chuyên gia thẩm định hồ sơ kỹ thuật...\nCV Ứng viên: {{cvText}}\n...",
      "description": "Dùng để phân tích độ tương thích giữa CV và yêu cầu vị trí tuyển dụng",
      "availableVariables": ["{{cvText}}", "{{candidateInfoJson}}", "{{programCriteriaJson}}", "{{rulesJson}}"],
      "isActive": true,
      "updatedAt": "2026-10-19T10:00:00"
    }
  ]
}
```

### 5.2. Cập nhật hoặc Lưu phiên bản mới
- **Method & Path:** `PUT /api/v1/ai/admin/prompt-templates/{templateCode}`
- **Request Body:**
```json
{
  "templateName": "Prompt Thẩm định CV Cải tiến",
  "templateContent": "Nội dung template mới...",
  "description": "Bổ sung tiêu chí đánh giá kỹ năng mềm",
  "createNewVersion": true
}
```
- **Response `200 OK`:** Trả về template version mới được active.

### 5.3. Thử nghiệm xem trước nội suy (Preview Interpolation)
- **Method & Path:** `POST /api/v1/ai/admin/prompt-templates/{templateCode}/preview`
- **Request Body:**
```json
{
  "templateContent": "Xin chào {{candidateName}}, bạn ứng tuyển vị trí {{role}}",
  "sampleVariables": {
    "candidateName": "Nguyễn Văn A",
    "role": "Java Backend Intern"
  }
}
```
- **Response `200 OK`:**
```json
{
  "code": 200,
  "message": "Nội suy thử nghiệm thành công",
  "data": {
    "resolvedPrompt": "Xin chào Nguyễn Văn A, bạn ứng tuyển vị trí Java Backend Intern",
    "missingVariables": [],
    "unusedVariables": []
  }
}
```

### 5.4. Lịch sử phiên bản & Kích hoạt lại (Rollback)
- **GET** `/api/v1/ai/admin/prompt-templates/{templateCode}/versions`: Lấy danh sách tất cả các version trong quá khứ.
- **POST** `/api/v1/ai/admin/prompt-templates/{templateCode}/versions/{version}/activate`: Đặt version này làm active (chuyển các version khác thành inactive).

---

## 6. CHI TIẾT THUẬT TOÁN NỘI SUY (TEMPLATE ENGINE ALGORITHM)

Component [PromptTemplateEngine.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/ai-service/src/main/java/org/example/aiservice/core/prompt/PromptTemplateEngine.java):
1. **Regex Pattern:** Hỗ trợ song song cả hai dạng cú pháp `\{\{([a-zA-Z0-9_]+)\}\}` và `\{([a-zA-Z0-9_]+)\}`.
2. **Quy tắc Thay thế An toàn (Safe Interpolation Rules):**
   - Nếu `variables == null` $\rightarrow$ giữ nguyên văn bản template.
   - Nếu một biến có trong template nhưng không được truyền trong `Map<String, Object>` $\rightarrow$ thay bằng chuỗi rỗng `""` hoặc giữ nguyên token tùy cờ cấu hình (mặc định thay bằng chuỗi rỗng để tránh rò rỉ token template sang LLM).
   - Nếu giá trị biến là đối tượng phức tạp (List, DTO) $\rightarrow$ tự động chuyển đổi sang chuỗi JSON có format qua `ObjectMapper`.
   - Hiệu năng: Sử dụng `StringBuilder` hoặc `Matcher.appendReplacement` cho độ phức tạp thời gian $O(N)$ với văn bản lớn > 50,000 ký tự thực thi trong $< 3ms$.

---

## 7. RÀNG BUỘC KỸ THUẬT & CHẤT LƯỢNG (CONSTRAINTS ENFORCEMENT)
- **Zero FQCN:** Tuyệt đối không dùng tên lớp kèm package đầy đủ trong code Java.
- **Zero Suppression:** Tuyệt đối không dùng `@SuppressWarnings` hay `@ts-ignore` để giấu lỗi.
- **TDD:** Kiểm thử đơn vị 100% cho `PromptTemplateEngine` và `AiPromptTemplateService` trước khi tích hợp vào các Service chính.

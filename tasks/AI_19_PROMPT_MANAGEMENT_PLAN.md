# KẾ HOẠCH TRIỂN KHAI CHI TIẾT: AI-19 - PROMPT MANAGEMENT ENGINE & VERSIONING

- **Mã tính năng:** `AI-19`
- **Mức độ ưu tiên:** P1 (Clean Architecture & Production Maintenance)
- **Tài liệu Đặc tả Kỹ thuật (Deep Spec):** [SPEC_AI_19_PROMPT_MANAGEMENT_ENGINE.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/SPEC_AI_19_PROMPT_MANAGEMENT_ENGINE.md)
- **Mục tiêu:** Xóa bỏ hoàn toàn các prompt hardcode rải rác trong mã nguồn backend; tập trung toàn bộ prompt vào một **Prompt Management Engine** linh hoạt lưu trữ trong CSDL, hỗ trợ phiên bản hóa (versioning), nội suy biến an toàn (`{{variable}}`), và giao diện quản trị trực quan trên Frontend cho phép Admin tinh chỉnh prompt tức thì mà không cần rebuild/redeploy backend.

---

## 1. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

Tạo Flyway Migration `V14__create_ai_prompt_templates_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL,              -- Mã định danh: SCREENING_USER_PROMPT, INTERVIEW_GEN_SYSTEM, etc.
    template_name VARCHAR(150) NOT NULL,              -- Tên hiển thị: Prompt Sàng lọc CV, Prompt Sinh câu hỏi...
    feature_group VARCHAR(50) NOT NULL,               -- Nhóm tính năng: SCREENING, INTERVIEW_GEN, INTERVIEW_GRADE, REPORT_GRADE
    prompt_type VARCHAR(30) NOT NULL DEFAULT 'USER',  -- SYSTEM hoặc USER
    version INT NOT NULL DEFAULT 1,                   -- Phiên bản template (1, 2, 3...)
    template_content MEDIUMTEXT NOT NULL,             -- Nội dung prompt chứa placeholder {{variable}}
    description VARCHAR(255) NULL,                    -- Mô tả mục đích của prompt
    available_variables_json TEXT NULL,               -- Danh sách biến hỗ trợ (dạng JSON Array: ["{{cvText}}", "{{internInfo}}"])
    is_active BOOLEAN NOT NULL DEFAULT TRUE,          -- Trạng thái kích hoạt (chỉ 1 version active cho mỗi template_code)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_code_version UNIQUE (template_code, version),
    INDEX idx_template_code_active (template_code, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 2. KIẾN TRÚC THÀNH PHẦN BACKEND (CLEAN ARCHITECTURE)

```
[Admin UI / User Flow]
       │
       ▼
[AiPromptTemplateAdminController] ──► Quản lý CRUD / Active Version / Preview
       │
       ▼
[AiPromptTemplateService] ──────────► Lấy active template (hoặc cache Redis)
       │
       ▼
[PromptTemplateEngine] ─────────────► Nội suy biến: compile(template, Map<String, Object> variables)
       │
       ├──► 1. AiScreeningService (Sàng lọc CV)
       ├──► 2. AiInterviewQuestionService (Sinh bộ câu hỏi)
       ├──► 3. AiInterviewGraderService (Chấm điểm phỏng vấn)
       └──► 4. AiWeeklyReportGraderService (Chấm báo cáo tuần)
```

### 2.1. Thành phần mới cần tạo:
1. **Entity & Repository:**
   - `AiPromptTemplate.java`
   - `AiPromptTemplateRepository.java`
2. **Core Template Engine:**
   - `PromptTemplateEngine.java`:
     - Xử lý nội suy biến `{{varName}}` và `{varName}`.
     - Escape an toàn, giữ nguyên văn bản nếu biến không truyền (hoặc cảnh báo).
3. **Service & Admin Controller:**
   - `AiPromptTemplateService.java`:
     - `getResolvedPrompt(templateCode, Map<String, Object> variables, String fallbackDefault)`: Đảm bảo nếu DB chưa có template, tự động fallback về prompt mặc định (Zero-Downtime Guarantee).
     - `createOrUpdateTemplate(...)`: Tự động tăng version nếu chỉnh sửa template đang active.
     - `previewPrompt(...)`: Trộn biến mẫu để test thử kết quả hiển thị.
   - `AiPromptTemplateAdminController.java`

### 2.2. Seed Data Mặc Định (4 nhóm tính năng):
- **SCREENING_USER_PROMPT:** Prompt phân tích CV ứng viên so với tiêu chuẩn chương trình.
- **INTERVIEW_GEN_SYSTEM & INTERVIEW_GEN_USER:** Prompt chỉ dẫn chuyên gia tuyển dụng & dữ liệu ứng viên/CV.
- **INTERVIEW_GRADE_SYSTEM & INTERVIEW_GRADE_USER:** Prompt chấm rubric 4 trục kỹ thuật.
- **REPORT_GRADE_USER:** Prompt chấm báo cáo tuần thực tập sinh đối chiếu Kanban.

---

## 3. GIAO DIỆN QUẢN TRỊ FRONTEND (UI/UX HOÀN HẢO)

Bổ sung Tab **"Prompt Templates"** tại `AiSettingsPage.tsx`:
1. **Tabs Navigation:**
   - Cấu hình chung (Global Config & Provider)
   - Kho Khóa API (Multi-Key Vault & Cooldown)
   - Quản lý Prompt Templates (Prompt Management & Versioning) 🌟
   - Sandbox Thử nghiệm (Test Console)
2. **Prompt Template Card / Accordion:**
   - Danh sách hiển thị theo từng Feature Group (Sàng lọc CV, Sinh câu hỏi, Chấm phỏng vấn, Chấm báo cáo).
   - Hiển thị badge: Feature, Type (SYSTEM / USER), Version hiện tại.
   - Danh sách các biến hợp lệ (Chips: `{{cvText}}`, `{{programCriteria}}` - click để tự động chèn vào con trỏ).
   - Textarea Editor lớn với phông chữ monospace (`JetBrains Mono` / `Courier`).
   - Nút **"Khôi phục mặc định"** và nút **"Lưu phiên bản mới"**.
   - Modal **"Xem trước (Preview)"**: Điền thử dữ liệu giả lập và xem văn bản prompt sau khi nội suy.

---

## 4. QUY TRÌNH KIỂM THỬ TDD (TEST-DRIVEN DEVELOPMENT)

1. **Unit Test Core Template Engine (`PromptTemplateEngineTest.java`):**
   - Test 1: Thay thế biến chính xác với cú pháp `{{name}}` và `{name}`.
   - Test 2: Xử lý biến null / thiếu biến an toàn mà không quăng NullPointerException.
   - Test 3: Xử lý văn bản lớn (CV 10,000 ký tự) nhanh gọn < 5ms.
2. **Integration Test Service (`AiPromptTemplateServiceTest.java`):**
   - Test 1: Lấy prompt từ Database khi đã có active template.
   - Test 2: Fallback an toàn về mã gốc khi Database chưa có dữ liệu.
   - Test 3: Tạo version mới thành công khi sửa nội dung.

---

## 5. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA)
- [ ] 100% các prompt trong `AiScreeningServiceImpl`, `AiInterviewQuestionServiceImpl`, `AiInterviewGraderService`, `AiWeeklyReportGraderService` được chuyển sang gọi qua `AiPromptTemplateService`.
- [ ] Cơ chế Fallback an toàn tuyệt đối: Nếu tắt DB hoặc xóa bảng, hệ thống vẫn hoạt động bình thường với prompt mặc định.
- [ ] Frontend có giao diện trực quan, không lỗi giao diện, responsive đầy đủ.
- [ ] Tuân thủ Clean Code: Không dùng FQCN, không vi phạm bất kỳ ràng buộc chất lượng nào trong `CONSTRAINTS.md`.

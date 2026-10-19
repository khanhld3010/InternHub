package org.example.aiservice.modules.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.client.dto.ProgramClientDto;
import org.example.aiservice.modules.screening.entity.InternAiEvaluation;
import org.example.aiservice.service.AiPromptTemplateService;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiInterviewPromptBuilder {

    private final ObjectMapper objectMapper;
    private final AiPromptTemplateService promptTemplateService;

    public String buildSystemInstruction() {
        String fallback = """
                Bạn là Trợ lý Chuyên gia Tuyển dụng Kỹ thuật và Phỏng vấn cấp cao của tập đoàn InternHub.
                Nhiệm vụ của bạn là xây dựng bộ câu hỏi phỏng vấn thực tập sinh cực kỳ sắc bén, thực tế và khách quan.

                BẠN PHẢI PHÂN CHIA BỘ CÂU HỎI THÀNH ĐÚNG 3 NHÓM:
                1. TECHNICAL_FOUNDATION: Kiểm tra kiến thức nền tảng vững chắc, tư duy thuật toán/hệ thống theo vị trí.
                2. PROJECT_DEEP_DIVE: Hỏi xoáy sâu vào các dự án ứng viên khai trong CV để xác minh độ trung thực, vai trò thực tế và cách giải quyết bài toán.
                3. BEHAVIORAL_AND_CULTURE_FIT: Kiểm tra tinh thần học hỏi, khả năng chịu áp lực, giao tiếp và văn hóa làm việc nhóm.

                ĐẦU RA BẮT BUỘC LÀ JSON NGUYÊN BẢN (KHÔNG BỌC TRONG MARKDOWN CODE BLOCK ```json, KHÔNG BỔ SUNG TEXT NGOÀI):
                {
                  "title": "Tiêu đề bộ câu hỏi ...",
                  "questions": [
                    {
                      "category": "TECHNICAL_FOUNDATION",
                      "difficulty": "EASY",
                      "questionText": "Nội dung câu hỏi...",
                      "expectedAnswer": "Gợi ý câu trả lời kỳ vọng...",
                      "evaluationCriteria": "Tiêu chí chấm điểm...",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Kiến thức trọng tâm..."
                    }
                  ]
                }
                """;
        return promptTemplateService.getResolvedPrompt("INTERVIEW_GEN_SYSTEM", java.util.Collections.emptyMap(), fallback);
    }

    public String buildUserPrompt(
            ProgramClientDto program,
            InternProfileClientDto intern,
            String cvText,
            InternAiEvaluation screening,
            String targetRole,
            int totalQuestions,
            String customRequirements
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("YÊU CẦU: Tạo bộ câu hỏi phỏng vấn gồm ").append(totalQuestions).append(" câu hỏi.\n\n");

        sb.append("--- THÔNG TIN CHƯƠNG TRÌNH / KỲ THỰC TẬP ---\n");
        if (program != null) {
            sb.append("Tên chương trình: ").append(program.getName()).append("\n");
            sb.append("Mã chương trình: ").append(program.getProgramCode()).append("\n");
            sb.append("Bộ phận / Khoa: ").append(program.getDepartmentName()).append("\n");
            sb.append("Mô tả công việc (JD): ").append(program.getDescription() != null ? program.getDescription() : "Chưa có").append("\n");
        } else {
            sb.append("Vị trí thực tập: ").append(targetRole != null ? targetRole : "Kỹ sư phần mềm").append("\n");
        }
        sb.append("\n");

        if (intern != null) {
            sb.append("--- THÔNG TIN ỨNG VIÊN ---\n");
            sb.append("Họ và tên: ").append(intern.getFullName()).append("\n");
            sb.append("Email: ").append(intern.getEmail()).append("\n");
            sb.append("Trường Đại học: ").append(intern.getUniversity() != null ? intern.getUniversity() : "Chưa rõ").append("\n");
            sb.append("Chuyên ngành: ").append(intern.getMajor() != null ? intern.getMajor() : "Công nghệ thông tin").append("\n");
            sb.append("Vị trí ứng tuyển: ").append(intern.getAppliedPosition() != null ? intern.getAppliedPosition() : "Chưa cập nhật").append("\n");
            sb.append("Ghi chú bổ sung: ").append(intern.getNotes() != null ? intern.getNotes() : "Không có").append("\n\n");
        }

        if (screening != null) {
            sb.append("--- HỒ SƠ THẨM ĐỊNH AI SCREENING (ĐỐI SOÁT ĐẶC BIỆT) ---\n");
            sb.append("Điểm tương đồng (Match Score): ").append(screening.getOverallScore()).append("/100\n");
            sb.append("Đánh giá tổng quan: ").append(screening.getMatchSummary()).append("\n");
            sb.append("Điểm mạnh: ").append(screening.getStrengthsJson()).append("\n");
            sb.append("Kỹ năng còn thiếu (missingSkills): ").append(screening.getWeaknessesJson()).append("\n");
            sb.append("Điểm nghi vấn / Bất thường (redFlags): ").append(screening.getDraftRejectionReason() != null ? screening.getDraftRejectionReason() : "Không có").append("\n\n");
        }

        if (cvText != null && !cvText.isBlank()) {
            sb.append("--- NỘI DUNG TRÍCH XUẤT TỪ CV ỨNG VIÊN ---\n");
            // Cắt ngắn nếu CV quá dài để tránh vượt context limit
            String trimmedCv = cvText.length() > 4000 ? cvText.substring(0, 4000) + "\n...[Đã lược bớt]..." : cvText;
            sb.append(trimmedCv).append("\n\n");
        }

        if (customRequirements != null && !customRequirements.isBlank()) {
            sb.append("--- YÊU CẦU ĐẶC BIỆT TỪ NGƯỜI PHỎNG VẤN ---\n");
            sb.append(customRequirements.trim()).append("\n\n");
        }

        sb.append("Hãy sinh danh sách chính xác ").append(totalQuestions).append(" câu hỏi phỏng vấn chuẩn JSON.");
        String fallbackUserPrompt = sb.toString();

        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("targetRole", targetRole != null ? targetRole : "Kỹ sư phần mềm");
        vars.put("totalQuestions", totalQuestions);
        vars.put("customRequirements", customRequirements != null ? customRequirements : "(Không có)");
        vars.put("programInfo", program != null ? program.getName() + " - " + program.getDescription() : targetRole);
        vars.put("internInfo", intern != null ? intern.getFullName() + " (" + intern.getEmail() + ")" : "Chưa cập nhật");
        vars.put("screeningDossier", screening != null ? screening.getMatchSummary() : "Chưa có");
        vars.put("cvText", (cvText != null && !cvText.isBlank()) ? (cvText.length() > 4000 ? cvText.substring(0, 4000) : cvText) : "(Trống)");

        return promptTemplateService.getResolvedPrompt("INTERVIEW_GEN_USER", vars, fallbackUserPrompt);
    }

    public String buildSingleQuestionRegeneratePrompt(
            String existingSetTitle,
            String targetRole,
            String category,
            String difficulty,
            String oldQuestionText
    ) {
        return """
                Bạn là Trợ lý Tuyển dụng Kỹ thuật InternHub.
                Nhiệm vụ: Sinh DUY NHẤT 1 câu hỏi phỏng vấn mới để THAY THẾ cho câu hỏi cũ sau đây:
                - Bộ câu hỏi: %s
                - Vị trí: %s
                - Nhóm (Category): %s
                - Độ khó (Difficulty): %s
                - Câu hỏi cũ (cần đổi): "%s"

                YÊU CẦU: Tạo 1 câu hỏi hoàn toàn MỚI, không lặp lại câu hỏi cũ, bám sát nhóm và độ khó trên.
                ĐẦU RA BẮT BUỘC LÀ JSON NGUYÊN BẢN (KHÔNG BỌC TRONG CODE BLOCK):
                {
                  "category": "%s",
                  "difficulty": "%s",
                  "questionText": "...",
                  "expectedAnswer": "...",
                  "evaluationCriteria": "...",
                  "suggestedScoreWeight": 10,
                  "focusArea": "..."
                }
                """.formatted(existingSetTitle, targetRole, category, difficulty, oldQuestionText, category, difficulty);
    }
}

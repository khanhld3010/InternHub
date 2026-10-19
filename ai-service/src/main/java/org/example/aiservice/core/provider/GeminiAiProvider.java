package org.example.aiservice.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.entity.AiSystemConfig;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.example.aiservice.service.AiConfigAdminService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiProvider implements AiProvider {

    private final GeminiApiClient geminiApiClient;
    private final AiConfigAdminService configAdminService;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Override
    public ScreeningResultDto evaluateCandidate(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    ) {
        AiSystemConfig config = configAdminService.getOrCreateDefaultConfig();

        if (Boolean.FALSE.equals(config.getIsActive())) {
            log.warn("Hệ thống AI đang tắt hoặc bảo trì. Fallback sang kết quả giả lập chuẩn.");
            return generateMockEvaluation(cvText, candidateInfoJson);
        }

        String rawApiKey = null;
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank()) {
            try {
                rawApiKey = encryptionUtil.decrypt(config.getApiKeyEncrypted(), config.getKeyVersion());
            } catch (Exception e) {
                log.error("Không thể giải mã API key cho screening: {}", e.getMessage());
            }
        }

        if (rawApiKey == null || rawApiKey.isBlank() || isMockKey(rawApiKey)) {
            log.info("Chưa cấu hình API Key thật cho AI Service. Sử dụng kết quả thẩm định có cấu trúc chuẩn.");
            return generateMockEvaluation(cvText, candidateInfoJson);
        }

        try {
            String prompt = buildPrompt(cvText, candidateInfoJson, programCriteriaJson, rulesJson);
            String systemInstruction = "Bạn là chuyên gia thẩm định hồ sơ tuyển dụng nhân sự (Senior HR Technical Recruiter). " +
                    "Hãy phân tích và trả về DUY NHẤT một chuỗi JSON chuẩn 100%, không bọc trong markdown block.";

            String responseText = geminiApiClient.generateContent(
                    rawApiKey,
                    config.getModelName(),
                    systemInstruction,
                    Collections.emptyList(),
                    prompt,
                    new BigDecimal("0.20"),
                    2048
            );

            return parseGeminiResponse(responseText);

        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API thẩm định CV: {}", e.getMessage(), e);
            return ScreeningResultDto.builder()
                    .overallScore(0)
                    .recommendation(AiRecommendation.CONSIDER)
                    .matchSummary("Không thể hoàn tất đánh giá AI do sự cố kết nối tới máy chủ Google.")
                    .errorMessage("Lỗi kết nối Gemini: " + e.getMessage())
                    .evaluatedAt(LocalDateTime.now())
                    .build();
        }
    }

    private String buildPrompt(String cvText, String candidateInfoJson, String programCriteriaJson, String rulesJson) {
        return "Hãy đóng vai trò Chuyên gia thẩm định hồ sơ kỹ thuật (Senior Technical Recruiter) tại nền tảng InternHub.\n" +
                "Nhiệm vụ: Phân tích CV ứng viên so với tiêu chuẩn chương trình thực tập và bộ quy tắc, sau đó trả về kết quả theo định dạng JSON duy nhất.\n\n" +
                "--- 1. BỘ QUY TẮC & TIÊU CHUẨN ĐÁNH GIÁ (RULES) ---\n" +
                rulesJson + "\n\n" +
                "--- 2. THÔNG TIN CHƯƠNG TRÌNH THỰC TẬP (PROGRAM CRITERIA) ---\n" +
                programCriteriaJson + "\n\n" +
                "--- 3. THÔNG TIN ỨNG VIÊN (CANDIDATE INFO) ---\n" +
                candidateInfoJson + "\n\n" +
                "--- 4. NỘI DUNG CV BÓC TÁCH (CV TEXT) ---\n" +
                (cvText != null && !cvText.isBlank() ? cvText : "(Không có nội dung CV hoặc file rỗng)") + "\n\n" +
                "YÊU CẦU ĐẶC BIỆT:\n" +
                "- Bắt buộc trả về kết quả theo định dạng JSON chuẩn 100% với các trường:\n" +
                "{\n" +
                "  \"overallScore\": <số nguyên từ 0 đến 100>,\n" +
                "  \"recommendation\": <\"RECOMMENDED_APPROVE\" | \"CONSIDER\" | \"RECOMMENDED_REJECT\">,\n" +
                "  \"matchSummary\": <\"đoạn văn tóm tắt 2-3 câu bằng tiếng Việt\">,\n" +
                "  \"strengths\": [<\"điểm mạnh 1\">, <\"điểm mạnh 2\">, ...],\n" +
                "  \"weaknesses\": [<\"điểm cần cải thiện 1\">, <\"điểm cần cải thiện 2\">, ...],\n" +
                "  \"skillsMatched\": [<\"kỹ năng 1\">, <\"kỹ năng 2\">, ...],\n" +
                "  \"draftRejectionReason\": <\"lý do từ chối mẫu lịch sự bằng tiếng Việt nếu điểm < 75, nếu điểm >= 75 thì để null\">,\n" +
                "  \"suggestedInterviewQuestions\": [<\"câu hỏi phỏng vấn gợi ý 1\">, <\"câu hỏi 2\">, ...]\n" +
                "}\n" +
                "- KHÔNG ĐƯỢC thêm ký tự markdown (như ```json) bên ngoài chuỗi JSON.";
    }

    private ScreeningResultDto parseGeminiResponse(String rawText) throws Exception {
        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("Gemini trả về phản hồi rỗng");
        }

        // Xóa markdown wrappers nếu có
        rawText = rawText.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("```$", "").trim();

        JsonNode evalNode = objectMapper.readTree(rawText);

        int score = evalNode.path("overallScore").asInt(50);
        String recStr = evalNode.path("recommendation").asText("CONSIDER");
        AiRecommendation recommendation;
        try {
            recommendation = AiRecommendation.valueOf(recStr);
        } catch (Exception e) {
            recommendation = score >= 75 ? AiRecommendation.RECOMMENDED_APPROVE :
                    (score >= 50 ? AiRecommendation.CONSIDER : AiRecommendation.RECOMMENDED_REJECT);
        }

        List<String> strengths = extractStringList(evalNode.path("strengths"));
        List<String> weaknesses = extractStringList(evalNode.path("weaknesses"));
        List<String> skills = extractStringList(evalNode.path("skillsMatched"));
        List<String> questions = extractStringList(evalNode.path("suggestedInterviewQuestions"));

        String draftRejection = evalNode.path("draftRejectionReason").isNull() ? null :
                evalNode.path("draftRejectionReason").asText(null);

        return ScreeningResultDto.builder()
                .overallScore(score)
                .recommendation(recommendation)
                .matchSummary(evalNode.path("matchSummary").asText(""))
                .strengths(strengths)
                .weaknesses(weaknesses)
                .skillsMatched(skills)
                .draftRejectionReason(draftRejection)
                .suggestedInterviewQuestions(questions)
                .evaluatedAt(LocalDateTime.now())
                .fromCache(false)
                .build();
    }

    private List<String> extractStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(item.asText());
        }
        return list;
    }

    private boolean isMockKey(String apiKey) {
        return apiKey == null || apiKey.equalsIgnoreCase("mock-key") || apiKey.contains("dummy");
    }

    private ScreeningResultDto generateMockEvaluation(String cvText, String candidateInfoJson) {
        String lowerCv = cvText != null ? cvText.toLowerCase() : "";
        int score = 70;
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> skills = new ArrayList<>();

        if (lowerCv.contains("java") || lowerCv.contains("spring")) {
            score += 15;
            skills.add("Java / Spring Boot Core");
            strengths.add("Có nền tảng lập trình hướng đối tượng (OOP) và kiến thức Spring Boot.");
        }
        if (lowerCv.contains("react") || lowerCv.contains("typescript") || lowerCv.contains("javascript")) {
            score += 10;
            skills.add("Frontend: React / TypeScript");
            strengths.add("Có khả năng xây dựng giao diện người dùng hiện đại với React.");
        }
        if (lowerCv.contains("sql") || lowerCv.contains("mysql")) {
            skills.add("Cơ sở dữ liệu MySQL");
        }

        score = Math.min(score, 95);

        AiRecommendation rec = score >= 75 ? AiRecommendation.RECOMMENDED_APPROVE :
                (score >= 50 ? AiRecommendation.CONSIDER : AiRecommendation.RECOMMENDED_REJECT);

        weaknesses.add("Kinh nghiệm làm việc với hệ thống Microservices quy mô lớn còn ở mức cơ bản.");
        weaknesses.add("Cần nâng cao kỹ năng viết Unit Test và tích hợp CI/CD tự động.");

        List<String> questions = List.of(
                "Em có thể chia sẻ cách quản lý Transaction và tính toàn vẹn dữ liệu trong các dự án đã làm không?",
                "Theo em, sự khác biệt lớn nhất giữa REST API truyền thống và cơ chế Event-Driven là gì?",
                "Em đã từng xử lý vấn đề tối ưu câu truy vấn cơ sở dữ liệu (N+1 query problem) bao giờ chưa?"
        );

        String rejectionReason = score < 75 ?
                "Cảm ơn bạn đã quan tâm đến chương trình thực tập. Qua rà soát hồ sơ, kỹ năng chuyên môn của bạn hiện chưa đáp ứng đầy đủ tiêu chí kỹ thuật của kỳ này. Chúc bạn tiếp tục trau dồi và thành công trong các đợt ứng tuyển tiếp theo." : null;

        return ScreeningResultDto.builder()
                .overallScore(score)
                .recommendation(rec)
                .matchSummary("Ứng viên có kiến thức nền tảng tốt, hồ sơ phù hợp với lộ trình đào tạo của chương trình thực tập.")
                .strengths(strengths)
                .weaknesses(weaknesses)
                .skillsMatched(skills)
                .draftRejectionReason(rejectionReason)
                .suggestedInterviewQuestions(questions)
                .evaluatedAt(LocalDateTime.now())
                .fromCache(false)
                .build();
    }
}

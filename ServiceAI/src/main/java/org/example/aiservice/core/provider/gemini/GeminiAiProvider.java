package org.example.aiservice.core.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.config.GeminiProperties;
import org.example.aiservice.core.provider.AiProvider;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiProvider implements AiProvider {

    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ScreeningResultDto evaluateCandidate(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    ) {
        if (geminiProperties.isMockMode() || isMockKey(geminiProperties.getApiKey())) {
            log.info("Gemini đang ở chế độ MOCK_MODE hoặc chưa có API Key thật. Trả về kết quả phân tích giả lập có cấu trúc chuẩn.");
            return generateMockEvaluation(cvText, candidateInfoJson);
        }

        try {
            return callGeminiApi(cvText, candidateInfoJson, programCriteriaJson, rulesJson);
        } catch (Exception e) {
            log.error("Lỗi khi gọi Google Gemini API: {}", e.getMessage(), e);
            // Fallback sang mock hoặc trả về kết quả có lỗi
            return ScreeningResultDto.builder()
                    .overallScore(0)
                    .recommendation(AiRecommendation.CONSIDER)
                    .matchSummary("Không thể hoàn tất đánh giá AI do lỗi kết nối tới máy chủ Google.")
                    .errorMessage("Lỗi kết nối Gemini: " + e.getMessage())
                    .evaluatedAt(LocalDateTime.now())
                    .build();
        }
    }

    private boolean isMockKey(String key) {
        return key == null || key.isBlank() || key.contains("mock") || key.contains("placeholder");
    }

    private ScreeningResultDto callGeminiApi(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    ) throws Exception {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                geminiProperties.getModel(),
                geminiProperties.getApiKey()
        );

        String prompt = buildPrompt(cvText, candidateInfoJson, programCriteriaJson, rulesJson);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> part = Collections.singletonMap("text", prompt);
        Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(part));
        requestBody.put("contents", Collections.singletonList(content));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.put("temperature", 0.2);
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Gemini API trả về mã lỗi HTTP: " + response.getStatusCode());
        }

        return parseGeminiResponse(response.getBody());
    }

    private String buildPrompt(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    ) {
        return "BẠN LÀ CHUYÊN GIA THẨM ĐỊNH HỒ SƠ TUYỂN DỤNG CỦA INTERNHUB.\n" +
                "Hãy phân tích và đánh giá ứng viên thực tập theo các tiêu chí và dữ liệu sau:\n\n" +
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

    private ScreeningResultDto parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new RuntimeException("Gemini không trả về candidate nào trong response");
        }

        String rawText = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

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

    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private ScreeningResultDto generateMockEvaluation(String cvText, String candidateInfoJson) {
        int score = 82;
        AiRecommendation recommendation = AiRecommendation.RECOMMENDED_APPROVE;

        List<String> strengths = new ArrayList<>();
        strengths.add("Nền tảng kiến thức công nghệ và lập trình phù hợp với vị trí ứng tuyển.");
        strengths.add("Có định hướng chuyên môn rõ ràng và từng tham gia dự án phần mềm.");
        strengths.add("Hồ sơ học vấn đạt chuẩn yêu cầu tiếp nhận thực tập.");

        List<String> weaknesses = new ArrayList<>();
        weaknesses.add("Chưa có nhiều kinh nghiệm làm việc thực tế với quy trình Agile/Scrum.");
        weaknesses.add("Cần bổ sung thêm chứng chỉ chuyên môn hoặc dự án quy mô lớn hơn.");

        List<String> skills = new ArrayList<>();
        skills.add("Java");
        skills.add("Spring Boot");
        skills.add("REST API");
        skills.add("MySQL");
        skills.add("Git");

        List<String> questions = new ArrayList<>();
        questions.add("Em đã từng xây dựng RESTful API nào trong dự án trước đây? Hãy chia sẻ về kiến trúc?");
        questions.add("Em xử lý các vấn đề giao dịch và phân quyền trong hệ thống như thế nào?");
        questions.add("Mục tiêu em muốn đạt được sau 3 tháng thực tập tại công ty là gì?");

        return ScreeningResultDto.builder()
                .overallScore(score)
                .recommendation(recommendation)
                .matchSummary("Ứng viên có tiềm năng tốt, kỹ năng kỹ thuật khớp với tiêu chí chương trình thực tập. Khuyến nghị HR tiếp nhận.")
                .strengths(strengths)
                .weaknesses(weaknesses)
                .skillsMatched(skills)
                .draftRejectionReason(null)
                .suggestedInterviewQuestions(questions)
                .evaluatedAt(LocalDateTime.now())
                .fromCache(false)
                .build();
    }
}

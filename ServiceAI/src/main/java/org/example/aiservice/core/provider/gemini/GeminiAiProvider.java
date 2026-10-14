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

    @Override
    public org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse generateQuizQuestions(
            String contextText,
            String topic,
            String programName,
            String groupName,
            int questionCount,
            org.example.aiservice.modules.quiz.enums.QuizDifficulty difficulty
    ) {
        if (geminiProperties.isMockMode() || isMockKey(geminiProperties.getApiKey())) {
            log.info("Gemini đang ở chế độ MOCK_MODE hoặc chưa có API Key thật. Trả về bộ câu hỏi Quiz giả lập.");
            return generateMockQuiz(topic, programName, groupName, questionCount, difficulty);
        }

        try {
            return callGeminiQuizApi(contextText, topic, programName, groupName, questionCount, difficulty);
        } catch (Exception e) {
            log.error("Lỗi khi gọi Google Gemini API để sinh Quiz: {}", e.getMessage(), e);
            // Fallback sang mock có cảnh báo
            org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse mock =
                    generateMockQuiz(topic, programName, groupName, questionCount, difficulty);
            mock.setErrorMessage("Đã chuyển sang bộ câu hỏi dự phòng do lỗi kết nối Gemini: " + e.getMessage());
            mock.setFromMock(true);
            return mock;
        }
    }

    private org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse callGeminiQuizApi(
            String contextText,
            String topic,
            String programName,
            String groupName,
            int questionCount,
            org.example.aiservice.modules.quiz.enums.QuizDifficulty difficulty
    ) throws Exception {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                geminiProperties.getModel(),
                geminiProperties.getApiKey()
        );

        String prompt = buildQuizPrompt(contextText, topic, programName, groupName, questionCount, difficulty);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> part = Collections.singletonMap("text", prompt);
        Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(part));
        requestBody.put("contents", Collections.singletonList(content));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.put("temperature", 0.3);
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Gemini API trả về mã lỗi HTTP: " + response.getStatusCode());
        }

        return parseGeminiQuizResponse(response.getBody(), topic, difficulty);
    }

    private String buildQuizPrompt(
            String contextText,
            String topic,
            String programName,
            String groupName,
            int questionCount,
            org.example.aiservice.modules.quiz.enums.QuizDifficulty difficulty
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("BẠN LÀ MỘT GIẢNG VIÊN VÀ CHUYÊN GIA CÔNG NGHỆ CAO CẤP TẠI INTERNHUB.\n");
        sb.append("Nhiệm vụ của bạn là tạo ra một bộ câu hỏi trắc nghiệm ôn tập (Quiz) chất lượng cao cho các thực tập sinh.\n\n");

        if (programName != null && !programName.isBlank()) {
            sb.append("Chương trình thực tập: ").append(programName).append("\n");
        }
        if (groupName != null && !groupName.isBlank()) {
            sb.append("Nhóm thực tập sinh: ").append(groupName).append("\n");
        }
        if (topic != null && !topic.isBlank()) {
            sb.append("Chủ đề ôn tập: ").append(topic).append("\n");
        }
        sb.append("Mức độ khó: ").append(difficulty != null ? difficulty.name() : "MEDIUM").append("\n");
        sb.append("Số lượng câu hỏi cần tạo: ").append(questionCount).append(" câu\n\n");

        if (contextText != null && !contextText.isBlank()) {
            sb.append("--- NỘI DUNG BÁO CÁO TUẦN CỦA CÁC THỰC TẬP SINH TRONG NHÓM (DỮ LIỆU THỰC TẾ) ---\n");
            sb.append(contextText).append("\n\n");
            sb.append("YÊU CẦU ĐẶC BIỆT: Hãy bám sát vào những kiến thức, công nghệ, công việc và khó khăn mà các thực tập sinh đã nêu trong báo cáo tuần ở trên để tạo câu hỏi ôn tập sát thực tế nhất.\n\n");
        }

        sb.append("QUY TẮC BẮT BUỘC:\n");
        sb.append("1. Bộ câu hỏi bao gồm cả câu hỏi trắc nghiệm 1 đáp án đúng (questionType: 'SINGLE_CHOICE') và trắc nghiệm nhiều đáp án đúng (questionType: 'MULTIPLE_CHOICE').\n");
        sb.append("2. Mỗi câu hỏi bắt buộc có đúng 4 phương án lựa chọn với nhãn lần lượt là 'A', 'B', 'C', 'D'.\n");
        sb.append("3. Với câu 'SINGLE_CHOICE', có đúng 1 phương án có isCorrect = true.\n");
        sb.append("4. Với câu 'MULTIPLE_CHOICE', có từ 2 phương án trở lên có isCorrect = true.\n");
        sb.append("5. Mỗi câu hỏi phải có lời giải thích chi tiết ('explanation') bằng tiếng Việt giải thích rõ vì sao đáp án đó đúng.\n");
        sb.append("6. Bắt buộc trả về kết quả theo định dạng JSON chuẩn 100% không có markdown bọc ngoài:\n");
        sb.append("{\n");
        sb.append("  \"title\": <tiêu đề bài quiz ngắn gọn, hấp dẫn bằng tiếng Việt>,\n");
        sb.append("  \"topic\": <chủ đề chính>,\n");
        sb.append("  \"estimatedMinutes\": <số phút ước tính làm bài, vd 15 hoặc 20>,\n");
        sb.append("  \"questions\": [\n");
        sb.append("    {\n");
        sb.append("      \"questionText\": <nội dung câu hỏi>,\n");
        sb.append("      \"questionType\": <\"SINGLE_CHOICE\" hoặc \"MULTIPLE_CHOICE\">,\n");
        sb.append("      \"orderIndex\": <số nguyên thứ tự bắt đầu từ 1>,\n");
        sb.append("      \"points\": 10,\n");
        sb.append("      \"explanation\": <lời giải thích chi tiết>,\n");
        sb.append("      \"options\": [\n");
        sb.append("        { \"optionLabel\": \"A\", \"optionText\": <nội dung đáp án A>, \"isCorrect\": <true|false>, \"orderIndex\": 1 },\n");
        sb.append("        { \"optionLabel\": \"B\", \"optionText\": <nội dung đáp án B>, \"isCorrect\": <true|false>, \"orderIndex\": 2 },\n");
        sb.append("        { \"optionLabel\": \"C\", \"optionText\": <nội dung đáp án C>, \"isCorrect\": <true|false>, \"orderIndex\": 3 },\n");
        sb.append("        { \"optionLabel\": \"D\", \"optionText\": <nội dung đáp án D>, \"isCorrect\": <true|false>, \"orderIndex\": 4 }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse parseGeminiQuizResponse(
            String responseJson,
            String fallbackTopic,
            org.example.aiservice.modules.quiz.enums.QuizDifficulty difficulty
    ) throws Exception {
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

        rawText = rawText.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("```$", "").trim();

        JsonNode quizNode = objectMapper.readTree(rawText);

        String title = quizNode.path("title").asText("Bài Quiz Ôn Tập Kiến Thức");
        String topic = quizNode.path("topic").asText(fallbackTopic != null ? fallbackTopic : "Kiến thức chuyên môn");
        int estMinutes = quizNode.path("estimatedMinutes").asInt(15);

        List<org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto> questions = new ArrayList<>();
        JsonNode questionsNode = quizNode.path("questions");

        if (questionsNode.isArray()) {
            int qIndex = 1;
            for (JsonNode qNode : questionsNode) {
                String qText = qNode.path("questionText").asText("");
                String typeStr = qNode.path("questionType").asText("SINGLE_CHOICE");
                org.example.aiservice.modules.quiz.enums.QuestionType qType;
                try {
                    qType = org.example.aiservice.modules.quiz.enums.QuestionType.valueOf(typeStr);
                } catch (Exception e) {
                    qType = org.example.aiservice.modules.quiz.enums.QuestionType.SINGLE_CHOICE;
                }

                int points = qNode.path("points").asInt(10);
                String explanation = qNode.path("explanation").asText("");

                List<org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto> options = new ArrayList<>();
                JsonNode optionsNode = qNode.path("options");

                if (optionsNode.isArray()) {
                    int optIndex = 1;
                    for (JsonNode optNode : optionsNode) {
                        String label = optNode.path("optionLabel").asText(String.valueOf((char) ('A' + optIndex - 1)));
                        String text = optNode.path("optionText").asText("");
                        boolean isCorrect = optNode.path("isCorrect").asBoolean(false);

                        options.add(org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto.builder()
                                .optionLabel(label)
                                .optionText(text)
                                .isCorrect(isCorrect)
                                .orderIndex(optIndex++)
                                .build());
                    }
                }

                questions.add(org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto.builder()
                        .questionText(qText)
                        .questionType(qType)
                        .orderIndex(qIndex++)
                        .points(points)
                        .explanation(explanation)
                        .options(options)
                        .build());
            }
        }

        return org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse.builder()
                .title(title)
                .topic(topic)
                .difficulty(difficulty)
                .totalQuestions(questions.size())
                .estimatedMinutes(estMinutes)
                .questions(questions)
                .fromMock(false)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse generateMockQuiz(
            String topic,
            String programName,
            String groupName,
            int questionCount,
            org.example.aiservice.modules.quiz.enums.QuizDifficulty difficulty
    ) {
        String finalTopic = (topic != null && !topic.isBlank()) ? topic : "Spring Boot, JPA & Microservices";
        String title = "Bài Quiz Ôn Tập: " + finalTopic + (groupName != null ? " - " + groupName : "");

        List<org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto> questions = new ArrayList<>();

        // Câu 1: Single Choice
        List<org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto> opt1 = new ArrayList<>();
        opt1.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("A", "Do cấu hình fetch type EAGER hoặc truy cập lazy collection trong vòng lặp", true, 1));
        opt1.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("B", "Do không khai báo Primary Key cho Entity", false, 2));
        opt1.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("C", "Do sử dụng Hibernate 6 thay vì Hibernate 5", false, 3));
        opt1.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("D", "Do đặt tên method trong Repository sai chuẩn", false, 4));

        questions.add(org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto.builder()
                .questionText("Nguyên nhân chính dẫn đến vấn đề N+1 Query trong Spring Data JPA là gì?")
                .questionType(org.example.aiservice.modules.quiz.enums.QuestionType.SINGLE_CHOICE)
                .orderIndex(1)
                .points(10)
                .explanation("N+1 Query xảy ra khi truy vấn danh sách cha và sau đó Hibernate tự động thực thi thêm N câu truy vấn phụ để nạp các quan hệ con.")
                .options(opt1)
                .build());

        // Câu 2: Multiple Choice
        List<org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto> opt2 = new ArrayList<>();
        opt2.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("A", "Sử dụng từ khóa JOIN FETCH trong câu lệnh JPQL", true, 1));
        opt2.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("B", "Khai báo chú thích @EntityGraph trên Repository method", true, 2));
        opt2.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("C", "Chuyển toàn bộ quan hệ thành FetchType.EAGER", false, 3));
        opt2.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("D", "Sử dụng DTO Projection với constructor expression", true, 4));

        questions.add(org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto.builder()
                .questionText("Những phương pháp nào sau đây giúp khắc phục triệt để lỗi N+1 Query trong JPA? (Chọn nhiều đáp án)")
                .questionType(org.example.aiservice.modules.quiz.enums.QuestionType.MULTIPLE_CHOICE)
                .orderIndex(2)
                .points(10)
                .explanation("Sử dụng JOIN FETCH trong JPQL, @EntityGraph và DTO Projection là các giải pháp tối ưu nhất để nạp dữ liệu liên kết trong 1 truy vấn SQL duy nhất.")
                .options(opt2)
                .build());

        // Câu 3: Single Choice
        List<org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto> opt3 = new ArrayList<>();
        opt3.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("A", "@Transactional(readOnly = true)", true, 1));
        opt3.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("B", "@Transactional(propagation = Propagation.REQUIRES_NEW)", false, 2));
        opt3.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("C", "@Transactional(isolation = Isolation.SERIALIZABLE)", false, 3));
        opt3.add(new org.example.aiservice.modules.quiz.dto.response.AiQuizOptionDto("D", "@Async", false, 4));

        questions.add(org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto.builder()
                .questionText("Để tối ưu hiệu năng cho các phương thức chỉ đọc dữ liệu trong Service layer, nên sử dụng cấu hình giao dịch nào?")
                .questionType(org.example.aiservice.modules.quiz.enums.QuestionType.SINGLE_CHOICE)
                .orderIndex(3)
                .points(10)
                .explanation("@Transactional(readOnly = true) thông báo cho Hibernate không cần thực hiện flush dirty checking, giúp tiết kiệm bộ nhớ và tăng tốc độ truy vấn.")
                .options(opt3)
                .build());

        // Giới hạn số câu theo yêu cầu
        int count = Math.min(questionCount, questions.size());
        List<org.example.aiservice.modules.quiz.dto.response.AiQuizQuestionDto> finalQuestions = questions.subList(0, count);

        return org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse.builder()
                .title(title)
                .topic(finalTopic)
                .difficulty(difficulty != null ? difficulty : org.example.aiservice.modules.quiz.enums.QuizDifficulty.MEDIUM)
                .totalQuestions(finalQuestions.size())
                .estimatedMinutes(15)
                .questions(finalQuestions)
                .fromMock(true)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}


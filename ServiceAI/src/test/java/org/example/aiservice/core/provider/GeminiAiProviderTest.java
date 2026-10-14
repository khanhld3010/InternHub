package org.example.aiservice.core.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiservice.config.GeminiProperties;
import org.example.aiservice.core.provider.gemini.GeminiAiProvider;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiAiProviderTest {

    private GeminiAiProvider geminiAiProvider;
    private GeminiProperties geminiProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.setMockMode(true);
        objectMapper = new ObjectMapper();
        geminiAiProvider = new GeminiAiProvider(geminiProperties, objectMapper);
    }

    @Test
    @DisplayName("Mock Mode: Đánh giá trả về đầy đủ điểm số, khuyến nghị và câu hỏi phỏng vấn")
    void testEvaluateCandidateInMockMode() {
        String cvText = "Họ tên: Nguyễn Văn A. Kỹ năng: Java, Spring Boot, MySQL. GPA: 3.5";
        String candidateInfo = "{\"fullName\": \"Nguyễn Văn A\", \"university\": \"Đại học Bách Khoa\"}";
        String programCriteria = "{\"name\": \"Thực tập sinh Java Backend\", \"department\": \"R&D\"}";
        String rules = "{\"scoring_weights\": {\"technical_skills_percent\": 40}}";

        ScreeningResultDto result = geminiAiProvider.evaluateCandidate(
                cvText, candidateInfo, programCriteria, rules
        );

        assertNotNull(result, "Kết quả đánh giá không được null");
        assertTrue(result.getOverallScore() >= 0 && result.getOverallScore() <= 100, "Điểm phải nằm trong khoảng 0-100");
        assertNotNull(result.getRecommendation(), "Khuyến nghị không được null");
        assertFalse(result.getStrengths().isEmpty(), "Phải có danh sách điểm mạnh");
        assertFalse(result.getSkillsMatched().isEmpty(), "Phải có kỹ năng trùng khớp");
        assertFalse(result.getSuggestedInterviewQuestions().isEmpty(), "Phải có câu hỏi phỏng vấn gợi ý");
    }
}

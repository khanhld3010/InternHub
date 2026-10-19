package org.example.aiservice.core.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateEngineTest {

    private PromptTemplateEngine engine;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        engine = new PromptTemplateEngine(objectMapper);
    }

    @Test
    @DisplayName("Nội suy chính xác biến đơn giản với cả 2 cú pháp {{var}} và {var}")
    void shouldInterpolateSimpleVariablesCorrectly() {
        String template = "Xin chào {{name}}, bạn đang nộp hồ sơ vào vị trí {role} tại công ty {{company}}.";
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Nguyễn Văn A");
        vars.put("role", "Java Backend Intern");
        vars.put("company", "InternHub");

        String result = engine.compile(template, vars);

        assertThat(result).isEqualTo("Xin chào Nguyễn Văn A, bạn đang nộp hồ sơ vào vị trí Java Backend Intern tại công ty InternHub.");
    }

    @Test
    @DisplayName("Xử lý an toàn khi biến null hoặc không có trong Map")
    void shouldHandleMissingAndNullVariablesSafely() {
        String template = "Ứng viên: {{name}}, Điểm: {{score}}, Ghi chú: {{note}}";
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Lê Thị B");
        vars.put("score", null);

        String result = engine.compile(template, vars);

        assertThat(result).isEqualTo("Ứng viên: Lê Thị B, Điểm: , Ghi chú: ");
    }

    @Test
    @DisplayName("Tự động serialize Object phức tạp (List, Map) sang JSON")
    void shouldSerializeComplexObjectsToJson() {
        String template = "Danh sách kỹ năng: {{skills}}";
        Map<String, Object> vars = new HashMap<>();
        vars.put("skills", List.of("Java", "Spring Boot", "MySQL"));

        String result = engine.compile(template, vars);

        assertThat(result).contains("[\"Java\",\"Spring Boot\",\"MySQL\"]");
    }

    @Test
    @DisplayName("Xử lý văn bản lớn (> 20,000 ký tự) không bị nghẽn và giữ nguyên cấu trúc")
    void shouldHandleLargeTextFastAndAccurately() {
        String largeCv = "Kinh nghiệm làm việc... ".repeat(1000);
        String template = "Phân tích CV sau:\n<cv>\n{{cvText}}\n</cv>\nVị trí: {{targetRole}}";
        Map<String, Object> vars = Map.of(
                "cvText", largeCv,
                "targetRole", "Frontend React Intern"
        );

        long start = System.currentTimeMillis();
        String result = engine.compile(template, vars);
        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(200); // Rất nhanh < 200ms
        assertThat(result).contains("Frontend React Intern");
        assertThat(result).contains(largeCv);
    }
}

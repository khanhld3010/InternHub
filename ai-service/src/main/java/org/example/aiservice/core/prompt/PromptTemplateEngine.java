package org.example.aiservice.core.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine nội suy biến cho Prompt Templates.
 * Hỗ trợ cú pháp: {{varName}} và {varName}.
 * Đảm bảo Null-safe, thread-safe và hiệu năng cao.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromptTemplateEngine {

    private final ObjectMapper objectMapper;

    // Regex pattern nhận diện cả {{varName}} và {varName}
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{?([a-zA-Z0-9_]+)\\}?\\}");

    /**
     * Nội suy biến vào template.
     *
     * @param templateContent Nội dung prompt template
     * @param variables       Map các biến truyền vào
     * @return Chuỗi prompt sau khi nội suy
     */
    public String compile(String templateContent, Map<String, Object> variables) {
        if (templateContent == null || templateContent.isBlank()) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return templateContent;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (variables.containsKey(variableName)) {
                Object value = variables.get(variableName);
                String stringValue = formatValue(value);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(stringValue));
            } else {
                // Biến không có trong Map, giữ nguyên token hoặc thay rỗng tùy nhu cầu
                matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        // Đối tượng phức tạp (List, Map, DTO) -> chuyển sang JSON
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Không thể chuyển đổi object sang JSON trong PromptTemplateEngine: {}", e.getMessage());
            return String.valueOf(value);
        }
    }
}

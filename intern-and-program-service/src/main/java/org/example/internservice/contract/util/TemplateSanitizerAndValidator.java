package org.example.internservice.contract.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateSanitizerAndValidator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    // Whitelist approved variables theo mục 1 của Business Contract
    private static final Set<String> APPROVED_VARIABLES = Set.of(
            "intern.fullName",
            "intern.cccd",
            "intern.university",
            "intern.email",
            "intern.phone",
            "contract.number",
            "contract.allowance",
            "contract.startDate",
            "contract.endDate",
            "contract.position",
            "contract.department",
            "contract.supervisor",
            "contract.customTerms"
    );

    /**
     * Kiểm tra cú pháp và danh sách biến hợp lệ, ngăn chặn biến lạ (unknown placeholder)
     */
    public void validateTemplatePlaceholders(String templateContent) {
        if (templateContent == null || templateContent.isBlank()) {
            throw new IllegalArgumentException("Nội dung mẫu hợp đồng không được để trống");
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent);
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            if (!APPROVED_VARIABLES.contains(placeholder)) {
                throw new IllegalArgumentException("Phát hiện placeholder không hợp lệ trong mẫu hợp đồng: {{" + placeholder + "}}");
            }
        }
    }

    /**
     * Làm sạch HTML phòng chống tấn công XSS theo mục 10 của Business Contract
     */
    public String sanitizeHtml(String htmlContent) {
        if (htmlContent == null) return "";
        // Loại bỏ thẻ script, iframe, các handler nguy hiểm
        return htmlContent
                .replaceAll("(?i)<script.*?>.*?</script>", "")
                .replaceAll("(?i)<iframe.*?>.*?</iframe>", "")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)onerror\\s*=", "data-blocked=")
                .replaceAll("(?i)onclick\\s*=", "data-blocked=");
    }

    /**
     * Render Canonical HTML Snapshot từ template và dictionary variables
     */
    public String renderCanonicalSnapshot(String templateContent, Map<String, String> variables) {
        String result = sanitizeHtml(templateContent);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String replacement = entry.getValue() != null ? Matcher.quoteReplacement(entry.getValue()) : "";
            result = result.replace(placeholder, replacement);
        }
        return result.trim();
    }

    /**
     * Tính mã băm SHA-256 Checksum của Canonical Snapshot theo mục 15
     */
    public String calculateSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán SHA-256", e);
        }
    }
}

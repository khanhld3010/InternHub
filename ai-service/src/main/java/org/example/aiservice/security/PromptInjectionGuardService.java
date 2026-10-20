package org.example.aiservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * AI-15: Prompt Injection & Jailbreak Defense cấp Web/Controller.
 * Kiểm tra và chặn đứng các chuỗi tấn công phá rào (Jailbreak, DAN mode, Prompt Leaks, Score Manipulation)
 * trước khi gọi tới Cloud LLM nhằm tiết kiệm 100% token ngân sách và ngăn chặn rủi ro bảo mật.
 */
@Service
@Slf4j
public class PromptInjectionGuardService {

    // Danh sách các Regex phát hiện các mẫu câu ép buộc / ghi đè prompt hệ thống
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 1. Instruction Overrides & Disregard Commands
            Pattern.compile("(?i)\\b(ignore|disregard|forget|override)\\s+(all\\s+)?(previous|prior|above|existing)\\s+(instructions?|directions?|prompts?|rules?)\\b"),
            Pattern.compile("(?i)\\bdisregard\\s+(all\\s+)?(above|everything)\\b"),

            // 2. Jailbreak Modes & Persona Switching
            Pattern.compile("(?i)\\b(dan\\s+mode|do\\s+anything\\s+now|developer\\s+mode\\s+enabled|jailbreak\\s+mode)\\b"),
            Pattern.compile("(?i)\\byou\\s+are\\s+now\\s+(an?\\s+)?(unfiltered|evil|jailbroken|free)\\s+ai\\b"),
            Pattern.compile("(?i)\\bact\\s+as\\s+(an?\\s+)?(unfiltered|unrestricted|jailbroken)\\b"),

            // 3. Score & Grading Manipulation (Ứng viên ép AI chấm điểm tối đa)
            Pattern.compile("(?i)\\b(give\\s+me|assign\\s+me|score:?|grade:?)\\s+(100|full\\s+marks?|5\\s+stars?|10/10)\\b"),
            Pattern.compile("(?i)\\b(always\\s+reply\\s+with|output\\s+only|print)\\s+['\"]?passed['\"]?\\b"),

            // 4. System Prompt Leaking (Khai thác prompt nội bộ)
            Pattern.compile("(?i)\\b(output|print|reveal|show|display)\\s+(your\\s+)?(initial|system|original)\\s+(prompt|instruction|rules)\\b")
    );

    // Ký tự đặc biệt lặp lại liên tục >= 20 ký tự (Spam payload làm tràn context window)
    private static final Pattern REPEATED_SPECIAL_CHARS = Pattern.compile("([^a-zA-Z0-9\\s\\u00C0-\\u1EF9])\\1{19,}");

    /**
     * Kiểm tra tính hợp lệ và an toàn của chuỗi văn bản đầu vào do người dùng nhập.
     * Ném ResponseStatusException(HttpStatus.BAD_REQUEST) nếu phát hiện hành vi tấn công hoặc bất thường.
     *
     * @param text Văn bản cần kiểm duyệt
     * @param contextName Tên ngữ cảnh (CHATBOT, INTERVIEW_ANSWER, REPORT_SUBMISSION...)
     * @param userId Định danh người gửi hoặc IP
     */
    public void validatePromptInput(String text, String contextName, String userId) {
        if (text == null || text.isBlank()) {
            return;
        }

        String normalizedText = text.trim();

        // 1. Kiểm tra các mẫu câu Prompt Injection & Jailbreak đã định nghĩa
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(normalizedText).find()) {
                log.warn("[AI-15 SECURITY ALERT] Phát hiện Prompt Injection từ User: [{}], Ngữ cảnh: [{}], Pattern: [{}]",
                        userId, contextName, pattern.pattern());
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Yêu cầu bị từ chối do nội dung chứa mẫu câu có dấu hiệu thao túng mô hình AI (Jailbreak / Prompt Injection)."
                );
            }
        }

        // 2. Kiểm tra chuỗi spam ký tự đặc biệt bất thường
        if (REPEATED_SPECIAL_CHARS.matcher(normalizedText).find()) {
            log.warn("[AI-15 SECURITY ALERT] Phát hiện Spam Ký Tự Bất Thường từ User: [{}], Ngữ cảnh: [{}]",
                    userId, contextName);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yêu cầu bị từ chối do văn bản chứa chuỗi ký tự lặp lại bất thường."
            );
        }
    }
}

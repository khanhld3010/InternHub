package org.example.aiservice.security;

import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * AI-13: Khử định danh & Bảo vệ quyền riêng tư dữ liệu cá nhân (PII Sanitization).
 * Chuyển đổi CCCD, SĐT, Email, STK ngân hàng, Ngày sinh và Địa chỉ chi tiết thành các Placeholder
 * đồng nhất trước khi chuyển tiếp dữ liệu sang Cloud LLM (Gemini).
 */
@Slf4j
@Service
public class PiiDataSanitizerService {

    public static final String MASK_CITIZEN_ID = "[CITIZEN_ID_MASKED]";
    public static final String MASK_PHONE = "[PHONE_MASKED]";
    public static final String MASK_EMAIL = "[EMAIL_MASKED]";
    public static final String MASK_DOB = "[DOB_MASKED]";
    public static final String MASK_BANK_ACCOUNT = "[BANK_ACCOUNT_MASKED]";
    public static final String MASK_ADDRESS = "[ADDRESS_MASKED]";

    // Email Pattern chuẩn RFC 5322 giản lược
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    // CCCD (12 số) hoặc CMND (9 số) có ranh giới từ hoặc tiền tố CCCD/CMND
    private static final Pattern CITIZEN_ID_PATTERN = Pattern.compile(
            "(?i)(?:cccd|cmnd|căn\\s*cước|chứng\\s*minh)?\\s*(?:[:#\\-]?\\s*)\\b(\\d{12}|\\d{9})\\b"
    );

    // Số điện thoại Việt Nam (09x, 08x, 07x, 03x, 05x hoặc +84) kèm dấu chấm / gạch ngang
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+84|0)(?:\\s*\\.?\\s*|\\s*\\-?\\s*)[1-9](?:\\d\\s*\\.?\\s*|\\d\\s*\\-?\\s*){8}\\b"
    );

    // Ngày tháng năm sinh dạng dd/MM/yyyy hoặc dd-MM-yyyy (tránh bắt nhầm năm kinh nghiệm lẻ như 2023)
    private static final Pattern DOB_PATTERN = Pattern.compile(
            "\\b(?:0?[1-9]|[12][0-9]|3[01])[\\/\\-](?:0?[1-9]|1[012])[\\/\\-](?:19\\d{2}|20\\d{2})\\b"
    );

    // Số tài khoản ngân hàng: STK / tài khoản / bank account đi kèm dãy 8-16 chữ số
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile(
            "(?i)(?:stk|số\\s*tài\\s*khoản|tài\\s*khoản\\s*(?:ngân\\s*hàng)?|bank\\s*account)?\\s*(?:[:#\\-]?\\s*)\\b(\\d{8,16})\\b"
    );

    // Địa chỉ chi tiết (bắt đầu bằng Số x ngõ/ngách/hẻm hoặc đường/phố chi tiết)
    private static final Pattern SPECIFIC_ADDRESS_PATTERN = Pattern.compile(
            "(?i)\\b(?:số\\s*\\d+[^,\\n]{0,30}(?:ngõ|ngách|hẻm|đường|phố)[^,\\n]*)"
    );

    /**
     * Khử định danh toàn bộ PII trong văn bản tự do (CV text, báo cáo, câu trả lời)
     *
     * @param text Chuỗi đầu vào
     * @return Chuỗi đã được thay thế PII bằng placeholder
     */
    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;

        // 1. Khử Email trước (để tránh các regex số bắt nhầm domain)
        result = EMAIL_PATTERN.matcher(result).replaceAll(MASK_EMAIL);

        // 2. Khử Ngày sinh
        result = DOB_PATTERN.matcher(result).replaceAll(MASK_DOB);

        // 3. Khử CCCD / CMND: Dùng Matcher.replaceAll lambda trực tiếp
        result = CITIZEN_ID_PATTERN.matcher(result).replaceAll(match -> {
            String full = match.group();
            String digits = match.group(1);
            if (digits != null) {
                return full.replace(digits, MASK_CITIZEN_ID);
            }
            return MASK_CITIZEN_ID;
        });

        // 4. Khử Số tài khoản ngân hàng (nếu có từ khóa nhận diện đi kèm)
        result = BANK_ACCOUNT_PATTERN.matcher(result).replaceAll(match -> {
            String full = match.group();
            String digits = match.group(1);
            if (digits != null) {
                String lower = full.toLowerCase();
                if (lower.contains("stk") || lower.contains("khoản") || lower.contains("bank")) {
                    return full.replace(digits, MASK_BANK_ACCOUNT);
                }
            }
            return full;
        });

        // 5. Khử Số điện thoại
        result = PHONE_PATTERN.matcher(result).replaceAll(MASK_PHONE);

        // 6. Khử Địa chỉ nhà chi tiết
        result = SPECIFIC_ADDRESS_PATTERN.matcher(result).replaceAll(MASK_ADDRESS);

        return result;
    }

    /**
     * Tạo bản sao đã khử định danh của InternProfileClientDto trước khi nạp vào AI Prompt
     */
    public InternProfileClientDto sanitizeInternProfile(InternProfileClientDto original) {
        if (original == null) {
            return null;
        }

        return InternProfileClientDto.builder()
                .id(original.getId())
                .internCode(original.getInternCode())
                .fullName(original.getFullName()) // Tên ứng viên giữ nguyên cho context phỏng vấn/chào hỏi
                .email(original.getEmail() != null && !original.getEmail().isBlank() ? MASK_EMAIL : original.getEmail())
                .phone(original.getPhone() != null && !original.getPhone().isBlank() ? MASK_PHONE : original.getPhone())
                .university(original.getUniversity())
                .major(original.getMajor())
                .academicYear(original.getAcademicYear())
                .appliedPosition(original.getAppliedPosition())
                .status(original.getStatus())
                .programId(original.getProgramId())
                .userId(original.getUserId())
                .notes(sanitize(original.getNotes()))
                .build();
    }
}

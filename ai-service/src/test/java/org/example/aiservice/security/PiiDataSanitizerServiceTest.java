package org.example.aiservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDataSanitizerServiceTest {

    private PiiDataSanitizerService sanitizerService;

    @BeforeEach
    void setUp() {
        sanitizerService = new PiiDataSanitizerService();
    }

    @Test
    @DisplayName("CCCD/CMND: Che mờ căn cước công dân 12 chữ số và CMND 9 chữ số thành [CITIZEN_ID_MASKED]")
    void sanitize_citizenId_shouldMaskCorrectly() {
        String input = "Ứng viên có số CCCD: 001202008987 và CMND cũ: 013456789 đi kèm.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[CITIZEN_ID_MASKED]")
                .doesNotContain("001202008987")
                .doesNotContain("013456789");
    }

    @Test
    @DisplayName("Số điện thoại: Che mờ các định dạng SĐT Việt Nam thành [PHONE_MASKED]")
    void sanitize_phoneNumber_shouldMaskCorrectly() {
        String input = "Liên hệ qua SĐT: 0987654321 hoặc +84912345678 hoặc 032.123.4567 để trao đổi.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[PHONE_MASKED]")
                .doesNotContain("0987654321")
                .doesNotContain("+84912345678")
                .doesNotContain("032.123.4567");
    }

    @Test
    @DisplayName("Email: Che mờ địa chỉ email cá nhân thành [EMAIL_MASKED]")
    void sanitize_email_shouldMaskCorrectly() {
        String input = "Gửi CV về email sinhvien.it@gmail.com hoặc huyla.work@company.edu.vn nhé.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[EMAIL_MASKED]")
                .doesNotContain("sinhvien.it@gmail.com")
                .doesNotContain("huyla.work@company.edu.vn");
    }

    @Test
    @DisplayName("Ngày sinh DOB: Che mờ ngày tháng năm sinh định dạng dd/MM/yyyy hoặc dd-MM-yyyy thành [DOB_MASKED]")
    void sanitize_dateOfBirth_shouldMaskCorrectly() {
        String input = "Sinh ngày 15/08/2003 hoặc Ngày sinh: 02-11-2002 tại Hà Nội.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[DOB_MASKED]")
                .doesNotContain("15/08/2003")
                .doesNotContain("02-11-2002");
    }

    @Test
    @DisplayName("Số tài khoản ngân hàng: Che mờ số tài khoản (STK / Bank account) thành [BANK_ACCOUNT_MASKED]")
    void sanitize_bankAccount_shouldMaskCorrectly() {
        String input = "Thông tin nhận lương: STK: 19036789123456 Techcombank hoặc tài khoản ngân hàng 001100223344 MBBank.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[BANK_ACCOUNT_MASKED]")
                .doesNotContain("19036789123456")
                .doesNotContain("001100223344");
    }

    @Test
    @DisplayName("Địa chỉ nhà cụ thể: Che mờ số nhà tên đường chi tiết thành [ADDRESS_MASKED]")
    void sanitize_specificAddress_shouldMaskCorrectly() {
        String input = "Địa chỉ thường trú: Số 45 ngõ 128 đường Xuân Thủy, Cầu Giấy, Hà Nội.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("[ADDRESS_MASKED]")
                .doesNotContain("Số 45 ngõ 128");
    }

    @Test
    @DisplayName("Bảo toàn kỹ năng công nghệ và năm kinh nghiệm: Không che nhầm từ khóa kỹ thuật")
    void sanitize_technicalKeywords_shouldNotBeMasked() {
        String input = "Kinh nghiệm làm việc từ năm 2023 đến 2026. Sử dụng Java 17, Spring Boot 3.2, Python 3.11, Docker port 8080.";
        String result = sanitizerService.sanitize(input);

        assertThat(result)
                .contains("2023")
                .contains("2026")
                .contains("Java 17")
                .contains("Spring Boot 3.2")
                .contains("Python 3.11")
                .contains("8080");
    }

    @Test
    @DisplayName("Khử định danh cho DTO InternProfile: Giữ nguyên DTO gốc, sinh ra bản sao an toàn")
    void sanitizeInternProfileDto_shouldReturnMaskedCopy() {
        org.example.aiservice.client.dto.InternProfileClientDto original = org.example.aiservice.client.dto.InternProfileClientDto.builder()
                .id(101L)
                .internCode("INT_2026_01")
                .fullName("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .phone("0988776655")
                .university("Đại học Bách Khoa")
                .major("Công nghệ thông tin")
                .appliedPosition("Backend Java Intern")
                .notes("Địa chỉ: Số 12 ngách 4 đường Giải Phóng, STK: 9876543210 VCB")
                .build();

        org.example.aiservice.client.dto.InternProfileClientDto sanitized = sanitizerService.sanitizeInternProfile(original);

        // Bản gốc không bị đột biến (Immutable principle)
        assertThat(original.getEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(original.getPhone()).isEqualTo("0988776655");

        // Bản đã khử định danh
        assertThat(sanitized.getEmail()).isEqualTo("[EMAIL_MASKED]");
        assertThat(sanitized.getPhone()).isEqualTo("[PHONE_MASKED]");
        assertThat(sanitized.getUniversity()).isEqualTo("Đại học Bách Khoa");
        assertThat(sanitized.getMajor()).isEqualTo("Công nghệ thông tin");
        assertThat(sanitized.getNotes()).contains("[ADDRESS_MASKED]");
        assertThat(sanitized.getNotes()).contains("[BANK_ACCOUNT_MASKED]");
    }
}

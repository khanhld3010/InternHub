package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendContractNotificationEmailRequest {

    private Long contractId;

    @NotBlank(message = "Email người nhận không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String recipientEmail;

    @NotBlank(message = "Họ tên người nhận không được để trống")
    private String recipientName;

    @NotBlank(message = "Mã hợp đồng không được để trống")
    private String contractNumber;

    @NotBlank(message = "Tiêu đề hợp đồng không được để trống")
    private String contractTitle;

    private String contractType; // OFFICIAL_INTERNSHIP, EXTENSION_APPENDIX

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal allowanceAmount;

    /**
     * Loại sự kiện email:
     * - CONTRACT_INVITATION: HR phát hành hợp đồng, mời TTS ký
     * - CONTRACT_REMINDER: HR gửi nhắc nhở TTS ký hợp đồng
     * - CONTRACT_SIGNED: TTS đã ký xác nhận, báo HR
     * - CONTRACT_FEEDBACK: TTS gửi thắc mắc (scan mờ, sai điều khoản), báo HR
     */
    @NotBlank(message = "Loại sự kiện thông báo không được để trống")
    private String eventType;

    private String notes;

    private String feedbackNotes;

    private String signerFullName;
}

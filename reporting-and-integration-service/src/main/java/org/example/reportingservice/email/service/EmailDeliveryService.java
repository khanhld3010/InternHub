package org.example.reportingservice.email.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.reportingservice.email.client.InternServiceCallbackClient;
import org.example.reportingservice.email.dto.request.SendInternDecisionEmailRequest;
import org.example.reportingservice.email.entity.EmailLog;
import org.example.reportingservice.email.entity.EmailStatus;
import org.example.reportingservice.email.repository.EmailLogRepository;
import org.example.reportingservice.email.template.EmailTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateBuilder emailTemplateBuilder;
    private final InternServiceCallbackClient callbackClient;
    private final JavaMailSender mailSender;

    @Value("${app.mail.mock-mode:true}")
    private boolean mockMode;

    @Value("${spring.mail.username:noreply.internhub@gmail.com}")
    private String fromEmail;

    @Value("${app.mail.company-name:InternHub Technology}")
    private String companyName;

    @Async
    public void processAndSendAsync(SendInternDecisionEmailRequest request) {
        log.info("Bat dau xu ly gui email async cho ho so ID: {}, email: {}, decision: {}",
                request.getInternProfileId(), request.getEmail(), request.getDecision());

        String templateCode = "APPROVED".equalsIgnoreCase(request.getDecision()) ? "INTERN_APPROVED" : "INTERN_REJECTED";
        String subject = "APPROVED".equalsIgnoreCase(request.getDecision())
                ? "[InternHub] Chúc mừng! Hồ sơ thực tập của bạn đã được tiếp nhận"
                : "[InternHub] Thông báo kết quả ứng tuyển thực tập sinh";

        // 1. Lưu EmailLog ở trạng thái PENDING
        EmailLog emailLog = EmailLog.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .referenceId(request.getInternProfileId())
                .recipientEmail(request.getEmail())
                .recipientName(request.getFullName())
                .subject(subject)
                .templateCode(templateCode)
                .status(EmailStatus.PENDING)
                .build();

        emailLog = emailLogRepository.save(emailLog);

        // 2. Build HTML Content
        String htmlContent = "APPROVED".equalsIgnoreCase(request.getDecision())
                ? emailTemplateBuilder.buildApprovedEmail(request)
                : emailTemplateBuilder.buildRejectedEmail(request);

        try {
            if (mockMode || fromEmail == null || fromEmail.isBlank()) {
                log.info("[MOCK EMAIL SENDER] Gui email gia lap toi {}. Tieu de: '{}'.", request.getEmail(), subject);
                // Giả lập độ trễ mạng ngắn
                Thread.sleep(500);
            } else {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, companyName);
                helper.setTo(request.getEmail());
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Da gui email SMTP thuc te thanh cong toi: {}", request.getEmail());
            }

            // 3. Cập nhật trạng thái SENT
            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            emailLog.setErrorMessage(null);
            emailLogRepository.save(emailLog);

            // 4. Callback sang InternService
            callbackClient.notifyStatusCallback(request.getInternProfileId(), "SENT", null, request.getIdempotencyKey());

        } catch (Exception e) {
            log.error("Loi khi gui email toi {}: {}", request.getEmail(), e.getMessage(), e);
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 900
                    ? e.getMessage().substring(0, 900) : e.getMessage());
            emailLogRepository.save(emailLog);

            // Callback sang InternService báo FAILED
            callbackClient.notifyStatusCallback(request.getInternProfileId(), "FAILED", emailLog.getErrorMessage(), request.getIdempotencyKey());
        }
    }
}

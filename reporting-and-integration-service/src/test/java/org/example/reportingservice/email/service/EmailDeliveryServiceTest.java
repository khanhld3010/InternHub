package org.example.reportingservice.email.service;

import org.example.reportingservice.email.client.InternServiceCallbackClient;
import org.example.reportingservice.email.dto.request.SendInternDecisionEmailRequest;
import org.example.reportingservice.email.entity.EmailLog;
import org.example.reportingservice.email.entity.EmailStatus;
import org.example.reportingservice.email.repository.EmailLogRepository;
import org.example.reportingservice.email.template.EmailTemplateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private EmailTemplateBuilder emailTemplateBuilder;

    @Mock
    private InternServiceCallbackClient callbackClient;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailDeliveryService emailDeliveryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailDeliveryService, "mockMode", true);
        ReflectionTestUtils.setField(emailDeliveryService, "fromEmail", "noreply@internhub.com");
        ReflectionTestUtils.setField(emailDeliveryService, "companyName", "InternHub Technology");
    }

    @Test
    @DisplayName("processAndSendAsync: Gui email approved thanh cong va goi callback sang InternService")
    void processAndSendAsync_whenApproved_shouldSaveEmailLogAndNotifyCallback() {
        SendInternDecisionEmailRequest request = SendInternDecisionEmailRequest.builder()
                .idempotencyKey("KEY-123")
                .internProfileId(1L)
                .internCode("INT-2026-0001")
                .fullName("Nguyen Van A")
                .email("vana@example.com")
                .decision("APPROVED")
                .appliedPosition("Java Backend Intern")
                .startDate(LocalDate.now().plusDays(7))
                .onboardingToken("token-test-123")
                .build();

        EmailLog pendingLog = EmailLog.builder()
                .id(10L)
                .status(EmailStatus.PENDING)
                .build();

        when(emailLogRepository.save(any(EmailLog.class))).thenReturn(pendingLog);
        when(emailTemplateBuilder.buildApprovedEmail(request)).thenReturn("<html>Approved</html>");

        emailDeliveryService.processAndSendAsync(request);

        verify(emailLogRepository, atLeast(2)).save(any(EmailLog.class));
        verify(callbackClient).notifyStatusCallback(eq(1L), eq("SENT"), isNull(), eq("KEY-123"));
    }

    @Test
    @DisplayName("processAndSendAsync: Gui email rejected thanh cong va goi callback sang InternService")
    void processAndSendAsync_whenRejected_shouldSaveEmailLogAndNotifyCallback() {
        SendInternDecisionEmailRequest request = SendInternDecisionEmailRequest.builder()
                .idempotencyKey("KEY-456")
                .internProfileId(2L)
                .internCode("INT-2026-0002")
                .fullName("Tran Thi B")
                .email("thib@example.com")
                .decision("REJECTED")
                .rejectionReason("Chưa đủ kinh nghiệm về hệ thống phân tán")
                .appliedPosition("DevOps Intern")
                .build();

        EmailLog pendingLog = EmailLog.builder()
                .id(11L)
                .status(EmailStatus.PENDING)
                .build();

        when(emailLogRepository.save(any(EmailLog.class))).thenReturn(pendingLog);
        when(emailTemplateBuilder.buildRejectedEmail(request)).thenReturn("<html>Rejected</html>");

        emailDeliveryService.processAndSendAsync(request);

        verify(emailLogRepository, atLeast(2)).save(any(EmailLog.class));
        verify(callbackClient).notifyStatusCallback(eq(2L), eq("SENT"), isNull(), eq("KEY-456"));
    }
}

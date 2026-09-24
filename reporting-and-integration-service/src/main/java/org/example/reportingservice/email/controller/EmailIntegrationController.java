package org.example.reportingservice.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.reportingservice.common.dto.response.ApiResponse;
import org.example.reportingservice.email.dto.request.SendInternDecisionEmailRequest;
import org.example.reportingservice.email.dto.response.EmailStatusResponse;
import org.example.reportingservice.email.entity.EmailLog;
import org.example.reportingservice.email.repository.EmailLogRepository;
import org.example.reportingservice.email.service.EmailDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/integration/emails")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email Integration API", description = "API tiếp nhận yêu cầu gửi email và kiểm tra trạng thái từ các Microservice khác")
public class EmailIntegrationController {

    private final EmailDeliveryService emailDeliveryService;
    private final EmailLogRepository emailLogRepository;

    @PostMapping("/intern-decision")
    @Operation(summary = "Tiếp nhận yêu cầu gửi email thông báo kết quả duyệt/từ chối hồ sơ")
    public ResponseEntity<ApiResponse<Void>> sendInternDecisionEmail(
            @Valid @RequestBody SendInternDecisionEmailRequest request) {

        log.info("Nhan yeu cau gui email cho ho so ID: {}, email: {}, decision: {}",
                request.getInternProfileId(), request.getEmail(), request.getDecision());

        // Kiểm tra Idempotency nếu request đã được xử lý trước đó
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<EmailLog> existing = emailLogRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotency key '{}' da ton tai voi trang thai: {}. Khong gui lai trung lap.",
                        request.getIdempotencyKey(), existing.get().getStatus());
                return ResponseEntity.ok(ApiResponse.success(200, "Yêu cầu đã được tiếp nhận trước đó", null));
            }
        }

        // Kích hoạt xử lý ngầm bất đồng bộ
        emailDeliveryService.processAndSendAsync(request);

        return ResponseEntity.ok(ApiResponse.success(200, "Đã tiếp nhận yêu cầu gửi email thành công", null));
    }

    @GetMapping("/status/{internProfileId}")
    @Operation(summary = "Lấy trạng thái email gần nhất của hồ sơ thực tập sinh")
    public ResponseEntity<ApiResponse<EmailStatusResponse>> getLatestEmailStatus(
            @PathVariable Long internProfileId) {

        Optional<EmailLog> latest = emailLogRepository.findFirstByReferenceIdOrderByCreatedAtDesc(internProfileId);
        if (latest.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(200, "Chưa có lịch sử gửi email cho hồ sơ này", null));
        }

        EmailLog logEntity = latest.get();
        EmailStatusResponse response = EmailStatusResponse.builder()
                .id(logEntity.getId())
                .referenceId(logEntity.getReferenceId())
                .recipientEmail(logEntity.getRecipientEmail())
                .status(logEntity.getStatus())
                .errorMessage(logEntity.getErrorMessage())
                .sentAt(logEntity.getSentAt())
                .createdAt(logEntity.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(200, "Lấy trạng thái email thành công", response));
    }
}

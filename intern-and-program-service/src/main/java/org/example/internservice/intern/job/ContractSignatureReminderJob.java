package org.example.internservice.intern.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.service.InternContractService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job định kỳ quét và tự động gửi email nhắc nhở ký hợp đồng thực tập.
 * Mặc định: Chạy hàng ngày lúc 08:00 sáng (hoặc có thể cấu hình cron).
 * Quét các hợp đồng ở trạng thái PENDING_SIGNATURE tạo quá 3 ngày và chưa được nhắc trong 2 ngày qua.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContractSignatureReminderJob {

    private final InternContractService internContractService;

    @Value("${app.contract.reminder.enabled:true}")
    private boolean enabled;

    @Value("${app.contract.reminder.overdue-days:3}")
    private int overdueDays;

    @Value("${app.contract.reminder.cooldown-days:2}")
    private int cooldownDays;

    /**
     * Cron expression: "0 0 8 * * ?" -> 08:00:00 sáng mỗi ngày
     */
    @Scheduled(cron = "${app.contract.reminder.cron:0 0 8 * * ?}")
    public void executeContractReminderScan() {
        if (!enabled) {
            log.debug("[CONTRACT-JOB] Tính năng tự động quét nhắc nhở ký hợp đồng đang bị vô hiệu hóa.");
            return;
        }

        log.info("[CONTRACT-JOB] Bắt đầu tác vụ tự động quét hợp đồng chờ ký (overdueDays={}, cooldownDays={})",
                overdueDays, cooldownDays);

        try {
            int sentCount = internContractService.scanAndSendPendingContractReminders(overdueDays, cooldownDays);
            log.info("[CONTRACT-JOB] Kết thúc tác vụ tự động quét hợp đồng. Đã gửi {} email nhắc nhở thành công.", sentCount);
        } catch (Exception ex) {
            log.error("[CONTRACT-JOB] Đã xảy ra lỗi trong quá trình quét tự động nhắc nhở hợp đồng: {}", ex.getMessage(), ex);
        }
    }
}

package org.example.internservice.intern.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailReconciliationJob {

    private final InternProfileRepository internProfileRepository;

    /**
     * Chạy định kỳ mỗi 5 phút (300.000 ms)
     * Quét các hồ sơ có emailStatus = 'PENDING' và lastEmailSentAt quá 5 phút trước
     * để tự động giải tỏa trạng thái treo sang 'FAILED'
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void reconcileStuckPendingEmails() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<InternProfile> stuckProfiles = internProfileRepository
                .findByEmailStatusAndLastEmailSentAtBefore("PENDING", threshold);

        if (!stuckProfiles.isEmpty()) {
            log.warn("Phat hien {} ho so co emailStatus PENDING bi treo qua 5 phut. Dang chuyen sang FAILED de HR co the gui lai.",
                    stuckProfiles.size());
            for (InternProfile profile : stuckProfiles) {
                profile.updateEmailStatus("FAILED");
                internProfileRepository.save(profile);
            }
        }
    }
}

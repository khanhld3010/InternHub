package org.example.internservice.allowance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.meeting.client.MeetingEmailClient;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AllowanceEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAllowancePaidEvent(AllowancePaidEvent event) {
        log.info("[AFTER_COMMIT Event] Nhan su kien chi tra phu cap ID: {}, eventId: {} cho TTS: {} ({})",
                event.getRecordId(), event.getEventId(), event.getInternFullName(), event.getInternEmail());
        // Tai day he thong dispatch sang reporting-service gui email Payslip va WebSocket STOMP
    }
}

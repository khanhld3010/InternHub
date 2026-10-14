package org.example.internservice.allowance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowancePaidEvent {

    private String eventId; // EVT-ALLOWANCE-{recordId}-{status}-{timestamp}
    private Long recordId;
    private Long internUserId;
    private String internFullName;
    private String internEmail;
    private String periodMonth;
    private BigDecimal actualAmount;
    private LocalDate paymentDate;
    private AllowanceStatus status;
}

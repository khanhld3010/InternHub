package org.example.employeeservice.system.audit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogStatsResponse {

    private long totalToday;
    private long totalSuccess;
    private long totalFailed;
    private double successRate;
    private Map<String, Long> moduleBreakdown;
}

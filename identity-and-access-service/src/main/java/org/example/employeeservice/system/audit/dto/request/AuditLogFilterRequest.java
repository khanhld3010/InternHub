package org.example.employeeservice.system.audit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogFilterRequest {

    private String keyword;
    private String module;
    private String action;
    private String status;
    private String username;
    private String fromDate;
    private String toDate;
}

package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProctoringAlertEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long scheduleId;
    private Long sessionId;
    private String candidateName;
    private String eventType;
    private Integer strikeCount;
    private Integer maxStrikes;
    private String details;
    private Long timestamp;
}

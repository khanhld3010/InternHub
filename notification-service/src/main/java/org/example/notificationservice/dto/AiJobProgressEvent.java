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
public class AiJobProgressEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String jobId;
    private String jobType;
    private Long referenceId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private Integer progress;
    private String currentStep;
    private String errorMessage;
    private Object result;
}

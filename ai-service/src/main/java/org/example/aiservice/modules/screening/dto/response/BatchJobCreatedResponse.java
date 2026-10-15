package org.example.aiservice.modules.screening.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobCreatedResponse {

    private String jobId;
    private int totalCount;
    private String status;
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}

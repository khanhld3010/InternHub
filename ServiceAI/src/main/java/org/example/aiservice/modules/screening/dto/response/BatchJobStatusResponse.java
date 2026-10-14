package org.example.aiservice.modules.screening.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobStatusResponse {

    private String jobId;
    private Long programId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private int totalCount;
    private int processedCount;
    private int percent;

    @Builder.Default
    private List<ScreeningResultDto> results = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package org.example.internservice.allowance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveResultResponse {

    private int totalProcessed;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<BatchErrorItem> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchErrorItem {
        private Long recordId;
        private Long internUserId;
        private String internName;
        private String reason;
    }
}

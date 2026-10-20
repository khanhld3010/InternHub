package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.aiservice.modules.interview.enums.HrEvaluationStatus;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HrEvaluationReviewRequest {

    @NotNull(message = "Trạng thái phê duyệt không được để trống")
    private HrEvaluationStatus hrStatus;

    private String notes;

    private List<QuestionOverrideItem> questionOverrides;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionOverrideItem {
        @NotNull(message = "questionId không được để trống")
        private Long questionId;

        private BigDecimal overrideScore;

        private String hrNotes;
    }
}

package org.example.internservice.intern.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrApproveEvaluationRequest {

    private String hrComments;

    @NotBlank(message = "Kết luận đánh giá không được để trống")
    @Builder.Default
    private String internshipResult = "PASSED"; // PASSED, EXCELLENT, FAILED
}

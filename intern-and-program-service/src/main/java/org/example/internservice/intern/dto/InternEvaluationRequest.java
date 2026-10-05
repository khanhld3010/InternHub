package org.example.internservice.intern.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternEvaluationRequest {

    @NotBlank(message = "evaluationType không được để trống")
    @Builder.Default
    private String evaluationType = "FINAL"; // 'MIDTERM', 'FINAL'

    @NotNull(message = "technicalScore không được để trống")
    @DecimalMin(value = "1.0", message = "technicalScore tối thiểu 1.0")
    @DecimalMax(value = "10.0", message = "technicalScore tối đa 10.0")
    private BigDecimal technicalScore;

    @NotBlank(message = "technicalComments không được để trống")
    private String technicalComments;

    @NotNull(message = "attitudeScore không được để trống")
    @DecimalMin(value = "1.0", message = "attitudeScore tối thiểu 1.0")
    @DecimalMax(value = "10.0", message = "attitudeScore tối đa 10.0")
    private BigDecimal attitudeScore;

    @NotBlank(message = "attitudeComments không được để trống")
    private String attitudeComments;

    @NotNull(message = "softSkillsScore không được để trống")
    @DecimalMin(value = "1.0", message = "softSkillsScore tối thiểu 1.0")
    @DecimalMax(value = "10.0", message = "softSkillsScore tối đa 10.0")
    private BigDecimal softSkillsScore;

    private String strengths;
    private String areasForImprovement;

    @NotBlank(message = "recommendation không được để trống")
    private String recommendation; // HIRE_FULLTIME, EXTEND_INTERNSHIP, PASS, FAIL

    private String recommendationNote;

    @Builder.Default
    private Boolean isSubmit = false;
}

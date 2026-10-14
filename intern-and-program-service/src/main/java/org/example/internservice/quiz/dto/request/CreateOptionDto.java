package org.example.internservice.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class CreateOptionDto {

    private String optionLabel;

    @NotBlank(message = "Nội dung phương án không được để trống")
    private String optionText;

    @Builder.Default
    private Boolean isCorrect = false;

    private Integer orderIndex;
}

package org.example.internservice.quiz.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQuizAnswerItem {

    @NotNull(message = "Mã câu hỏi không được để trống")
    private Long questionId;

    @Builder.Default
    private List<Long> selectedOptionIds = new ArrayList<>();
}

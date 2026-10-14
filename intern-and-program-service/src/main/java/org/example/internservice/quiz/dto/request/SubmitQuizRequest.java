package org.example.internservice.quiz.dto.request;

import jakarta.validation.Valid;
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
public class SubmitQuizRequest {

    @Valid
    @Builder.Default
    private List<SubmitQuizAnswerItem> answers = new ArrayList<>();
}

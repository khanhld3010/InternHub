package org.example.internservice.quiz.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.AssignmentTargetType;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignQuizRequest {

    @NotNull(message = "Loại đối tượng giao bài không được để trống (INDIVIDUAL hoặc GROUP)")
    private AssignmentTargetType targetType;

    @NotNull(message = "Mã đối tượng giao bài (ID thực tập sinh hoặc ID nhóm) không được để trống")
    private Long targetId;

    @NotNull(message = "Hạn chót nộp bài không được để trống")
    private LocalDate dueDate;

    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxAttempts;

    @Builder.Default
    private Boolean createKanbanTask = true;
}

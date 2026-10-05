package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class WeeklyAssessmentRequest {

    @NotNull(message = "Số tuần đánh giá không được để trống")
    @Min(value = 1, message = "Tuần đánh giá tối thiểu là 1")
    @Max(value = 52, message = "Tuần đánh giá tối đa là 52")
    private Integer weekNumber;

    @NotNull(message = "Điểm kỹ thuật chuyên môn không được để trống")
    @Min(value = 1, message = "Điểm tối thiểu là 1")
    @Max(value = 5, message = "Điểm tối đa là 5")
    private Integer technicalScore;

    @NotNull(message = "Điểm thái độ tác phong không được để trống")
    @Min(value = 1, message = "Điểm tối thiểu là 1")
    @Max(value = 5, message = "Điểm tối đa là 5")
    private Integer attitudeScore;

    @NotNull(message = "Điểm tinh thần đồng đội không được để trống")
    @Min(value = 1, message = "Điểm tối thiểu là 1")
    @Max(value = 5, message = "Điểm tối đa là 5")
    private Integer teamworkScore;

    @NotNull(message = "Điểm tiến độ công việc không được để trống")
    @Min(value = 1, message = "Điểm tối thiểu là 1")
    @Max(value = 5, message = "Điểm tối đa là 5")
    private Integer productivityScore;

    @NotBlank(message = "Nội dung nhận xét tuần không được để trống")
    private String feedback;

    private String nextWeekGoals;

    private Boolean isPublish;
}

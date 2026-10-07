package org.example.internservice.intern.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveWeeklyReportRequest {

    @NotNull(message = "Số tuần báo cáo không được để trống")
    @Min(value = 1, message = "Số tuần báo cáo tối thiểu là 1")
    @Max(value = 52, message = "Số tuần báo cáo tối đa là 52")
    private Integer weekNumber;

    private LocalDate reportDate;

    private String completedTasksSummary;

    private String unfinishedTasksSummary;

    private String difficultiesAndChallenges;

    private String learningsAndKnowledge;

    private String nextWeekPlan;

    @Size(max = 500, message = "Đường dẫn đính kèm tối đa 500 ký tự")
    @Pattern(
            regexp = "^(https?://.*)?$",
            message = "Đường dẫn đính kèm phải là URL hợp lệ bắt đầu bằng http:// hoặc https://"
    )
    private String reportAttachmentUrl;

    @Builder.Default
    private Boolean isSubmit = false;

    @Valid
    @Builder.Default
    private List<TaskItem> tasks = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private Long missionItemId;

        @NotNull(message = "Tiêu đề nhiệm vụ không được để trống")
        private String taskTitle;

        @NotNull(message = "Trạng thái nhiệm vụ không được để trống")
        private String taskStatus;

        @Size(max = 500, message = "Đường dẫn nộp bài tối đa 500 ký tự")
        private String submissionUrl;

        private String note;

        @Builder.Default
        private Boolean isCompleted = false;
    }
}

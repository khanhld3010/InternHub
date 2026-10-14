package org.example.internservice.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.entity.enums.AttendanceReviewStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAttendanceRequest {

    @NotNull(message = "Trạng thái phê duyệt không được để trống")
    private AttendanceReviewStatus reviewStatus;

    private String reviewNote;
}

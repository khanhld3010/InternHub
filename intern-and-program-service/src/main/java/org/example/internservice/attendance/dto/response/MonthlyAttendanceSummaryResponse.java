package org.example.internservice.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyAttendanceSummaryResponse {

    private int month;
    private int year;
    private long totalWorkingDays;
    private long onTimeDays;
    private long lateDays;
    private long earlyLeaveDays;
    private Double totalWorkingHours;
    private List<AttendanceResponse> attendances;
}

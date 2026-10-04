package org.example.internservice.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodayAttendanceResponse {

    private LocalDate workDate;
    private boolean hasCheckedIn;
    private boolean hasCheckedOut;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double totalWorkingHours;
    private AttendanceStatus status;
    private String officeName;
    private Double allowedRadiusMeters;
}

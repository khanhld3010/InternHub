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
public class AttendanceResponse {

    private Long id;
    private Long internId;
    private LocalDate workDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double checkInDistance;
    private Double checkOutDistance;
    private Double totalWorkingHours;
    private AttendanceStatus status;
    private String notes;
}

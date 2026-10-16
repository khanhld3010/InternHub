package org.example.internservice.attendance.report.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.report.enums.DiligenceStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * DTO chứa các tiêu chí lọc đa chiều cho báo cáo chuyên cần (TM-26).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReportFilterRequest {

    @Min(value = 1, message = "Tháng tra cứu phải từ 1 đến 12")
    @Max(value = 12, message = "Tháng tra cứu phải từ 1 đến 12")
    private Integer month;

    @Min(value = 2000, message = "Năm tra cứu phải từ năm 2000 trở đi")
    private Integer year;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Long departmentId;

    private Long programId;

    private Long mentorId;

    private DiligenceStatus diligenceStatus;

    private String keyword;
}

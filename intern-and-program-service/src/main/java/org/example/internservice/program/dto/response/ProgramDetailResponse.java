package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramDetailResponse {

    private Long id;
    private String programCode;
    private String name;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private String description;
    private Integer maxInterns;
    private Long currentInterns;
    private Long availableSlots;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long durationWeeks;
    private ProgramStatus status;
    private String statusDisplayName;
    private Boolean isRecruitmentOpen;
    private Boolean isHistorical;
    private String cancellationReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProgramDetailResponse fromEntity(InternshipProgram program, long activeInternCount) {
        if (program == null) return null;

        long calculatedCurrent = Boolean.TRUE.equals(program.getIsHistorical())
                ? (program.getCurrentInterns() != null ? program.getCurrentInterns().longValue() : 0L)
                : activeInternCount;

        long max = program.getMaxInterns() != null ? program.getMaxInterns().longValue() : 0L;
        long available = Math.max(0, max - calculatedCurrent);

        long weeks = 0;
        if (program.getStartDate() != null && program.getEndDate() != null) {
            long totalDays = ChronoUnit.DAYS.between(program.getStartDate(), program.getEndDate()) + 1;
            weeks = Math.max(1, totalDays / 7);
        }

        return ProgramDetailResponse.builder()
                .id(program.getId())
                .programCode(program.getProgramCode())
                .name(program.getName())
                .departmentId(program.getDepartment() != null ? program.getDepartment().getId() : null)
                .departmentName(program.getDepartment() != null ? program.getDepartment().getName() : null)
                .departmentCode(program.getDepartment() != null ? program.getDepartment().getCode() : null)
                .description(program.getDescription())
                .maxInterns(program.getMaxInterns())
                .currentInterns(calculatedCurrent)
                .availableSlots(available)
                .startDate(program.getStartDate())
                .endDate(program.getEndDate())
                .durationWeeks(weeks)
                .status(program.getStatus())
                .statusDisplayName(program.getStatus() != null ? program.getStatus().getDisplayName() : null)
                .isRecruitmentOpen(program.getIsRecruitmentOpen())
                .isHistorical(program.getIsHistorical())
                .cancellationReason(program.getCancellationReason())
                .createdBy(program.getCreatedBy())
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .build();
    }
}

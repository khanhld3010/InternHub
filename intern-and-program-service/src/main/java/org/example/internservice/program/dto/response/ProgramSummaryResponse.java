package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramSummaryResponse {

    private Long id;
    private String programCode;
    private String name;
    private String departmentName;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long durationWeeks;
    private Integer maxInterns;
    private Integer currentInterns;
    private Boolean isRecruitmentOpen;
    private ProgramStatus status;
    private String statusDisplayName;

    public static ProgramSummaryResponse fromEntity(InternshipProgram program) {
        if (program == null) return null;

        long weeks = 0;
        if (program.getStartDate() != null && program.getEndDate() != null) {
            long totalDays = ChronoUnit.DAYS.between(program.getStartDate(), program.getEndDate()) + 1;
            weeks = Math.max(1, totalDays / 7);
        }

        return ProgramSummaryResponse.builder()
                .id(program.getId())
                .programCode(program.getProgramCode())
                .name(program.getName())
                .departmentName(program.getDepartment() != null ? program.getDepartment().getName() : null)
                .description(program.getDescription())
                .startDate(program.getStartDate())
                .endDate(program.getEndDate())
                .durationWeeks(weeks)
                .maxInterns(program.getMaxInterns())
                .currentInterns(program.getCurrentInterns())
                .isRecruitmentOpen(program.getIsRecruitmentOpen())
                .status(program.getStatus())
                .statusDisplayName(program.getStatus() != null ? program.getStatus().getDisplayName() : null)
                .build();
    }
}

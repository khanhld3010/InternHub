package org.example.internservice.mission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.enums.ProgramStatus;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProgramResponse {

    private Long programId;
    private String programCode;
    private String name;
    private Long departmentId;
    private String departmentName;
    private ProgramStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalInterns;
    private Integer activeInterns;
    private Integer groupCount;
    private Integer totalTaskCount;
    private Integer completedTaskCount;
    private Double progressPercent;
    private Integer mentorCount;

    public Long getId() {
        return programId;
    }
}

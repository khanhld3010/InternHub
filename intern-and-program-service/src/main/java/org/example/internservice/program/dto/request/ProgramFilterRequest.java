package org.example.internservice.program.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.enums.ProgramStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramFilterRequest {

    private String keyword;
    private Long departmentId;
    private ProgramStatus status;
    private Boolean isRecruitmentOpen;
}

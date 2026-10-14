package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignMentorToProgramResponse {

    private Long programId;
    private String programName;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private int totalAssignedInterns;
    private int replacedMentorsCount;
    private List<String> affectedInternCodes;
    private LocalDateTime assignedAt;
}

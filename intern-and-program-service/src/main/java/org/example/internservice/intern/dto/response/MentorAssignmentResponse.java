package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorAssignmentResponse {

    private Long id;
    private Long internId;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private MentorAssignmentStatus status;
    private String notes;
    private LocalDateTime revokedAt;
    private String revocationReason;
}

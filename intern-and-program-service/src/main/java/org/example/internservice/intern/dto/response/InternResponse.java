package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternResponse {

    private Long id;
    private String internCode;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String address;
    private String university;
    private String major;
    private String academicYear;
    private String appliedPosition;
    private LocalDate startDate;
    private LocalDate endDate;
    private InternStatus status;
    private String notes;
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String emailStatus;
    private LocalDateTime emailSentAt;
    private Integer emailRetryCount;
    private LocalDateTime lastEmailSentAt;
    private Long programId;
    private String programCode;
    private String programName;
    private Boolean needsReassignment;
    private String reassignmentReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

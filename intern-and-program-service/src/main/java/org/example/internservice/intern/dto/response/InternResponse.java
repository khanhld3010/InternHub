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
    private Long userId;
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
    private org.example.internservice.intern.entity.enums.CandidateType candidateType;
    private Long desiredDepartmentId;
    private String desiredDepartmentName;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private Boolean needsReassignment;
    private String reassignmentReason;
    private Boolean needsMentorReassignment;
    private String mentorReassignmentReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InternResponse fromEntity(org.example.internservice.intern.entity.InternProfile profile) {
        if (profile == null) return null;
        return InternResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .internCode(profile.getInternCode())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .address(profile.getAddress())
                .university(profile.getUniversity())
                .major(profile.getMajor())
                .academicYear(profile.getAcademicYear())
                .appliedPosition(profile.getAppliedPosition())
                .startDate(profile.getStartDate() != null ? profile.getStartDate() : (profile.getProgram() != null ? profile.getProgram().getStartDate() : null))
                .endDate(profile.getEndDate() != null ? profile.getEndDate() : (profile.getProgram() != null ? profile.getProgram().getEndDate() : null))
                .status(profile.getStatus())
                .notes(profile.getNotes())
                .rejectionReason(profile.getRejectionReason())
                .reviewedBy(profile.getReviewedBy())
                .reviewedAt(profile.getReviewedAt())
                .emailStatus(profile.getEmailStatus())
                .emailSentAt(profile.getEmailSentAt())
                .emailRetryCount(profile.getEmailRetryCount())
                .lastEmailSentAt(profile.getLastEmailSentAt())
                .programId(profile.getProgram() != null ? profile.getProgram().getId() : null)
                .programCode(profile.getProgram() != null ? profile.getProgram().getProgramCode() : null)
                .programName(profile.getProgram() != null ? profile.getProgram().getName() : null)
                .candidateType(profile.getCandidateType())
                .desiredDepartmentId(profile.getDesiredDepartmentId())
                .desiredDepartmentName(profile.getDesiredDepartmentName())
                .mentorId(profile.getMentorId())
                .mentorName(profile.getMentorName())
                .mentorEmail(profile.getMentorEmail())
                .needsReassignment(profile.getNeedsReassignment())
                .reassignmentReason(profile.getReassignmentReason())
                .needsMentorReassignment(profile.getNeedsMentorReassignment())
                .mentorReassignmentReason(profile.getMentorReassignmentReason())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}

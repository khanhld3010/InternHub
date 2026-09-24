package org.example.internservice.intern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "intern_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternProfile extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "intern_code", unique = true, nullable = false, length = 50)
    private String internCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "phone", unique = true, nullable = false, length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "university", nullable = false, length = 150)
    private String university;

    @Column(name = "major", nullable = false, length = 100)
    private String major;

    @Column(name = "academic_year", length = 50)
    private String academicYear;

    @Column(name = "applied_position", nullable = false, length = 100)
    private String appliedPosition;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InternStatus status = InternStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public void applyDecision(InternStatus decision, String reason, String reviewerUsername) {
        if (!this.status.canTransitionTo(decision)) {
            throw new IllegalStateException("Không thể chuyển đổi trạng thái từ " + this.status + " sang " + decision);
        }
        this.status = decision;
        if (decision == InternStatus.APPROVED) {
            this.rejectionReason = null;
        } else if (decision == InternStatus.REJECTED) {
            this.rejectionReason = reason;
        }
        this.reviewedBy = reviewerUsername;
        this.reviewedAt = LocalDateTime.now();
    }

    public void updateInformation(
            String fullName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            Gender gender,
            String address,
            String university,
            String major,
            String academicYear,
            String appliedPosition,
            LocalDate startDate,
            LocalDate endDate,
            InternStatus status,
            String notes
    ) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.university = university;
        this.major = major;
        this.academicYear = academicYear;
        this.appliedPosition = appliedPosition;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.notes = notes;
    }
}

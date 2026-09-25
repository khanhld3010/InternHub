package org.example.internservice.program.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.program.entity.enums.ProgramStatus;

import java.time.LocalDate;

@Entity
@Table(name = "internship_programs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternshipProgram extends BaseEntity {

    @Column(name = "program_code", unique = true, nullable = false, length = 50)
    private String programCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_interns", nullable = false)
    @Builder.Default
    private Integer maxInterns = 10;

    @Column(name = "current_interns", nullable = false)
    @Builder.Default
    private Integer currentInterns = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_recruitment_open", nullable = false)
    @Builder.Default
    private Boolean isRecruitmentOpen = true;

    @Column(name = "is_historical", nullable = false)
    @Builder.Default
    private Boolean isHistorical = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProgramStatus status = ProgramStatus.PLANNING;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}

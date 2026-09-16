package org.example.employeeservice.entity;

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
import org.example.employeeservice.common.entity.BaseEntity;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.entity.enums.InternStatus;

import java.time.LocalDate;

@Entity
@Table(name = "intern_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternProfile extends BaseEntity {

    @Column(name = "intern_code", nullable = false, unique = true, length = 30)
    private String internCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone", nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "university", nullable = false, length = 150)
    private String university;

    @Column(name = "major", nullable = false, length = 100)
    private String major;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "gpa")
    private Double gpa;

    @Column(name = "applied_position", nullable = false, length = 100)
    private String appliedPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InternStatus status = InternStatus.PENDING;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}

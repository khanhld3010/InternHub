package org.example.employeeservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.entity.enums.InternStatus;

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
    private String university;
    private String major;
    private String academicYear;
    private Double gpa;
    private String appliedPosition;
    private InternStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String address;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

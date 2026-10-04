package org.example.internservice.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "attendances",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_intern_work_date", columnNames = {"intern_id", "work_date"})
        },
        indexes = {
                @Index(name = "idx_attendance_intern_date", columnList = "intern_id, work_date"),
                @Index(name = "idx_attendance_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance extends BaseEntity {

    @Column(name = "intern_id", nullable = false)
    private Long internId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "check_in_latitude", nullable = false)
    private Double checkInLatitude;

    @Column(name = "check_in_longitude", nullable = false)
    private Double checkInLongitude;

    @Column(name = "check_in_distance", nullable = false)
    private Double checkInDistance;

    @Column(name = "check_out_latitude")
    private Double checkOutLatitude;

    @Column(name = "check_out_longitude")
    private Double checkOutLongitude;

    @Column(name = "check_out_distance")
    private Double checkOutDistance;

    @Column(name = "total_working_hours")
    private Double totalWorkingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttendanceStatus status;

    @Column(name = "notes", length = 255)
    private String notes;
}

package org.example.internservice.attendance.repository;

import org.example.internservice.attendance.entity.AttendanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceAuditLogRepository extends JpaRepository<AttendanceAuditLog, Long> {

    List<AttendanceAuditLog> findByInternIdOrderByActionTimeDesc(Long internId);

    List<AttendanceAuditLog> findByAttendanceId(Long attendanceId);
}

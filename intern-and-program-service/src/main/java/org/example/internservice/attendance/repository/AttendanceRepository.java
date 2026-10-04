package org.example.internservice.attendance.repository;

import org.example.internservice.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByInternIdAndWorkDate(Long internId, LocalDate workDate);

    boolean existsByInternIdAndWorkDate(Long internId, LocalDate workDate);

    List<Attendance> findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(Long internId, LocalDate startDate, LocalDate endDate);
}

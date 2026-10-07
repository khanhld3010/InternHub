package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternWeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternWeeklyReportRepository extends JpaRepository<InternWeeklyReport, Long> {

    Optional<InternWeeklyReport> findByInternCodeAndWeekNumber(String internCode, Integer weekNumber);

    /**
     * Tải chi tiết báo cáo tuần kèm danh sách tasks snapshot bằng JOIN FETCH
     * để chống triệt để lỗi N+1 Query JPA (Rule 22)
     */
    @Query("SELECT DISTINCT r FROM InternWeeklyReport r " +
           "LEFT JOIN FETCH r.tasks " +
           "WHERE r.internCode = :internCode AND r.weekNumber = :weekNumber")
    Optional<InternWeeklyReport> findByInternCodeAndWeekNumberWithTasks(
            @Param("internCode") String internCode,
            @Param("weekNumber") Integer weekNumber
    );

    List<InternWeeklyReport> findByInternCodeOrderByWeekNumberAsc(String internCode);

    List<InternWeeklyReport> findByMentorIdOrderByCreatedAtDesc(Long mentorId);
}

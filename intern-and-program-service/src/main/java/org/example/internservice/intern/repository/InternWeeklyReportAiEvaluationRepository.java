package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternWeeklyReportAiEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternWeeklyReportAiEvaluationRepository extends JpaRepository<InternWeeklyReportAiEvaluation, Long> {

    Optional<InternWeeklyReportAiEvaluation> findByReportIdAndIsActiveTrue(Long reportId);

    List<InternWeeklyReportAiEvaluation> findByReportIdOrderByVersionDesc(Long reportId);

    @Modifying
    @Query("UPDATE InternWeeklyReportAiEvaluation e SET e.isActive = false, e.updatedAt = CURRENT_TIMESTAMP WHERE e.reportId = :reportId")
    void deactivateAllVersionsByReportId(@Param("reportId") Long reportId);

    @Query("SELECT COALESCE(MAX(e.version), 0) FROM InternWeeklyReportAiEvaluation e WHERE e.reportId = :reportId")
    int findMaxVersionByReportId(@Param("reportId") Long reportId);
}

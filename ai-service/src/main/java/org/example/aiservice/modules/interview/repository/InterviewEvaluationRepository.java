package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewEvaluation;
import org.example.aiservice.modules.interview.enums.GradingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    Optional<InterviewEvaluation> findBySessionIdAndIsActiveTrue(Long sessionId);

    List<InterviewEvaluation> findByGradingStatusAndIsActiveTrue(GradingStatus gradingStatus);

    List<InterviewEvaluation> findBySessionIdOrderByVersionDesc(Long sessionId);

    @Modifying
    @Query("UPDATE InterviewEvaluation e SET e.gradingStatus = 'IN_PROGRESS', e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.id = :id AND e.gradingStatus IN ('PENDING', 'FAILED_RETRYABLE')")
    int claimGradingJob(@Param("id") Long id);

    @Modifying
    @Query("UPDATE InterviewEvaluation e SET e.isActive = false, e.updatedAt = CURRENT_TIMESTAMP WHERE e.sessionId = :sessionId")
    void deactivateAllBySessionId(@Param("sessionId") Long sessionId);
}

package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InternWeeklyAssessmentRepository extends JpaRepository<InternWeeklyAssessment, Long> {

    List<InternWeeklyAssessment> findByInternCodeOrderByWeekNumberDesc(String internCode);

    List<InternWeeklyAssessment> findByInternCodeAndStatusOrderByWeekNumberDesc(String internCode, InternWeeklyAssessment.AssessmentStatus status);

    Optional<InternWeeklyAssessment> findByInternCodeAndWeekNumber(String internCode, Integer weekNumber);

    List<InternWeeklyAssessment> findByMentorIdOrderByCreatedAtDesc(Long mentorId);
}

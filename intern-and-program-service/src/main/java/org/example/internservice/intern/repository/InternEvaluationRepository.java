package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternEvaluationRepository extends JpaRepository<InternEvaluation, Long> {
    Optional<InternEvaluation> findByInternCodeAndEvaluationType(String internCode, String evaluationType);
    List<InternEvaluation> findByInternCode(String internCode);
    List<InternEvaluation> findByMentorId(Long mentorId);
}

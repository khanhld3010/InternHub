package org.example.aiservice.modules.screening.repository;

import org.example.aiservice.modules.screening.entity.InternAiEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiEvaluationRepository extends JpaRepository<InternAiEvaluation, Long> {

    Optional<InternAiEvaluation> findTopByInternIdOrderByCreatedAtDesc(Long internId);

    Optional<InternAiEvaluation> findTopByInternIdAndProgramIdOrderByCreatedAtDesc(Long internId, Long programId);

    List<InternAiEvaluation> findByInternIdIn(List<Long> internIds);
}

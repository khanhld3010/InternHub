package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewAnswerEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewAnswerEvaluationRepository extends JpaRepository<InterviewAnswerEvaluation, Long> {

    List<InterviewAnswerEvaluation> findByEvaluationId(Long evaluationId);

    void deleteByEvaluationId(Long evaluationId);
}

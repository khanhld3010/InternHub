package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewQuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewQuestionSetRepository extends JpaRepository<InterviewQuestionSet, Long> {

    List<InterviewQuestionSet> findByProgramIdOrderByCreatedAtDesc(Long programId);

    List<InterviewQuestionSet> findByInternIdOrderByCreatedAtDesc(Long internId);

    @Query("SELECT s FROM InterviewQuestionSet s LEFT JOIN FETCH s.questions WHERE s.id = :id")
    Optional<InterviewQuestionSet> findByIdWithQuestions(@Param("id") Long id);

    Optional<InterviewQuestionSet> findFirstByInternIdOrderByCreatedAtDesc(Long internId);
}

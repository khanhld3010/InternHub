package org.example.internservice.quiz.repository;

import org.example.internservice.quiz.entity.QuizSubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizSubmissionAnswerRepository extends JpaRepository<QuizSubmissionAnswer, Long> {

    List<QuizSubmissionAnswer> findBySubmissionId(Long submissionId);
}

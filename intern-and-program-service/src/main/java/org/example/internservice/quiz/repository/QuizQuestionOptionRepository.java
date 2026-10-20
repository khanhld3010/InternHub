package org.example.internservice.quiz.repository;

import org.example.internservice.quiz.entity.QuizQuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionOptionRepository extends JpaRepository<QuizQuestionOption, Long> {

    List<QuizQuestionOption> findByQuestionIdOrderByOrderIndexAsc(Long questionId);
}

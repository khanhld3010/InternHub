package org.example.internservice.quiz.repository;

import org.example.internservice.quiz.entity.QuizAssignment;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAssignmentRepository extends JpaRepository<QuizAssignment, Long> {

    List<QuizAssignment> findByInternIdOrderByCreatedAtDesc(Long internId);

    List<QuizAssignment> findByQuizId(Long quizId);

    List<QuizAssignment> findByInternIdAndStatus(Long internId, AssignmentStatus status);

    Optional<QuizAssignment> findByQuizIdAndInternId(Long quizId, Long internId);

    @Query("SELECT qa FROM QuizAssignment qa " +
            "JOIN FETCH qa.quiz q " +
            "JOIN FETCH q.program " +
            "WHERE qa.id = :id")
    Optional<QuizAssignment> findByIdWithQuizAndProgram(@Param("id") Long id);
}

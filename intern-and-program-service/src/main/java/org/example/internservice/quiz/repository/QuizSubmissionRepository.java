package org.example.internservice.quiz.repository;

import org.example.internservice.quiz.entity.QuizSubmission;
import org.example.internservice.quiz.entity.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    List<QuizSubmission> findByAssignmentIdOrderByAttemptNumberDesc(Long assignmentId);

    List<QuizSubmission> findByAssignmentIdAndInternIdOrderByAttemptNumberDesc(Long assignmentId, Long internId);

    long countByAssignmentIdAndInternIdAndStatusNot(Long assignmentId, Long internId, SubmissionStatus status);

    @Query("SELECT qs FROM QuizSubmission qs " +
            "WHERE qs.assignment.id = :assignmentId AND qs.intern.id = :internId " +
            "ORDER BY qs.score DESC, qs.submittedAt DESC")
    List<QuizSubmission> findSubmissionsOrderByScoreDesc(
            @Param("assignmentId") Long assignmentId,
            @Param("internId") Long internId
    );

    @Query("SELECT qs FROM QuizSubmission qs " +
            "WHERE qs.intern.id = :internId AND qs.assignment.id IN :assignmentIds " +
            "ORDER BY qs.score DESC, qs.submittedAt DESC")
    List<QuizSubmission> findByInternIdAndAssignmentIdInOrderByScoreDesc(
            @Param("internId") Long internId,
            @Param("assignmentIds") java.util.Collection<Long> assignmentIds
    );

    @Query("SELECT qs FROM QuizSubmission qs " +
            "WHERE qs.assignment.id IN :assignmentIds " +
            "ORDER BY qs.score DESC, qs.submittedAt DESC")
    List<QuizSubmission> findByAssignmentIdInOrderByScoreDesc(
            @Param("assignmentIds") java.util.Collection<Long> assignmentIds
    );

    @Query("SELECT qs FROM QuizSubmission qs " +
            "LEFT JOIN FETCH qs.answers ans " +
            "LEFT JOIN FETCH ans.question q " +
            "LEFT JOIN FETCH q.options " +
            "WHERE qs.id = :id")
    Optional<QuizSubmission> findByIdWithDetails(@Param("id") Long id);
}

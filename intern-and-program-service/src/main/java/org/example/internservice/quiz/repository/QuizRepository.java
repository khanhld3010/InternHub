package org.example.internservice.quiz.repository;

import org.example.internservice.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByProgramIdOrderByCreatedAtDesc(Long programId);

    List<Quiz> findByMentorIdOrderByCreatedAtDesc(Long mentorId);

    Optional<Quiz> findByIdAndProgramId(Long id, Long programId);

    @Query("SELECT q FROM Quiz q " +
            "LEFT JOIN FETCH q.questions qs " +
            "LEFT JOIN FETCH qs.options " +
            "WHERE q.id = :id")
    Optional<Quiz> findByIdWithDetails(@Param("id") Long id);
}

package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    Optional<InterviewAnswer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<InterviewAnswer> findBySessionId(Long sessionId);

    // Optimistic locking update: Chỉ update nếu revision trùng khớp
    @Modifying
    @Query("UPDATE InterviewAnswer a SET a.answerText = :answerText, a.revision = a.revision + 1, " +
           "a.timeSpentSeconds = :timeSpent, a.isSubmitted = :isSubmitted, a.serverReceivedAt = :now " +
           "WHERE a.sessionId = :sessionId AND a.questionId = :questionId AND a.revision = :baseRevision")
    int updateAnswerWithRevision(@Param("sessionId") Long sessionId,
                                 @Param("questionId") Long questionId,
                                 @Param("answerText") String answerText,
                                 @Param("baseRevision") Integer baseRevision,
                                 @Param("timeSpent") Integer timeSpent,
                                 @Param("isSubmitted") Boolean isSubmitted,
                                 @Param("now") LocalDateTime now);
}

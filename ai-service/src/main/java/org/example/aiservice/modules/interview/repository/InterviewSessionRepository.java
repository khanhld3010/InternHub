package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Optional<InterviewSession> findByInviteTokenHash(String inviteTokenHash);

    List<InterviewSession> findByScheduleId(Long scheduleId);

    List<InterviewSession> findByStatus(SessionStatus status);

    List<InterviewSession> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    List<InterviewSession> findByCandidateEmailOrderByCreatedAtDesc(String candidateEmail);

    @Query("SELECT s FROM InterviewSession s WHERE s.candidateId = :candidateId OR LOWER(s.candidateEmail) = LOWER(:email) ORDER BY s.createdAt DESC")
    List<InterviewSession> findByCandidateIdOrEmail(@Param("candidateId") Long candidateId, @Param("email") String email);

    // Atomic Compare-And-Swap (CAS) cho Submit bài thi chống race-condition
    @Modifying
    @Query("UPDATE InterviewSession s SET s.status = :toStatus, s.submittedAt = :submittedAt, s.submissionReason = :reason " +
           "WHERE s.id = :sessionId AND s.status = :fromStatus")
    int updateStatusAtomic(@Param("sessionId") Long sessionId,
                           @Param("fromStatus") SessionStatus fromStatus,
                           @Param("toStatus") SessionStatus toStatus,
                           @Param("submittedAt") LocalDateTime submittedAt,
                           @Param("reason") org.example.aiservice.modules.interview.enums.SubmissionReason reason);
}

package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_session_hash", columnList = "invite_token_hash", unique = true),
        @Index(name = "idx_interview_session_schedule", columnList = "schedule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "candidate_email", nullable = false, length = 150)
    private String candidateEmail;

    @Column(name = "candidate_name", nullable = false, length = 150)
    private String candidateName;

    @Column(name = "invite_token_hash", nullable = false, length = 64, unique = true)
    private String inviteTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SessionStatus status = SessionStatus.INVITED;

    @Column(name = "session_started_at")
    private LocalDateTime sessionStartedAt;

    @Column(name = "session_deadline")
    private LocalDateTime sessionDeadline;

    @Column(name = "current_question_id")
    private Long currentQuestionId;

    @Column(name = "current_question_deadline")
    private LocalDateTime currentQuestionDeadline;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "blur_count", nullable = false)
    @Builder.Default
    private Integer blurCount = 0;

    @Column(name = "paste_count", nullable = false)
    @Builder.Default
    private Integer pasteCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_reason", length = 50)
    private SubmissionReason submissionReason;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

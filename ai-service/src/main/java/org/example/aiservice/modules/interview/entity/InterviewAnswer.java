package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.InputMethod;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_question", columnNames = {"session_id", "question_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", columnDefinition = "MEDIUMTEXT")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_method", nullable = false, length = 20)
    @Builder.Default
    private InputMethod inputMethod = InputMethod.TYPING;

    @Column(nullable = false)
    @Builder.Default
    private Integer revision = 1;

    @Column(name = "time_spent_seconds", nullable = false)
    @Builder.Default
    private Integer timeSpentSeconds = 0;

    @Column(name = "is_submitted", nullable = false)
    @Builder.Default
    private Boolean isSubmitted = false;

    @Column(name = "server_received_at", nullable = false)
    private LocalDateTime serverReceivedAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.serverReceivedAt = LocalDateTime.now();
    }
}

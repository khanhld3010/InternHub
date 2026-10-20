package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "question_set_id", nullable = false)
    private Long questionSetId;

    @Column(name = "program_id")
    private Long programId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_mode", nullable = false, length = 30)
    @Builder.Default
    private InterviewMode interviewMode = InterviewMode.PER_QUESTION;

    @Column(name = "duration_per_question_sec")
    @Builder.Default
    private Integer durationPerQuestionSec = 180;

    @Column(name = "total_duration_minutes")
    private Integer totalDurationMinutes;

    @Column(name = "allow_revisit_questions", nullable = false)
    @Builder.Default
    private Boolean allowRevisitQuestions = false;

    @Column(name = "max_blur_strikes", nullable = false)
    @Builder.Default
    private Integer maxBlurStrikes = 3;

    @Column(name = "auto_submit_on_exceed", nullable = false)
    @Builder.Default
    private Boolean autoSubmitOnExceed = true;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Column(name = "created_by_hr_id", nullable = false, length = 100)
    private String createdByHrId;

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

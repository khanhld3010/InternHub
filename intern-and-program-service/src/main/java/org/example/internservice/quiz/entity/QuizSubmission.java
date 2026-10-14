package org.example.internservice.quiz.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.quiz.entity.enums.SubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "quiz_submissions",
        indexes = {
                @Index(name = "idx_sub_assignment", columnList = "assignment_id"),
                @Index(name = "idx_sub_intern", columnList = "intern_id"),
                @Index(name = "idx_sub_quiz", columnList = "quiz_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private QuizAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile intern;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Integer totalPoints = 100;

    @Column(name = "percentage_score", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal percentageScore = BigDecimal.ZERO;

    @Column(name = "passed", nullable = false)
    @Builder.Default
    private Boolean passed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuizSubmissionAnswer> answers = new ArrayList<>();

    public void addAnswer(QuizSubmissionAnswer answer) {
        answers.add(answer);
        answer.setSubmission(this);
    }
}

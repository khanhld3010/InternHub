package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.QuestionScope;
import org.example.aiservice.modules.interview.enums.QuestionSetStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interview_question_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "program_id")
    private Long programId;

    @Column(name = "intern_id")
    private Long internId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private QuestionScope scope = QuestionScope.PROGRAM;

    @Column(name = "target_role", nullable = false, length = 100)
    private String targetRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private QuestionSetStatus status = QuestionSetStatus.DRAFT;

    @Column(name = "total_questions", nullable = false)
    @Builder.Default
    private Integer totalQuestions = 0;

    @Column(name = "screening_result_id")
    private Long screeningResultId;

    @Column(name = "screening_match_score", precision = 5, scale = 2)
    private BigDecimal screeningMatchScore;

    @Column(name = "created_by_user_id", nullable = false, length = 100)
    private String createdByUserId;

    @Column(name = "created_by_name", nullable = false, length = 150)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<InterviewQuestion> questions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addQuestion(InterviewQuestion question) {
        questions.add(question);
        question.setQuestionSet(this);
        this.totalQuestions = questions.size();
    }

    public void removeQuestion(InterviewQuestion question) {
        questions.remove(question);
        question.setQuestionSet(null);
        this.totalQuestions = questions.size();
    }
}

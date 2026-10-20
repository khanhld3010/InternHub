package org.example.internservice.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.program.entity.InternGroup;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.example.internservice.quiz.entity.enums.AssignmentTargetType;

import java.time.LocalDate;

@Entity
@Table(
        name = "quiz_assignments",
        indexes = {
                @Index(name = "idx_assign_quiz", columnList = "quiz_id"),
                @Index(name = "idx_assign_intern", columnList = "intern_id"),
                @Index(name = "idx_assign_group", columnList = "group_id"),
                @Index(name = "idx_assign_mission", columnList = "mission_item_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AssignmentTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id")
    private InternProfile intern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private InternGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_item_id")
    private MissionItem missionItem;

    @Column(name = "assigned_by_mentor_id", nullable = false)
    private Long assignedByMentorId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;
}

package org.example.internservice.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "mission_items",
    indexes = {
        @Index(name = "idx_item_board_status", columnList = "board_id, status"),
        @Index(name = "idx_item_due_date", columnList = "due_date")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private MissionBoard board;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private MissionPriority priority = MissionPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MissionItemStatus status = MissionItemStatus.TODO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "submission_url", length = 500)
    private String submissionUrl;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "mission_item_assignees",
        joinColumns = @JoinColumn(name = "item_id"),
        inverseJoinColumns = @JoinColumn(name = "intern_id")
    )
    @Builder.Default
    private Set<InternProfile> assignees = new HashSet<>();
}

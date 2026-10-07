package org.example.internservice.intern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
        name = "intern_weekly_report_tasks",
        indexes = {
                @Index(name = "idx_report_tasks_report_id", columnList = "report_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReportTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private InternWeeklyReport report;

    @Column(name = "mission_item_id")
    private Long missionItemId;

    @Column(name = "task_title", nullable = false, length = 200)
    private String taskTitle;

    @Column(name = "task_status", nullable = false, length = 20)
    private String taskStatus;

    @Column(name = "submission_url", length = 500)
    private String submissionUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;
}

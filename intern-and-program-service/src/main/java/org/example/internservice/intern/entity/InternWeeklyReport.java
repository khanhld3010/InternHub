package org.example.internservice.intern.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "intern_weekly_reports",
        indexes = {
                @Index(name = "idx_weekly_report_intern", columnList = "intern_code"),
                @Index(name = "idx_weekly_report_mentor_status", columnList = "mentor_id, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_weekly_report_intern_week", columnNames = {"intern_code", "week_number"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReport extends BaseEntity {

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    // --- 4 Trụ cột cốt lõi của Báo Cáo Tuần (TM-21) ---

    @Column(name = "completed_tasks_summary", nullable = false, columnDefinition = "TEXT")
    private String completedTasksSummary;

    @Column(name = "unfinished_tasks_summary", columnDefinition = "TEXT")
    private String unfinishedTasksSummary;

    @Column(name = "difficulties_and_challenges", columnDefinition = "TEXT")
    private String difficultiesAndChallenges;

    @Column(name = "learnings_and_knowledge", columnDefinition = "TEXT")
    private String learningsAndKnowledge;

    // Kế hoạch tuần tới & Link sản phẩm/tài liệu
    @Column(name = "next_week_plan", columnDefinition = "TEXT")
    private String nextWeekPlan;

    @Column(name = "report_attachment_url", length = 500)
    private String reportAttachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WeeklyReportStatus status = WeeklyReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InternWeeklyReportTask> tasks = new ArrayList<>();

    public void addTask(InternWeeklyReportTask task) {
        tasks.add(task);
        task.setReport(this);
    }

    public void clearTasks() {
        tasks.clear();
    }
}

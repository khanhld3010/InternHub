package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.AuditEventType;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_audit_logs", indexes = {
        @Index(name = "idx_audit_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "event_metadata", length = 500)
    private String eventMetadata;

    @Column(name = "client_timestamp", nullable = false)
    private LocalDateTime clientTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

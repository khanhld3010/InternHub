package org.example.reportingservice.email.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs", indexes = {
        @Index(name = "idx_email_reference", columnList = "reference_id, template_code"),
        @Index(name = "idx_email_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "template_code", length = 64, nullable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private EmailStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = EmailStatus.PENDING;
        }
    }
}

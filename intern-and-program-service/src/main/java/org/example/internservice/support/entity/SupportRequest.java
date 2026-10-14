package org.example.internservice.support.entity;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "support_requests", indexes = {
        @Index(name = "idx_support_intern_user", columnList = "intern_user_id"),
        @Index(name = "idx_support_status", columnList = "status"),
        @Index(name = "idx_support_type", columnList = "request_type"),
        @Index(name = "idx_support_sla", columnList = "sla_due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportRequest extends BaseEntity {

    @Column(name = "intern_user_id", nullable = false)
    private Long internUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_profile_id", nullable = false)
    private InternProfile internProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 50)
    private SupportRequestType requestType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_storage_key", length = 500)
    private String attachmentStorageKey;

    @Column(name = "attachment_filename", length = 255)
    private String attachmentFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SupportRequestStatus status = SupportRequestStatus.PENDING;

    @Column(name = "hr_response", columnDefinition = "TEXT")
    private String hrResponse;

    @Column(name = "response_storage_key", length = 500)
    private String responseStorageKey;

    @Column(name = "response_filename", length = 255)
    private String responseFilename;

    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "handler_name", length = 100)
    private String handlerName;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "sla_due_date", nullable = false)
    private LocalDateTime slaDueDate;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @OneToMany(mappedBy = "supportRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("changedAt DESC")
    @Builder.Default
    private List<SupportRequestStatusLog> statusLogs = new ArrayList<>();
}

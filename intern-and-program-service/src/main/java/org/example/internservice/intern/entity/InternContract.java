package org.example.internservice.intern.entity;

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
import org.example.internservice.intern.entity.enums.ContractStatus;
import org.example.internservice.intern.entity.enums.ContractType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "intern_contracts", indexes = {
        @Index(name = "idx_contract_intern", columnList = "intern_id"),
        @Index(name = "idx_contract_number", columnList = "contract_number"),
        @Index(name = "idx_contract_status", columnList = "status"),
        @Index(name = "idx_contract_parent", columnList = "parent_contract_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternContract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile internProfile;

    /**
     * Hợp đồng gốc được gia hạn (nếu đây là Phụ lục gia hạn)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_contract_id")
    private InternContract parentContract;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 30)
    @Builder.Default
    private ContractType contractType = ContractType.OFFICIAL_INTERNSHIP;

    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    private String contractNumber;

    @Column(name = "contract_title", nullable = false, length = 200)
    private String contractTitle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "allowance_amount", precision = 12, scale = 2)
    private BigDecimal allowanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.PENDING_SIGNATURE;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signer_full_name", length = 100)
    private String signerFullName;

    @Column(name = "intern_confirmation_note", columnDefinition = "TEXT")
    private String internConfirmationNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Phản hồi / thắc mắc của TTS khi bản scan mờ hoặc sai điều khoản
     */
    @Column(name = "feedback_notes", columnDefinition = "TEXT")
    private String feedbackNotes;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    /**
     * Thông tin chấm dứt hợp đồng trước hạn (Legal termination)
     */
    @Column(name = "termination_reason", columnDefinition = "TEXT")
    private String terminationReason;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "terminated_by", length = 100)
    private String terminatedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Thời điểm gửi nhắc nhở gần nhất (cho cả gửi thủ công và cron job)
     */
    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    /**
     * Số lần đã phát lệnh nhắc nhở ký
     */
    @Column(name = "reminder_count")
    @Builder.Default
    private Integer reminderCount = 0;
}

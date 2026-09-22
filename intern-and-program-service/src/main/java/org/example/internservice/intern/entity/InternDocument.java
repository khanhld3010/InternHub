package org.example.internservice.intern.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.enums.DocumentStatus;
import org.example.internservice.intern.entity.enums.DocumentType;

@Entity
@Table(name = "intern_documents", indexes = {
        @Index(name = "idx_document_intern", columnList = "intern_id"),
        @Index(name = "idx_document_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile internProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING_REVIEW;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}

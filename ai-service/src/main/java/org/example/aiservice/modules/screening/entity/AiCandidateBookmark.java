package org.example.aiservice.modules.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.entity.BaseEntity;

@Entity
@Table(name = "ai_candidate_bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_bookmark_program_intern", columnNames = {"program_id", "intern_id"})
        },
        indexes = {
                @Index(name = "idx_ai_bookmark_program", columnList = "program_id"),
                @Index(name = "idx_ai_bookmark_intern", columnList = "intern_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCandidateBookmark extends BaseEntity {

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "intern_id", nullable = false)
    private Long internId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "notes", length = 500)
    private String notes;
}

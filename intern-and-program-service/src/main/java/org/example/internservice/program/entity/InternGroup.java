package org.example.internservice.program.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;

/**
 * Thực thể nhóm thực tập thuộc chương trình thực tập.
 */
@Entity
@Table(name = "intern_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_program_name", columnNames = {"program_id", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private InternshipProgram program;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "max_members", nullable = false)
    @Builder.Default
    private Integer maxMembers = 4;

    @Column(name = "mentor_id")
    private Long mentorId;

    @Column(name = "mentor_name", length = 100)
    private String mentorName;
}

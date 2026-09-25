package org.example.internservice.program.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "program_code_sequence")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCodeSequence {

    @Id
    @Column(name = "`year_month`", length = 6, nullable = false)
    private String yearMonth;

    @Column(name = "current_seq", nullable = false)
    @Builder.Default
    private Long currentSeq = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

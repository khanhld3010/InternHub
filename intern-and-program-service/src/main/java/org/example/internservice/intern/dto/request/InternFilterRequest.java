package org.example.internservice.intern.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.intern.entity.enums.InternStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternFilterRequest {

    private String keyword;
    private String university;
    private String major;
    private String appliedPosition;
    private InternStatus status;
    private Long programId;
}

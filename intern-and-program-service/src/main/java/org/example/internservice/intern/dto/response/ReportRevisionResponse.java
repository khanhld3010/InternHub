package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRevisionResponse {

    private String internCode;
    private Integer weekNumber;
    private String status;
    private String statusDisplayName;
    private String revisionNote;
}

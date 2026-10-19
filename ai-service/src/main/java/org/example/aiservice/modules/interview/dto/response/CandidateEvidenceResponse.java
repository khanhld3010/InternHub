package org.example.aiservice.modules.interview.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateEvidenceResponse {

    private Boolean recorded;
    private Integer currentBlurCount;
    private Integer maxStrikes;
    private Boolean isTerminated;
    private String warningMessage;
}

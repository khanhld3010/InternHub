package org.example.aiservice.modules.interview.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveCandidateAnswerResponse {

    private Long questionId;
    private Integer newRevision;
    private LocalDateTime serverDeadline;
    private Boolean isSubmitted;
    private Boolean synced;
    private String message;
}

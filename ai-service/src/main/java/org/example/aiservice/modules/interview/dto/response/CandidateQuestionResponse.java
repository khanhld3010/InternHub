package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.QuestionCategory;
import org.example.aiservice.modules.interview.enums.QuestionDifficulty;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateQuestionResponse {

    private Long questionId;
    private Integer displayOrder;
    private QuestionCategory category;
    private QuestionDifficulty difficulty;
    private String questionText;
    private String focusArea;
    private Integer suggestedScoreWeight;
    
    // Server authoritative timer info
    private LocalDateTime serverDeadline;
    private Long remainingSeconds;

    // Trạng thái trả lời (nếu đang khôi phục/xem lại)
    private String savedAnswerText;
    private Integer currentRevision;
    private Boolean isSubmitted;
    private String questionStatus; // COMPLETED, ACTIVE, PENDING
}

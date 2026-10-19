package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegradeRequest {

    @NotBlank(message = "Lý do chấm lại không được để trống")
    private String reason;
}

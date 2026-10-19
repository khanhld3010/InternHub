package org.example.aiservice.modules.screening.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToggleBookmarkRequest {

    @NotNull(message = "programId không được để trống")
    private Long programId;

    @NotNull(message = "internId không được để trống")
    private Long internId;

    private String notes;
}

package org.example.aiservice.modules.screening.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchScreeningRequest {

    @NotNull(message = "programId không được để trống")
    private Long programId;

    @NotEmpty(message = "Danh sách internIds không được để trống")
    private List<Long> internIds;

    @Builder.Default
    private boolean forceReevaluate = false;
}

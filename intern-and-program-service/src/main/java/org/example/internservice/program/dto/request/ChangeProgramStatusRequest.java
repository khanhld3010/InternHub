package org.example.internservice.program.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.enums.ProgramStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeProgramStatusRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private ProgramStatus targetStatus;

    private String cancellationReason;
}

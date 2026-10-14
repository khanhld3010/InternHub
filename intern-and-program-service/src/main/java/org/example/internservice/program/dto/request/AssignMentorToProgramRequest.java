package org.example.internservice.program.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class AssignMentorToProgramRequest {

    @NotNull(message = "ID của Mentor không được để trống")
    private Long mentorId;

    private String notes;
}

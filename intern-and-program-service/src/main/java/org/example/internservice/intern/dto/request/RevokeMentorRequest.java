package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class RevokeMentorRequest {

    @NotBlank(message = "Lý do thu hồi người hướng dẫn không được để trống")
    private String reason;
}

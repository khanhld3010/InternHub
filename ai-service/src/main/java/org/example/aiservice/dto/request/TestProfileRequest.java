package org.example.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestProfileRequest {

    @NotBlank(message = "Prompt thử nghiệm không được để trống")
    private String testPrompt;
}

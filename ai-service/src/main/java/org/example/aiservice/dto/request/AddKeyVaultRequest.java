package org.example.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddKeyVaultRequest {

    @NotBlank(message = "Tên gợi nhớ của khóa không được để trống")
    private String keyName;

    @Builder.Default
    private String provider = "GEMINI";

    @NotBlank(message = "Mã khóa API không được để trống")
    private String apiKey;

    @Builder.Default
    private Integer priority = 1;
}

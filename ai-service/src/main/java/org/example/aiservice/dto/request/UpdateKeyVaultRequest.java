package org.example.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKeyVaultRequest {

    @NotBlank(message = "Tên gợi nhớ của khóa không được để trống")
    private String keyName;

    private Integer priority;

    private String status;
}

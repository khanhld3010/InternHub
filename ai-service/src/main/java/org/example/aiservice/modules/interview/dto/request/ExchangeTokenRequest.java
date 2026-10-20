package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeTokenRequest {

    @NotBlank(message = "Raw invite token must not be blank")
    private String rawInviteToken;
}

package org.example.aiservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {

    @NotBlank(message = "Session ID khong duoc de trong")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Session ID phai la UUID hop le"
    )
    private String sessionId;

    @NotBlank(message = "Tin nhan khong duoc de trong")
    @Size(min = 1, max = 2000, message = "Tin nhan phai co do dai tu 1 den 2000 ky tu")
    private String message;
}

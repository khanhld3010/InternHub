package org.example.internservice.program.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollInternsRequest {

    @NotEmpty(message = "Danh sách ID thực tập sinh tiếp nhận không được để trống")
    private List<Long> internIds;
}

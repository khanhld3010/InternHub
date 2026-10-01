package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractFeedbackRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Size(min = 5, max = 1000, message = "Nội dung phản hồi phải từ 5 đến 1000 ký tự")
    private String feedbackNotes;
}

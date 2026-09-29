package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmContractRequest {

    @NotNull(message = "Vui lòng xác nhận đồng ý các điều khoản hợp đồng")
    @AssertTrue(message = "Bạn phải đồng ý với các điều khoản hợp đồng để tiếp tục")
    private Boolean agreeTerms;

    @NotBlank(message = "Họ và tên người ký không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên người ký phải từ 2 đến 100 ký tự")
    private String signerFullName;

    @Size(max = 1000, message = "Ghi chú xác nhận không được vượt quá 1000 ký tự")
    private String confirmationNote;
}

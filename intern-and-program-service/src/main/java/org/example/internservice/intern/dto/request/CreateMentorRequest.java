package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMentorRequest {

    @NotBlank(message = "Họ và tên người hướng dẫn không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải có độ dài từ 2 đến 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng hợp lệ")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "(0[3|5|7|8|9])+([0-9]{8})\\b",
            message = "Số điện thoại phải gồm 10 chữ số hợp lệ theo định dạng Việt Nam"
    )
    private String phone;

    @NotNull(message = "Vui lòng chọn phòng ban chuyên môn")
    private Long departmentId;
}

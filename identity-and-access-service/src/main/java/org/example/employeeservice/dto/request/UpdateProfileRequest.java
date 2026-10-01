package org.example.employeeservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.employeeservice.entity.enums.Gender;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu cập nhật hồ sơ cá nhân (Self-Service Profile Update)")
public class UpdateProfileRequest {

    @Schema(description = "Họ và tên (Nhóm A - Readonly sau kích hoạt)")
    private String fullName;

    @Schema(description = "Ngày sinh (Nhóm A - Readonly sau kích hoạt)")
    private LocalDate dateOfBirth;

    @Schema(description = "Số điện thoại liên lạc (Nhóm B - Tự do chỉnh sửa)")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    @Schema(description = "Số điện thoại phụ/alias")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phone;

    @Schema(description = "Giới tính (Nhóm B - Tự do chỉnh sửa)")
    private Gender gender;

    @Schema(description = "Địa chỉ liên lạc (Nhóm B - Tự do chỉnh sửa)")
    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Schema(description = "Giới thiệu bản thân (Nhóm B - Tự do chỉnh sửa)")
    @Size(max = 500, message = "Giới thiệu bản thân không được vượt quá 500 ký tự")
    private String bio;
}

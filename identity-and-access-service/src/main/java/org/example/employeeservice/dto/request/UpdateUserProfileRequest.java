package org.example.employeeservice.dto.request;

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
public class UpdateUserProfileRequest {

    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phoneNumber;

    private String phone;

    private LocalDate dateOfBirth;

    private Gender gender;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Size(max = 500, message = "Tiểu sử/Bio tối đa 500 ký tự")
    private String bio;
}

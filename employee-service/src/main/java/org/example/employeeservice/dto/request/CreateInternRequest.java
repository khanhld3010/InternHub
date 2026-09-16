package org.example.employeeservice.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInternRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$", message = "Số điện thoại phải là số di động Việt Nam 10 chữ số hợp lệ")
    private String phone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Gender gender;

    @NotBlank(message = "Trường học không được để trống")
    @Size(max = 150, message = "Tên trường học tối đa 150 ký tự")
    private String university;

    @NotBlank(message = "Chuyên ngành không được để trống")
    @Size(max = 100, message = "Tên chuyên ngành tối đa 100 ký tự")
    private String major;

    @Size(max = 20, message = "Khóa học/năm học tối đa 20 ký tự")
    private String academicYear;

    private Double gpa;

    @NotBlank(message = "Vị trí thực tập ứng tuyển không được để trống")
    @Size(max = 100, message = "Vị trí thực tập tối đa 100 ký tự")
    private String appliedPosition;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    private String notes;
}

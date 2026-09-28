package org.example.internservice.intern.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.example.internservice.intern.entity.enums.Gender;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyInternRequest {

    private Long userId;

    @NotBlank(message = "Họ và tên không được để trống")
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Gender gender;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @NotBlank(message = "Trường đại học/cao đẳng không được để trống")
    @Size(max = 150, message = "Tên trường không được vượt quá 150 ký tự")
    private String university;

    @NotBlank(message = "Chuyên ngành không được để trống")
    @Size(max = 100, message = "Chuyên ngành không được vượt quá 100 ký tự")
    private String major;

    @Size(max = 50, message = "Niên khóa không được vượt quá 50 ký tự")
    private String academicYear;

    @NotBlank(message = "Vị trí thực tập không được để trống")
    @Size(max = 100, message = "Vị trí ứng tuyển không được vượt quá 100 ký tự")
    private String appliedPosition;

    @NotNull(message = "Vui lòng chọn chương trình thực tập ứng tuyển")
    private Long programId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String notes;
}

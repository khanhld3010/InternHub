package org.example.internservice.program.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProgramRequest {

    @NotBlank(message = "Tên chương trình không được để trống")
    @Size(max = 150, message = "Tên chương trình tối đa 150 ký tự")
    private String name;

    @NotNull(message = "Phòng ban tiếp nhận không được để trống")
    private Long departmentId;

    private String description;

    private Integer maxInterns;

    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;
}

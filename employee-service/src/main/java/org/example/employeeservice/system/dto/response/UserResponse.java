package org.example.employeeservice.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private String position;
    private String status;
    private String role;
    private LocalDateTime createdAt;
}

package org.example.employeeservice.oauth2.dto.response;

import lombok.*;

/**
 * DTO nội bộ chứa thông tin người dùng được bóc tách từ Google ID Token sau khi đã xác thực mật mã.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleUserInfo {

    private String email;
    private String name;
    private String picture;
    private String sub;
    private boolean emailVerified;
}

package org.example.reportingservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "InternHub - Employee Service API",
        version = "1.0",
        description = "Tài liệu API và giao diện kiểm thử Swagger UI cho dịch vụ Employee Service & Authentication"
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Nhập chuỗi JWT accessToken nhận được sau khi đăng nhập thành công (hệ thống sẽ tự thêm tiền tố Bearer)"
)
public class OpenApiConfig {
}

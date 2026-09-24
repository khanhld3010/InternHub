package org.example.internservice.intern.controller;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.service.OnboardingTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Onboarding Activation API", description = "API xác thực token và kích hoạt tài khoản ứng viên (chống Safe Links scanner)")
public class OnboardingController {

    private final OnboardingTokenService tokenService;

    @GetMapping("/verify-token")
    @Operation(summary = "Xác thực token onboarding (Safe Links scanner an toàn, chỉ đọc)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyToken(@RequestParam("token") String token) {
        log.info("Nhan yeu cau verify token onboarding");
        Claims claims = tokenService.parseAndVerifyToken(token);

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("internId", claims.getSubject());
        data.put("email", claims.get("email"));
        data.put("fullName", claims.get("fullName"));

        return ResponseEntity.ok(ApiResponse.success(200, "Token hợp lệ", data));
    }

    @PostMapping("/activate")
    @Operation(summary = "Kích hoạt tài khoản và thiết lập mật khẩu lần đầu")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateAccount(
            @RequestBody Map<String, String> request
    ) {
        String token = request.get("token");
        String password = request.get("password");

        if (token == null || token.isBlank() || password == null || password.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Token và mật khẩu (tối thiểu 6 ký tự) là bắt buộc"));
        }

        Claims claims = tokenService.parseAndVerifyToken(token);
        log.info("Kich hoat tai khoan thanh cong cho intern ID: {}, email: {}", claims.getSubject(), claims.get("email"));

        Map<String, Object> data = new HashMap<>();
        data.put("activated", true);
        data.put("email", claims.get("email"));
        data.put("message", "Thiết lập mật khẩu thành công. Bạn có thể đăng nhập ngay bây giờ.");

        return ResponseEntity.ok(ApiResponse.success(200, "Kích hoạt tài khoản thành công", data));
    }
}

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
    private final org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;
    private final org.example.internservice.intern.repository.InternProfileRepository internProfileRepository;

    private final org.example.internservice.intern.repository.MentorProfileRepository mentorProfileRepository;

    @GetMapping("/verify-token")
    @Operation(summary = "Xác thực token onboarding (Safe Links scanner an toàn, chỉ đọc)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyToken(@RequestParam(value = "token", required = false) String token) {
        log.info("Nhan yeu cau verify token onboarding");
        if (token == null || token.isBlank()) {
            Map<String, Object> data = new HashMap<>();
            data.put("valid", false);
            return ResponseEntity.ok(ApiResponse.success(200, "Token không được cung cấp", data));
        }

        try {
            Claims claims = tokenService.parseAndVerifyToken(token);
            Map<String, Object> data = new HashMap<>();
            data.put("valid", true);
            data.put("targetId", claims.getSubject());
            data.put("email", claims.get("email"));
            data.put("fullName", claims.get("fullName"));
            data.put("role", claims.get("role") != null ? claims.get("role") : "INTERN");

            return ResponseEntity.ok(ApiResponse.success(200, "Token hợp lệ", data));
        } catch (Exception e) {
            log.warn("Verify token that bai: {}", e.getMessage());
            Map<String, Object> data = new HashMap<>();
            data.put("valid", false);
            return ResponseEntity.ok(ApiResponse.success(200, "Token không hợp lệ hoặc đã hết hạn", data));
        }
    }

    @PostMapping("/activate")
    @Operation(summary = "Kích hoạt tài khoản và thiết lập mật khẩu lần đầu (TM-12 & TM-29)")
    @org.springframework.transaction.annotation.Transactional
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
        Long targetId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            role = "INTERN";
        }

        log.info("Kich hoat tai khoan cho role: {}, ID: {}, email: {}", role, targetId, email);

        if ("MENTOR".equalsIgnoreCase(role)) {
            org.example.internservice.intern.entity.MentorProfile mentorProfile = mentorProfileRepository.findById(targetId)
                    .orElseThrow(() -> new org.example.internservice.exception.ResourceNotFoundException("Không tìm thấy hồ sơ người hướng dẫn ID: " + targetId));

            if (mentorProfile.getUserId() != null || "ACTIVE".equalsIgnoreCase(mentorProfile.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "Hồ sơ người hướng dẫn này đã được kích hoạt tài khoản trước đó. Vui lòng đăng nhập trực tiếp."));
            }

            Map<String, Object> regReq = new HashMap<>();
            regReq.put("username", email);
            regReq.put("password", password);
            regReq.put("fullName", mentorProfile.getFullName());
            regReq.put("email", mentorProfile.getEmail());
            regReq.put("phoneNumber", mentorProfile.getPhone());
            regReq.put("role", "MENTOR");

            Long createdUserId = null;
            try {
                Map<String, Object> regRes = identityServiceClient.createUserAccount(regReq);
                if (regRes.get("userId") != null) {
                    createdUserId = Long.valueOf(regRes.get("userId").toString());
                } else if (regRes.get("id") != null) {
                    createdUserId = Long.valueOf(regRes.get("id").toString());
                }
            } catch (Exception ex) {
                log.warn("Tạo tài khoản Mentor trên Identity Service gặp lỗi: {}. Thử phục hồi userId theo email...", ex.getMessage());
                createdUserId = identityServiceClient.findUserIdByEmail(email);
                if (createdUserId == null) {
                    throw ex;
                }
                log.info("Phục hồi thành công userId={} cho Mentor email={}", createdUserId, email);
            }

            if (createdUserId == null) {
                throw new IllegalStateException("Không nhận được userId hợp lệ từ Identity Service");
            }

            mentorProfile.setUserId(createdUserId);
            mentorProfile.setStatus("ACTIVE");
            mentorProfileRepository.save(mentorProfile);

            log.info("Kich hoat tai khoan Mentor thanh cong: mentorId={}, createdUserId={}, email={}", targetId, createdUserId, email);

            Map<String, Object> data = new HashMap<>();
            data.put("activated", true);
            data.put("email", email);
            data.put("role", "MENTOR");
            data.put("userId", createdUserId);
            data.put("message", "Thiết lập mật khẩu thành công. Bạn có thể đăng nhập ngay với tài khoản " + email);

            return ResponseEntity.ok(ApiResponse.success(200, "Kích hoạt tài khoản Mentor thành công", data));
        }

        // Luồng kích hoạt cho Intern (mặc định)
        Long internId = targetId;
        org.example.internservice.intern.entity.InternProfile profile = internProfileRepository.findById(internId)
                .orElseThrow(() -> new org.example.internservice.exception.ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh ID: " + internId));

        if (profile.getUserId() != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Hồ sơ thực tập sinh này đã được kích hoạt tài khoản trước đó. Vui lòng đăng nhập trực tiếp."));
        }

        // Tạo tài khoản trên Identity Service
        Map<String, Object> regReq = new HashMap<>();
        regReq.put("username", email);
        regReq.put("password", password);
        regReq.put("fullName", profile.getFullName());
        regReq.put("email", profile.getEmail());
        regReq.put("phoneNumber", profile.getPhone());
        regReq.put("role", "INTERN");

        Long createdUserId = null;
        try {
            Map<String, Object> regRes = identityServiceClient.createUserAccount(regReq);
            if (regRes.get("userId") != null) {
                createdUserId = Long.valueOf(regRes.get("userId").toString());
            } else if (regRes.get("id") != null) {
                createdUserId = Long.valueOf(regRes.get("id").toString());
            }
        } catch (Exception ex) {
            log.warn("Tạo tài khoản Intern trên Identity Service gặp lỗi: {}. Thử phục hồi userId theo email...", ex.getMessage());
            createdUserId = identityServiceClient.findUserIdByEmail(email);
            if (createdUserId == null) {
                throw ex;
            }
            log.info("Phục hồi thành công userId={} cho Intern email={}", createdUserId, email);
        }

        if (createdUserId == null) {
            throw new IllegalStateException("Không nhận được userId hợp lệ từ Identity Service");
        }

        // Liên kết chính thức và an toàn userId vào InternProfile
        profile.setUserId(createdUserId);
        internProfileRepository.save(profile);

        log.info("Liên kết tài khoản thành công: internId={}, createdUserId={}, email={}", internId, createdUserId, email);

        Map<String, Object> data = new HashMap<>();
        data.put("activated", true);
        data.put("email", email);
        data.put("role", "INTERN");
        data.put("userId", createdUserId);
        data.put("message", "Thiết lập mật khẩu thành công. Bạn có thể đăng nhập ngay bây giờ với tài khoản " + email);

        return ResponseEntity.ok(ApiResponse.success(200, "Kích hoạt tài khoản thành công", data));
    }
}

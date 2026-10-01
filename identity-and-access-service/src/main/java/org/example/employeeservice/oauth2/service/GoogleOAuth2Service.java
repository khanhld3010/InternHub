package org.example.employeeservice.oauth2.service;

import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;

/**
 * Service xử lý toàn bộ luồng nghiệp vụ đăng nhập bằng Google OAuth2,
 * đồng bộ tài khoản người dùng, cấp phát JWT và tích hợp kiểm toán.
 */
public interface GoogleOAuth2Service {

    /**
     * Tiếp nhận Google ID Token, xác thực, liên kết hoặc tạo tài khoản và sinh JWT Token nội bộ.
     *
     * @param request chứa idToken từ client
     * @return LoginResponse chứa JWT accessToken và thông tin định danh
     */
    LoginResponse loginWithGoogle(GoogleLoginRequest request);
}

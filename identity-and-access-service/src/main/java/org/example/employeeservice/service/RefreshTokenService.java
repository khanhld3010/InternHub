package org.example.employeeservice.service;

import org.example.employeeservice.dto.response.TokenRotationResult;
import org.example.employeeservice.entity.Account;

public interface RefreshTokenService {

    /**
     * Sinh mới Refresh Token cho tài khoản khi đăng nhập
     *
     * @param account Tài khoản đăng nhập
     * @param rememberMe Trạng thái ghi nhớ đăng nhập
     * @return Chuỗi raw Refresh Token để gửi về Client qua Cookie
     */
    String createRefreshToken(Account account, boolean rememberMe);

    /**
     * Xác thực và xoay vòng Refresh Token (Token Rotation)
     *
     * @param rawRefreshToken Chuỗi raw token từ client
     * @param rememberMe Trạng thái ghi nhớ đăng nhập
     * @return Cặp dữ liệu bao gồm Account và chuỗi raw Refresh Token mới
     */
    TokenRotationResult verifyAndRotate(String rawRefreshToken, boolean rememberMe);

    /**
     * Thu hồi một Refresh Token cụ thể (khi người dùng bấm Đăng xuất)
     *
     * @param rawRefreshToken Chuỗi raw token cần thu hồi
     */
    void revokeToken(String rawRefreshToken);

    /**
     * Thu hồi toàn bộ Refresh Tokens của một tài khoản (khi phát hiện Replay Attack hoặc vi phạm bảo mật)
     *
     * @param accountId ID của tài khoản
     */
    void revokeAllAccountTokens(Integer accountId);

    /**
     * Dọn dẹp định kỳ các token đã hết hạn hoặc đã thu hồi
     */
    void cleanupExpiredTokens();
}

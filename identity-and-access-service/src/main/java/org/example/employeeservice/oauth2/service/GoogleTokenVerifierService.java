package org.example.employeeservice.oauth2.service;

import org.example.employeeservice.oauth2.dto.response.GoogleUserInfo;

/**
 * Service xác thực chữ ký mật mã và tính hợp lệ của chuỗi Google ID Token từ phía Google Authentication Servers.
 */
public interface GoogleTokenVerifierService {

    /**
     * Xác thực cryptographic Google ID Token và trích xuất thông tin người dùng.
     *
     * @param idToken chuỗi JWT Google ID Token từ Client
     * @return GoogleUserInfo thông tin bóc tách được từ token
     */
    GoogleUserInfo verify(String idToken);
}

package org.example.employeeservice.oauth2.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.oauth2.config.GoogleOAuth2Properties;
import org.example.employeeservice.oauth2.dto.response.GoogleUserInfo;
import org.example.employeeservice.oauth2.service.GoogleTokenVerifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Triển khai xác thực Google ID Token bằng thư viện chính thức com.google.api-client.
 * Hỗ trợ tự động cache Google Public Key Certificates và kiểm tra Audience / Expiration.
 */
@Slf4j
@Service
public class GoogleTokenVerifierServiceImpl implements GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifierServiceImpl(GoogleOAuth2Properties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(properties.getClientId()))
                .build();
    }

    public GoogleTokenVerifierServiceImpl(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            log.warn("Yêu cầu xác thực với chuỗi ID Token rỗng");
            throw new UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn");
        }

        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                log.warn("Google ID Token không vượt qua bước kiểm tra chữ ký hoặc thời hạn (aud/exp)");
                throw new UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            String sub = payload.getSubject();
            Boolean emailVerified = payload.getEmailVerified();

            String resolvedName = resolveDisplayName(name, email);

            return GoogleUserInfo.builder()
                    .email(email != null ? email.trim().toLowerCase() : null)
                    .name(resolvedName)
                    .picture(picture)
                    .sub(sub)
                    .emailVerified(Boolean.TRUE.equals(emailVerified))
                    .build();
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            // Defense-in-depth: Tuyệt đối không log chuỗi token thô của người dùng ra log file
            log.warn("Lỗi khi giải mã Google ID Token: {}", ex.getMessage());
            throw new UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn");
        }
    }

    private String resolveDisplayName(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.split("@")[0];
        }
        return "Google User";
    }
}

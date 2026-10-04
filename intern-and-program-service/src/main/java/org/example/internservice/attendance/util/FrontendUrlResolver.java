package org.example.internservice.attendance.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

@Component
@Slf4j
public class FrontendUrlResolver {

    private final String defaultFrontendUrl;

    public FrontendUrlResolver(@Value("${app.frontend-url:http://localhost:5173}") String defaultFrontendUrl) {
        this.defaultFrontendUrl = normalizeUrl(defaultFrontendUrl);
    }

    /**
     * Phân giải Base URL của Frontend theo chiến lược 3 tầng:
     * 1. clientBaseUrl từ Request Body (nếu client chủ động truyền)
     * 2. Header 'Origin' hoặc 'Referer' từ HttpServletRequest trong RequestContext hiện tại
     * 3. Fallback về cấu hình mặc định (app.frontend-url)
     */
    public String resolveBaseUrl(String clientBaseUrl) {
        if (clientBaseUrl != null && !clientBaseUrl.isBlank()) {
            String normalized = normalizeUrl(clientBaseUrl.trim());
            log.debug("Sử dụng clientBaseUrl do client chỉ định: {}", normalized);
            return normalized;
        }

        HttpServletRequest request = getCurrentHttpRequest();
        if (request != null) {
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank() && !origin.equalsIgnoreCase("null")) {
                String normalized = normalizeUrl(origin.trim());
                log.debug("Sử dụng URL từ header Origin: {}", normalized);
                return normalized;
            }

            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    URI uri = URI.create(referer.trim());
                    String originFromReferer = uri.getScheme() + "://" + uri.getAuthority();
                    log.debug("Sử dụng URL trích xuất từ header Referer: {}", originFromReferer);
                    return originFromReferer;
                } catch (Exception e) {
                    log.warn("Không thể parse header Referer [{}]: {}", referer, e.getMessage());
                }
            }
        }

        log.debug("Sử dụng fallback defaultFrontendUrl: {}", defaultFrontendUrl);
        return defaultFrontendUrl;
    }

    public String buildConfirmationUrl(String baseUrl, String token) {
        String cleanBase = normalizeUrl(baseUrl);
        return String.format("%s/intern/attendance/confirm?token=%s", cleanBase, token);
    }

    private HttpServletRequest getCurrentHttpRequest() {
        try {
            RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
            if (attribs instanceof ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes.getRequest();
            }
        } catch (Exception e) {
            log.debug("Không tìm thấy HttpServletRequest trong context hiện tại: {}", e.getMessage());
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:5173";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

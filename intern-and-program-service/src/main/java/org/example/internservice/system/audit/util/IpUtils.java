package org.example.internservice.system.audit.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR",
            "X-Real-IP"
    };

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }

        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (ipList != null && !ipList.trim().isEmpty() && !"unknown".equalsIgnoreCase(ipList.trim())) {
                // X-Forwarded-For có thể chứa danh sách IP ngăn cách bởi dấu phẩy, IP đầu tiên là của client
                return ipList.split(",")[0].trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr != null ? remoteAddr : "UNKNOWN";
    }
}

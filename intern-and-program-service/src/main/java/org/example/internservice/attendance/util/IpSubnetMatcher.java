package org.example.internservice.attendance.util;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Tiện ích kiểm tra địa chỉ IP client có nằm trong dải CIDR/IP văn phòng hay không (ADR 0003 & TM-25 v2.0).
 */
@Component
public class IpSubnetMatcher {

    /**
     * Kiểm tra client IP có khớp với bất kỳ dải IP/Subnet nào trong danh sách được cấu hình hay không.
     * Hỗ trợ cả IP đơn lẻ (e.g. 192.168.1.15, 127.0.0.1, 0:0:0:0:0:0:0:1) và định dạng CIDR (e.g. 192.168.1.0/24, 10.0.0.0/8).
     */
    public boolean matches(String clientIp, String configuredSubnets) {
        if (clientIp == null || clientIp.trim().isEmpty() || configuredSubnets == null || configuredSubnets.trim().isEmpty()) {
            return false;
        }

        // Tự động công nhận localhost trong môi trường dev/local testing
        if ("127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp) || "localhost".equalsIgnoreCase(clientIp)) {
            return true;
        }

        List<String> subnets = Arrays.stream(configuredSubnets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        for (String subnet : subnets) {
            if (matchesSubnet(clientIp, subnet)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSubnet(String clientIp, String subnet) {
        try {
            if (subnet.contains("/")) {
                String[] parts = subnet.split("/");
                InetAddress target = InetAddress.getByName(parts[0]);
                InetAddress client = InetAddress.getByName(clientIp);

                if (target.getAddress().length != client.getAddress().length) {
                    return false;
                }

                int prefixLength = Integer.parseInt(parts[1]);
                byte[] targetBytes = target.getAddress();
                byte[] clientBytes = client.getAddress();

                int bytesToCheck = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < bytesToCheck; i++) {
                    if (targetBytes[i] != clientBytes[i]) {
                        return false;
                    }
                }

                if (remainingBits > 0) {
                    int mask = 0xFF << (8 - remainingBits);
                    return (targetBytes[bytesToCheck] & mask) == (clientBytes[bytesToCheck] & mask);
                }

                return true;
            } else {
                return clientIp.trim().equalsIgnoreCase(subnet.trim());
            }
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}

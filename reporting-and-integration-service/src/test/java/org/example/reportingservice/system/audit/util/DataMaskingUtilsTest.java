package org.example.reportingservice.system.audit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataMaskingUtilsTest {

    @Test
    @DisplayName("UT-BE-01: Mask JSON string containing sensitive password and token")
    void maskJson_shouldMaskSensitiveFields() {
        String json = "{\"username\":\"admin\",\"password\":\"secret123\",\"token\":\"jwt_xyz_999\",\"email\":\"admin@test.com\"}";

        String masked = DataMaskingUtils.maskJson(json);

        assertNotNull(masked);
        assertFalse(masked.contains("secret123"), "Mật khẩu thô không được xuất hiện trong JSON sau khi mask");
        assertFalse(masked.contains("jwt_xyz_999"), "Token thô không được xuất hiện trong JSON sau khi mask");
        assertTrue(masked.contains("******"));
        assertTrue(masked.contains("admin@test.com"));
    }

    @Test
    @DisplayName("UT-BE-02: Mask Java Object Map containing sensitive keys")
    void maskObject_shouldMaskKeysInMap() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "intern_01");
        payload.put("newPassword", "pass2026");
        payload.put("secretKey", "abc-secret-key");

        String masked = DataMaskingUtils.maskObject(payload);

        assertNotNull(masked);
        assertFalse(masked.contains("pass2026"));
        assertFalse(masked.contains("abc-secret-key"));
        assertTrue(masked.contains("intern_01"));
        assertTrue(masked.contains("******"));
    }

    @Test
    @DisplayName("UT-BE-03: Null or empty input should return null safely")
    void maskJson_withNullOrEmpty_shouldReturnNull() {
        assertNull(DataMaskingUtils.maskJson(null));
        assertNull(DataMaskingUtils.maskJson("   "));
        assertNull(DataMaskingUtils.maskObject(null));
    }
}

package org.example.aiservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionUtilTest {

    private AesGcmEncryptionUtil encryptionUtil;
    private static final String SAMPLE_KEY_BASE64 = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes() // đúng 32 bytes
    );

    @BeforeEach
    void setUp() {
        encryptionUtil = new AesGcmEncryptionUtil(SAMPLE_KEY_BASE64);
    }

    @Test
    @DisplayName("Mã hóa và giải mã thành công chuỗi API Key")
    void testEncryptDecryptSuccess() {
        String apiKey = "AIzaSyD_RandomSampleKey123456789";
        String encrypted = encryptionUtil.encrypt(apiKey, 1);

        assertNotNull(encrypted);
        assertNotEquals(apiKey, encrypted);

        String decrypted = encryptionUtil.decrypt(encrypted, 1);
        assertEquals(apiKey, decrypted);
    }

    @Test
    @DisplayName("Hai lần mã hóa cùng 1 plaintext phải sinh ra 2 ciphertext khác nhau (do IV ngẫu nhiên)")
    void testRandomIvGeneratesDifferentCiphertext() {
        String apiKey = "AIzaSyD_RandomSampleKey123456789";
        String encrypted1 = encryptionUtil.encrypt(apiKey, 1);
        String encrypted2 = encryptionUtil.encrypt(apiKey, 1);

        assertNotEquals(encrypted1, encrypted2);
        assertEquals(apiKey, encryptionUtil.decrypt(encrypted1, 1));
        assertEquals(apiKey, encryptionUtil.decrypt(encrypted2, 1));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi ciphertext bị giả mạo (Authentication Tag phát hiện)")
    void testTamperedCiphertextThrowsException() {
        String apiKey = "AIzaSyD_RandomSampleKey123456789";
        String encrypted = encryptionUtil.encrypt(apiKey, 1);

        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01; // Biến đổi 1 bit
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(RuntimeException.class, () -> encryptionUtil.decrypt(tampered, 1));
    }
}

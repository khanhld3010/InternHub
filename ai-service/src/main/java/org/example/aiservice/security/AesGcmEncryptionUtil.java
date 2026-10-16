package org.example.aiservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiện ích mã hóa AES-256-GCM tuân thủ đặc tả bảo mật:
 * - IV 12 bytes ngẫu nhiên cho mỗi lần mã hóa (SecureRandom)
 * - Tag 128-bit xác thực tính toàn vẹn
 * - Lưu định dạng Base64(IV(12B) || ciphertext || tag(16B))
 * - Hỗ trợ quản lý khóa theo version (key_version) phục vụ xoay khóa
 */
@Component
public class AesGcmEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Integer, SecretKey> keyRing = new ConcurrentHashMap<>();

    public AesGcmEncryptionUtil(
            @Value("${ai.encryption.key-v1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}") String base64KeyV1
    ) {
        if (base64KeyV1 == null || base64KeyV1.isBlank()) {
            throw new IllegalStateException("AI_ENCRYPTION_KEY_V1 khong duoc de trong!");
        }
        byte[] decoded = Base64.getDecoder().decode(base64KeyV1.trim());
        if (decoded.length != 32) {
            throw new IllegalStateException("AI_ENCRYPTION_KEY_V1 phai co do dai dung 32 bytes (AES-256)!");
        }
        keyRing.put(1, new SecretKeySpec(decoded, "AES"));
    }

    /**
     * Mã hóa plaintext bằng khóa của version tương ứng.
     */
    public String encrypt(String plaintext, int keyVersion) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        SecretKey key = getKey(keyVersion);
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Loi trong qua trinh ma hoa AES-GCM: " + e.getMessage(), e);
        }
    }

    /**
     * Giải mã ciphertext theo keyVersion chỉ định.
     */
    public String decrypt(String base64Payload, int keyVersion) {
        if (base64Payload == null || base64Payload.isEmpty()) {
            return null;
        }
        SecretKey key = getKey(keyVersion);
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Payload);
            if (decoded.length < IV_LENGTH_BYTE + 16) {
                throw new IllegalArgumentException("Payload AES-GCM khong hop le (qua ngan)!");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Loi trong qua trinh giai ma AES-GCM: " + e.getMessage(), e);
        }
    }

    private SecretKey getKey(int version) {
        SecretKey key = keyRing.get(version);
        if (key == null) {
            throw new IllegalArgumentException("Khong tim thay khoa ma hoa voi key_version = " + version);
        }
        return key;
    }
}

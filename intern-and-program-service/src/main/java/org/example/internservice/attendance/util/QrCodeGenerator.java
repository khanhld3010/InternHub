package org.example.internservice.attendance.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class QrCodeGenerator {

    private static final int DEFAULT_WIDTH = 280;
    private static final int DEFAULT_HEIGHT = 280;

    public String generateQrCodeDataUrl(String content) {
        return generateQrCodeDataUrl(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public String generateQrCodeDataUrl(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            log.error("Lỗi khi sinh ảnh mã QR: {}", e.getMessage(), e);
            throw new IllegalStateException("Không thể tạo mã QR xác thực", e);
        }
    }
}

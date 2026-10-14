package org.example.internservice.attendance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý kết nối Server-Sent Events (SSE) theo qrToken để đồng bộ real-time tức thì từ Điện thoại về Laptop (ADR 0003 & TM-25 v2.0).
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private static final Long EMITTER_TIMEOUT = 120_000L; // 2 phút (dư giả so với TTL 60s của QR)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String qrToken) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        emitter.onCompletion(() -> {
            emitters.remove(qrToken);
            log.debug("SSE Emitter hoàn tất cho qrToken: {}", qrToken);
        });

        emitter.onTimeout(() -> {
            emitters.remove(qrToken);
            log.debug("SSE Emitter timeout cho qrToken: {}", qrToken);
        });

        emitter.onError((e) -> {
            emitters.remove(qrToken);
            log.debug("SSE Emitter gặp lỗi cho qrToken: {}", qrToken);
        });

        emitters.put(qrToken, emitter);

        // Gửi event CONNECTED ban đầu
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("{\"status\":\"CONNECTED\",\"message\":\"Đã kết nối luồng SSE chấm công thành công\"}"));
        } catch (IOException e) {
            emitters.remove(qrToken);
            log.warn("Không thể gửi event CONNECTED tới qrToken: {}", qrToken);
        }

        return emitter;
    }

    public void emitCheckInSuccess(String qrToken, Object data) {
        SseEmitter emitter = emitters.get(qrToken);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("CHECK_IN_SUCCESS")
                        .data(data));
                emitter.complete();
                log.info("Đã bắn event CHECK_IN_SUCCESS qua SSE cho qrToken: {}", qrToken);
            } catch (IOException e) {
                log.warn("Lỗi khi bắn event CHECK_IN_SUCCESS cho qrToken {}: {}", qrToken, e.getMessage());
            } finally {
                emitters.remove(qrToken);
            }
        } else {
            log.warn("Không tìm thấy active SSE emitter cho qrToken: {}. Có thể client đang dùng fallback short-polling.", qrToken);
        }
    }
}

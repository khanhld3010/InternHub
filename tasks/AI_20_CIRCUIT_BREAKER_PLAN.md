# KẾ HOẠCH TRIỂN KHAI CHI TIẾT: AI-20 - RESILIENCE4J CIRCUIT BREAKER & FALLBACK CHO AI CLIENT

- **Mã tính năng:** `AI-20`
- **Mức độ ưu tiên:** P1 (Production Hardening & High Availability)
- **Mục tiêu:** Ngăn chặn hiện tượng lỗi dây chuyền (Cascading Failure), giải phóng thread pool ngay lập tức khi Google Gemini API gặp sự cố/quá tải hoặc timeout, đồng thời trả về thông điệp phản hồi thân thiện (Graceful Degradation) thay vì quăng lỗi HTTP 500/503 khó hiểu cho người dùng.

---

## 1. KIẾN TRÚC MẠCH BẢO VỆ (CIRCUIT BREAKER ARCHITECTURE)

```
[User Request] 
      │
      ▼
[AiChatService / ScreeningService / InterviewService]
      │
      ▼
[AiKeyVaultManager: acquireActiveKey()] ──► (Weighted Round-Robin + Cooldown 60s)
      │
      ▼
┌────────────────────────────────────────────────────────────────────────┐
│  GeminiApiClient (Bọc bởi Resilience4j Circuit Breaker: 'geminiClient') │
│                                                                        │
│  - Trạng thái CLOSED (Bình thường): Cho 100% request gọi REST API       │
│  - Trạng thái OPEN (Mở mạch - Trip): Chặn ngay tại cổng trong 30s       │
│  - Trạng thái HALF_OPEN (Thử nghiệm): Cho 3 request thăm dò đi qua     │
│  - TimeLimiter: Timeout 15s/request                                    │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
           ┌─────────────────────────┴─────────────────────────┐
           ▼                                                   ▼
     [Thành công]                                         [Thất bại]
  (Google Gemini 200 OK)                   (CallNotPermittedException / 429 / 5xx)
                                                               │
                                                               ▼
                                               ┌───────────────────────────────┐
                                               │ Fallback Method (Tự chữa lành)│
                                               │ - Đưa key vào Cooldown        │
                                               │ - Trả về message thân thiện:  │
                                               │   "Hệ thống AI hiện đang      │
                                               │   trong thời gian bảo trì/quá │
                                               │   tải tạm thời..."            │
                                               └───────────────────────────────┘
```

---

## 2. BỐ TRÍ THÀNH PHẦN & CÁC BƯỚC TRIỂN KHAI

### Bước 1: Bổ sung Dependency Resilience4j vào `ai-service/build.gradle`
- Thêm thư viện:
  ```groovy
  implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
  implementation 'org.springframework.boot:spring-boot-starter-aop'
  ```

### Bước 2: Thiết lập Cấu hình Circuit Breaker trong `config-repo-local/ai-service.yml`
```yaml
resilience4j:
  circuitbreaker:
    instances:
      geminiClient:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10              # Đánh giá trên 10 request gần nhất
        minimum-number-of-calls: 5           # Cần tối thiểu 5 cuộc gọi để tính toán
        failure-rate-threshold: 50           # Nếu >= 50% cuộc gọi lỗi -> MỞ MẠCH (OPEN)
        slow-call-rate-threshold: 60         # Nếu >= 60% cuộc gọi chậm (> 12s) -> MỞ MẠCH
        slow-call-duration-threshold: 12000ms
        wait-duration-in-open-state: 30000ms # Ở trạng thái OPEN trong 30s trước khi thử HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3 # Cho phép 3 request thăm dò
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - org.springframework.web.client.RestClientResponseException
          - java.net.http.HttpTimeoutException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
  timelimiter:
    instances:
      geminiClient:
        timeout-duration: 15000ms           # Cắt kết nối nếu treo quá 15s
        cancel-running-future: true
```

### Bước 3: Tích hợp `@CircuitBreaker` & Fallback vào `GeminiApiClient.java`
1. Đặt annotation `@CircuitBreaker(name = "geminiClient", fallbackMethod = "fallbackGenerateContent")` cho:
   - `generateContent(...)`
   - `generateStructuredJson(...)`
2. Viết các method Fallback nhận ngoại lệ `Throwable t`:
   - Ghi log cảnh báo mức WARN rõ ràng về trạng thái mạch mở.
   - Trả về câu thông báo mặc định thân thiện cho người dùng:
     > *"Hệ thống AI hiện đang trong thời gian bảo trì hoặc quá tải lưu lượng tạm thời. Vui lòng quay lại sau ít phút hoặc trao đổi trực tiếp với HR/Mentor qua mục Hỗ trợ."*

### Bước 4: Kiểm thử Đơn vị & Tích hợp (TDD Verification)
1. Tạo Unit Test `GeminiApiClientResilienceTest.java`:
   - Test 1: Khi gọi thành công -> Mạch giữ nguyên trạng thái CLOSED.
   - Test 2: Khi bị lỗi liên tiếp 5 lần -> Mạch chuyển sang OPEN, request thứ 6 bị chặn ngay lập tức (fail-fast) mà không gửi ra mạng bên ngoài.
   - Test 3: Phương thức Fallback kích hoạt và trả về thông báo chuẩn xác.

---

## 3. CHECKLIST NGHIỆM THU (ACCEPTANCE CRITERIA)
- [ ] Không làm gãy API hiện có của Chatbot TTS, Sàng lọc CV hay Sinh câu hỏi phỏng vấn.
- [ ] Không có FQCN (Fully Qualified Class Names), tuân thủ Clean Code 100%.
- [ ] Khi Circuit Breaker mở mạch, hệ thống phản hồi ngay tức thì trong < 50ms (không bị treo 10s-30s).
- [ ] Build dự án thành công không lỗi compile.

# 06. Quy Trình Kiểm Thử & Xác Minh Chất Lượng (Testing & Verification)

Tài liệu này quy định các bước kiểm tra tự động qua Gradle, tiêu chuẩn viết Unit Test với JUnit 5/Mockito và danh mục kiểm thử API bằng dữ liệu thực tế bắt buộc phải thực hiện trước khi bàn giao bất kỳ tính năng hoặc bản sửa lỗi nào trong **InternHub (Backend)**.

---

## 1. Kiểm Tra Tự Động (Automated Verification Pipeline)

Trước khi hoàn tất nhiệm vụ và lập báo cáo `walkthrough.md`, Agent **bắt buộc** phải chạy và đảm bảo vượt qua các lệnh kiểm tra tự động sau tại thư mục gốc `InternHub/` hoặc tại thư mục microservice tương ứng (nhớ nêu rõ mục đích trước khi chạy lệnh):

### 1.1. Kiểm Tra Biên Dịch Java (`compileJava`)
- **Lệnh thực thi**:
  ```powershell
  # Kiểm tra service cụ thể (khuyên dùng):
  .\gradlew :identity-and-access-service:compileJava
  .\gradlew :intern-and-program-service:compileJava
  .\gradlew :reporting-and-integration-service:compileJava
  .\gradlew :api-gateway:compileJava
  ```
- **Yêu cầu**: Biên dịch thành công 100% (`BUILD SUCCESSFUL`), không phát sinh lỗi cú pháp, thiếu import hay xung đột type.

### 1.2. Kiểm Tra Chạy Unit Test (`test`)
- **Lệnh thực thi**:
  ```powershell
  .\gradlew :<service-name>:test
  ```
- **Yêu cầu**: 100% các bài test trong test suite phải pass, không có bất kỳ failure hoặc error nào.

### 1.3. Kiểm Tra Đóng Gói JAR File (`bootJar`)
- **Lệnh thực thi**:
  ```powershell
  .\gradlew :<service-name>:bootJar
  ```
- **Yêu cầu**: File `.jar` được đóng gói thành công trong thư mục `build/libs/`, bảo đảm ứng dụng sẵn sàng build Docker container hoặc chạy độc lập.

---

## 2. Tiêu Chuẩn Viết Unit Test & Slice Test

### 2.1. Unit Test Cho Tầng Service (JUnit 5 + Mockito)
- **Mục tiêu**: Kiểm tra độc lập toàn bộ các nhánh logic nghiệp vụ, tính toán, và xử lý exception mà không cần kết nối Database thật.
- **Quy chuẩn bắt buộc**:
  - Sử dụng `@ExtendWith(MockitoExtension.class)`.
  - Giả lập Repository và Client bằng `@Mock`.
  - Tiêm đối tượng kiểm thử bằng `@InjectMocks`.
  - Đặt tên test rõ ràng theo mẫu: `givenCondition_whenAction_thenExpectedResult`:
    ```java
    @ExtendWith(MockitoExtension.class)
    class InternServiceImplTest {

        @Mock
        private InternRepository internRepository;

        @InjectMocks
        private InternServiceImpl internService;

        @Test
        @DisplayName("Ném ResourceNotFoundException khi không tìm thấy thực tập sinh theo ID")
        void givenInvalidId_whenGetInternById_thenThrowResourceNotFoundException() {
            // Given
            Long internId = 999L;
            when(internRepository.findById(internId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(ResourceNotFoundException.class, () -> internService.getInternById(internId));
            verify(internRepository, times(1)).findById(internId);
        }
    }
    ```

### 2.2. Slice Test Cho Tầng Controller (`@WebMvcTest`)
- **Mục tiêu**: Kiểm tra ánh xạ HTTP, tính hợp lệ của Request Body (`@Valid`), và format của `ApiResponse<T>`.
- **Quy chuẩn**:
  - Dùng `@WebMvcTest(InternController.class)`.
  - Mock tầng Service bằng `@MockBean`.
  - Sử dụng `MockMvc` để gửi request giả lập và kiểm tra HTTP Status code (`200 OK`, `201 CREATED`, `400 BAD REQUEST`).

---

## 3. Tiêu Chuẩn Kiểm Thử Bằng Dữ Liệu Thực Tế (Real Data Testing)

> [!CAUTION]
> **MỌI HOẠT ĐỘNG KIỂM THỬ TÍCH HỢP PHẢI DỰA TRÊN DỮ LIỆU THỰC TẾ:**
>
> 1. **Cấm chạy SQL phá hoại**: Không tự ý chạy các lệnh `INSERT`, `UPDATE`, `DELETE` bừa bãi vào cơ sở dữ liệu.
> 2. **Yêu cầu người dùng cung cấp tài khoản**: Nếu cần tài khoản theo role (`ADMIN`, `HR`, `MENTOR`, `INTERN`) để lấy token JWT, Agent bắt buộc phải hỏi người dùng.
> 3. **Kiểm thử qua API Gateway**: Luôn gọi API thông qua cổng API Gateway (Port `8080`) kèm Bearer JWT Token để bảo đảm kiểm tra đúng luồng định tuyến và phân quyền của toàn hệ thống:
>    ```bash
>    curl -X GET http://localhost:8080/api/v1/interns \
>      -H "Authorization: Bearer <valid_jwt_token>" \
>      -H "Content-Type: application/json"
>    ```

---

## 4. Kiểm Tra Trạng Thái Vận Hành Microservices (Healthcheck Matrix)

Trước khi nghiệm thu toàn diện, Agent kiểm tra trạng thái của các service thông qua Spring Boot Actuator:

| Dịch Vụ | Port | URL Actuator Healthcheck | Kết Quả Kỳ Vọng |
| :--- | :---: | :--- | :---: |
| **Config Server** | `8888` | `http://localhost:8888/actuator/health` | `{"status":"UP"}` |
| **Discovery Server (Eureka)** | `8761` | `http://localhost:8761/actuator/health` | `{"status":"UP"}` |
| **API Gateway** | `8080` | `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| **Identity & Access Service** | `8081` | `http://localhost:8081/actuator/health` | `{"status":"UP"}` |
| **Intern & Program Service** | `8082` | `http://localhost:8082/actuator/health` | `{"status":"UP"}` |
| **Reporting & Integration Service** | `8083` | `http://localhost:8083/actuator/health` | `{"status":"UP"}` |

> 💡 **Kiểm tra Eureka Dashboard**: Truy cập `http://localhost:8761/` trên trình duyệt để kiểm tra danh sách các instance đang được đăng ký (Registered instances). Tất cả các microservices phải ở trạng thái **UP**.

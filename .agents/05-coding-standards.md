# 05. Tiêu Chuẩn Lập Trình & Clean Code (Coding Standards)

Tài liệu này thiết lập các tiêu chuẩn viết mã nguồn Java 17/21, Spring Boot 3.x, quy chuẩn thiết kế Class, quản lý Transaction, truy vấn tối ưu Spring Data JPA và quy tắc Anti-God-Class trong dự án **InternHub (Backend)**.

---

## 1. Triết Lý Clean Code & Thiết Kế Hướng Đối Tượng

Mọi dòng code Java được sinh ra hoặc sửa đổi phải đáp ứng nghiêm ngặt:

1. **SOLID Principles**:
   - **Single Responsibility (SRP)**: Mỗi class, interface hoặc method chỉ giải quyết duy nhất một nhiệm vụ.
   - **Open/Closed (OCP)**: Mở rộng tính năng bằng cách thêm class/method mới, hạn chế sửa đổi mã nguồn đang chạy ổn định.
   - **Dependency Inversion (DIP)**: Tầng cao phụ thuộc vào Abstraction (Interface), không phụ thuộc trực tiếp vào Implementation cụ thể.
2. **KISS & YAGNI**: Code tường minh, dễ đọc, không cài đặt thêm cấu hình phức tạp khi bài toán nghiệp vụ chưa yêu cầu.
3. **Không dùng `System.out.println`**: Bắt buộc sử dụng `@Slf4j` từ Lombok để ghi log có phân cấp rõ ràng (`log.info()`, `log.warn()`, `log.error()`, `log.debug()`).

---

## 2. Tiêu Chuẩn Anti-God-Class (Giới Hạn Độ Dài Tập Tin)

> [!CAUTION]
> **NGHIÊM CẤM TẠO CÁC CLASS NGUYÊN KHỐI (GOD CLASSES) CHỨA QUÁ NHIỀU TRÁCH NHIỆM.**

### 2.1. Giới Hạn Chiều Dài File (Class Length Ceiling)
- Khuyến nghị mọi file `.java` (Controller, ServiceImpl, Repository, DTO) **không dài quá 200 - 300 dòng code**.
- Khi một class `ServiceImpl` có xu hướng vượt quá 300 dòng, bắt buộc phải phân rã thành các Service chuyên trách nhỏ hơn (ví dụ: `InternProfileService`, `InternDocumentService`, `InternEvaluationService`) hoặc tách các Helper/Validator riêng.

### 2.2. Giới Hạn Độ Dài Hàm (Method Length Ceiling)
- Mỗi method không nên dài quá **30 - 40 dòng code**.
- Các khối logic lồng ghép phức tạp (vòng lặp lồng nhau, kiểm tra điều kiện dày đặc) phải được trích xuất thành các private helper methods có tên gọi rõ ràng mang tính tự giải thích (Self-explanatory).

---

## 3. Quản Lý Import & Cấu Trúc File Java

### 3.1. Import Tường Minh - Cấm Sử Dụng FQN
- **Bắt buộc**: Luôn khai báo câu lệnh `import` tường minh ở đầu file cho mọi class, entity, DTO, annotation hay utility.
- **TUYỆT ĐỐI KHÔNG** viết Fully Qualified Name (FQN) trực tiếp trong thân mã nguồn:
  ```java
  // ❌ SAI: Dùng FQN trong code
  org.example.internservice.intern.entity.Intern intern = new org.example.internservice.intern.entity.Intern();

  // ✅ ĐÚNG: Import ở đầu file
  import org.example.internservice.intern.entity.Intern;

  // Trong thân class:
  Intern intern = new Intern();
  ```

### 3.2. Thứ Tự Import 4 Tầng Chuẩn Hóa
Thứ tự import từ trên xuống dưới trong mọi file `.java`:
```java
// Tầng 1: Java Core Libraries
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Tầng 2: Jakarta EE / Validation
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// Tầng 3: Third-party Frameworks (Spring, Lombok, SLF4J)
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Tầng 4: Internal Project Packages
import org.example.internservice.common.dto.ApiResponse;
import org.example.internservice.intern.service.InternService;
```

---

## 4. Quy Chuẩn Dependency Injection (DI)

- **Bắt buộc sử dụng Constructor Injection**:
  - Khai báo các dependency dưới dạng `private final`.
  - Sử dụng annotation `@RequiredArgsConstructor` từ Lombok ở cấp độ Class để tự động sinh constructor tương ứng:
    ```java
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class InternServiceImpl implements InternService {

        private final InternRepository internRepository;
        private final UserClient userClient;
        // ...
    }
    ```
- **TUYỆT ĐỐI CẤM SỬ DỤNG `@Autowired` TRÊN FIELD**:
  - Việc tiêm phụ thuộc trực tiếp trên field vi phạm tính đóng gói, gây khó khăn cho việc viết Unit Test bằng Mockito và làm che giấu sự phình to của class.

---

## 5. Quy Chuẩn Spring Data JPA & Cơ Sở Dữ Liệu

### 5.1. Kế Thừa `BaseEntity` Cho 100% JPA Entities
- Tất cả JPA Entities trong hệ thống bắt buộc phải kế thừa `BaseEntity` từ `common/entity/BaseEntity.java`.
- `BaseEntity` tự động quản lý các trường dùng chung:
  - `Long id` (`@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`)
  - `LocalDateTime createdAt`
  - `LocalDateTime updatedAt`
  - Lifecycle hooks `@PrePersist` và `@PreUpdate` để tự động cập nhật mốc thời gian.

### 5.2. Quy Tắc Đặt Tên Bảng & Cột Trong Database
- **Tên bảng**: Chữ thường, số nhiều, snake_case (ví dụ: `intern_profiles`, `training_programs`, `evaluations`).
- **Tên cột**: Chữ thường, snake_case (ví dụ: `first_name`, `phone_number`, `created_at`).
- **Khóa ngoại**: `<tên_bảng_số_ít>_id` (ví dụ: `program_id`, `mentor_id`, `user_id`).

### 5.3. Sử Dụng Annotation Lombok Trên Entity Hợp Lý
- **Được khuyến nghị**: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.
- **Cảnh báo nguy cơ khi dùng `@Data`**:
  - Tránh dùng `@Data` hoặc `@ToString` trên các Entity có quan hệ hai chiều (`@OneToMany` ↔ `@ManyToOne`).
  - Việc này sẽ gây lỗi tràn bộ nhớ `StackOverflowError` do đệ quy vô hạn trong các phương thức `hashCode()`, `equals()` và `toString()`.
  - Nếu cần `toString()`, loại trừ thuộc tính quan hệ bằng `@ToString.Exclude`.

### 5.4. Phòng Chống Triệt Để Lỗi N+1 Query
- **Nguyên nhân**: Khi tải một danh sách Entity chứa quan hệ `@ManyToOne` hoặc `@OneToMany`, Hibernate mặc định gửi thêm N truy vấn con để lấy dữ liệu quan hệ.
- **Giải pháp bắt buộc**:
  1. Sử dụng `JOIN FETCH` trong câu truy vấn JPQL:
     ```java
     @Query("SELECT i FROM Intern i JOIN FETCH i.program WHERE i.status = :status")
     List<Intern> findAllWithProgramByStatus(@Param("status") InternStatus status);
     ```
  2. Sử dụng `@EntityGraph`:
     ```java
     @EntityGraph(attributePaths = {"program", "mentor"})
     Optional<Intern> findWithDetailsById(Long id);
     ```
  3. Sử dụng DTO Projections khi chỉ cần lấy một số trường cụ thể phục vụ báo cáo/hiển thị bảng.

---

## 6. Quản Lý Giao Dịch (`@Transactional`)

- **Class Level (ServiceImpl)**:
  - Luôn đánh dấu `@Transactional(readOnly = true)` ở cấp độ Class trong `ServiceImpl`. Việc này tối ưu hóa Hibernate Session (bỏ qua dirty checking) và tăng hiệu năng truy vấn dữ liệu đọc.
- **Method Level**:
  - Đánh dấu `@Transactional` tường minh trên các method thực hiện thao tác thay đổi dữ liệu (`create`, `update`, `delete`, `changeStatus`).
  - Nếu xảy ra RuntimeException, toàn bộ giao dịch sẽ được tự động rollback để bảo vệ toàn vẹn dữ liệu:
    ```java
    @Service
    @RequiredArgsConstructor
    @Transactional(readOnly = true)
    public class InternServiceImpl implements InternService {

        @Override
        @Transactional
        public InternResponse createIntern(CreateInternRequest request) {
            // Logic ghi dữ liệu...
        }
    }
    ```

---

## 7. Phân Tách DTO & Quy Chuẩn REST Controller

1. **Tuyệt đối không trả JPA Entity ra Controller**:
   - Luôn map Entity sang Response DTO trước khi trả về Client để tránh lộ cấu trúc DB hoặc gây lỗi serialization quan hệ Lazy loading.
2. **Tách biệt Request DTO và Response DTO**:
   - `CreateInternRequest`, `UpdateInternRequest` nằm tại `<feature>/dto/request/`.
   - `InternResponse`, `InternSummaryResponse` nằm tại `<feature>/dto/response/`.
3. **Đóng gói Response chuẩn hóa**:
   - Mọi API endpoint thành công đều phải bọc kết quả trong `ApiResponse<T>`:
     ```java
     @PostMapping
     public ResponseEntity<ApiResponse<InternResponse>> createIntern(@Valid @RequestBody CreateInternRequest request) {
         InternResponse response = internService.createIntern(request);
         return ResponseEntity.status(HttpStatus.CREATED)
                 .body(ApiResponse.success(response, "Tạo mới hồ sơ thực tập sinh thành công"));
     }
     ```

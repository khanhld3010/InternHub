# Specification: [Tên Tính Năng Bằng Tiếng Việt] ([English Feature Name])

> **Trạng thái:** DRAFT / IN_REVIEW / APPROVED / IMPLEMENTED  
> **Lưu trữ tại:** `InternHub/docs/specs/<mã-task>-<tên-tính-năng>-spec.md`  
> **Áp dụng quy tắc:** [Persistent Spec & Change Rationale](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/04-development-guide.md)

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất.

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | YYYY-MM-DD | [Tên / Agent] | `TM-X` | Tạo mới | Thiết kế đặc tả ban đầu cho tính năng. |
| **v1.1** | YYYY-MM-DD | [Tên / Agent] | `TM-Y` | Sửa logic / API / DB | [Nêu rõ nguyên nhân thay đổi và căn cứ kỹ thuật/nghiệp vụ]. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** [Tên tiếng Việt] ([English Name])
- **Jira Ticket:** [TM-X](https://robluccibn9935.atlassian.net/browse/TM-X)
- **Target Microservice:** `identity-and-access-service` / `intern-and-program-service` / `reporting-and-integration-service` / `api-gateway`
- **Target Users & Roles:** `ADMIN` / `HR` / `MENTOR` / `INTERN`
- **Change Level:** **L2** (API mở rộng) / **L3** (Tính năng mới, tác động DB/Auth) / **L4** (Kiến trúc, Migration)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
1. **[Mục tiêu 1]:** [Mô tả vấn đề thực tế cần giải quyết].
2. **[Mục tiêu 2]:** [Lợi ích mang lại cho hệ thống hoặc người dùng].
3. **[Mục tiêu 3]:** [Khả năng mở rộng / tích hợp trong tương lai].

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- [Hành vi / API / Logic 1]
- [Hành vi / API / Logic 2]
- [Validate dữ liệu đầu vào cụ thể]

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)
- **[Những gì tuyệt đối KHÔNG LÀM trong ticket này]**
- [Tính năng thuộc ticket khác, ví dụ TM-X]

---

## 4. Potential Logic Loopholes & Mitigations (Tối thiểu 5 Edge Cases Cốt Lõi)

### 4.1. Case 1: [Trùng lặp dữ liệu / Unique Constraint]
- **Vấn đề:** [Mô tả tình huống xảy ra lỗi]
- **Giải pháp:** [Kiểm tra trước ở Service, bắt ngoại lệ DuplicateResourceException, trả về HTTP 409 Conflict]

### 4.2. Case 2: [Concurrency / Double-submit]
- **Vấn đề:** [Hai request gửi đồng thời]
- **Giải pháp:** [Xử lý qua Database Unique Index / Giao dịch @Transactional]

### 4.3. Case 3: [Dữ liệu biên / Format ngày tháng / Nullable]
- **Vấn đề:** [Client gửi định dạng sai hoặc để trống]
- **Giải pháp:** [Bean Validation @NotNull, @Pattern, @JsonFormat]

### 4.4. Case 4: [Xung đột trạng thái / State Machine Conflict]
- **Vấn đề:** [Ví dụ: Cập nhật trạng thái không hợp lệ từ APPROVED về PENDING]
- **Giải pháp:** [Validate chuyển đổi trạng thái ở tầng Service]

### 4.5. Case 5: [Bảo mật & Phân quyền / Unauthorized Access]
- **Vấn đề:** [Người dùng truy cập dữ liệu của người khác hoặc sai vai trò]
- **Giải pháp:** [@PreAuthorize("hasRole('ADMIN') or hasRole('HR')")]

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)
- **FR-1:** Hệ thống cung cấp API cho phép...
- **FR-2:** Khi người dùng gửi yêu cầu hợp lệ, hệ thống sẽ...
- **FR-3:** Khi dữ liệu không hợp lệ, hệ thống trả về thông báo lỗi tiếng Việt cụ thể...

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)
- **BR-1:** [Quy tắc ràng buộc 1, ví dụ: Mã thực tập sinh tự động sinh theo mẫu INT-YYYY-XXXX]
- **BR-2:** [Quy tắc ràng buộc 2, ví dụ: Email không được trùng lặp trong toàn bộ bảng]
- **BR-3:** [Quy tắc tính toán hoặc điều kiện chuyển trạng thái]

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Cấu Trúc Bảng MySQL (`[table_name]`)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | Định danh kế thừa từ `BaseEntity` |
| `created_at` | `DATETIME` | `NOT NULL` | Kế thừa từ `BaseEntity` |
| `updated_at` | `DATETIME` | `NULL` | Kế thừa từ `BaseEntity` |
| `...` | `...` | `...` | `...` |

### 7.2. JPA Entity Mapping
- Kế thừa: `common/entity/BaseEntity.java`
- Annotations: `@Entity`, `@Table(name = "...")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- *Lưu ý: Không dùng `@Data` nếu có quan hệ hai chiều.*

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### Endpoint: `[METHOD] /api/v1/[resources]`
- **Authentication:** `Bearer Token` (Header `Authorization: Bearer <jwt>`)
- **Phân quyền:** `@PreAuthorize("hasAnyRole('ADMIN', 'HR')")`

#### Request Headers:
```http
Content-Type: application/json
Authorization: Bearer eyJhbGciOi...
```

#### Request Body JSON:
```json
{
  "field1": "value",
  "field2": 123
}
```

#### Response Success (200 OK / 201 Created):
```json
{
  "success": true,
  "message": "Thực hiện thành công",
  "data": {
    "id": 1,
    "field1": "value"
  },
  "timestamp": "2026-09-23T08:00:00"
}
```

#### Response Errors (400 / 404 / 409):
```json
{
  "success": false,
  "message": "Thông điệp lỗi chi tiết bằng tiếng Việt",
  "errors": ["Lỗi cụ thể 1", "Lỗi cụ thể 2"],
  "timestamp": "2026-09-23T08:00:00"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)
```text
Client ➔ API Gateway (8080) ➔ Controller (@Valid) ➔ Service (@Transactional) ➔ Repository ➔ MySQL DB
```
1. **Bước 1:** Client gửi request qua Gateway, Gateway chuyển tiếp kèm header danh tính.
2. **Bước 2:** Controller nhận Request DTO, kiểm tra `@Valid`. Nếu sai, quăng `MethodArgumentNotValidException`.
3. **Bước 3:** Service kiểm tra nghiệp vụ độc nhất, trạng thái và logic xử lý.
4. **Bước 4:** Lưu vào Database qua Repository.
5. **Bước 5:** Map sang Response DTO và bọc trong `ApiResponse<T>`.

---

## 10. Non-Functional Requirements & Constraints
- **Performance:** Thời gian phản hồi API < 200ms với 95th percentile.
- **JPA N+1 Prevention:** Dùng `JOIN FETCH` hoặc `@EntityGraph` cho các quan hệ Lazy.
- **Anti-God-Class:** Class Controller/Service không vượt quá 200 - 300 dòng code.
- **Transaction:** Đặt `@Transactional(readOnly = true)` tại Class, `@Transactional` tại method ghi.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)
- [ ] **AC-1:** Gửi request hợp lệ nhận về HTTP 200/201 kèm dữ liệu đúng định dạng `ApiResponse<T>`.
- [ ] **AC-2:** Gửi request thiếu trường bắt buộc nhận về HTTP 400 kèm thông báo lỗi rõ ràng.
- [ ] **AC-3:** Gửi dữ liệu trùng lặp nhận về HTTP 409 Conflict.
- [ ] **AC-4:** Token sai quyền nhận về HTTP 403 Forbidden.

---

## 12. Unit & Integration Test Cases Checklist
- [ ] **UT-BE-01:** `givenValidRequest_whenExecute_thenReturnSuccessResponse()`
- [ ] **UT-BE-02:** `givenDuplicateResource_whenExecute_thenThrowDuplicateResourceException()`
- [ ] **IT-BE-01:** Kiểm tra toàn bộ luồng qua MockMvc (`@WebMvcTest` hoặc `@SpringBootTest`).

---

## 13. Implementation Checklist (Danh Sách File Triển Khai)
- [ ] **Entity:** `.../entity/FeatureEntity.java` (kế thừa `BaseEntity`)
- [ ] **DTOs:** `.../dto/request/FeatureRequest.java`, `.../dto/response/FeatureResponse.java`
- [ ] **Repository:** `.../repository/FeatureRepository.java`
- [ ] **Service:** `.../service/FeatureService.java`, `.../service/impl/FeatureServiceImpl.java`
- [ ] **Controller:** `.../controller/FeatureController.java`
- [ ] **Tests:** `.../service/impl/FeatureServiceImplTest.java`

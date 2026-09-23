# 04. Hướng Dẫn Phát Triển Tính Năng & Đặc Tả Kỹ Thuật (Development Guide)

Tài liệu này hướng dẫn chi tiết quy trình phát triển một tính năng mới trong **InternHub (Backend)** theo triết lý **Spec-Driven Development** (kết hợp cấu trúc đặc tả chuẩn 13 phần) và quy trình triển khai từng bước theo mô hình **Package-by-Feature**.

---

## 1. Triết Lý Cốt Lõi: Spec → Plan → Tasks → Implement & Converge

Mọi tính năng hoặc thay đổi nghiệp vụ quan trọng đều phải tuân theo chu trình chuẩn hóa:

```
[1. Đặc Tả: Spec.md] 
         ↓
[2. Kế Hoạch Kỹ Thuật: Plan.md] 
         ↓
[3. Danh Sách Nhiệm Vụ: Tasks.md] 
         ↓
[4. Triển Khai Mã Nguồn: Implement] 
         ↓
[5. Đối Chiếu & Nghiệm Thu: Converge]
```

1. **Spec (Cần đạt điều gì):** Đứng từ góc độ nghiệp vụ và người dùng. Xác định rõ ràng phạm vi (In Scope / Out of Scope), ràng buộc dữ liệu, và điều kiện biên.
2. **Plan (Làm như thế nào):** Bản đồ kỹ thuật kết nối Spec vào hệ thống Microservices hiện có. Xác định rõ microservice nào chịu trách nhiệm, file nào được tạo/sửa, luồng transaction và rủi ro.
3. **Tasks (Từng bước cụ thể):** Chia nhỏ thành các đầu mục độc lập có thể kiểm tra và review trong một lần diff.
4. **Implement & Converge (Thực thi & Đối chiếu):** Sau khi code xong, **bắt buộc đối chiếu từng tiêu chí chấp nhận (Acceptance Criteria)** với bằng chứng kiểm thử thực tế.

---

## 2. Phân Cấp Mức Độ Thay Đổi & Cổng Kiểm Soát

| Cấp độ | Loại thay đổi | Tài liệu yêu cầu | Cổng kiểm soát (Gateways) |
| :---: | :--- | :--- | :--- |
| **L1** | Sửa log message, sửa lỗi chính tả comment, format code nhỏ (< 10 dòng) | Giải trình ngắn gọn trong câu trả lời & commit message | Chạy compile và kiểm tra (miễn trừ cập nhật Spec) |
| **L2** | Thêm API mới trong feature quen thuộc (ví dụ thêm filter, validate) | `implementation_plan.md` + **Cập nhật Spec & Revision History** | Người dùng phê duyệt trước khi code |
| **L3** | Tính năng mới hoàn toàn, tác động Database, Auth, Role | **`spec.md` (lưu tại `docs/specs/`)** + `implementation_plan.md` | Cổng làm rõ câu hỏi, duyệt Spec và Plan |
| **L4** | Thay đổi kiến trúc, DB Migration, sửa đổi Gateway/Config Server | Bộ tài liệu đầy đủ (`spec.md` tại `docs/specs/`) + Rollback Plan | Người dùng phê duyệt phương án chi tiết |

---

## 3. Cấu Trúc Tài Liệu Đặc Tả Chuẩn 14 Phần (`spec.md`)

Mọi tài liệu đặc tả tính năng (`spec.md`) của Backend bắt buộc phải được lưu cố định tại thư mục:
👉 `InternHub/docs/specs/<mã-task>-<tên-tính-năng>-spec.md` (Ví dụ: `docs/specs/TM-1-create-intern-profile-spec.md`).

Tài liệu `spec.md` chuẩn hóa gồm 14 phần (bắt đầu bằng Bảng Nhật ký thay đổi):

0. **Revision History & Change Rationale (Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật):** Bảng ghi nhận lịch sử phiên bản, ngày, người/agent thực hiện, mã task Jira `TM`, loại thay đổi và **lý do kỹ thuật/nghiệp vụ cụ thể (Rationale)** vì sao cần thay đổi.
1. **Feature Overview (Tổng Quan Tính Năng):** Tên tính năng, Jira ticket (`TM-X`), Target Microservice, Phân quyền người dùng áp dụng, Cấp độ thay đổi (L1 - L4).
2. **Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ):** Bối cảnh thực tế và vấn đề cốt lõi cần giải quyết.
3. **Scope of Work (Phạm Vi Tính Năng):**
   - *In Scope:* Các hành vi, API endpoints, logic tính toán cụ thể.
   - *Out of Scope:* Những gì tuyệt đối KHÔNG LÀM để tránh over-engineering.
4. **Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases):** Tối thiểu 5 edge cases cốt lõi (concurrency, validation, failure handling, data mismatch, security/access control...).
5. **Functional Requirements (Yêu Cầu Chức Năng):** FR-1, FR-2,... liệt kê các chức năng hệ thống cung cấp.
6. **Business Rules (Quy Tắc Nghiệp Vụ):** BR-1, BR-2,... quy tắc tính toán, ràng buộc trạng thái, kiểm tra tính toàn vẹn.
7. **Data Model (Mô Hình Dữ Liệu):** Cấu trúc bảng MySQL, các quan hệ `@OneToMany`, `@ManyToOne`, indexes, constraints, Java Entity mapping kế thừa `BaseEntity`.
8. **API Contract (Đặc Tả Giao Tiếp REST API):** HTTP Method, URL Endpoint, Headers, Request Body JSON, Response 200/201 JSON mẫu, mã lỗi và định dạng `ApiResponse<T>`.
9. **Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi):** Luồng xử lý chi tiết từng bước, service layer sequence, xử lý transaction và rollback.
10. **Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng):** Tech stack, Database constraints, Performance, Caching, Security & Role-based Access Control, Audit logging.
11. **Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận):** AC-1, AC-2,... đo lường và quan sát được (testable).
12. **Unit & Integration Test Cases Checklist:** Danh sách test method cụ thể cho Service (`UT-BE-XX`) và Integration/Controller (`IT-BE-XX`).
13. **Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai):** Checklist chi tiết từng Entity, DTO, Repository, Service, Controller, Exception handler, Tests.

---

### 3.1. Quy Tắc "Đồng Bộ Nguyên Tử" Giữa Spec & Code (Atomic Spec-Code Sync)

> [!CAUTION]
> **KHÔNG MỘT THAY ĐỔI LOGIC NÀO ĐƯỢC COI LÀ HOÀN TẤT NẾU CHƯA CẬP NHẬT SPEC VÀ GHI GIẢI TRÌNH.**
> 
> - **Khi code thay đổi**: Bất kể khi nào lập trình viên hay AI Agent sửa mã nguồn (thêm/bớt validation, đổi trường DTO, thay đổi câu query JPA, đổi flow xử lý Service):
>   1. **Bắt buộc mở file Spec tương ứng tại `docs/specs/`** để cập nhật nội dung phản ánh đúng code mới.
>   2. **Ghi thêm 1 dòng vào bảng Revision History** giải thích cặn kẽ: *Tại sao phải sửa đổi? Quyết định kỹ thuật dựa trên căn cứ nào?*
> - **Đồng bộ trong cùng một task**: Không tách rời việc sửa code và sửa Spec thành hai lần làm việc riêng biệt. Phải đồng bộ ngay trong cùng một PR/phiên làm việc.

## 4. Quy Trình 7 Bước Triển Khai Code Theo Chuẩn Package-by-Feature

Khi bắt tay vào viết code cho một feature mới trong microservice tương ứng, Agent bắt buộc tuân theo thứ tự 7 bước:

```
[Bước 1: Entity & BaseEntity] 
              ↓
[Bước 2: Request & Response DTOs] 
              ↓
[Bước 3: Spring Data JPA Repository] 
              ↓
[Bước 4: Service Interface & ServiceImpl] 
              ↓
[Bước 5: REST Controller & Validation] 
              ↓
[Bước 6: Global Exception Handling] 
              ↓
[Bước 7: Unit Testing & Verification]
```

### Bước 1: Thiết kế Entity kế thừa `BaseEntity`
- Nằm trong `<feature>/entity/`.
- Bắt buộc kế thừa `BaseEntity` (`id`, `createdAt`, `updatedAt`, `@PrePersist`, `@PreUpdate`).
- Dùng `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.
- Tránh dùng `@Data` nếu entity có quan hệ `@OneToMany` hoặc `@ManyToOne` hai chiều.

### Bước 2: Xây dựng Request & Response DTOs
- Nằm trong `<feature>/dto/request/` và `<feature>/dto/response/`.
- Request DTO: Áp dụng đầy đủ annotation validation từ `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@Email`, `@Min`, `@Max`).
- Response DTO: Chỉ chứa các trường cần thiết trả về cho Client, **tuyệt đối không để lộ mật khẩu, hash hay trường nhạy cảm**.

### Bước 3: Xây dựng Repository
- Nằm trong `<feature>/repository/`.
- Kế thừa `JpaRepository<Entity, Long>` và `JpaSpecificationExecutor<Entity>` (nếu cần lọc động).
- Sử dụng `@Query` kết hợp `JOIN FETCH` hoặc `@EntityGraph` khi cần lấy dữ liệu quan hệ để chống lỗi N+1 Query.

### Bước 4: Xây dựng Service Interface & ServiceImpl
- Nằm trong `<feature>/service/` và `<feature>/service/impl/`.
- Đánh dấu `@Service` và `@RequiredArgsConstructor` (Constructor Injection).
- Cấp Class: Đánh dấu `@Transactional(readOnly = true)`.
- Các method ghi/sửa/xóa: Đánh dấu `@Transactional` tường minh.
- Chuyển đổi giữa Entity và DTO (qua Builder hoặc Mapper).

### Bước 5: Xây dựng REST Controller
- Nằm trong `<feature>/controller/`.
- Đánh dấu `@RestController`, `@RequestMapping("/api/...")`, `@RequiredArgsConstructor`.
- Nhận request body với `@Valid @RequestBody RequestDTO request`.
- **Tuyệt đối không viết logic nghiệp vụ tại Controller**.
- Trả về chuẩn: `ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(data, "Thông báo"));`.

### Bước 6: Đăng ký Exception Handler
- Khi có lỗi nghiệp vụ (ví dụ: `InternNotFoundException`, `DuplicateEmailException`), ném custom exception.
- Bắt ngoại lệ tại `exception/GlobalExceptionHandler.java` và chuyển thành `ResponseEntity<ApiResponse<Void>>` kèm mã lỗi thích hợp (`400`, `404`, `409`).

### Bước 7: Kiểm thử & Xác minh
- Viết Unit Test cho Service với Mockito (`UT-BE-XX`).
- Chạy lệnh Gradle kiểm tra biên dịch:
  ```powershell
  .\gradlew :<service-folder>:compileJava
  .\gradlew :<service-folder>:test
  ```
- Kiểm tra trực tiếp API với cơ sở dữ liệu thực tế.

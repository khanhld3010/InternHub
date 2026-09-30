# Implementation Plan: TM-14 Xác Nhận Hợp Đồng Thực Tập (Internship Contract Confirmation by Intern)

> **Mã công việc:** TM-14  
> **Nhánh Git:** `feature/TM-14/contract-confirm`  
> **Tài liệu đặc tả:** [TM-14 Spec v1.0](file:///d:/Module_6/InternHub/docs/specs/TM-14-contract-confirm-spec.md)  
> **Phạm vi tác động:** `intern-and-program-service` (Backend Only)

---

## 1. Mục Tiêu (Objective)
Triển khai toàn diện tính năng cho phép Thực tập sinh (`ROLE_INTERN`) xem danh sách hợp đồng của mình, xem chi tiết hợp đồng, xác nhận ký hợp đồng (chuyển đổi hợp đồng sang `SIGNED` và hồ sơ `InternProfile` sang `INTERNING`), hoặc từ chối hợp đồng kèm lý do (`REJECTED_BY_INTERN`). Đảm bảo kiểm soát an ninh chống lỗ hổng IDOR, kiểm tra thời hạn và ràng buộc trạng thái State Machine theo đúng quy chuẩn dự án.

---

## 2. Danh Sách Tệp Tác Động (Impacted Files)

### 2.1. Cập nhật Model & Enum:
- `[MODIFY]` [ContractStatus.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/ContractStatus.java): Bổ sung trạng thái `REJECTED_BY_INTERN`.
- `[MODIFY]` [AuditAction.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/system/audit/entity/AuditAction.java): Bổ sung `CONFIRM_CONTRACT`, `REJECT_CONTRACT`.
- `[MODIFY]` [InternContract.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternContract.java): Bổ sung các cột `signerFullName`, `internConfirmationNote`, `rejectionReason`.

### 2.2. DTOs:
- `[NEW]` `ConfirmContractRequest.java`: DTO xác nhận ký với validate `@AssertTrue agreeTerms`, `@NotBlank @Size(2, 100) signerFullName`, `@Size(max = 1000) confirmationNote`.
- `[NEW]` `RejectContractRequest.java`: DTO từ chối với validate `@NotBlank @Size(10, 1000) rejectionReason`.
- `[MODIFY]` [ContractResponse.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/ContractResponse.java): Bổ sung các trường `signerFullName`, `internConfirmationNote`, `rejectionReason`, `internProfileStatus`.

### 2.3. Repository:
- `[MODIFY]` [InternContractRepository.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternContractRepository.java): Thêm truy vấn tối ưu nạp hợp đồng theo `userId` hoặc `email` (`findAllByUserIdOrEmailWithProfile`).

### 2.4. Service & Controller:
- `[MODIFY]` [InternContractService.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/InternContractService.java): Định nghĩa các phương thức nghiệp vụ mới.
- `[MODIFY]` [InternContractServiceImpl.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternContractServiceImpl.java): Xử lý toàn vẹn dữ liệu, chống IDOR, kiểm tra thời hạn, chuyển trạng thái atomic trong `@Transactional`.
- `[MODIFY]` [InternContractController.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/InternContractController.java): Bổ sung 5 endpoints RESTful với phân quyền Spring Security và Swagger documentation.

### 2.5. Unit Testing:
- `[MODIFY]` [InternContractServiceTest.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/test/java/org/example/internservice/intern/service/InternContractServiceTest.java): Bổ sung 10 unit test cases bao phủ từ UT-BE-01 đến UT-BE-10.

---

## 3. Kế Hoạch Triển Khai Chi Tiết (Implementation Steps)

### Bước 1: Mở rộng Model, Enum & DTOs
- Bổ sung `REJECTED_BY_INTERN` vào `ContractStatus.java`.
- Bổ sung `CONFIRM_CONTRACT` và `REJECT_CONTRACT` vào `AuditAction.java`.
- Bổ sung 3 trường vào `InternContract.java`:
  - `signerFullName` (`VARCHAR(100)`)
  - `internConfirmationNote` (`TEXT`)
  - `rejectionReason` (`TEXT`)
- Tạo 2 Request DTOs: `ConfirmContractRequest` và `RejectContractRequest`.
- Cập nhật `ContractResponse` để phản ánh đầy đủ thông tin chữ ký và trạng thái của `InternProfile`.

### Bước 2: Tối ưu hóa Repository
- Thêm query `findAllByUserIdOrEmailWithProfile` trong `InternContractRepository` sử dụng `JOIN FETCH c.internProfile` để tránh lỗi N+1 Query.

### Bước 3: Triển khai Business Logic tại Service Layer
- Viết hàm kiểm tra quyền sở hữu IDOR: `verifyContractOwnership(InternContract contract, CustomUserDetails userDetails)`.
- Triển khai `getMyContracts(CustomUserDetails userDetails)`: Tra cứu danh sách hợp đồng của TTS đang đăng nhập.
- Triển khai `getMyActiveContract(CustomUserDetails userDetails)`: Tìm hợp đồng đang `PENDING_SIGNATURE` hoặc hợp đồng gần nhất.
- Triển khai `getContractById(Long contractId, CustomUserDetails userDetails)`: Kiểm tra quyền sở hữu nếu người gọi là `INTERN`, cho phép `HR`/`ADMIN`/`MENTOR` xem.
- Triển khai `confirmContract(Long contractId, ConfirmContractRequest request, CustomUserDetails userDetails)`:
  - Kiểm tra trạng thái: Phải là `PENDING_SIGNATURE`.
  - Kiểm tra hạn: `LocalDate.now().isAfter(contract.getEndDate())` $\rightarrow$ set `EXPIRED` và ném `BadRequestException`.
  - Chuyển `contract.setStatus(SIGNED)`, cập nhật `signedAt = LocalDateTime.now()`, `signerFullName`, `internConfirmationNote`.
  - Nếu `internProfile.getStatus() == APPROVED`: chuyển thành `INTERNING`.
- Triển khai `rejectContract(Long contractId, RejectContractRequest request, CustomUserDetails userDetails)`:
  - Kiểm tra trạng thái: Phải là `PENDING_SIGNATURE`.
  - Chuyển `contract.setStatus(REJECTED_BY_INTERN)`, cập nhật `rejectionReason`.

### Bước 4: Xây dựng REST API Endpoints tại Controller
- `GET /api/interns/contracts/my-contracts` (`hasRole('INTERN')`)
- `GET /api/interns/contracts/my-contracts/active` (`hasRole('INTERN')`)
- `GET /api/interns/contracts/{contractId}` (`hasAnyRole('INTERN', 'HR', 'ADMIN', 'MENTOR')`)
- `POST /api/interns/contracts/{contractId}/confirm` (`hasRole('INTERN')`)
- `POST /api/interns/contracts/{contractId}/reject` (`hasRole('INTERN')`)
- Tích hợp `@Auditable` cho các thao tác tác động dữ liệu.

### Bước 5: Unit Testing & Biên Dịch
- Viết đầy đủ 10 unit test cases trong `InternContractServiceTest.java`.
- Chạy kiểm tra biên dịch và test:
  ```powershell
  .\gradlew :intern-and-program-service:compileJava
  .\gradlew :intern-and-program-service:test --tests "org.example.internservice.intern.service.InternContractServiceTest"
  ```
- Cập nhật tài liệu Spec và bảng Revision History.

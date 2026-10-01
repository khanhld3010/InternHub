# Specification: Hệ Thống Phân Quyền Động (Dynamic Role & Permission Authorization System)

> **Trạng thái:** IMPLEMENTED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-31-dynamic-role-permission-authorization-spec.md`  
> **Áp dụng quy tắc:** [Persistent Spec & Change Rationale](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/04-development-guide.md)  
> **Mã Task Jira:** `TM-31`

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-10-01 | Senior Backend AI Pair-Programmer | `TM-31` | Tạo mới | Thiết kế đặc tả kỹ thuật 13 phần cho hệ thống phân quyền động (Dynamic RBAC) đáp ứng nhu cầu quản trị vai trò, ma trận đặc quyền và bảo mật hạt mịn cho InternHub. |
| **v1.1** | 2026-10-01 | Senior Backend AI Pair-Programmer | `TM-31` | Hoàn tất triển khai | Triển khai hoàn chỉnh toàn bộ mã nguồn Backend: Entity Permission, Role, DTOs, Services, Controllers, JwtTokenProvider, DataInitializer, API Gateway routes và bộ Unit Tests đạt 100% tỷ lệ vượt qua. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Phân Quyền Động Theo Vai Trò & Đặc Quyền (Dynamic Role-Based & Permission-Based Access Control)
- **Jira Ticket:** `TM-31`
- **Target Microservice:** `identity-and-access-service` (Service nghiệp vụ cốt lõi), `api-gateway` (Định tuyến)
- **Target Users & Roles:** 
  - `ADMIN`: Toàn quyền quản trị danh mục vai trò, cấu hình phân quyền permissions cho từng vai trò, phân quyền cho người dùng.
  - `Tất cả người dùng`: Nhận token có chứa danh sách permissions động và tra cứu quyền hạn cá nhân qua API.
- **Change Level:** **L3** (Tính năng mới, tác động cấu trúc DB Schema, JWT Claims và Spring Security Context).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
1. **Chuyển đổi từ phân quyền tĩnh sang phân quyền động (Dynamic RBAC)**: Loại bỏ sự phụ thuộc vào các role cứng trong mã nguồn, cho phép Admin tạo mới hoặc điều chỉnh danh sách quyền của bất kỳ vai trò nào trong hệ thống mà không cần build hay deploy lại mã nguồn.
2. **Kiểm soát bảo mật hạt mịn (Granular Permissions)**: Mỗi hành động nghiệp vụ (xem, tạo, sửa, xóa, duyệt, phân công mentor, sao lưu...) được gắn liền với một mã quyền (`permission code`), giúp doanh nghiệp phân định trách nhiệm rõ ràng giữa các phòng ban.
3. **Cung cấp API cho Frontend hiển thị linh hoạt**: Giúp giao diện Client (React) tự động ẩn/hiện menu, tab, và các nút thao tác tương ứng chính xác với các đặc quyền mà tài khoản đang sở hữu thông qua API `GET /api/auth/me/permissions`.
4. **Bảo toàn dữ liệu & tương thích ngược 100%**: Không phá hủy dữ liệu của các tài khoản và 4 vai trò mặc định (`Admin`, `HR`, `Mentor`, `Intern`), giữ nguyên khả năng hoạt động của các API cũ.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- Tạo Entity `Permission` và mở rộng Entity `Role` với quan hệ Many-to-Many (`role_permissions`).
- Cung cấp danh mục 16 đặc quyền chuẩn hóa phân theo 6 nhóm module: `USER`, `ROLE`, `INTERN`, `PROGRAM`, `CONTRACT`, `SYSTEM`.
- Tự động nạp dữ liệu ban đầu (Seed Data) cho bảng permissions và thiết lập quyền mặc định cho 4 vai trò có sẵn thông qua `DataInitializer`.
- Cập nhật cơ chế sinh JWT Token để đính kèm `permissions` vào Claims.
- Cập nhật `CustomUserDetailsService` để nạp danh sách permissions vào `GrantedAuthority` của Spring Security Context.
- Xây dựng trọn bộ RESTful API CRUD Vai trò (`/api/system/roles`) và tra cứu Danh mục quyền (`/api/system/permissions`).
- Xây dựng API tra cứu quyền hạn của user đang đăng nhập (`/api/auth/me/permissions`).
- Cập nhật route routing tại `api-gateway`.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)
- Không sửa đổi mã nguồn Frontend (`InternHub-Frontend/`) theo nguyên tắc Boundary Isolation (Rule 7).
- Không chuyển đổi mô hình 1 tài khoản nhiều role (`account_roles` Many-to-Many) trong giai đoạn này để tránh rủi ro gãy đổ liên kết tài khoản ở các service khác.
- Không can thiệp sửa trực tiếp DB bằng các lệnh SQL phá hoại.

---

## 4. Potential Logic Loopholes & Mitigations (Tối thiểu 5 Edge Cases Cốt Lõi)

### 4.1. Case 1: Xóa Role đang có người dùng hoạt động
- **Vấn đề:** Nếu Admin xóa một Role đang được gán cho 1 hoặc nhiều tài khoản trong bảng `accounts`, các tài khoản này sẽ bị lỗi ngoại lệ Foreign Key hoặc không thể đăng nhập.
- **Giải pháp:** Trước khi xóa, Service kiểm tra `accountRepository.countByRoleId(roleId) > 0`. Nếu có tài khoản đang dùng, từ chối xóa và ném `BadRequestException("Không thể xóa vai trò đang có người dùng sử dụng. Vui lòng chuyển vai trò cho người dùng trước.")`.

### 4.2. Case 2: Xóa hoặc thay đổi định danh Role cốt lõi hệ thống (`Admin`, `HR`, `Mentor`, `Intern`)
- **Vấn đề:** Các service khác (`intern-and-program-service`, `reporting-and-integration-service`) vẫn dựa trên tên các role cốt lõi này trong `@PreAuthorize`. Nếu Admin xóa hoặc đổi tên `Admin` thành tên khác, hệ thống sẽ gặp sự cố vận hành.
- **Giải pháp:** Đặt cờ `is_system = true` cho 4 role mặc định. Chặn hoàn toàn thao tác `DELETE` và chặn việc sửa đổi trường `name` đối với các role có `is_system = true`. Chỉ cho phép sửa `description` và danh sách `permissions`.

### 4.3. Case 3: Trùng lặp tên Role hoặc mã Permission
- **Vấn đề:** Admin tạo vai trò mới có tên trùng với vai trò đã tồn tại (không phân biệt hoa thường).
- **Giải pháp:** Áp dụng `UNIQUE` constraint trên cột `name` của bảng `roles` và `code` của bảng `permissions`. Tại tầng Service, kiểm tra trước bằng `roleRepository.existsByNameIgnoreCase(name)`. Nếu trùng, ném `DuplicateResourceException` và trả về HTTP `409 Conflict`.

### 4.4. Case 4: Gán mã quyền không tồn tại (Invalid Permission Codes)
- **Vấn đề:** Client gửi lên danh sách permission codes có chứa mã không có thực trong hệ thống.
- **Giải pháp:** Service truy vấn danh sách permissions theo tập mã codes gửi lên. Nếu kích thước kết quả khác với số lượng mã gửi lên, xác định các mã không hợp lệ và ném `BadRequestException("Danh sách quyền chứa mã không hợp lệ: ...")`.

### 4.5. Case 5: Độ trễ hiệu lực quyền khi cập nhật Role (Token Desync Mitigation)
- **Vấn đề:** Khi Admin vừa tước một quyền của Role `HR`, người dùng `hr` đang đăng nhập vẫn cầm Access Token cũ có chứa quyền đó cho đến khi token hết hạn.
- **Giải pháp:** Cung cấp API `GET /api/auth/me/permissions` truy vấn trực tiếp từ DB theo user ID để Frontend có thể làm mới quyền ngay lập tức sau mỗi lần chuyển route hoặc sau các thao tác quan trọng, kết hợp cơ chế Refresh Token khi token hết hạn.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)
- **FR-1:** Hệ thống cung cấp API `GET /api/system/roles` cho phép Admin xem danh sách tất cả các vai trò, kèm số lượng người dùng và số lượng quyền của từng vai trò.
- **FR-2:** Hệ thống cung cấp API `GET /api/system/roles/{id}` xem chi tiết một vai trò kèm danh sách các mã quyền đã gán.
- **FR-3:** Hệ thống cung cấp API `POST /api/system/roles` cho phép tạo vai trò mới và gán các đặc quyền ban đầu.
- **FR-4:** Hệ thống cung cấp API `PUT /api/system/roles/{id}` cho phép chỉnh sửa mô tả và cập nhật ma trận quyền của vai trò.
- **FR-5:** Hệ thống cung cấp API `DELETE /api/system/roles/{id}` cho phép xóa vai trò tùy chỉnh (có kiểm tra an toàn theo Case 1 và Case 2).
- **FR-6:** Hệ thống cung cấp API `GET /api/system/permissions` trả về danh mục quyền gom theo module (`USER`, `ROLE`, `INTERN`, `PROGRAM`, `CONTRACT`, `SYSTEM`).
- **FR-7:** Hệ thống cung cấp API `GET /api/auth/me/permissions` cho người dùng đã xác thực tra cứu vai trò và danh sách đặc quyền hiện tại của mình.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)
- **BR-1:** Tên vai trò (Role Name) chỉ được chứa chữ cái, chữ số và dấu gạch dưới, độ dài từ 2 đến 50 ký tự, tự động chuyển về dạng IN HOA.
- **BR-2:** Bốn vai trò mặc định (`ADMIN`, `HR`, `MENTOR`, `INTERN`) có `is_system = true`, không thể bị xóa và không thể đổi tên.
- **BR-3:** Vai trò `ADMIN` mặc định luôn có đủ 100% tất cả các quyền trong hệ thống.
- **BR-4:** Mã quyền (`permission code`) tuân thủ quy tắc UPPERCASE snake_case, ví dụ: `MODULE_ACTION` (ví dụ: `INTERN_APPROVE`, `USER_MANAGE`).
- **BR-5:** Mọi thay đổi về vai trò và phân quyền đều phải được ghi log kiểm toán (Audit Log) theo tiêu chuẩn của hệ thống.

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Bảng `permissions`
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `INT` | `PRIMARY KEY, AUTO_INCREMENT` | Định danh quyền |
| `code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Mã quyền (ví dụ `INTERN_VIEW`) |
| `name` | `VARCHAR(100)` | `NOT NULL` | Tên hiển thị quyền |
| `module` | `VARCHAR(50)` | `NOT NULL` | Nhóm module nghiệp vụ |
| `description` | `VARCHAR(255)` | `NULL` | Mô tả chi tiết quyền |
| `created_at` | `DATETIME` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Thời điểm tạo |
| `updated_at` | `DATETIME` | `NULL, ON UPDATE CURRENT_TIMESTAMP` | Thời điểm cập nhật |

### 7.2. Bảng `roles` (Cập nhật)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `INT` | `PRIMARY KEY, AUTO_INCREMENT` | Định danh vai trò |
| `name` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Tên vai trò |
| `description` | `VARCHAR(255)` | `NULL` | Mô tả vai trò |
| `is_system` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | Cờ đánh dấu vai trò hệ thống |
| `created_at` | `DATETIME` | `DEFAULT CURRENT_TIMESTAMP` | Thời điểm tạo |
| `updated_at` | `DATETIME` | `ON UPDATE CURRENT_TIMESTAMP` | Thời điểm cập nhật |

### 7.3. Bảng `role_permissions`
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `role_id` | `INT` | `NOT NULL, FK -> roles(id) ON DELETE CASCADE` | Khóa ngoại vai trò |
| `permission_id` | `INT` | `NOT NULL, FK -> permissions(id) ON DELETE CASCADE` | Khóa ngoại quyền |
| *Primary Key* | `(role_id, permission_id)` | `PRIMARY KEY` | Khóa chính phức hợp |

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### 8.1. `GET /api/system/roles`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")`
- **Response 200 OK:**
```json
{
  "success": true,
  "message": "Lấy danh sách vai trò thành công",
  "data": [
    {
      "id": 1,
      "name": "Admin",
      "description": "Quản trị viên toàn quyền hệ thống",
      "isSystem": true,
      "userCount": 1,
      "permissionCount": 16,
      "createdAt": "2026-10-01T08:00:00"
    }
  ],
  "timestamp": "2026-10-01T09:00:00"
}
```

### 8.2. `GET /api/system/roles/{id}`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")`
- **Response 200 OK:**
```json
{
  "success": true,
  "message": "Lấy thông tin vai trò thành công",
  "data": {
    "id": 2,
    "name": "HR",
    "description": "Chuyên viên quản lý nhân sự & tuyển dụng",
    "isSystem": true,
    "permissions": [
      "INTERN_VIEW",
      "INTERN_CREATE",
      "INTERN_EDIT",
      "INTERN_APPROVE",
      "INTERN_ASSIGN_MENTOR",
      "PROGRAM_VIEW",
      "PROGRAM_MANAGE",
      "CONTRACT_VIEW",
      "CONTRACT_MANAGE",
      "DOCUMENT_VIEW"
    ],
    "createdAt": "2026-10-01T08:00:00"
  },
  "timestamp": "2026-10-01T09:00:00"
}
```

### 8.3. `POST /api/system/roles`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")`
- **Request Body:**
```json
{
  "name": "DEPARTMENT_LEAD",
  "description": "Trưởng bộ phận chuyên môn phụ trách thực tập",
  "permissionCodes": [
    "INTERN_VIEW",
    "PROGRAM_VIEW",
    "INTERN_ASSIGN_MENTOR"
  ]
}
```
- **Response 201 Created:** Trả về `ApiResponse<RoleDetailResponse>`.

### 8.4. `PUT /api/system/roles/{id}`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")`
- **Request Body:**
```json
{
  "name": "DEPARTMENT_LEAD",
  "description": "Mô tả mới cập nhật",
  "permissionCodes": [
    "INTERN_VIEW",
    "PROGRAM_VIEW",
    "INTERN_ASSIGN_MENTOR",
    "DOCUMENT_VIEW"
  ]
}
```
- **Response 200 OK:** Trả về `ApiResponse<RoleDetailResponse>`.

### 8.5. `DELETE /api/system/roles/{id}`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")`
- **Response 200 OK:** Trả về `ApiResponse<Void>` với thông báo "Xóa vai trò thành công".

### 8.6. `GET /api/system/permissions`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")`
- **Response 200 OK:** Trả về danh sách quyền gom theo module (`List<PermissionModuleGroupResponse>`).

### 8.7. `GET /api/auth/me/permissions`
- **Header:** `Authorization: Bearer <jwt_token>`
- **Phân quyền:** `authenticated()`
- **Response 200 OK:** Trả về `ApiResponse<UserPermissionsResponse>`.

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)
```text
[Client] 
   ➔ API Gateway (8080) 
   ➔ JwtAuthenticationFilter (Giải mã JWT, trích xuất roles & permissions vào SecurityContext)
   ➔ RoleManagementController (@Valid, @PreAuthorize) 
   ➔ RoleManagementService (@Transactional) 
   ➔ RoleRepository / PermissionRepository 
   ➔ MySQL Database
```
1. **Bước 1 (Xác thực & Nạp quyền):** Client gửi request với Bearer Token. Filter giải mã Token và gán `authorities` gồm cả `ROLE_<NAME>` và từng `PERMISSION_CODE` vào `SecurityContextHolder`.
2. **Bước 2 (Kiểm tra đặc quyền):** Method Security kiểm tra người dùng có quyền `ROLE_MANAGE` hoặc vai trò `ADMIN`. Nếu không có, ném `403 Forbidden`.
3. **Bước 3 (Thực thi nghiệp vụ):** Service thực hiện kiểm tra nghiệp vụ độc nhất, kiểm tra ràng buộc vai trò hệ thống, lưu thay đổi vào MySQL trong một transaction nguyên tử.
4. **Bước 4 (Phản hồi):** Đóng gói kết quả vào `ApiResponse<T>` với HTTP status phù hợp.

---

## 10. Non-Functional Requirements & Constraints
- **Performance:** Thời gian phản hồi API tra cứu vai trò và quyền < 100ms.
- **Chống N+1 Query:** Sử dụng `LEFT JOIN FETCH` nạp permissions khi truy vấn chi tiết vai trò.
- **Anti-God-Class:** Tách biệt `RoleManagementService` và `PermissionCatalogService`, mỗi class dưới 200 dòng.
- **Dependency Injection:** Dùng Constructor Injection qua `@RequiredArgsConstructor`, cấm `@Autowired` trên field.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)
- [x] **AC-1:** Admin có thể tạo vai trò mới kèm danh sách permissions và nhận về mã HTTP 201.
- [x] **AC-2:** Hệ thống từ chối xóa hoặc sửa tên của các vai trò hệ thống (`is_system = true`) với mã HTTP 400.
- [x] **AC-3:** Hệ thống từ chối xóa vai trò đang có tài khoản sử dụng với mã HTTP 400.
- [x] **AC-4:** Khi người dùng đăng nhập, JWT Token chứa đầy đủ claim `permissions` và API `/api/auth/me/permissions` trả về đúng danh sách quyền của tài khoản.
- [x] **AC-5:** Tất cả 16 quyền chuẩn được tự động khởi tạo trong DB khi ứng dụng khởi động lần đầu.

---

## 12. Unit Test Cases Checklist
- [x] **UT-BE-01:** `getAllRoles_shouldReturnListWithCounts()`
- [x] **UT-BE-02:** `createRole_withValidData_shouldSaveSuccessfully()`
- [x] **UT-BE-03:** `createRole_withDuplicateName_shouldThrowDuplicateException()`
- [x] **UT-BE-04:** `deleteRole_whenRoleIsSystem_shouldThrowBadRequestException()`
- [x] **UT-BE-05:** `deleteRole_whenRoleHasAssignedUsers_shouldThrowBadRequestException()`
- [x] **UT-BE-06:** `updateRolePermissions_shouldUpdateRolePermissionsCorrectly()`

---

## 13. Implementation Checklist (Danh Sách File Triển Khai)
- [x] `identity-and-access-service/.../entity/Permission.java`
- [x] `identity-and-access-service/.../entity/Role.java` (Cập nhật)
- [x] `identity-and-access-service/.../repository/PermissionRepository.java`
- [x] `identity-and-access-service/.../repository/RoleRepository.java` (Cập nhật)
- [x] `identity-and-access-service/.../rbac/dto/...` (Các Request/Response DTOs)
- [x] `identity-and-access-service/.../rbac/service/...` (Interfaces & Implementations)
- [x] `identity-and-access-service/.../rbac/controller/...` (Controllers)
- [x] `identity-and-access-service/.../security/JwtTokenProvider.java` (Cập nhật)
- [x] `identity-and-access-service/.../security/CustomUserDetailsService.java` (Cập nhật)
- [x] `identity-and-access-service/.../config/DataInitializer.java` (Cập nhật)
- [x] `config-repo-local/api-gateway.yml` (Cập nhật routes)
- [x] Unit Tests trong `src/test/java/...`

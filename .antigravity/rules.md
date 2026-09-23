# Backend Technical Rules & Architecture Guidelines (InternHub)

> [!IMPORTANT]
> **TÀI LIỆU QUY CHUẨN ĐÃ ĐƯỢC NÂNG CẤP VÀ CHUYỂN VỀ THƯ MỤC CHUẨN:**
> Hệ thống quy chuẩn hoạt động, 29 nguyên tắc bất biến và 7 tài liệu chuyên sâu của Backend đã được quy tụ đồng bộ tại:
> - **Chỉ thị Master:** [AGENTS.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/AGENTS.md)
> - **Bộ quy chuẩn chuyên sâu:** [InternHub/.agents/](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/)
>   - [01-working-rules.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/01-working-rules.md): Quy tắc làm việc, giao thức 4 bước, cấm SQL phá hoại, Git/Jira TM, giải trình thay đổi
>   - [02-system-architecture.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/02-system-architecture.md): Bản đồ 6 Microservices, Port map, Docker, Package-by-Feature
>   - [03-compliance-constraints.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/03-compliance-constraints.md): Bảo mật JWT, RBAC 4 role, bảo toàn DB, cấm sửa Frontend
>   - [04-development-guide.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/04-development-guide.md): Triết lý Spec-Driven 14 phần, lưu trữ spec vĩnh cửu, đồng bộ nguyên tử
>   - [05-coding-standards.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/05-coding-standards.md): Clean Code Java, Constructor Injection, BaseEntity, chống N+1 JPA
>   - [06-testing-verification.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/06-testing-verification.md): Kiểm thử Gradle, JUnit 5, Mockito, Actuator healthcheck
>   - [07-debugging-troubleshooting.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/07-debugging-troubleshooting.md): Gỡ lỗi Microservices (Eureka, Gateway, Config Server, HikariCP)

---

## 1. Tóm Tắt Quy Chuẩn Kỹ Thuật Cốt Lõi (Core Snapshot)

1. **Cấu Trúc Package-by-Feature (Bắt Buộc)**:
   - Tổ chức theo feature nghiệp vụ (`<service>/<feature>/entity`, `repository`, `service`, `controller`, `dto/request`, `dto/response`).
   - Tuyệt đối không tạo God-Classes (trần độ dài 200 - 300 dòng code/file).
2. **Kế Thừa `BaseEntity`**:
   - 100% JPA Entity phải kế thừa `BaseEntity` (quản lý `id`, `createdAt`, `updatedAt`, `@PrePersist`, `@PreUpdate`).
3. **Phân Tách DTO**:
   - Tách riêng Request DTO và Response DTO. Tuyệt đối không trả JPA Entity trực tiếp ra Controller hoặc nhận trực tiếp ở `@RequestBody`.
4. **Constructor Injection**:
   - Bắt buộc dùng `@RequiredArgsConstructor` trên các field `private final`. Cấm dùng `@Autowired` trên field.
5. **Spring Data JPA & Tránh N+1**:
   - Sử dụng `JOIN FETCH`, `@EntityGraph`, hoặc DTO projection khi nạp quan hệ lười (Lazy).
   - Tránh dùng `@Data` trên Entity có quan hệ hai chiều.
6. **Quản Lý `@Transactional`**:
   - `@Transactional(readOnly = true)` tại class level của ServiceImpl, `@Transactional` tại method ghi dữ liệu.
7. **Ranh Giới An Toàn (Boundary Isolation)**:
   - Đang làm Backend: **Tuyệt đối cấm tự ý sửa mã nguồn Frontend (`InternHub-Frontend/`)**.
   - Cấm chạy SQL phá hoại (`DROP`, `TRUNCATE`, `ALTER` xóa cột) làm hỏng database `internhub_db`.
8. **Quy Trình Git & Jira `TM`**:
   - Nhánh chức năng: `<type>/<mã-task-jira>/<tên-kebab-case>`. Luôn rẽ nhánh từ `develop` và tạo PR vào `develop`.
   - Commit message: `<type>(<mã-task-jira>): <mô tả ngắn gọn tiếng Việt>`.
9. **Lưu Trữ Spec Vĩnh Cửu & Đồng Bộ Nguyên Tử (Atomic Spec-Code Sync)**:
   - 100% Spec lưu tại `InternHub/docs/specs/`.
   - Mọi thay đổi code từ L2 trở lên bắt buộc phải cập nhật Spec và ghi nhận lý do vào bảng Revision History & Change Rationale. Không hoàn tất task nếu Spec chưa đồng bộ.

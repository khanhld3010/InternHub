# ADR 0001: Chuyển Đổi Mô Hình Phân Quyền Sang Permission-First Triệt Để (Single Source of Truth)

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-04
- **Tác giả / Người quyết định:** Luong Anh Huy & Antigravity Pair-Programming
- **Phạm vi:** Toàn bộ hệ thống Microservices (`identity-and-access-service`, `intern-and-program-service`) và Frontend Web Client (`InternHub-Frontend`).

---

## 1. Ngữ cảnh & Vấn đề (Context & Problem Statement)

1. **Lỗ hổng & Sự phân mảnh Role-Gate:**
   - Trước đây, Frontend (`ProtectedRoute`, `Sidebar`) kiểm tra cứng theo 4 vai trò cố định: `ADMIN`, `HR`, `MENTOR`, `INTERN`.
   - Khi Admin dùng ma trận RBAC động tạo ra một vai trò mới (ví dụ: `HR_LEAD`, `DEPT_HEAD`), tài khoản mang vai trò mới bị đá văng khỏi route và menu Sidebar bị rỗng / fallback về Intern.
   - Cơ chế kiểm tra tĩnh tạo ra khe hở đồng bộ (sync drift) giữa giao diện và Backend.

2. **Rủi ro rò rỉ phạm vi dữ liệu đã từng xảy ra tại TM-16:**
   - Tuyến đường `/hr/interns` (HR xem toàn bộ thực tập sinh của công ty) và `/mentor/interns` (Mentor chỉ được xem danh sách được gán cho chính mình) từng dùng chung mã kiểm tra sơ sài `INTERN_VIEW`.
   - Nếu ở tầng Route-Guard tiếp tục gộp chung một mã `INTERN_VIEW`, hệ thống sẽ vô tình hợp thức hóa lại lỗ hổng bảo mật rò rỉ dữ liệu chéo giữa các Mentor và HR.

3. **Vấn đề quán tính scope cũ:**
   - Tuyến đường `/intern/apply` ("Nộp hồ sơ ứng tuyển" sau khi đăng nhập) là tàn dư của giai đoạn cũ, trong khi vòng đời tài khoản thực tập sinh hiện tại chỉ được tạo và kích hoạt qua thư mời `/onboarding/activate`. Cần loại bỏ triệt để khỏi ma trận.

4. **Ngoại lệ trá hình:**
   - Việc để các trang Intern hoặc `/profile` ở dạng "Mặc định cho tài khoản đăng nhập" thực chất là kiểm tra Role ngầm. Cần phải được lượng hóa bằng Permission Code tường minh để đảm bảo **100% route đều đi qua một cơ chế thẩm định duy nhất: `hasPermission(CODE)`**.

---

## 2. Quyết định Kiến trúc (Decision)

Hệ thống quyết định áp dụng **Mô hình Phân Quyền Permission-First Triệt Để (Phương án A)** với các chuẩn mực sau:

### A. Tái cấu trúc Role & Permission (Single Source of Truth)
- **Role không biến mất:** Role chuyển vai trò từ *"Điều kiện kiểm tra (If-Condition)"* thành *"Khóa tra cứu danh mục quyền (Permission Container)"*.
- **Permission Code là đơn vị nguyên tử duy nhất:** Mọi quyết định hiển thị Menu, mở Route, hiển thị Nút bấm (Button Action) và Cho phép API Backend (`@PreAuthorize`) đều dùng chung mã Permission.

### B. Tách bạch Data-Scope Permissions (Khắc phục TM-16)
- Tách `INTERN_VIEW` thành 2 mã quyền độc lập:
  1. `INTERN_VIEW_ALL`: Dành cho HR / Admin / Ban Giám đốc để xem toàn bộ thực tập sinh trong doanh nghiệp $\rightarrow$ Bảo vệ route `/hr/interns`.
  2. `INTERN_VIEW_OWN`: Dành riêng cho Mentor $\rightarrow$ Bảo vệ route `/mentor/interns` và chỉ truy xuất dữ liệu có `mentorId == currentUserId`.
  3. `INTERN_VIEW_OWN_PROFILE`: Dành riêng cho chính thực tập sinh để xem tiến độ và thông tin cá nhân của bản thân $\rightarrow$ Bảo vệ route `/intern/dashboard`.

### C. Chuẩn hóa Permission Module MENTOR & DOCUMENT
- Đổi quyền truy cập route `/hr/mentors` thành `MENTOR_VIEW` (Page-level). Nút "+ Thêm Mentor" hoặc gán điều phối kiểm tra riêng `MENTOR_CREATE` / `INTERN_ASSIGN_MENTOR` (Action-level).
- Bổ sung `MENTOR_VIEW_OWN_DOCS` và `INTERN_VIEW_OWN_DOCUMENTS` để phân định rõ ràng giữa duyệt tài liệu diện rộng (`DOCUMENT_REVIEW`) và xem tài liệu cá nhân.

### D. Chuẩn hóa Universal Route `/profile`
- Bổ sung permission cơ bản `PROFILE_VIEW_OWN` và tự động seed cho mọi Role trong hệ thống khi khởi tạo. Route `/profile` kiểm tra `hasPermission('PROFILE_VIEW_OWN')`, không có ngoại lệ hardcode.

### E. Loại bỏ `/intern/apply`
- Chính thức đóng và xóa route `/intern/apply` ra khỏi ma trận điều hướng và AppRoutes để phù hợp với vòng đời kích hoạt tài khoản OTP (TM-10 / Onboarding).

### F. Chuyển hướng thông minh thuần túy dựa trên Permission (Smart Permission Redirection)
- Sau khi đăng nhập, hệ thống duyệt danh sách Permissions của người dùng để điều hướng vào Dashboard thích hợp nhất:
  1. Nếu có `ROLE_VIEW` hoặc `SYSTEM_AUDIT_VIEW` hoặc `USER_VIEW` $\rightarrow$ Điều hướng `/admin/dashboard`.
  2. Nếu có `INTERN_VIEW_ALL` hoặc `PROGRAM_MANAGE` $\rightarrow$ Điều hướng `/hr/dashboard`.
  3. Nếu có `INTERN_VIEW_OWN` $\rightarrow$ Điều hướng `/mentor/dashboard`.
  4. Nếu có `INTERN_VIEW_OWN_PROFILE` $\rightarrow$ Điều hướng `/intern/dashboard`.
  5. Fallback an toàn $\rightarrow$ Điều hướng `/profile`.

### G. Thiết Kế Giao Diện Quản Trị: Inline Tabs Trực Quan (Admin Role Matrix UI)
- **Tập trung vào 4 Role Hệ Thống:** ADMIN, HR, MENTOR, INTERN.
- **Bố cục Inline Tabs trên trang:**
  - 4 Role được hiển thị thành 4 Tab lớn nổi bật trên đầu trang quản trị vai trò (`[🛡️ ADMIN] [💼 HR] [🎓 MENTOR] [👨‍🎓 INTERN]`).
  - Bấm vào Tab nào thì bảng ma trận các Module (TTS, Chương trình, Mentor, Hợp đồng, Tài liệu, Hệ thống) của Role đó hiển thị trực tiếp ngay bên dưới trang (không cần popup modal che khuất màn hình).
  - Admin toggle BẬT / TẮT các quyền theo từng Module và bấm nút **"Lưu Thay Đổi"** ở góc trên để cập nhật tức thì.
- **Tách bạch tính năng "Tạo Vai Trò Tùy Biến":** Tạm thời ẩn / disable nút tạo role tùy biến kèm ghi chú rõ ràng để tránh phá vỡ phạm vi 4 role cốt lõi hiện tại.

---

## 3. Ma Trận Quyền Hạn Toàn Diện Mới (The Normalized Matrix)

| Tuyến Đường (Route) | Tên Menu / Chức Năng | Required Permission Code | Mô Tả Ý Nghĩa Nghiệp Vụ |
| :--- | :--- | :--- | :--- |
| `/admin/dashboard` | Bảng Điều Khiển Admin | `ROLE_VIEW` \|\| `USER_VIEW` \|\| `SYSTEM_AUDIT_VIEW` | Trang tổng quan quản trị |
| `/admin/users` | Quản Lý Người Dùng | `USER_VIEW` | Xem danh sách tài khoản |
| `/admin/roles` | Phân Quyền & Vai Trò | `ROLE_VIEW` | Cấu hình ma trận phân quyền |
| `/admin/system` | Giám Sát & Sao Lưu | `SYSTEM_AUDIT_VIEW` \|\| `SYSTEM_BACKUP` | Quản trị hệ thống |
| `/hr/dashboard` | Bảng Điều Khiển HR | `INTERN_VIEW_ALL` \|\| `PROGRAM_VIEW` | Trang tổng quan nhân sự |
| `/hr/programs` | Chương Trình Thực Tập | `PROGRAM_VIEW` | Quản lý kỳ tuyển dụng đào tạo |
| `/hr/mentors` | Danh Sách Mentor | `MENTOR_VIEW` | Tra cứu đội ngũ hướng dẫn |
| `/hr/interns` | Hồ Sơ TTS Toàn Công Ty | `INTERN_VIEW_ALL` | Quản lý TTS toàn doanh nghiệp |
| `/hr/evaluations` | Đánh Giá Cuối Kỳ | `INTERN_APPROVE` | Ký duyệt đánh giá & xuất điểm |
| `/hr/contracts` | Quản Lý Hợp Đồng | `CONTRACT_VIEW` | Quản lý hợp đồng & trợ cấp |
| `/hr/review` | Duyệt Tài Liệu & CV | `DOCUMENT_REVIEW` | Thẩm định hồ sơ ứng viên/TTS |
| `/mentor/dashboard` | Bảng Điều Khiển Mentor | `INTERN_VIEW_OWN` | Theo dõi nhóm TTS phụ trách |
| `/mentor/interns` | TTS Phụ Trách | `INTERN_VIEW_OWN` | Đánh giá & quản lý TTS của mình |
| `/mentor/documents` | Tài Liệu Hướng Dẫn | `MENTOR_VIEW_OWN_DOCS` | Xem tài liệu training mentor |
| `/intern/dashboard` | Tiến Độ Thực Tập | `INTERN_VIEW_OWN_PROFILE` | Xem lộ trình thực tập cá nhân |
| `/intern/documents` | Quản Lý Tài Liệu TTS | `INTERN_VIEW_OWN_DOCUMENTS` | Quản lý hồ sơ/báo cáo cá nhân |
| `/profile` | Hồ Sơ Cá Nhân | `PROFILE_VIEW_OWN` | Xem & cập nhật thông tin tài khoản |

---

## 4. Hệ Quả & Lợi Ích (Consequences)

- **Ưu điểm:**
  - Triệt tiêu 100% bug bảo mật rò rỉ dữ liệu (TM-16), phân định rạch ròi giữa quyền xem diện rộng (`_ALL`) và quyền theo ngữ cảnh sở hữu (`_OWN`).
  - Hỗ trợ phân quyền động hoàn hảo: Khi Admin tạo bất kỳ Role mới nào, chỉ cần tích chọn các quyền tương ứng, người dùng sẽ tự động thấy đúng menu và vào đúng các trang được cấp phép.
  - Loại bỏ hoàn toàn các điều kiện rẽ nhánh ngầm theo `role === 'MENTOR'` hay `role === 'INTERN'`.
- **Nhiệm vụ cần thực hiện tiếp theo:**
  - Bổ sung seed data các permission mới (`INTERN_VIEW_ALL`, `INTERN_VIEW_OWN`, `MENTOR_VIEW`, `MENTOR_VIEW_OWN_DOCS`, `INTERN_VIEW_OWN_PROFILE`, `INTERN_VIEW_OWN_DOCUMENTS`, `PROFILE_VIEW_OWN`) trong `DataInitializer.java`.
  - Cập nhật `ProtectedRoute.tsx`, `AppRoutes.tsx` và `Sidebar.tsx` chuyển sang `requiredPermissions`.
  - Bỏ route `/intern/apply`.

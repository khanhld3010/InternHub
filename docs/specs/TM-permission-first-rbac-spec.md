# Spec: Permission-First RBAC Architecture & Dynamic Navigation (Phân Quyền Động Triệt Để)

**Status:** Proposed  
**Authors:** Lead Architect & Antigravity Pair-Programming  
**Date:** 2026-10-04  
**Branch:** `feature/mentor-evaluation-hub`  
**Related ADR:** [docs/adr/0001-permission-first-rbac-architecture.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/adr/0001-permission-first-rbac-architecture.md)  
**Target:** 
- Backend: `identity-and-access-service`, `intern-and-program-service`
- Frontend: `InternHub-Frontend`

---

## 1. Mục Tiêu & Yêu Cầu Nghiệp Vụ (Business Objectives)

### 1.1 Vấn đề cốt lõi
- Xóa bỏ hoàn toàn việc "cột cứng" Route và Sidebar theo 4 role tĩnh (`ADMIN`, `HR`, `MENTOR`, `INTERN`).
- Đảm bảo **Role chỉ là khóa tra cứu danh mục quyền**, còn **Permission Code là Single Source of Truth** cho cả Frontend UI lẫn Backend APIs.
- Phân tách dứt khoát ranh giới bảo mật dữ liệu giữa HR và Mentor (ngăn chặn tái hiện bug TM-16).
- Loại bỏ các ngoại lệ ngầm: mọi route từ Admin, HR, Mentor, cho đến Intern và Profile đều phải kiểm tra qua `hasPermission(CODE)`.

---

## 2. Danh Mục Permission Chuẩn Hóa Mới (The Normalized Permission Catalog)

Hệ thống bổ sung và chuẩn hóa các mã đặc quyền trong `identity-and-access-service`:

### 2.1 Quyền quản lý Thực tập sinh (Module: `INTERN`)
- `INTERN_VIEW_ALL`: Xem toàn bộ danh sách TTS trong công ty (dành cho HR/Admin).
- `INTERN_VIEW_OWN`: Xem danh sách TTS thuộc quyền hướng dẫn của bản thân (dành cho Mentor, Backend lọc theo `mentorId`).
- `INTERN_VIEW_OWN_PROFILE`: Xem thông tin và tiến độ thực tập cá nhân (dành cho chính Thực tập sinh).
- `INTERN_CREATE`: Thêm mới hồ sơ thực tập sinh.
- `INTERN_EDIT`: Cập nhật thông tin thực tập sinh.
- `INTERN_APPROVE`: Phê duyệt kết quả thực tập cuối kỳ / hoàn thành kỳ thực tập.
- `INTERN_ASSIGN_MENTOR`: Phân công người hướng dẫn cho thực tập sinh.

### 2.2 Quyền quản lý Mentor (Module: `MENTOR`)
- `MENTOR_VIEW`: Xem danh sách Mentor của công ty (bảo vệ trang `/hr/mentors`).
- `MENTOR_CREATE`: Thêm mới thông tin Mentor vào hệ thống (action button trong trang).

### 2.3 Quyền quản lý Tài liệu & Báo cáo (Module: `DOCUMENT`)
- `DOCUMENT_VIEW`: Xem tài liệu, CV, báo cáo chung.
- `DOCUMENT_REVIEW`: Phê duyệt / Từ chối tài liệu và CV nộp lên.
- `MENTOR_VIEW_OWN_DOCS`: Xem tài liệu hướng dẫn dành riêng cho Mentor.
- `INTERN_VIEW_OWN_DOCUMENTS`: Xem và tải lên tài liệu / báo cáo của riêng TTS.

### 2.4 Quyền quản lý Chương trình, Hợp đồng & Hệ thống
- `PROGRAM_VIEW`, `PROGRAM_MANAGE` (Module: `PROGRAM`)
- `CONTRACT_VIEW`, `CONTRACT_MANAGE` (Module: `CONTRACT`)
- `USER_VIEW`, `USER_MANAGE` (Module: `USER`)
- `ROLE_VIEW`, `ROLE_MANAGE` (Module: `ROLE`)
- `SYSTEM_BACKUP`, `SYSTEM_AUDIT_VIEW` (Module: `SYSTEM`)
- `PROFILE_VIEW_OWN` (Module: `PROFILE`): Quyền xem hồ sơ của chính mình (tự động seed cho tất cả các role).

---

## 3. Kiến Trúc Frontend (Frontend Architecture)

### 3.1 Cập nhật `ProtectedRoute.tsx`
- Đổi prop từ `allowedRoles?: RoleType[]` sang `requiredPermissions?: string[]` (hỗ trợ toán tử `mode?: 'ANY' | 'ALL'`, mặc định là `'ANY'`).
- Kiểm tra trực tiếp qua `hasAnyPermission(...requiredPermissions)`.
- Nếu tài khoản có `role === 'ADMIN'`, luôn bypass cho phép truy cập.
- Nếu không thỏa mãn: Chuyển hướng thông minh (Smart Redirect) về trang dashboard cao nhất mà user có quyền.

### 3.2 Tái cấu trúc `Sidebar.tsx`
- Bỏ hoàn toàn `switch(role)` cứng nhắc.
- Định nghĩa mảng danh mục navigation phẳng kèm mã quyền:
```ts
interface NavigationItem {
  to: string;
  label: string;
  icon: any;
  requiredPermission: string;
}
```
- Sử dụng hàm lọc động:
```ts
const navLinks = ALL_NAV_ITEMS.filter(item => hasPermission(item.requiredPermission));
```
- Khi Admin tạo role mới và tích chọn quyền nào, menu đó lập tức xuất hiện trên Sidebar mà không cần sửa code!

### 3.3 Loại bỏ Tuyến Đường `/intern/apply`
- Xóa bỏ `ROUTES.INTERN.APPLY` khỏi `AppRoutes.tsx` và `Sidebar.tsx`.

### 3.4 Logic Smart Redirection sau khi đăng nhập (`getDashboardRedirect`)
Hệ thống duyệt danh sách quyền của người dùng:
1. Có `ROLE_VIEW` || `USER_VIEW` || `SYSTEM_AUDIT_VIEW` $\rightarrow$ `/admin/dashboard`
2. Có `INTERN_VIEW_ALL` || `PROGRAM_MANAGE` $\rightarrow$ `/hr/dashboard`
3. Có `INTERN_VIEW_OWN` $\rightarrow$ `/mentor/dashboard`
4. Có `INTERN_VIEW_OWN_PROFILE` $\rightarrow$ `/intern/dashboard`
5. Fallback $\rightarrow$ `/profile`

### 3.5 Tái Cấu Trúc Giao Diện Quản Trị RBAC: Inline Tabs 4 Role (`AdminRoleTab.tsx`)
- Thay thế danh sách card phân mảnh bằng **Bố Cục 4 Tab Lớn Nổi Bật** trên đầu trang:
  - `[🛡️ QUẢN TRỊ VIÊN (ADMIN)]`
  - `[💼 CHUYÊN VIÊN NHÂN SỰ (HR)]`
  - `[🎓 NGƯỜI HƯỚNG DẪN (MENTOR)]`
  - `[👨‍🎓 THỰC TẬP SINH (INTERN)]`
- Bấm vào Tab nào, toàn bộ **Bảng Ma Trận Phân Quyền (Matrix Table)** của Role đó hiển thị trực tiếp ngay bên dưới trang theo dạng Inline (không mở modal pop-up):
  - Hiển thị đầy đủ danh sách Phân hệ / Module (TTS, Chương trình, Mentor, Hợp đồng, Tài liệu, Hệ thống).
  - Admin gạt toggle BẬT / TẮT trực quan theo từng ô quyền hoặc bật/tắt toàn bộ cả Module/Cột.
  - Nút **"Lưu Thay Đổi Phân Quyền"** đặt cố định ở góc trên thanh công cụ kèm chỉ báo số quyền đã cấp (ví dụ: `15/19 đặc quyền`).
- Ẩn/Disable nút "Tạo Vai Trò Mới" kèm tooltip ghi chú *"Tính năng tạo vai trò tùy biến sẽ được mở rộng trong phiên bản tiếp theo"*.

---

## 4. Kế Hoạch Triển Khai (Step-by-Step Implementation)

1. **Backend Seed & Roles (`identity-and-access-service`):**
   - Cập nhật `DataInitializer.java` bổ sung các Permission mới và map mặc định cho 4 Role chuẩn.
   - Biên dịch và restart service.
2. **Frontend Type & Helpers (`InternHub-Frontend`):**
   - Cập nhật `ProtectedRoute.tsx` hỗ trợ `requiredPermissions`.
   - Cập nhật `Sidebar.tsx` render menu động 100% theo `hasPermission()`.
   - Cập nhật `AppRoutes.tsx` gán `requiredPermissions` cho toàn bộ các Route, gỡ bỏ `/intern/apply`.
3. **Kiểm Thử & Xác Minh (Verification):**
   - Kiểm thử đăng nhập với HR, Mentor, Intern, Admin: kiểm tra Sidebar và URL routing.
   - Kiểm thử tài khoản có Role tùy chỉnh: xác nhận quyền nào mở ra đúng menu và trang đó.

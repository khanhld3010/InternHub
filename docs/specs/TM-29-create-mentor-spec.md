# Specification: Thêm Mới & Quản Lý Danh Sách Mentor (TM-29 / Story 29)

> **Tài liệu Đặc Tả Kỹ Thuật Toàn Diện (Comprehensive Feature Specification)**  
> **Dự án:** [InternHub](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub) (Backend Microservices) & [InternHub-Frontend](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend) (React + Vite + TypeScript)  
> **Mã Story:** **Story 29** (Nhóm tính năng: *Quản lý mentor & phòng ban* -> *Quản lý mentor*)  
> **Tiêu đề User Story:** *"Là HR, tôi muốn thêm mới mentor để phân công cho thực tập sinh."*  
> **Nhánh Git:** `feature/TM-16/assign-mentor-to-intern` (tích hợp trực tiếp hoặc branch tương đương)  
> **Mức độ thay đổi (Change Level):** **L3** (Tạo mới Domain Entity `MentorProfile` trong `intern-and-program-service`, tích hợp luồng Onboarding kích hoạt qua Signed Token, xóa bỏ hoàn toàn password ở form HR, BẮT BUỘC gửi email kích hoạt qua `reporting-and-integration-service`, và mở rộng trang `/onboarding/activate` cho Mentor).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/04-development-guide.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/04-development-guide.md) và [`.agents/01-working-rules.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/.agents/01-working-rules.md).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History)

| Phiên bản | Ngày | Người thực hiện | Task / Story | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-09-26 | AI Agent & Developer | `Story 29` | Tạo mới | Thiết kế đặc tả ban đầu. |
| **v2.0** | 2026-09-26 | AI Agent & Developer | `Story 29` | Tái cấu trúc toàn diện kiến trúc (Architecture Redesign) | 1. **Khắc phục Heuristic bói email**: Tạo bảng `mentor_profiles` quản lý chính danh Mentor & Phòng ban trong `intern-and-program-service`.<br>2. **Đóng lỗ hổng bảo mật mật khẩu**: Bỏ hoàn toàn việc HR sinh/nhìn thấy password. Tái sử dụng luồng Onboarding token-based (`PENDING_ACTIVATION`).<br>3. **Chuyển Email thành BẮT BUỘC (Mandatory)**: `reporting-and-integration-service` là kênh duy nhất để Mentor nhận link đặt mật khẩu và biết mình được mời làm người hướng dẫn.<br>4. **Tối ưu UX Form**: HR chỉ nhập 4 thông tin định danh (Tên, Email, SĐT, Phòng ban). |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Thêm Mới Mentor & Kích Hoạt Tài Khoản Hướng Dẫn Kỹ Thuật (Create Mentor Profile & Token-Based Onboarding)
- **Story ID:** **Story 29** (*Là HR, tôi muốn thêm mới mentor để phân công cho thực tập sinh*)
- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082 - **Phân hệ chủ quản domain thực tập**):
     - Tạo mới Entity `MentorProfile` (lưu trữ `fullName`, `email`, `phone`, `department_id`, `status = PENDING_ACTIVATION`, `user_id = null`).
     - Endpoint `POST /api/interns/mentors`: HR submit hồ sơ Mentor.
     - Sinh Onboarding Token (thời hạn 7 ngày) qua `OnboardingTokenService`.
     - Gọi sang `reporting-and-integration-service` gửi email kích hoạt cho Mentor.
     - Mở rộng endpoint `POST /api/onboarding/activate`: Khi Mentor tự thiết lập mật khẩu, gọi sang Identity Service tạo Account với vai trò `MENTOR`, sau đó map `userId` vào `MentorProfile` và chuyển `status = ACTIVE`.
  2. `reporting-and-integration-service` (Port 8083 - **BẮT BUỘC - Kênh truyền thông duy nhất**):
     - Endpoint: `POST /api/integration/emails/mentor-onboarding`
     - Template Email: `MENTOR_ONBOARDING_INVITATION` gửi lời chào mừng gia nhập đội ngũ Mentor tại InternHub, giải thích vai trò hướng dẫn, và cung cấp nút bấm kích hoạt bảo mật: `${APP_FRONTEND_URL}/onboarding/activate?token=xxx&role=MENTOR`.
  3. `identity-and-access-service` (Port 8081 - **Quản lý Auth & Phân quyền**):
     - Cấp tài khoản Account với vai trò `ROLE_MENTOR` / `Mentor` khi nhận request kích hoạt thành công từ `intern-and-program-service`.
  4. `InternHub-Frontend` (React + Vite):
     - Trang `/hr/mentors` (`HrMentorManagementPage.tsx`): Nút **`+ Thêm Mentor`** mở `CreateMentorModal.tsx`.
     - `CreateMentorModal.tsx`: Form gọn nhẹ chỉ gồm 4 trường định danh (Họ tên, Email, SĐT, Phòng ban). Không có password field.
     - Trang `/onboarding/activate` (`OnboardingActivationPage.tsx`): Đón cả Intern và Mentor, hiển thị đúng thông điệp chào mừng Mentor và điều hướng đăng nhập sau khi đặt mật khẩu thành công.
- **Target Users & Roles:** Ban Nhân Sự (`ROLE_HR`), Quản Trị Viên (`ROLE_ADMIN`).
- **Change Level:** **L3** (Domain Entity mới + Tích hợp liên Service + Luồng kích hoạt Token-Based + UI Modal).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Chuẩn hóa dữ liệu Mentor trong phân hệ quản lý:** Không còn tình trạng "bói" phòng ban bằng email. Bảng `mentor_profiles` lưu trữ tường minh mối quan hệ giữa Mentor và Phòng ban chuyên môn (`Department`).
2. **Bảo mật tuyệt đối (Zero-Knowledge Password):** HR không sinh mật khẩu, không nhìn thấy mật khẩu, hệ thống không lưu mật khẩu tạm. Mentor là người duy nhất tự tay thiết lập mật khẩu cho tài khoản của mình.
3. **Trải nghiệm Onboarding mượt mà:** Tận dụng lại 100% cơ chế Onboarding Token (chống Safe Links scanner) của TM-11/12, gửi email kích hoạt chuyên nghiệp.
4. **Tối giản hóa thao tác cho HR:** HR chỉ mất chưa tới 15 giây để nhập Họ tên, Email, SĐT và chọn Phòng ban là đã hoàn tất việc bổ sung Mentor vào hệ thống.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- **Domain Model (`intern-and-program-service`):**
  - Tạo entity `MentorProfile` (kế thừa `BaseEntity`):
    - `id` (BIGINT PK)
    - `userId` (BIGINT, nullable - được gán sau khi kích hoạt tài khoản trên Identity Service)
    - `fullName` (VARCHAR(100), NOT NULL)
    - `email` (VARCHAR(100), NOT NULL, UNIQUE)
    - `phone` (VARCHAR(20), NOT NULL, UNIQUE)
    - `department` (`@ManyToOne` tới `Department`, NOT NULL)
    - `status` (VARCHAR(20), NOT NULL: `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`)
- **Luồng kích hoạt Token & Gửi Email (BẮT BUỘC):**
  - Sinh JWT Token qua `OnboardingTokenService` chứa claim `role: "MENTOR"`, `mentorProfileId`, `email`, `fullName`.
  - Bắn sự kiện / gọi REST sang `reporting-and-integration-service` gửi email kích hoạt.
  - Xử lý tại `POST /api/onboarding/activate`: Nhận diện role `MENTOR`, gọi Identity Service tạo User/Account quyền `MENTOR`, cập nhật `userId` và `status = ACTIVE` cho `MentorProfile`.
- **API `GET /api/interns/mentors`:**
  - Viết lại hàm này để query trực tiếp từ `MentorProfile` (kèm JOIN `Department` và đếm số lượng TTS đang phụ trách từ `InternProfile`), xóa bỏ hoàn toàn code heuristic cũ!
- **Frontend UI:**
  - Nút **`+ Thêm Mentor`** trên Header của `/hr/mentors`.
  - Component `CreateMentorModal.tsx`: Nhập Tên, Email, SĐT, chọn Phòng ban (`departments`).
  - Trang `/onboarding/activate`: Hỗ trợ kích hoạt cho Mentor.

### 3.2. Ngoài phạm vi (Out of Scope)
- Không bắt buộc HR nhập mật khẩu hoặc username.
- Không import danh sách Mentor từ Excel (để dành Story nâng cao).

---

## 4. Potential Logic Loopholes & Mitigations (Edge Cases & Biện Pháp Xử Lý)

### 4.1. Case 1: Trùng lặp Email hoặc Số điện thoại của Mentor
- **Rủi ro:** HR thêm Mentor có email hoặc SĐT đã tồn tại trong `mentor_profiles` hoặc đã có tài khoản User.
- **Biện pháp:** `mentorProfileRepository.existsByEmail(email)` ➔ Trả về `409 CONFLICT`: *"Email này đã tồn tại trong danh sách Mentor"*.

### 4.2. Case 2: Email kích hoạt gửi thất bại (Network / Mail Server Error)
- **Rủi ro:** SMTP bị gián đoạn, Mentor không nhận được email kích hoạt.
- **Biện pháp:** 
  - `EmailLog` lưu trạng thái `FAILED`.
  - Trên bảng danh sách Mentor (`/hr/mentors`), nếu Mentor đang ở trạng thái `PENDING_ACTIVATION`, hiển thị badge cam **"Chờ kích hoạt"** kèm nút **"Gửi lại email kích hoạt"** (Resend Invitation Link).

### 4.3. Case 3: Link kích hoạt bị mở bởi hệ thống quét link tự động (Outlook Safe Links / Gmail Scanner)
- **Rủi ro:** Bot quét tự động mở link làm hủy token trước khi Mentor bấm vào.
- **Biện pháp:** Đã được thiết kế từ TM-12: Link dẫn tới trang giao diện, frontend gọi `GET /api/onboarding/verify-token` (chỉ đọc, không làm thay đổi state). Token chỉ được tiêu thụ khi Mentor thực sự điền mật khẩu và ấn nút **Xác nhận** (`POST /api/onboarding/activate`).

### 4.4. Case 4: Token kích hoạt hết hạn (sau 7 ngày)
- **Rủi ro:** Mentor bấm vào link sau hơn 7 ngày kể từ khi HR gửi thư mời.
- **Biện pháp:** `OnboardingTokenService` ném ngoại lệ hết hạn. Giao diện hiển thị: *"Liên kết kích hoạt đã hết hạn. Vui lòng liên hệ HR để nhận liên kết mới."*.

### 4.5. Case 5: Phân công TTS cho Mentor chưa kích hoạt tài khoản
- **Rủi ro:** HR phân công TTS cho Mentor đang ở trạng thái `PENDING_ACTIVATION` (chưa có tài khoản đăng nhập để theo dõi, đánh giá hay nhận thông báo).
- **Biện pháp (Chặn Tuyệt Đối - Đơn Giản, An Toàn, Nhất Quán):**
  - **Tầng Backend (`intern-and-program-service`):** Trong API `POST /api/interns/{id}/assign-mentor`, kiểm tra trạng thái của Mentor. Nếu `mentor.status != ACTIVE` (ví dụ đang `PENDING_ACTIVATION`), lập tức chặn và trả về `400 BAD_REQUEST`: *"Người hướng dẫn này chưa kích hoạt tài khoản. Vui lòng yêu cầu Mentor kích hoạt trước khi phân công."*.
  - **Tầng Frontend (`AssignMentorModal.tsx`):** Trong dropdown chọn Mentor, các Mentor có `status === 'PENDING_ACTIVATION'` sẽ bị `disabled` lựa chọn kèm nhãn trực quan: `[Tên Mentor] — (Chưa kích hoạt tài khoản - Vô hiệu hóa)`. HR chỉ có thể phân công khi Mentor đã kích hoạt thành công (`status === 'ACTIVE'`).

---

## 5. API Contracts (Đặc Tả Giao Tiếp REST API)

### 5.1. Thêm Mới Mentor (`POST /api/interns/mentors`)
- **Microservice:** `intern-and-program-service`
- **Method & URL:** `POST /api/interns/mentors`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Request Body JSON:**
```json
{
  "fullName": "Nguyễn Hoàng Long",
  "email": "long.nh@internhub.com",
  "phone": "0987654321",
  "departmentId": 1
}
```
- **Response (`201 CREATED`):**
```json
{
  "code": 201,
  "message": "Thêm mới Mentor và gửi thư mời kích hoạt thành công",
  "data": {
    "id": 12,
    "fullName": "Nguyễn Hoàng Long",
    "email": "long.nh@internhub.com",
    "phone": "0987654321",
    "departmentId": 1,
    "departmentName": "Trung tâm Phát triển Phần mềm",
    "departmentCode": "IT-DEV",
    "status": "PENDING_ACTIVATION",
    "activeInternCount": 0,
    "interningCount": 0,
    "assignedPendingStartCount": 0
  }
}
```

### 5.2. Gửi Lại Email Kích Hoạt (`POST /api/interns/mentors/{id}/resend-invitation`)
- **Method & URL:** `POST /api/interns/mentors/{id}/resend-invitation`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
- **Tái sử dụng cơ chế Rate-limit & Chống Spam từ TM-12 (Không thiết kế mới):**
  - **Trạng thái hợp lệ:** Chỉ cho phép gửi lại khi Mentor đang ở trạng thái `status == 'PENDING_ACTIVATION'`. Nếu đã `ACTIVE`, trả về `400 BAD_REQUEST`: *"Tài khoản Mentor đã được kích hoạt thành công"*.
  - **Cooldown (45 giây):** Kiểm tra `lastInvitationSentAt`. Nếu thời gian từ lần gửi trước `< 45 giây`, ném `RateLimitException` trả về HTTP `429 TOO_MANY_REQUESTS` kèm header `Retry-After: <số giây còn lại>` và body:
    ```json
    {
      "code": 429,
      "message": "Thư mời vừa được gửi cách đây ít phút. Vui lòng đợi trước khi gửi lại.",
      "data": { "retryAfter": 32 }
    }
    ```
  - **Hạn mức (Max 5 lần/ngày):** Giới hạn trường `invitationRetryCount <= 5`. Nếu vượt quá, trả về `400 BAD_REQUEST`: *"Đã vượt quá số lần gửi lại tối đa trong ngày (5 lần). Vui lòng liên hệ trực tiếp với Mentor."*.
  - **Xử lý UI Frontend:** Khi ấn "Gửi lại", nút bị disable và kích hoạt bộ đếm ngược Cooldown (45s -> 0s) giống hệt nút gửi lại ở `DetailInternModal.tsx`.
- **Response Thành Công (`200 OK`):**
```json
{
  "code": 200,
  "message": "Đã gửi lại email thư mời kích hoạt tài khoản cho Mentor",
  "data": {
    "mentorId": 12,
    "lastInvitationSentAt": "2026-09-26T23:30:00",
    "retryCount": 1
  }
}
```

### 5.3. Gửi Email Thư Mời Kích Hoạt Mentor (`POST /api/integration/emails/mentor-onboarding`)
- **Microservice:** `reporting-and-integration-service` (Port 8083 - **BẮT BUỘC**)
- **Request Body JSON:**
```json
{
  "mentorProfileId": 12,
  "email": "long.nh@internhub.com",
  "fullName": "Nguyễn Hoàng Long",
  "departmentName": "Trung tâm Phát triển Phần mềm",
  "onboardingToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 6. UI/UX Specifications (`InternHub-Frontend`)

### 6.1. Trang Quản Lý Mentor (`HrMentorManagementPage.tsx`)
- Thêm nút **`+ Thêm Mentor`** (Icon `UserPlus`, Variant `primary`) tại thanh điều khiển.
- Cột "Trạng thái" hiển thị:
  - `Hoạt động` (Badge xanh lá `badge-success`) nếu `status === 'ACTIVE'`.
  - `Chờ kích hoạt` (Badge vàng cam `badge-warning`) nếu `status === 'PENDING_ACTIVATION'`.
  - Cột thao tác với Mentor chờ kích hoạt có thêm nút **"Gửi lại thư mời"** (Icon `Send` nhỏ).

### 6.2. Modal `CreateMentorModal.tsx`
- **Tiêu đề:** `Thêm Mới Người Hướng Dẫn (Mentor)`
- **Kích thước:** `md` (480px, nhỏ gọn, tinh tế).
- **Mô tả ngắn phía trên:** *"Hệ thống sẽ gửi email thư mời kích hoạt tài khoản. Mentor sẽ tự thiết lập mật khẩu cá nhân khi bấm vào liên kết trong thư."*
- **Form Fields (4 trường duy nhất):**
  1. Họ và tên (`fullName`): Bắt buộc, placeholder: "Ví dụ: Lê Hoàng Nam"
  2. Email công ty (`email`): Bắt buộc, placeholder: "nam.lh@internhub.com"
  3. Số điện thoại (`phone`): Bắt buộc, placeholder: "0901234567"
  4. Phòng ban chuyên môn (`departmentId`): Dropdown chọn từ danh sách phòng ban đang hoạt động.
- **Footer:**
  - Nút `Hủy`
  - Nút `Tạo Mentor & Gửi Thư Mời` (Variant `primary`, kèm icon `Mail`)

### 6.3. Trang `/onboarding/activate` (`OnboardingActivationPage.tsx`)
- Khi token mang vai trò `MENTOR`:
  - Tiêu đề: *"Kích Hoạt Tài Khoản Mentor"*
  - Thông điệp: *"Chào mừng **[Họ tên]** gia nhập đội ngũ Người Hướng Dẫn tại InternHub! Vui lòng thiết lập mật khẩu để hoàn tất."*
  - Sau khi thiết lập mật khẩu thành công: Hiện thông báo và nút chuyển sang Đăng nhập.

---

## 7. Acceptance Criteria Checklist (Tiêu Chí Nghiệm Thu)

- [ ] **AC-1:** HR có thể bấm nút **"+ Thêm Mentor"** trên trang `/hr/mentors` để mở modal.
- [ ] **AC-2:** Form chỉ yêu cầu 4 trường định danh (Họ tên, Email, SĐT, Phòng ban); tuyệt đối không có ô nhập mật khẩu.
- [ ] **AC-3:** Lưu thành công:
  - Bản ghi `MentorProfile` được tạo với `status = PENDING_ACTIVATION` gắn chuẩn xác `department_id`.
  - Email thư mời kích hoạt được tự động gửi đến hòm thư của Mentor qua `reporting-and-integration-service`.
- [ ] **AC-4:** Bảng danh sách Mentor lập tức xuất hiện Mentor mới với badge **"Chờ kích hoạt"** và `0 TTS`.
- [ ] **AC-5:** Mentor nhận được email, bấm link kích hoạt, tự nhập mật khẩu thành công.
- [ ] **AC-6:** Sau khi kích hoạt: Tài khoản được kích hoạt trên Identity Service, `MentorProfile.status` chuyển sang `ACTIVE`, Mentor có thể đăng nhập vào hệ thống ngay lập tức.
- [ ] **AC-7:** Logic heuristic đoán phòng ban bằng email trong `getAvailableMentors()` được xóa bỏ hoàn toàn; danh sách Mentor lấy phòng ban chuẩn xác từ quan hệ `department`.

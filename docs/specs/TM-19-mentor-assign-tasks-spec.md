# Specification: Quản Lý Bảng Nhiệm Vụ (MissionBoard) & Giao Việc Cho Thực Tập Sinh (TM-19)

> **Trạng thái:** ACTIVE / IMPLEMENTED & VERIFIED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md`  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-19](https://robluccibn9935.atlassian.net/browse/TM-19)  
> **Tiêu đề Jira:** *Mentor - Giao nhiệm vụ cho thực tập sinh (MissionBoard & Task Assignment)*  
> **Nhánh Git:** `feature/TM-19/mentor-assign-tasks`  
> **Cấp độ thay đổi (Change Level):** **L3** (Mô hình quan hệ N-N giữa Program và Mentor, Domain Model `MissionBoard` & `MissionItem`, giao việc cho 1 hoặc nhiều TTS, 3 trạng thái Kanban "chưa làm" / "đang làm" / "hoàn thiện", phân quyền RBAC và tích hợp Gateway).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, đặc biệt Rule 14, 15, 16, 17, 18, 19, 21, 22, 23, 26, 30, 31).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất (Tuân thủ Rule 30).

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-02 | AI Senior Pair-Programmer | `TM-19` | Tạo mới | Thiết kế đặc tả ban đầu theo mô hình Task đơn lẻ cá nhân. |
| **v2.0.0** | 2026-10-02 | AI Senior Pair-Programmer & User | `TM-19` | Tái cấu trúc toàn diện kiến trúc | Chuyển đổi sang mô hình **MissionBoard**: Program - Mentor quan hệ N-N; Mentor tạo MissionBoard trong Program; mỗi công việc chi tiết hỗ trợ gán 1 hoặc nhiều TTS; chuẩn hóa 3 trạng thái: "chưa làm" (`TODO`), "đang làm" (`IN_PROGRESS`), "hoàn thiện" (`COMPLETED`) theo thỏa thuận thiết kế. |
| **v2.1.0** | 2026-10-02 | AI Senior Pair-Programmer | `TM-19` | Cập nhật API & Cơ chế Fallback | Bổ sung API endpoints thực tế từ commit `e65323c`: Endpoint tạo board trực tiếp `POST /api/mission-boards`, các API quản lý phân công Mentor vào Program (`/api/programs/{id}/mentors` và `/api/mentor/programs/{id}/mentors`), cơ chế Smart Fallback tự động tìm kiếm/phân quyền Mentor khi chưa có bản ghi `ProgramMentor` cứng. |
| **v2.2.0** | 2026-10-04 | AI Senior Pair-Programmer & User | `TM-19` | Chuẩn hóa định nghĩa nghiệp vụ cốt lõi | **Xác định rõ ràng: "Công việc của Mentor là giao việc cho thực tập sinh"** (Mentor Task Delegation & Mission Assignment). Làm rõ ranh giới phân định: Mentor là người lập kế hoạch, tạo đầu việc và **giao việc cho thực tập sinh** (Assigner/Delegator), theo dõi tiến độ, hướng dẫn và nghiệm thu; Thực tập sinh là người nhận việc (Assignee) thực thi nhiệm vụ. Bắt buộc mỗi công việc chi tiết phải được giao cho tối thiểu 1 TTS trong Program (`internIds.size() >= 1`). Đồng bộ hóa 100% tài liệu Spec với các nguyên tắc bất biến của Backend. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Mentor Quản Lý Bảng Nhiệm Vụ (MissionBoard) & Giao Việc Cho Thực Tập Sinh (Mentor MissionBoard & Task Assignment).
- **Jira Ticket:** [TM-19](https://robluccibn9935.atlassian.net/browse/TM-19)
- **Tuyên Ngôn Nghiệp Vụ Cốt Lõi (Core Business Statement):**
  > **"CÔNG VIỆC CỦA MENTOR LÀ GIAO VIỆC CHO THỰC TẬP SINH"**
  >
  > Trong phân hệ này, Mentor đóng vai trò là **Người dẫn dắt / Quản lý chuyên môn (Assigner & Delegator)**. Mentor không phải là người tự thực hiện các thẻ công việc đơn lẻ cho chính mình; thay vào đó, công việc trọng tâm của Mentor là:
  > 1. Thiết lập các **Bảng Nhiệm Vụ (MissionBoard)** gắn liền với từng giai đoạn / sprint / tuần của Chương trình thực tập (`InternshipProgram`).
  > 2. Phân rã mục tiêu đào tạo thành các **Mục công việc chi tiết (MissionItems)** kèm yêu cầu kỹ thuật, độ ưu tiên và hạn chót.
  > 3. **Trực tiếp giao việc (phân công)** cho một hoặc một nhóm Thực tập sinh (`InternProfile`) trong Chương trình cùng thực hiện.
  > 4. Giám sát tiến độ thông qua mô hình 3 trạng thái Kanban trực quan và tiến hành nghiệm thu, đóng công việc khi hoàn thành.

- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082):
     - Mở rộng quan hệ N-N giữa `InternshipProgram` và `Mentor` thông qua thực thể `ProgramMentor`.
     - Xây dựng module `org.example.internservice.mission`: Quản lý thực thể `MissionBoard` và `MissionItem`.
     - Hỗ trợ giao một mục công việc cho 1 hoặc nhiều thực tập sinh cùng tham gia (`mission_item_assignees`).
     - Cơ chế Smart Fallback tự động nhận diện quyền Mentor dựa trên hồ sơ phân công TTS (`intern_profiles.mentor_id`).
  2. `api-gateway` (Port 8080): Định tuyến `/api/mission-boards/**`, `/api/mission-items/**`, và `/api/mentor/**` về `intern-and-program-service`.

- **Target Users & Roles (Phân Định Rõ Ràng Trách Nhiệm):**
  - **`ROLE_MENTOR` (Người Giao Việc - Assigner & Evaluator):**
    - Người hướng dẫn được phân công vào Chương trình thực tập.
    - Toàn quyền tạo và cấu hình MissionBoard cho Chương trình.
    - **Tạo đầu việc và trực tiếp giao việc cho Thực tập sinh** (chọn 1 hoặc nhiều TTS trong danh sách thực tập sinh của Program).
    - Cập nhật thông tin công việc, điều chỉnh danh sách TTS được giao việc, xóa công việc.
    - Điều phối và chuyển đổi 3 trạng thái công việc (`TODO` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`) để nghiệm thu kết quả của TTS.
  - **`ROLE_INTERN` (Người Nhận Việc - Assignee & Executor):**
    - Thực tập sinh đang học tập trong Chương trình (`INTERNING` hoặc `APPROVED`).
    - Là đối tượng nhận việc từ Mentor; được gán vào một hoặc nhiều `MissionItem`.
    - Tiếp nhận yêu cầu kỹ thuật, hạn chót và phối hợp cùng các thành viên khác trong nhóm để hoàn thành nhiệm vụ được giao.
  - **`ROLE_HR` & `ROLE_ADMIN` (Giám Sát & Quản Trị Hệ Thống):**
    - Phân công Mentor vào các Chương trình thực tập (quan hệ N-N qua `ProgramMentor`).
    - Giám sát toàn bộ tiến độ giao việc và kết quả đào tạo của tất cả các chương trình thực tập.

- **Change Level:** **L3** (Tạo mới bảng quan hệ N-N `program_mentors`, bảng `mission_boards`, `mission_items`, `mission_item_assignees`, thiết kế REST API chuẩn Enterprise và phân quyền sở hữu chặt chẽ).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Chuẩn hóa & Số hóa quy trình Giao việc từ Mentor sang Thực tập sinh:** Xóa bỏ hoàn toàn tình trạng giao việc qua tin nhắn rời rạc, giao việc miệng không có lưu vết hoặc theo dõi thủ công trên Excel. Mọi nhiệm vụ đều có tiêu đề, mô tả kỹ thuật, độ ưu tiên, deadline và người chịu trách nhiệm rõ ràng.
2. **Giao việc linh hoạt theo Cá nhân & Theo Nhóm (Pair / Group Delegation):** Cho phép Mentor giao một mục công việc chi tiết cho một cá nhân phụ trách riêng lẻ hoặc phân công một nhóm TTS cùng hợp tác thực hiện (Pair Programming, Team Project) trong cùng một chương trình đào tạo.
3. **Mô hình Quản lý Đào tạo theo Chương trình / Giai đoạn (Cohort/Program-Based Training):** Trong một chương trình thực tập, một nhóm TTS cùng trải qua các giai đoạn (Onboarding, Kiến trúc hệ thống, Lập trình chức năng, Kiểm thử). Bảng nhiệm vụ (MissionBoard) giúp Mentor đóng gói công việc theo từng tuần/sprint một cách khoa học.
4. **Hợp tác linh hoạt giữa nhiều Mentor trong một Chương trình (Many-to-Many Mentor - Program):** Một chương trình lớn có thể có nhiều Mentor cùng hướng dẫn chuyên môn (Mentor Backend, Mentor Frontend, Mentor DevOps); đồng thời một Mentor có thể tham gia hướng dẫn ở nhiều chương trình khác nhau.
5. **Mô hình Kanban 3 trạng thái trực quan & Nghiệm thu rõ ràng:** Chuẩn hóa quy trình theo 3 trạng thái:
   - **`"chưa làm"` (`TODO`)**: Công việc Mentor mới giao cho Thực tập sinh, chưa bắt đầu thực hiện.
   - **`"đang làm"` (`IN_PROGRESS`)**: Thực tập sinh đang trong quá trình nghiên cứu và thực hiện công việc.
   - **`"hoàn thiện"` (`COMPLETED`)**: Công việc đã được Mentor kiểm tra, review code, nghiệm thu đạt yêu cầu.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)

- **Mối quan hệ N-N giữa Program và Mentor & Tra cứu:**
  - Bảng liên kết `program_mentors` (`program_id`, `mentor_id`, `assigned_by`, `assigned_at`).
  - API lấy danh sách các Program mà Mentor đăng nhập đang phụ trách: `GET /api/mentor/programs`.
  - API lấy danh sách các Thực tập sinh thuộc Program để Mentor lựa chọn khi giao việc: `GET /api/mentor/programs/{programId}/interns`.
  - API thêm / gỡ Mentor vào Program dành cho HR/Admin: `POST /api/programs/{id}/mentors/{mentorId}`, `DELETE /api/programs/{id}/mentors/{mentorId}`, `GET /api/programs/{id}/mentors`.
  - Hỗ trợ các endpoint tương thích tại `/api/mentor/programs/{programId}/mentors/**`.

- **Quản lý Bảng Nhiệm Vụ (MissionBoard):**
  - Mentor tạo MissionBoard gắn với Program phụ trách: `POST /api/programs/{programId}/mission-boards` hoặc tạo trực tiếp `POST /api/mission-boards` (có truyền `programId` trong body).
  - Lấy danh sách MissionBoard của Program: `GET /api/programs/{programId}/mission-boards`.
  - Lấy chi tiết MissionBoard kèm toàn bộ các mục công việc phân theo 3 trạng thái: `GET /api/mission-boards/{boardId}`.
  - Cập nhật MissionBoard: `PUT /api/mission-boards/{boardId}` (tiêu đề, mô tả, trạng thái `ACTIVE` / `ARCHIVED`).
  - Xóa MissionBoard: `DELETE /api/mission-boards/{boardId}`.

- **Quản lý Mục Công Việc Chi Tiết & Giao Việc Cho Thực Tập Sinh (MissionItem):**
  - **Mentor giao việc mới cho Thực tập sinh:** `POST /api/mission-boards/{boardId}/items` (chọn 1 hoặc nhiều `internIds` thuộc Program làm assignees).
  - **Mentor điều chỉnh công việc & phân công lại TTS:** `PUT /api/mission-items/{itemId}` (sửa tiêu đề, mô tả, mức độ ưu tiên, hạn chót, danh sách `internIds`).
  - **Mentor điều phối / nghiệm thu trạng thái công việc:** `PATCH /api/mission-items/{itemId}/status` (chọn `TODO`, `IN_PROGRESS`, hoặc `COMPLETED`).
  - **Mentor xóa mục công việc chi tiết:** `DELETE /api/mission-items/{itemId}`.

### 3.2. Ngoài phạm vi (Out of Scope)

- Tuyệt đối không can thiệp sửa đổi giao diện Frontend TypeScript/React trong task Backend này (Tuân thủ Rule 7 - Boundary Isolation).
- Không chạy các lệnh SQL phá hoại (`DROP`, `TRUNCATE`, xóa cột CSDL) trên MySQL (Tuân thủ Rule 8).
- Không tự ý commit hay push code lên Git repository (Tuân thủ Rule 4).
- Chưa xây dựng WebSocket đẩy thông báo Kanban thời gian thực (được tách thành task riêng theo kiến trúc module thông báo).

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Mentor tạo Board hoặc giao việc cho Program mà mình KHÔNG phụ trách

- **Vấn đề:** Mentor A gọi API tạo Board hoặc thêm task vào Program mà Mentor A không được phân công trong `program_mentors` và không có TTS nào thuộc quyền hướng dẫn.
- **Giải pháp:** Service thực hiện kiểm tra quyền sở hữu chặt chẽ:

```java
boolean existsInProgramMentor = programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorId);
// Smart Fallback: Kiểm tra nếu Mentor có bất kỳ TTS nào trong Program đang phụ trách
boolean hasInternInProgram = internProfileRepository.findByProgramId(programId).stream()
        .anyMatch(i -> i.getMentorId() != null && (i.getMentorId().equals(mentorId) || i.getMentorId().equals(userId)));
if (!existsInProgramMentor && !hasInternInProgram && !isHrOrAdmin(userDetails)) {
    throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
}
```

### 4.2. Edge Case 2: Giao việc cho Thực tập sinh KHÔNG thuộc Program đó

- **Vấn đề:** Khi tạo/sửa MissionItem, Mentor truyền `internIds` chứa ID của một TTS thuộc Program khác hoặc TTS không tồn tại trên hệ thống.
- **Giải pháp:** Service kiểm tra tính hợp lệ của 100% `internIds`:
  - Mọi `internId` phải tồn tại trong cơ sở dữ liệu.
  - Mọi TTS được chọn phải có `intern.getProgram().getId().equals(board.getProgram().getId())`.
  - Nếu phát hiện bất kỳ TTS nào không khớp Program, lập tức ném:
    `BadRequestException("Thực tập sinh [" + intern.getFullName() + " - " + intern.getInternCode() + "] không thuộc chương trình đào tạo của bảng nhiệm vụ này")`.

### 4.3. Edge Case 3: Giao việc nhưng KHÔNG chọn Thực tập sinh nào (`internIds` rỗng)

- **Vấn đề:** Request tạo công việc gửi lên với `internIds` là `null` hoặc mảng rỗng `[]`.
- **Giải pháp:** Do bản chất **"công việc của Mentor là giao việc cho thực tập sinh"**, hệ thống nghiêm cấm tạo các công việc "vô chủ" không có người thực hiện. Validate chặt chẽ:
  - Tại DTO: `@NotEmpty(message = "Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc") Set<Long> internIds;`
  - Tại Service: Kiểm tra `internIds == null || internIds.isEmpty()`, ném `BadRequestException("Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc")`.

### 4.4. Edge Case 4: Trạng thái không hợp lệ của Thực tập sinh khi giao việc

- **Vấn đề:** TTS trong Program đã bị đình chỉ (`TERMINATED`), từ chối (`REJECTED`) hoặc đang bảo lưu (`ON_HOLD`).
- **Giải pháp:** API lấy danh sách TTS để giao việc (`GET /api/mentor/programs/{programId}/interns`) chủ động filter chỉ trả về các TTS có trạng thái `INTERNING` hoặc `APPROVED`. Tại Service khi gán việc, nếu TTS bị đình chỉ sẽ phát cảnh báo.

### 4.5. Edge Case 5: Lỗi N+1 Query JPA khi tải danh sách MissionItem kèm Assignees

- **Vấn đề:** Một MissionBoard có 20 mục công việc, mỗi mục có quan hệ `@ManyToMany` với 3-5 TTS. Nếu query thông thường bằng JPA Lazy Loading sẽ sinh ra 21 câu SELECT gây cạn kiệt pool kết nối và giảm hiệu năng hệ thống (vi phạm Rule 22).
- **Giải pháp:** Bắt buộc sử dụng `JOIN FETCH` trong Repository:

```java
@Query("SELECT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.board.id = :boardId ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
List<MissionItem> findByBoardIdWithAssignees(@Param("boardId") Long boardId);
```

### 4.6. Edge Case 6: Hạn chót công việc vượt quá thời gian kết thúc của Program

- **Vấn đề:** Program kết thúc ngày `2026-11-30`, nhưng Mentor đặt hạn chót mục công việc là `2026-12-15`.
- **Giải pháp:** Validate `if (board.getProgram().getEndDate() != null && dueDate.isAfter(board.getProgram().getEndDate()))`, ném `BadRequestException("Hạn chót công việc không được vượt quá ngày kết thúc chương trình (" + board.getProgram().getEndDate() + ")")`.

### 4.7. Edge Case 7: Tài khoản Mentor đăng nhập chưa có bản ghi `MentorProfile`

- **Vấn đề:** Người dùng đăng nhập có quyền `ROLE_MENTOR` trong JWT, nhưng chưa được khởi tạo hồ sơ trong bảng `mentor_profiles`.
- **Giải pháp:** Cơ chế Smart Provisioning trong Service:
  - Tìm theo `userId` $\rightarrow$ tìm theo `email/username` $\rightarrow$ nếu chưa có thì tự động tạo mới `MentorProfile` liên kết với phòng ban mặc định và lưu vào CSDL để không làm gián đoạn trải nghiệm của Mentor.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tra cứu Chương trình phụ trách):** Mentor đăng nhập có thể xem danh sách toàn bộ các chương trình thực tập mà mình đang được phân công phụ trách, kèm số lượng TTS và trạng thái chương trình.
- **FR-2 (Tra cứu Thực tập sinh để giao việc):** Mentor có thể lấy danh sách các Thực tập sinh hợp lệ thuộc Chương trình (họ tên, mã TTS, email, vị trí ứng tuyển) để lựa chọn làm người nhận việc.
- **FR-3 (Quản lý Bảng Nhiệm Vụ - MissionBoard):** Mentor có thể tạo nhiều MissionBoard trong một Program (chia theo giai đoạn, tuần học hoặc sprint), xem chi tiết, chỉnh sửa thông tin hoặc xóa board.
- **FR-4 (Giao Việc Cho Thực Tập Sinh - Create MissionItem with Assignees):** Mentor có thể tạo mục công việc chi tiết và **bắt buộc chọn 1 hoặc nhiều Thực tập sinh** trong Chương trình làm người thực hiện (`assignees`). Mỗi công việc có: Tiêu đề (3-200 ký tự), Mô tả chi tiết, Mức độ ưu tiên (`LOW`, `MEDIUM`, `HIGH`), Hạn chót hoàn thành (`dueDate`).
- **FR-5 (Cập Nhật Nội Dung & Điều Chỉnh Phân Công):** Mentor có thể cập nhật tiêu đề, mô tả, mức độ ưu tiên, hạn chót và thêm/bớt danh sách Thực tập sinh được giao việc.
- **FR-6 (Điều Phối Trạng Thái & Nghiệm Thu):** Mentor có thể cập nhật trạng thái công việc của TTS theo 3 nấc chuẩn:
  - `TODO` ("chưa làm")
  - `IN_PROGRESS` ("đang làm")
  - `COMPLETED` ("hoàn thiện" - nghiệm thu xong)
- **FR-7 (Xóa Công Việc Chi Tiết):** Mentor có thể xóa mục công việc chi tiết, hệ thống tự động dọn sạch các liên kết phân công trong bảng `mission_item_assignees`.
- **FR-8 (Phân Công Mentor Vào Program - HR/Admin):** HR/Admin có thể phân công Mentor vào Program hoặc gỡ Mentor khỏi Program.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Ràng buộc Phân công Mentor - Program):** Một Program có thể có nhiều Mentor; một Mentor có thể phụ trách nhiều Program. Chỉ Mentor thuộc danh sách `program_mentors` hoặc có hồ sơ hướng dẫn TTS trong Program mới có quyền tạo Board và giao việc trong Program đó.
- **BR-2 (Ràng buộc Thực tập sinh nhận việc - Program):** 100% Thực tập sinh được Mentor giao việc bắt buộc phải có hồ sơ (`InternProfile`) thuộc đúng `program_id` của Bảng nhiệm vụ đó. Tuyệt đối cấm giao việc chéo sang TTS của Program khác.
- **BR-3 (Ràng buộc Bắt Buộc Giao Việc - Mandatory Assignee Constraint):**
  > **Công việc của Mentor là giao việc cho thực tập sinh**, do đó danh sách `internIds` khi tạo mục công việc **tuyệt đối không được để trống** (`internIds.size() >= 1`). Hệ thống không cho phép tồn tại công việc không có người thực hiện.
- **BR-4 (Chuẩn hóa 3 Trạng thái duy nhất):**
  - Chỉ chấp nhận 3 giá trị Enum: `TODO`, `IN_PROGRESS`, `COMPLETED`.
  - Không sinh thêm các trạng thái trung gian gây phức tạp luồng nghiệp vụ.
- **BR-5 (Ràng buộc Hạn chót công việc):** `dueDate` (nếu có) phải lớn hơn hoặc bằng ngày tạo và không được vượt quá `program.endDate`.
- **BR-6 (Tính Toàn Vẹn Dữ Liệu Khi Xóa):** Khi xóa một `MissionBoard`, toàn bộ các `MissionItem` con sẽ bị xóa theo (`ON DELETE CASCADE`). Khi xóa một `MissionItem`, toàn bộ các bản ghi liên kết trong `mission_item_assignees` sẽ tự động được thu hồi mà không ảnh hưởng đến hồ sơ gốc của `InternProfile`.

---

## 7. Data Model (Mô Hình Dữ Liệu)

> [!NOTE]
> Tuân thủ Rule 15: 100% JPA Entities kế thừa `BaseEntity` (`id`, `createdAt`, `updatedAt`).  
> Tuân thủ Rule 31: Tái sử dụng tối đa các bảng hiện có (`internship_programs`, `mentor_profiles`, `intern_profiles`, `departments`).

### 7.1. Cấu Trúc Bảng MySQL

#### 1. Bảng liên kết `program_mentors` (N-N giữa Program và Mentor)

```sql
CREATE TABLE program_mentors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(100) NOT NULL,
    mentor_email VARCHAR(100) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_pm_program FOREIGN KEY (program_id) REFERENCES internship_programs (id) ON DELETE CASCADE,
    UNIQUE KEY uk_program_mentor (program_id, mentor_id),
    INDEX idx_pm_mentor (mentor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 2. Bảng `mission_boards`

```sql
CREATE TABLE mission_boards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    mentor_name VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_board_program FOREIGN KEY (program_id) REFERENCES internship_programs (id) ON DELETE CASCADE,
    INDEX idx_board_program (program_id),
    INDEX idx_board_mentor (mentor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 3. Bảng `mission_items`

```sql
CREATE TABLE mission_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    due_date DATE NULL,
    order_index INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_item_board FOREIGN KEY (board_id) REFERENCES mission_boards (id) ON DELETE CASCADE,
    INDEX idx_item_board_status (board_id, status),
    INDEX idx_item_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 4. Bảng liên kết `mission_item_assignees` (N-N giữa MissionItem và Thực tập sinh)

```sql
CREATE TABLE mission_item_assignees (
    item_id BIGINT NOT NULL,
    intern_id BIGINT NOT NULL,
    PRIMARY KEY (item_id, intern_id),
    CONSTRAINT fk_mia_item FOREIGN KEY (item_id) REFERENCES mission_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_mia_intern FOREIGN KEY (intern_id) REFERENCES intern_profiles (id) ON DELETE CASCADE,
    INDEX idx_mia_intern (intern_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 7.2. JPA Entity Mapping & Enums

- **Base Class:** `org.example.internservice.common.entity.BaseEntity`
- **Entity Classes:**
  - `org.example.internservice.program.entity.ProgramMentor`
  - `org.example.internservice.mission.entity.MissionBoard`
  - `org.example.internservice.mission.entity.MissionItem`
- **Enums:**
  - `MissionItemStatus`:
    - `TODO` (*"Chưa làm"*)
    - `IN_PROGRESS` (*"Đang làm"*)
    - `COMPLETED` (*"Hoàn thiện"*)
  - `MissionPriority`:
    - `LOW` (*"Thấp"*)
    - `MEDIUM` (*"Trung bình"*)
    - `HIGH` (*"Cao"*)
  - `BoardStatus`:
    - `ACTIVE` (*"Đang hoạt động"*)
    - `ARCHIVED` (*"Đã lưu trữ"*)

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

> [!NOTE]
> Tuân thủ Rule 18 (RESTful chuẩn hóa), Rule 19 (Đóng gói 100% trong `ResponseEntity<ApiResponse<T>>`), Rule 16 (Phân tách Request & Response DTOs).

### 8.1. Danh Mục Toàn Bộ Endpoints Đã Triển Khai

| STT | HTTP Method | Endpoint | Quyền (PreAuthorize) | Trách nhiệm nghiệp vụ |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `GET` | `/api/mentor/programs` | `ROLE_MENTOR`, `HR`, `ADMIN` | Lấy danh sách Chương trình Mentor đang phụ trách |
| **2** | `GET` | `/api/mentor/programs/{programId}/interns` | `ROLE_MENTOR`, `HR`, `ADMIN` | **Lấy danh sách Thực tập sinh trong Program để Mentor chọn khi giao việc** |
| **3** | `POST` | `/api/programs/{programId}/mission-boards` | `ROLE_MENTOR`, `HR`, `ADMIN` | Mentor tạo MissionBoard trong Program |
| **4** | `POST` | `/api/mission-boards` | `ROLE_MENTOR`, `HR`, `ADMIN` | Mentor tạo MissionBoard trực tiếp (truyền `programId` trong body) |
| **5** | `GET` | `/api/programs/{programId}/mission-boards` | `ROLE_MENTOR`, `HR`, `ADMIN` | Lấy danh sách MissionBoard của Program |
| **6** | `GET` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR`, `HR`, `ADMIN` | Xem chi tiết MissionBoard (bao gồm 3 cột items và assignees) |
| **7** | `PUT` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR`, `HR`, `ADMIN` | Cập nhật thông tin MissionBoard |
| **8** | `DELETE` | `/api/mission-boards/{boardId}` | `ROLE_MENTOR`, `HR`, `ADMIN` | Xóa MissionBoard |
| **9** | `POST` | `/api/mission-boards/{boardId}/items` | `ROLE_MENTOR`, `HR`, `ADMIN` | **Mentor giao việc mới cho Thực tập sinh (chọn 1 hoặc nhiều TTS)** |
| **10** | `PUT` | `/api/mission-items/{itemId}` | `ROLE_MENTOR`, `HR`, `ADMIN` | Cập nhật mục công việc & điều chỉnh danh sách TTS được giao |
| **11** | `PATCH` | `/api/mission-items/{itemId}/status` | `ROLE_MENTOR`, `HR`, `ADMIN` | **Mentor cập nhật trạng thái nghiệm thu (`TODO`, `IN_PROGRESS`, `COMPLETED`)** |
| **12** | `DELETE` | `/api/mission-items/{itemId}` | `ROLE_MENTOR`, `HR`, `ADMIN` | Xóa mục công việc chi tiết |
| **13** | `POST` | `/api/programs/{id}/mentors/{mentorId}` | `ROLE_HR`, `ROLE_ADMIN` | HR/Admin phân công Mentor vào Program |
| **14** | `GET` | `/api/programs/{id}/mentors` | `ROLE_HR`, `ROLE_ADMIN`, `ROLE_MENTOR` | Lấy danh sách Mentor của Program |
| **15** | `DELETE` | `/api/programs/{id}/mentors/{mentorId}` | `ROLE_HR`, `ROLE_ADMIN` | HR/Admin gỡ phân công Mentor khỏi Program |

---

### 8.2. Chi Tiết Payloads Mẫu Chuẩn Hóa

#### 1. Mentor Tạo Mục Công Việc & Giao Việc Cho Thực Tập Sinh: `POST /api/mission-boards/{boardId}/items`

**Request Body:**

```json
{
  "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
  "description": "Fork repository, cấu hình application.yml, chạy Eureka Discovery Server và chụp ảnh màn hình nộp báo cáo.",
  "priority": "HIGH",
  "dueDate": "2026-10-10",
  "internIds": [12, 15, 18]
}
```

**Response Success (201 Created):**

```json
{
  "code": 201,
  "success": true,
  "message": "Tạo mục công việc thành công",
  "data": {
    "id": 101,
    "boardId": 1,
    "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
    "description": "Fork repository, cấu hình application.yml, chạy Eureka Discovery Server và chụp ảnh màn hình nộp báo cáo.",
    "priority": "HIGH",
    "priorityDisplayName": "Cao",
    "status": "TODO",
    "statusDisplayName": "Chưa làm",
    "dueDate": "2026-10-10",
    "isOverdue": false,
    "orderIndex": 0,
    "assignees": [
      {
        "id": 12,
        "userId": 50,
        "internCode": "INT-2026-0012",
        "fullName": "Nguyễn Văn An",
        "email": "an.nguyen@example.com",
        "phone": "0912345678",
        "appliedPosition": "Java Backend Intern"
      },
      {
        "id": 15,
        "userId": 53,
        "internCode": "INT-2026-0015",
        "fullName": "Trần Thị Mai",
        "email": "mai.tran@example.com",
        "phone": "0987654321",
        "appliedPosition": "Frontend React Intern"
      }
    ],
    "createdAt": "2026-10-04T10:30:00",
    "updatedAt": "2026-10-04T10:30:00"
  },
  "timestamp": "2026-10-04T10:30:00"
}
```

#### 2. Mentor Cập Nhật Trạng Thái Nghiệm Thu: `PATCH /api/mission-items/{itemId}/status`

**Request Body:**

```json
{
  "status": "IN_PROGRESS"
}
```

*(Hoặc `"status": "COMPLETED"` khi Mentor đã nghiệm thu hoàn thành)*

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Cập nhật trạng thái công việc thành công",
  "data": {
    "id": 101,
    "boardId": 1,
    "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
    "status": "IN_PROGRESS",
    "statusDisplayName": "Đang làm",
    "isOverdue": false,
    "updatedAt": "2026-10-04T11:00:00"
  },
  "timestamp": "2026-10-04T11:00:00"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

```text
       [ MENTOR ]                                        [ THỰC TẬP SINH ]
           │                                                     │
           ├─► 1. Đăng nhập & Chọn Chương trình đào tạo           │
           │      (GET /api/mentor/programs)                     │
           │                                                     │
           ├─► 2. Xem danh sách TTS trong Program để giao việc   │
           │      (GET /api/mentor/programs/{id}/interns)        │
           │                                                     │
           ├─► 3. Tạo Bảng Nhiệm Vụ (MissionBoard)               │
           │      (POST /api/programs/{id}/mission-boards)       │
           │                                                     │
           ├─► 4. GIAO VIỆC CHO THỰC TẬP SINH (POST items) ──────┼──► Nhận nhiệm vụ (Assignee)
           │      - Nhập tiêu đề, mô tả, hạn chót                │    - Xem deadline & yêu cầu
           │      - CHỌN 1 HOẶC NHIỀU TTS (internIds)            │    - Phối hợp nhóm làm việc
           │      - Hệ thống validate TTS cùng thuộc Program     │
           │                                                     │
           ├─► 5. Chuyển sang "ĐANG LÀM" (IN_PROGRESS) ──────────┼──► Bắt đầu triển khai code
           │                                                     │
           └─► 6. NGHIỆM THU & HOÀN THIỆN (COMPLETED) ◄──────────┴──► Báo cáo kết quả hoàn thành
                  - Review code & đánh giá kết quả
                  - Đánh dấu hoàn thành trên Kanban
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Phòng chống triệt để lỗi N+1 Query (Rule 22):** 100% các truy vấn lấy danh sách hoặc chi tiết Board kèm Items và Assignees phải sử dụng `JOIN FETCH` trong Spring Data JPA (`JOIN FETCH mi.assignees`).
2. **Tuân thủ triệt để Package-by-Feature (Rule 14):**
   - Package `org.example.internservice.mission.*` tự chứa trọn vẹn: `entity/`, `dto/`, `repository/`, `service/`, `controller/`.
   - Package `org.example.internservice.program.*` chứa `ProgramMentor` liên kết.
3. **Tiêu chuẩn Anti-God-Class (Rule 17):** Các class Service không vượt quá 250 - 300 dòng. Đã phân rã độc lập thành `MissionBoardService` và `MissionItemService`.
4. **Constructor Injection an toàn (Rule 21):** Sử dụng `@RequiredArgsConstructor` trên toàn bộ Controller và ServiceImpl; tuyệt đối cấm `@Autowired` trên field.
5. **Quản lý Transaction rõ ràng (Rule 23):** Khai báo `@Transactional(readOnly = true)` ở cấp class của ServiceImpl và `@Transactional` tường minh trên các phương thức ghi/sửa/xóa.
6. **Đóng gói Response chuẩn hóa `ApiResponse<T>` (Rule 19):** 100% endpoints trả về `ResponseEntity<ApiResponse<T>>`.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [x] **AC-1:** Mentor chỉ xem được và chỉ tạo được Bảng nhiệm vụ trong các Program mà mình được phân công phụ trách.
- [x] **AC-2:** Mentor tra cứu được danh sách các Thực tập sinh đang học tập trong Program để sẵn sàng chọn người nhận việc.
- [x] **AC-3:** **Mentor giao việc thành công cho Thực tập sinh:** Chọn được 1 hoặc nhiều TTS trong Program làm assignees cho một mục công việc chi tiết.
- [x] **AC-4:** **Chặn giao việc vô chủ:** Hệ thống từ chối tạo công việc nếu Mentor không chọn bất kỳ TTS nào (`internIds` rỗng).
- [x] **AC-5:** **Chặn giao việc sai Program:** Hệ thống từ chối ngay lập tức (lỗi `400 BAD_REQUEST`) nếu Mentor chọn TTS không thuộc Program của Bảng nhiệm vụ.
- [x] **AC-6:** **Điều phối trạng thái & Nghiệm thu:** Mentor cập nhật trạng thái mục công việc thành công giữa 3 trạng thái: `TODO` ("chưa làm"), `IN_PROGRESS` ("đang làm"), `COMPLETED` ("hoàn thiện").
- [x] **AC-7:** **Tải dữ liệu hiệu năng cao:** API lấy chi tiết Board trả về đầy đủ items và assignees trong 1 truy vấn duy nhất, không bị lỗi N+1 Query JPA.
- [x] **AC-8:** **Toàn vẹn dữ liệu khi xóa:** Khi xóa mục công việc, quan hệ gán việc trong `mission_item_assignees` tự động được dọn sạch, không để lại rác dữ liệu.

---

## 12. Unit & Integration Test Cases Checklist

- [x] **UT-BE-01:** `createBoard_Success`: Mentor tạo board thành công cho Program phụ trách.
- [x] **UT-BE-02:** `createBoard_Fail_NotMentorOfProgram`: Ném `AccessDeniedException` khi Mentor không thuộc Program.
- [x] **UT-BE-03:** `getBoardsByProgram_Success`: Mentor lấy danh sách board kèm số lượng thống kê task chính xác.
- [x] **UT-BE-04:** `createItem_Success_SingleIntern`: Mentor giao việc cho 1 TTS thành công.
- [x] **UT-BE-05:** `createItem_Success_MultipleInterns`: Mentor giao việc cho nhiều TTS cùng lúc thành công.
- [x] **UT-BE-06:** `createItem_Fail_InternNotInProgram`: Ném `BadRequestException` khi chọn TTS không thuộc Program.
- [x] **UT-BE-07:** `updateItemStatus_Success`: Mentor cập nhật trạng thái công việc sang `IN_PROGRESS` và `COMPLETED`.
- [x] **UT-BE-08:** `deleteItem_Success`: Mentor xóa mục công việc, dọn dẹp sạch quan hệ liên kết assignees.

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

### 13.1. Entities & Enums

- [x] `org.example.internservice.program.entity.ProgramMentor.java` (kế thừa `BaseEntity`)
- [x] `org.example.internservice.mission.entity.MissionBoard.java` (kế thừa `BaseEntity`)
- [x] `org.example.internservice.mission.entity.MissionItem.java` (kế thừa `BaseEntity`)
- [x] `org.example.internservice.mission.entity.enums.MissionItemStatus.java` (`TODO`, `IN_PROGRESS`, `COMPLETED`)
- [x] `org.example.internservice.mission.entity.enums.MissionPriority.java` (`LOW`, `MEDIUM`, `HIGH`)
- [x] `org.example.internservice.mission.entity.enums.BoardStatus.java` (`ACTIVE`, `ARCHIVED`)

### 13.2. DTOs

- [x] `mission/dto/request/CreateMissionBoardRequest.java`
- [x] `mission/dto/request/UpdateMissionBoardRequest.java`
- [x] `mission/dto/request/CreateMissionItemRequest.java` (có `@NotEmpty Set<Long> internIds`)
- [x] `mission/dto/request/UpdateMissionItemRequest.java`
- [x] `mission/dto/request/UpdateItemStatusRequest.java`
- [x] `mission/dto/response/MissionBoardResponse.java`
- [x] `mission/dto/response/MissionBoardDetailResponse.java`
- [x] `mission/dto/response/MissionItemResponse.java`
- [x] `mission/dto/response/AssigneeResponse.java`
- [x] `mission/dto/response/MentorProgramResponse.java`

### 13.3. Repositories

- [x] `program/repository/ProgramMentorRepository.java`
- [x] `mission/repository/MissionBoardRepository.java`
- [x] `mission/repository/MissionItemRepository.java` (chống N+1 query với `JOIN FETCH`)

### 13.4. Services

- [x] `mission/service/MissionBoardService.java` & `MissionBoardServiceImpl.java`
- [x] `mission/service/MissionItemService.java` & `MissionItemServiceImpl.java`

### 13.5. Controllers

- [x] `mission/controller/MissionBoardController.java`
- [x] `mission/controller/MissionItemController.java`
- [x] `mission/controller/MentorProgramController.java`
- [x] `program/controller/ProgramController.java` (bổ sung API phân công Mentor)

### 13.6. Gateway Route & Configuration

- [x] `config-repo-local/api-gateway.yml`: Route cho `/api/mission-boards/**`, `/api/mission-items/**`, `/api/mentor/**`.

### 13.7. Unit Tests

- [x] `src/test/java/org/example/internservice/mission/service/MissionBoardServiceTest.java`
- [x] `src/test/java/org/example/internservice/mission/service/MissionItemServiceTest.java`

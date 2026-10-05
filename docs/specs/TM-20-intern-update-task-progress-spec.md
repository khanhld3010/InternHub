# Specification: Thực Tập Sinh - Chuyển Đổi Trạng Thái Nhiệm Vụ Trên Bảng Kanban (TM-20)

> **Trạng thái:** ACTIVE / IMPLEMENTED & VERIFIED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md`  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-20](https://robluccibn9935.atlassian.net/browse/TM-20)  
> **Tiêu đề Jira:** *Intern - Chuyển đổi trạng thái nhiệm vụ Kanban (Mission Kanban Transition & Task Execution)*  
> **Nhánh Git dự kiến:** `feature/TM-20/intern-update-task-progress`  
> **Cấp độ thay đổi (Change Level):** **L3** (Mở rộng quyền cập nhật trạng thái Kanban `MissionItem` cho `ROLE_INTERN`, cơ chế bảo vệ quyền sở hữu phân công Assignee chống IDOR, API tra cứu Kanban 3 cột cá nhân, lưu vết lịch sử chuyển đổi trạng thái và kích hoạt thông báo thời gian thực đến Mentor qua `NotificationEventDispatcher`).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, đặc biệt Rule 14, 15, 16, 17, 18, 19, 21, 22, 23, 26, 30, 31).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất (Tuân thủ Rule 30).

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-04 | AI Senior Pair-Programmer | `TM-20` | Tạo mới | Thiết kế đặc tả ban đầu theo mô hình theo dõi % tiến độ chi tiết (0% - 100%) và bảng nhật ký tiến độ định kỳ. |
| **v1.1.0** | 2026-10-04 | AI Senior Pair-Programmer & User | `TM-20` | Đơn giản hóa & Tinh gọn luồng nghiệp vụ | **Loại bỏ hoàn toàn trường tỷ lệ % tiến độ (0% - 100%)**. Người dùng xác nhận: Thực tập sinh không cần ước lượng số % trừu tượng, thay vào đó tập trung vào trải nghiệm chuyển đổi trạng thái nhiệm vụ trên bảng Kanban 3 nấc chuẩn (`TODO` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`). Tinh giản tối đa cấu trúc dữ liệu, tái sử dụng trường `status` sẵn có của `MissionItem`, hỗ trợ đính kèm link nộp bài/ghi chú tùy chọn khi hoàn thành. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Thực Tập Sinh Chuyển Đổi Trạng Thái Nhiệm Vụ Trên Bảng Kanban (Intern Mission Kanban Transition & Task Execution).
- **Jira Ticket:** [TM-20](https://robluccibn9935.atlassian.net/browse/TM-20)
- **Tuyên Ngôn Nghiệp Vụ Cốt Lõi (Core Business Statement):**
  > **"THỰC TẬP SINH LÀ NGƯỜI NHẬN VIỆC (ASSIGNEE) TRỰC TIẾP THỰC THI VÀ ĐIỀU PHỐI CÔNG VIỆC TRÊN BẢNG KANBAN 3 NẤC"**
  >
  > Trong quy trình quản trị nhiệm vụ đào tạo:
  > - **Mentor ([TM-19](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)):** Đóng vai trò là **Người giao việc (Assigner & Delegator)** — thiết lập Bảng nhiệm vụ (`MissionBoard`), phân rã công việc chi tiết (`MissionItem`), đặt hạn chót và giao cho Thực tập sinh.
  > - **Thực tập sinh ([TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)):** Đóng vai trò là **Người thực thi (Executor)**. Thay vì phải cập nhật các con số % phức tạp (10%, 25%, 70%), Thực tập sinh chỉ cần quản lý công việc của mình thông qua việc **chuyển đổi giữa 3 cột trạng thái Kanban rõ ràng**:
  >   1. **Cột 1: "Chưa làm" (`TODO`):** Nhiệm vụ Mentor mới giao, TTS tiếp nhận yêu cầu và tài liệu.
  >   2. **Cột 2: "Đang làm" (`IN_PROGRESS`):** TTS bắt đầu bắt tay vào nghiên cứu, thiết kế và viết code (chuyển từ `TODO` sang `IN_PROGRESS`).
  >   3. **Cột 3: "Hoàn thiện" (`COMPLETED`):** TTS đã hoàn thành sản phẩm/code, có thể đính kèm link nộp bài (GitHub PR, Figma, Doc) và ghi chú hoàn thành để chuyển sang `COMPLETED`, sẵn sàng cho Mentor kiểm tra và nghiệm thu.

- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082):
     - Mở rộng phân quyền endpoint cập nhật trạng thái `MissionItem` cho phép `ROLE_INTERN` (kèm xác thực người nhận việc Assignee).
     - Thêm trường tùy chọn `submission_url` và `completion_note` trên thực thể `MissionItem` để TTS nộp sản phẩm khi hoàn thành.
     - Cung cấp API tra cứu danh sách nhiệm vụ cá nhân và bảng Kanban 3 cột chuyên biệt cho Intern (`/api/mission-items/my-missions`, `/api/mission-items/my-missions/kanban`).
     - Tích hợp `NotificationEventDispatcher` để bắn thông báo thời gian thực đến Mentor khi TTS bắt đầu làm hoặc hoàn thành công việc.
  2. `api-gateway` (Port 8080):
     - Đảm bảo định tuyến thông suốt các endpoints `/api/mission-items/**` và `/api/intern/mission-items/**` về `intern-and-program-service`.

- **Target Users & Roles:**
  - **`ROLE_INTERN` (Người Thực Hiện - Primary Actor):**
    - Tra cứu các công việc mà mình được phân công (`assignees`).
    - Kéo / chuyển đổi thẻ công việc giữa 3 cột: `TODO` $\leftrightarrow$ `IN_PROGRESS` $\leftrightarrow$ `COMPLETED`.
    - Đính kèm link nộp bài và ghi chú tóm tắt khi hoàn thành nhiệm vụ.
  - **`ROLE_MENTOR` (Người Giám Sát & Nghiệm Thu - Assigner & Evaluator):**
    - Nhận thông báo thời gian thực khi TTS chuyển trạng thái công việc.
    - Xem bảng Kanban và nghiệm thu kết quả khi task chuyển sang `COMPLETED`.
  - **`ROLE_HR` & `ROLE_ADMIN` (Giám Sát & Quản Trị Hệ Thống):**
    - Xem tiến độ thực hiện công việc trên toàn bộ các chương trình thực tập.

- **Change Level:** **L3** (Phân quyền nghiệp vụ RBAC cấp Assignee chống IDOR, API Kanban cá nhân, cập nhật trạng thái Kanban, tích hợp gửi thông báo thời gian thực).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Trải nghiệm Kanban trực quan & tinh giản (Intuitive & Frictionless Kanban UX):** Loại bỏ gánh nặng nhập liệu số % tiến độ. TTS chỉ cần một thao tác kéo thả thẻ công việc trên Kanban hoặc chọn nút chuyển trạng thái một cách tự nhiên, nhanh chóng.
2. **Minh bạch trạng thái thực thi (Clear Execution Status):** Giúp Mentor và đồng đội nhìn vào Bảng nhiệm vụ là nhận diện được ngay đầu việc nào đang được làm, đầu việc nào chưa bắt đầu và đầu việc nào đã hoàn thành.
3. **Kênh nộp kết quả công việc tập trung (Centralized Submission Link):** Cho phép TTS gửi đường link sản phẩm thực tế (GitHub Pull Request, Google Docs báo cáo, Figma design...) trực tiếp trên thẻ công việc khi hoàn thành.
4. **Vòng phản hồi tức thì (Instant Realtime Feedback Loop):** Khi TTS chuyển task sang `COMPLETED`, Mentor nhận ngay thông báo để tiến hành review code và nghiệm thu kết quả, không làm nghẽn tiến độ đào tạo.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)

- **Tra cứu công việc cá nhân của Thực tập sinh:**
  - `GET /api/mission-items/my-missions`: Lấy danh sách nhiệm vụ được giao cho TTS đăng nhập, hỗ trợ lọc theo `status` (`TODO`, `IN_PROGRESS`, `COMPLETED`), `boardId`, `priority`, tìm kiếm từ khóa `keyword`, kiểm tra quá hạn `isOverdue`, hỗ trợ phân trang 0-indexed.
  - `GET /api/mission-items/my-missions/kanban`: Lấy danh sách nhiệm vụ cá nhân phân nhóm thành 3 danh sách cột Kanban: `todoItems`, `inProgressItems`, `completedItems`.
  - `GET /api/mission-items/{itemId}`: Xem chi tiết nhiệm vụ (tiêu đề, mô tả của Mentor, độ ưu tiên, hạn chót, đồng đội cùng làm việc, trạng thái, link nộp bài).

- **Chuyển đổi trạng thái Kanban & Nộp bài:**
  - `PATCH /api/mission-items/{itemId}/status`: Cho phép Thực tập sinh cập nhật trạng thái nhiệm vụ:
    + Chuyển sang `IN_PROGRESS` khi bắt đầu triển khai công việc.
    + Chuyển sang `COMPLETED` khi đã xong việc (hỗ trợ truyền thêm `submissionUrl` và `completionNote`).
    + Chuyển ngược về `TODO` hoặc `IN_PROGRESS` nếu cần điều chỉnh lại.
  - Tự động kích hoạt sự kiện gửi thông báo cho Mentor phụ trách qua `NotificationEventDispatcher`.

- **Kiểm soát bảo mật & Phân quyền Assignee:**
  - Kiểm tra IDOR: Thực tập sinh chỉ được chuyển trạng thái của những `MissionItem` mà ID của mình có trong danh sách `assignees`.
  - Chặn thao tác nếu hồ sơ thực tập sinh đã bị đình chỉ (`TERMINATED`), hủy bỏ (`REJECTED`) hoặc chưa bắt đầu (`PENDING`).

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)

- **TUYỆT ĐỐI KHÔNG TÍNH TOÁN HAY THEO DÕI TỶ LỆ 0% - 100%:** Nghiệp vụ chỉ xoay quanh 3 nấc trạng thái Kanban rõ ràng (`TODO`, `IN_PROGRESS`, `COMPLETED`).
- **TTS KHÔNG ĐƯỢC PHÉP SỬA YÊU CẦU CỦA MENTOR:** TTS không được sửa tiêu đề (`title`), mô tả yêu cầu (`description`), độ ưu tiên (`priority`), hạn chót (`dueDate`) hay tự ý thêm/bớt người nhận việc (`assignees`).
- **TTS KHÔNG ĐƯỢC XÓA CÔNG VIỆC HOẶC BẢNG NHIỆM VỤ:** Quyền xóa thuộc về Mentor / HR / Admin ở TM-19.
- **Không can thiệp Frontend:** Tuân thủ 100% Rule 7 (Boundary Isolation).
- **Không chạy SQL phá hoại & không tự commit/push Git:** Tuân thủ Rule 8 và Rule 4.

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Lỗ hổng IDOR - TTS chuyển trạng thái task của người khác

- **Vấn đề:** Thực tập sinh A gửi request chuyển trạng thái cho `itemId = 88` — một công việc do Mentor giao riêng cho Thực tập sinh B, A hoàn toàn không có tên trong `assignees`.
- **Giải pháp:** Service kiểm tra chặt chẽ danh sách `assignees`:

```java
InternProfile currentIntern = internProfileRepository.findByUserId(userDetails.getUserId())
        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh cho tài khoản hiện tại"));

boolean isAssignee = item.getAssignees().stream()
        .anyMatch(assignee -> assignee.getId().equals(currentIntern.getId()));

if (!isAssignee && !isMentorOrAdmin(userDetails, item)) {
    throw new AccessDeniedException("Bạn không được phân công thực hiện nhiệm vụ này, không có quyền chuyển trạng thái");
}
```

### 4.2. Edge Case 2: Trạng thái Kanban không hợp lệ hoặc gửi sai Enum

- **Vấn đề:** Client gửi giá trị trạng thái không nằm trong 3 trạng thái chuẩn (ví dụ `"DONE"`, `"CANCELLED"`, `"PENDING"`).
- **Giải pháp:**
  - Bean Validation và Exception Handler bắt `HttpMessageNotReadableException` / `IllegalArgumentException`.
  - Service chỉ chấp nhận 3 giá trị của `MissionItemStatus`: `TODO`, `IN_PROGRESS`, `COMPLETED`. Nếu null hoặc sai, ném `BadRequestException("Trạng thái công việc không hợp lệ. Chỉ chấp nhận TODO, IN_PROGRESS, hoặc COMPLETED")`.

### 4.3. Edge Case 3: Trạng thái hồ sơ Thực tập sinh không còn hoạt động

- **Vấn đề:** TTS đã bị chấm dứt hợp đồng (`TERMINATED`), bị từ chối (`REJECTED`) hoặc hồ sơ đang ở trạng thái `ON_HOLD`, nhưng cố tình gọi API chuyển trạng thái task.
- **Giải pháp:**
  ```java
  if (currentIntern.getStatus() != InternStatus.INTERNING && currentIntern.getStatus() != InternStatus.APPROVED) {
      throw new BadRequestException("Hồ sơ thực tập sinh đang ở trạng thái [" + currentIntern.getStatus() + "], không thể thực hiện công việc");
  }
  ```

### 4.4. Edge Case 4: Đồng đội cùng chuyển trạng thái trên task làm chung (Pair / Group Concurrency)

- **Vấn đề:** Task được Mentor giao cho 2 bạn (An và Mai). An bấm chuyển sang `COMPLETED` trong khi Mai cũng đang xem task ở trạng thái `IN_PROGRESS`.
- **Giải pháp:**
  - Cả 2 thành viên đều là `assignee` hợp lệ nên đều có quyền chuyển trạng thái.
  - Sử dụng giao dịch `@Transactional` cô lập. Trạng thái cuối cùng lưu trong CSDL là trạng thái mới nhất.
  - Trả về đối tượng `MissionItemResponse` đã cập nhật để client cập nhật UI tức thời.

### 4.5. Edge Case 5: URL nộp bài không hợp lệ hoặc chứa mã độc XSS

- **Vấn đề:** Khi chuyển sang `COMPLETED`, TTS nhập `submissionUrl` là một chuỗi nguy hiểm như `javascript:alert(1)`.
- **Giải pháp:**
  - Validate định dạng URL tại DTO:
    `@Pattern(regexp = "^(https?://).*", message = "Liên kết nộp bài phải là đường dẫn URL hợp lệ bắt đầu bằng http:// hoặc https://")`
  - Giới hạn độ dài `submissionUrl` tối đa 500 ký tự.

### 4.6. Edge Case 6: Lỗi N+1 Query JPA khi tải danh sách Kanban cá nhân

- **Vấn đề:** Khi TTS tải bảng Kanban cá nhân, nếu truy vấn lười (Lazy Loading) từng `board`, `program` và `assignees` sẽ phát sinh hàng chục câu query làm nghẽn connection pool.
- **Giải pháp:** Bắt buộc dùng `JOIN FETCH` trong Repository:
  ```java
  @Query("SELECT DISTINCT mi FROM MissionItem mi " +
         "JOIN FETCH mi.board b " +
         "JOIN FETCH b.program p " +
         "LEFT JOIN FETCH mi.assignees a " +
         "WHERE :internId IN (SELECT i.id FROM mi.assignees i) " +
         "ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
  List<MissionItem> findAssignedItemsByInternIdWithDetails(@Param("internId") Long internId);
  ```

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tra cứu nhiệm vụ cá nhân của TTS - My Assigned Tasks):**
  - Thực tập sinh đăng nhập có thể xem danh sách tất cả các mục công việc mà mình được phân công trong chương trình đào tạo.
  - Hỗ trợ lọc theo trạng thái Kanban (`TODO`, `IN_PROGRESS`, `COMPLETED`), theo Bảng nhiệm vụ (`boardId`), theo mức độ ưu tiên (`priority`), hoặc tìm kiếm từ khóa.
  - Tự động hiển thị cờ cảnh báo quá hạn (`isOverdue = true`) nếu công việc chưa hoàn thành mà ngày hiện tại đã vượt quá hạn chót (`dueDate`).

- **FR-2 (Bảng Kanban 3 Cột Cá Nhân):**
  - Cung cấp API chuyên biệt trả về danh mục nhiệm vụ của TTS được nhóm sẵn thành 3 danh sách:
    + `todoItems` (Chưa làm)
    + `inProgressItems` (Đang làm)
    + `completedItems` (Hoàn thiện)
  - Kèm tổng số lượng nhiệm vụ và tỷ lệ hoàn thành tổng quan (ví dụ: hoàn thành 2/5 tasks = 40%).

- **FR-3 (Xem Chi Tiết Mục Công Việc):**
  - TTS có thể xem đầy đủ chi tiết mục công việc: Tiêu đề, Mô tả yêu cầu từ Mentor, Mức độ ưu tiên, Deadline, Thông tin Mentor phụ trách, Danh sách các bạn cùng tham gia (`assignees`), Trạng thái hiện tại, Link nộp bài và Ghi chú hoàn thành (nếu có).

- **FR-4 (Chuyển Đổi Trạng Thái Kanban - Update Kanban Status):**
  - TTS có thể chuyển đổi trạng thái công việc bất cứ lúc nào:
    + Từ `TODO` sang `IN_PROGRESS` khi bắt đầu làm việc.
    + Từ `IN_PROGRESS` sang `COMPLETED` khi hoàn thành (có thể đính kèm link nộp bài `submissionUrl` và ghi chú `completionNote`).
    + Chuyển ngược lại giữa các trạng thái nếu cần điều chỉnh hoặc làm lại.

- **FR-5 (Kích Hoạt Thông Báo Thời Gian Thực Đến Mentor):**
  - Khi TTS chuyển trạng thái công việc, hệ thống tự động gửi thông báo cho Mentor phụ trách Bảng nhiệm vụ:
    + Chuyển sang `IN_PROGRESS`: *"TTS {internName} đã bắt đầu thực hiện nhiệm vụ: {taskTitle}."*
    + Chuyển sang `COMPLETED`: *"TTS {internName} đã hoàn thành nhiệm vụ: {taskTitle}. Vui lòng nghiệm thu."*

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Ràng buộc Quyền Sở Hữu Phân Công - Assignee Ownership):**
  Thực tập sinh chỉ được phép chuyển trạng thái đối với các `MissionItem` mà chính mình nằm trong danh sách `assignees`. Nếu không, hệ thống ném `403 FORBIDDEN`.
- **BR-2 (Chuẩn Hóa Duy Nhất 3 Trạng Thái Kanban):**
  Hệ thống chỉ chấp nhận 3 trạng thái duy nhất:
  - `TODO` ("Chưa làm")
  - `IN_PROGRESS` ("Đang làm")
  - `COMPLETED` ("Hoàn thiện")
  Tuyệt đối không sử dụng tỷ lệ % tiến độ và không sinh thêm trạng thái trung gian.
- **BR-3 (Bất Biến Yêu Cầu Công Việc - Immutable Requirements):**
  TTS không thể thay đổi `title`, `description`, `dueDate`, `priority`, `orderIndex` và danh sách `assignees`. Chỉ Mentor phụ trách mới có quyền sửa các trường này (theo TM-19).
- **BR-4 (Đồng Đội Cùng Nhận Việc - Pair/Team Collaboration):**
  Khi một công việc được giao cho nhiều TTS, bất kỳ ai trong danh sách `assignees` đều có thể bấm bắt đầu (`IN_PROGRESS`) hoặc báo cáo hoàn thành (`COMPLETED`) cho nhóm.
- **BR-5 (Thông Báo Tự Động Đến Mentor):**
  Mỗi sự kiện chuyển trạng thái sang `IN_PROGRESS` hoặc `COMPLETED` đều kích hoạt gửi thông báo tự động đến tài khoản của Mentor phụ trách Board đó.

---

## 7. Data Model (Mô Hình Dữ Liệu)

> [!NOTE]
> **Tuân thủ triệt để Rule 31 (Reuse First, Zero Unnecessary Redundancy):**
> - **Tái sử dụng 100% cấu trúc đã có từ TM-19:**
>   - Bảng `mission_items` với cột `status` (`TODO`, `IN_PROGRESS`, `COMPLETED`) đã tồn tại sẵn!
>   - Bảng liên kết `mission_item_assignees` kết nối `MissionItem` với `InternProfile`.
> - **Mở rộng tối thiểu, không tạo bảng thừa:**
>   - Bổ sung 2 cột tùy chọn trên bảng `mission_items` để lưu link nộp sản phẩm và ghi chú hoàn thành của TTS:
>     + `submission_url VARCHAR(500) NULL` (link GitHub PR, tài liệu...)
>     + `completion_note TEXT NULL` (ghi chú kết quả khi hoàn thành)
>     + `submitted_at DATETIME NULL` (thời điểm TTS nộp hoàn thành)
>   - **HOÀN TOÀN KHÔNG TẠO CỘT `progress_percent` VÀ KHÔNG TẠO BẢNG PHỨC TẠP KHÔNG CẦN THIẾT.**

### 7.1. Script Bổ Sung Cột Vào Bảng `mission_items`

```sql
-- Bổ sung trường phục vụ nộp bài & nghiệm thu cho Thực tập sinh
ALTER TABLE mission_items
ADD COLUMN submission_url VARCHAR(500) NULL AFTER due_date,
ADD COLUMN completion_note TEXT NULL AFTER submission_url,
ADD COLUMN submitted_at DATETIME NULL AFTER completion_note;
```

### 7.2. Cập Nhật JPA Entity Mapping (`MissionItem.java`)

```java
@Entity
@Table(name = "mission_items", indexes = {
    @Index(name = "idx_item_board_status", columnList = "board_id, status"),
    @Index(name = "idx_item_due_date", columnList = "due_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private MissionBoard board;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private MissionPriority priority = MissionPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MissionItemStatus status = MissionItemStatus.TODO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "submission_url", length = 500)
    private String submissionUrl;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "mission_item_assignees",
        joinColumns = @JoinColumn(name = "item_id"),
        inverseJoinColumns = @JoinColumn(name = "intern_id")
    )
    @Builder.Default
    private Set<InternProfile> assignees = new HashSet<>();
}
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

> [!NOTE]
> Tuân thủ Rule 18 (RESTful chuẩn hóa), Rule 19 (Đóng gói 100% trong `ResponseEntity<ApiResponse<T>>`), Rule 16 (Phân tách Request & Response DTOs).

### 8.1. Danh Mục Toàn Bộ Endpoints Cho TM-20

| STT | HTTP Method | Endpoint | Quyền (PreAuthorize) | Trách nhiệm nghiệp vụ |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `GET` | `/api/mission-items/my-missions` | `ROLE_INTERN` | Lấy danh sách nhiệm vụ được giao cho Thực tập sinh đăng nhập (lọc & phân trang) |
| **2** | `GET` | `/api/mission-items/my-missions/kanban` | `ROLE_INTERN` | Lấy danh sách nhiệm vụ cá nhân phân nhóm 3 cột Kanban (`TODO`, `IN_PROGRESS`, `COMPLETED`) |
| **3** | `GET` | `/api/mission-items/{itemId}` | `ROLE_INTERN`, `ROLE_MENTOR`, `HR`, `ADMIN` | Xem chi tiết mục công việc (kèm assignees, link nộp bài, ghi chú hoàn thành) |
| **4** | `PATCH` | `/api/mission-items/{itemId}/status` | `ROLE_INTERN`, `ROLE_MENTOR`, `HR`, `ADMIN` | **Thực tập sinh (hoặc Mentor) chuyển đổi trạng thái Kanban của công việc** |

*(Hỗ trợ các bí danh định tuyến tương đương qua Gateway: `/api/intern/mission-items`, `/api/intern/mission-items/kanban`, `/api/intern/mission-items/{itemId}/status`)*

---

### 8.2. Chi Tiết Payloads Mẫu Chuẩn Hóa

#### 1. Lấy danh sách nhiệm vụ cá nhân dạng Kanban 3 cột: `GET /api/mission-items/my-missions/kanban`

**Response Success (200 OK):**
```json
{
  "code": 200,
  "success": true,
  "message": "Lấy bảng nhiệm vụ cá nhân thành công",
  "data": {
    "todoItems": [
      {
        "id": 102,
        "boardId": 1,
        "boardTitle": "Tuần 1: Khởi động & Môi trường",
        "title": "Nghiên cứu kiến trúc Microservices và Eureka Client",
        "priority": "MEDIUM",
        "priorityDisplayName": "Trung bình",
        "status": "TODO",
        "statusDisplayName": "Chưa làm",
        "dueDate": "2026-10-12",
        "isOverdue": false,
        "assignees": [
          {
            "id": 12,
            "fullName": "Nguyễn Văn An",
            "internCode": "INT-2026-0012"
          }
        ]
      }
    ],
    "inProgressItems": [
      {
        "id": 101,
        "boardId": 1,
        "boardTitle": "Tuần 1: Khởi động & Môi trường",
        "title": "Cài đặt Docker, Redis và chạy Discovery Server trên máy cá nhân",
        "priority": "HIGH",
        "priorityDisplayName": "Cao",
        "status": "IN_PROGRESS",
        "statusDisplayName": "Đang làm",
        "dueDate": "2026-10-10",
        "isOverdue": false,
        "assignees": [
          {
            "id": 12,
            "fullName": "Nguyễn Văn An",
            "internCode": "INT-2026-0012"
          },
          {
            "id": 15,
            "fullName": "Trần Thị Mai",
            "internCode": "INT-2026-0015"
          }
        ]
      }
    ],
    "completedItems": [
      {
        "id": 98,
        "boardId": 1,
        "boardTitle": "Tuần 1: Khởi động & Môi trường",
        "title": "Đọc tài liệu quy chuẩn dự án AGENTS.md và hoàn thành Onboarding Quiz",
        "priority": "LOW",
        "priorityDisplayName": "Thấp",
        "status": "COMPLETED",
        "statusDisplayName": "Hoàn thiện",
        "dueDate": "2026-10-03",
        "isOverdue": false,
        "submissionUrl": "https://github.com/internhub/pull/1",
        "completionNote": "Đã làm bài quiz đạt 100/100 điểm.",
        "submittedAt": "2026-10-03T16:00:00"
      }
    ],
    "totalCount": 3,
    "todoCount": 1,
    "inProgressCount": 1,
    "completedCount": 1
  },
  "timestamp": "2026-10-04T15:30:00"
}
```

---

#### 2. Thực tập sinh chuyển đổi trạng thái Kanban: `PATCH /api/mission-items/{itemId}/status`

**Request Headers:**
```http
Content-Type: application/json
Authorization: Bearer <jwt-token-cua-intern>
```

**Trường hợp A: Bắt đầu làm việc (Chuyển sang `IN_PROGRESS`):**
```json
{
  "status": "IN_PROGRESS"
}
```

**Trường hợp B: Hoàn thành công việc & Nộp bài (Chuyển sang `COMPLETED`):**
```json
{
  "status": "COMPLETED",
  "submissionUrl": "https://github.com/internhub/pull/15",
  "completionNote": "Đã hoàn thành cấu hình Dockerfile và docker-compose, chạy thành công Discovery Server trên máy cá nhân."
}
```

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
    "status": "COMPLETED",
    "statusDisplayName": "Hoàn thiện",
    "priority": "HIGH",
    "priorityDisplayName": "Cao",
    "dueDate": "2026-10-10",
    "isOverdue": false,
    "submissionUrl": "https://github.com/internhub/pull/15",
    "completionNote": "Đã hoàn thành cấu hình Dockerfile và docker-compose, chạy thành công Discovery Server trên máy cá nhân.",
    "submittedAt": "2026-10-04T16:30:00",
    "updatedAt": "2026-10-04T16:30:00"
  },
  "timestamp": "2026-10-04T16:30:00"
}
```

---

#### 3. Phản hồi lỗi điển hình (Error Responses)

**Khi TTS không thuộc danh sách Assignees (HTTP 403 Forbidden):**
```json
{
  "code": 403,
  "success": false,
  "message": "Bạn không được phân công thực hiện nhiệm vụ này, không có quyền chuyển trạng thái",
  "errors": ["Access Denied"],
  "timestamp": "2026-10-04T16:35:00"
}
```

**Khi trạng thái gửi lên không hợp lệ (HTTP 400 Bad Request):**
```json
{
  "code": 400,
  "success": false,
  "message": "Trạng thái công việc không hợp lệ",
  "errors": ["Trạng thái chỉ chấp nhận TODO, IN_PROGRESS, hoặc COMPLETED"],
  "timestamp": "2026-10-04T16:36:00"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

### 9.1. Luồng Kanban Kéo Thả 3 Cột Giữa Intern và Mentor

```text
    [ THỰC TẬP SINH (INTERN) ]          [ BACKEND SERVICE ]                 [ MENTOR ]
                 │                               │                              │
                 ├─► 1. Đăng nhập hệ thống       │                              │
                 │                               │                              │
                 ├─► 2. GET /my-missions/kanban ─┼───┐                          │
                 │                               │   │ Nạp 3 cột Kanban         │
                 │   ◄── Trả về 3 cột Kanban ────┼───┘ (JOIN FETCH chống N+1)   │
                 │                               │                              │
                 ├─► 3. BẮT ĐẦU LÀM VIỆC         │                              │
                 │      (Kéo TODO ➔ IN_PROGRESS) │                              │
                 │      PATCH /{id}/status       │                              │
                 │      {"status": "IN_PROGRESS"}│                              │
                 │   ◄── Cập nhật thành công ────┼───► Gửi thông báo realtime ──┼──► "TTS đã bắt đầu làm task"
                 │                               │     (NotificationDispatcher) │
                 │                               │                              │
                 ├─► 4. HOÀN THÀNH NHIỆM VỤ      │                              │
                 │      (Kéo sang COMPLETED)     │                              │
                 │      PATCH /{id}/status       │                              │
                 │      {"status": "COMPLETED",  │                              │
                 │       "submissionUrl": "...", │                              │
                 │       "completionNote": "..."}│                              │
                 │   ◄── Ghi nhận hoàn thiện ────┼───► BẮN THÔNG BÁO NGHIỆM THU ┼──► "TTS đã hoàn thành task,
                 │                               │     (NotificationDispatcher) │    vui lòng nghiệm thu!"
                 │                               │                              │
                 │                               │   ◄── Mentor xem bài nộp ────┤
                 │                               │       và nghiệm thu          │
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Phòng chống triệt để lỗi N+1 Query JPA (Rule 22):**
   - 100% truy vấn lấy danh sách Kanban cá nhân phải sử dụng `JOIN FETCH` để tải `MissionBoard`, `InternshipProgram` và `Set<InternProfile> assignees` trong đúng 1 query duy nhất.
2. **Tiêu chuẩn Anti-God-Class (Rule 17):**
   - Tách biệt logic Kanban cá nhân của Thực tập sinh vào `InternMissionService` & `InternMissionController` độc lập, không làm phình to `MissionItemServiceImpl` vượt trần 300 dòng.
3. **Quản lý Giao dịch an toàn (`@Transactional`) (Rule 23):**
   - Khai báo `@Transactional(readOnly = true)` tại Class cấp ServiceImpl và `@Transactional` tường minh tại method chuyển trạng thái.
4. **Constructor Injection an toàn (Rule 21):**
   - Sử dụng `@RequiredArgsConstructor` từ Lombok; cấm dùng `@Autowired` trên field.
5. **Hiệu năng & Thời gian đáp ứng:**
   - Thời gian đáp ứng API chuyển đổi trạng thái Kanban < 80ms.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [x] **AC-1 (Tra Cứu Nhiệm Vụ Hợp Lệ):** TTS đăng nhập chỉ xem được danh sách công việc mà mình được phân công (`assignees`).
- [x] **AC-2 (Hiển Thị Bảng Kanban 3 Cột):** API Kanban cá nhân trả về chính xác 3 cột: `todoItems`, `inProgressItems`, `completedItems` kèm tổng số lượng.
- [x] **AC-3 (Chặn Quyền IDOR):** Nếu TTS gọi API chuyển trạng thái cho một task mà mình không thuộc danh sách `assignees`, hệ thống trả về lỗi `403 FORBIDDEN`.
- [x] **AC-4 (Chuyển Sang "Đang làm"):** TTS chuyển trạng thái sang `IN_PROGRESS` thành công khi bắt đầu làm việc.
- [x] **AC-5 (Chuyển Sang "Hoàn thiện" & Nộp bài):** TTS chuyển trạng thái sang `COMPLETED` thành công, lưu lại `submissionUrl`, `completionNote` và `submittedAt`.
- [x] **AC-6 (Kích Hoạt Thông Báo Cho Mentor):** Khi TTS chuyển trạng thái công việc sang `IN_PROGRESS` hoặc `COMPLETED`, Mentor phụ trách Board nhận được thông báo thời gian thực qua hệ thống.
- [x] **AC-7 (Chặn TTS Đình Chỉ):** Nếu tài khoản TTS có hồ sơ ở trạng thái `TERMINATED` hoặc `REJECTED`, hệ thống từ chối thao tác với mã `400 BAD_REQUEST`.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Unit Tests (`InternMissionServiceTest`)
- [x] **UT-BE-01:** `getMyKanban_Success`: Lấy dữ liệu 3 cột Kanban cho TTS thành công.
- [x] **UT-BE-02:** `updateStatus_Success_ToInProgress`: TTS chuyển task từ `TODO` sang `IN_PROGRESS` thành công, kiểm tra dispatch notification.
- [x] **UT-BE-03:** `updateStatus_Success_ToCompleted_WithSubmission`: TTS nộp bài chuyển sang `COMPLETED` kèm link nộp bài thành công.
- [x] **UT-BE-04:** `updateStatus_Fail_NotAssignee`: Ném `AccessDeniedException` khi TTS không có tên trong `assignees`.
- [x] **UT-BE-05:** `updateStatus_Fail_InvalidStatus`: Ném `BadRequestException` khi gửi trạng thái không hợp lệ.
- [x] **UT-BE-06:** `updateStatus_Fail_InternTerminated`: Ném `BadRequestException` khi TTS đã bị đình chỉ.

### 12.2. Integration Tests (`InternMissionControllerIT`)
- [x] **IT-BE-01:** Kiểm thử qua MockMvc: TTS đăng nhập gọi `PATCH /api/mission-items/{id}/status` chuyển trạng thái thành công (HTTP 200).
- [x] **IT-BE-02:** Kiểm thử qua MockMvc: Chặn TTS trái phép (HTTP 403).

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

### 13.1. Entity & Repository
- [x] `mission/entity/MissionItem.java` (Cập nhật: bổ sung `submissionUrl`, `completionNote`, `submittedAt`)
- [x] `mission/repository/MissionItemRepository.java` (Bổ sung method `findAssignedItemsByInternIdWithDetails` dùng `JOIN FETCH`)

### 13.2. DTOs
- [x] `mission/dto/request/UpdateKanbanStatusRequest.java` (Request body: `status`, `submissionUrl`, `completionNote`)
- [x] `mission/dto/response/InternKanbanBoardResponse.java` (Response phân nhóm 3 cột `todoItems`, `inProgressItems`, `completedItems`)
- [x] `mission/dto/response/MissionItemResponse.java` (Cập nhật: hiển thị thêm `submissionUrl`, `completionNote`, `submittedAt`)

### 13.3. Service Layer
- [x] `mission/service/InternMissionService.java` (Interface nghiệp vụ Kanban cho Thực tập sinh)
- [x] `mission/service/impl/InternMissionServiceImpl.java` (Xử lý Kanban, kiểm tra quyền Assignee chống IDOR, cập nhật trạng thái, dispatch notification)

### 13.4. Controller Layer
- [x] `mission/controller/InternMissionController.java` (Controller cho Intern: `/api/mission-items/my-missions`, `/api/mission-items/my-missions/kanban`, `/api/mission-items/{itemId}/status`)

### 13.5. Gateway & Configuration
- [x] `config-repo-local/api-gateway.yml` (Kiểm tra route `/api/mission-items/**` và `/api/intern/**`)

### 13.6. Automated Tests
- [x] `src/test/java/org/example/internservice/mission/service/InternMissionServiceTest.java` (Unit tests)

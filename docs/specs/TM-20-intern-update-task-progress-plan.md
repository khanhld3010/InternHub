# Kế Hoạch Triển Khai Kỹ Thuật (Technical Implementation Plan) - TM-20

> **Tính năng:** Thực Tập Sinh - Chuyển Đổi Trạng Thái Nhiệm Vụ Trên Bảng Kanban (Intern Mission Kanban Transition)  
> **Jira Ticket:** [TM-20](https://robluccibn9935.atlassian.net/browse/TM-20)  
> **Tài liệu đặc tả (Spec):** [TM-20-intern-update-task-progress-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)  
> **Trạng thái:** PROPOSED / CHỜ PHÊ DUYỆT (PENDING APPROVAL)  
> **Cấp độ thay đổi (Change Level):** **L3** (Mở rộng phân quyền RBAC cấp Assignee chống IDOR, cập nhật Entity `MissionItem`, xây dựng API Kanban 3 cột cá nhân cho TTS, tích hợp gửi thông báo realtime cho Mentor qua `NotificationEventDispatcher`).  
> **Service phụ trách:** `intern-and-program-service` (Port 8082), `api-gateway` (Port 8080).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (MANDATORY DIRECTIVE - Rule 31)

> [!CAUTION]
> **CHỈ THỊ CỐT LÕI (RULE 31): "ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"**

### 1.1. Rà soát hiện trạng mã nguồn & Database Schema
1. **Các thực thể & bảng CSDL hiện hữu:**
   - [MissionBoard.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/entity/MissionBoard.java) (`mission_boards`): Container chứa các công việc của Chương trình thực tập.
   - [MissionItem.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/entity/MissionItem.java) (`mission_items`): Chứa thông tin công việc, hạn chót (`dueDate`), độ ưu tiên (`priority`), và cột trạng thái `status` (`MissionItemStatus`: `TODO`, `IN_PROGRESS`, `COMPLETED`).
   - Bảng liên kết `mission_item_assignees`: Quan hệ `@ManyToMany` giữa `MissionItem` và `InternProfile`.
   - [InternProfileRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternProfileRepository.java): Đã có sẵn phương thức `findByUserId(Long userId)`.
   - [NotificationEventDispatcher.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/NotificationEventDispatcher.java): Cơ chế bắn thông báo nội bộ qua FeignClient đến `notification-service`.

### 1.2. Đánh giá tái sử dụng & Quyết định thiết kế
- **Tái sử dụng 100%:**
  - Không tạo bảng mới (loại bỏ hoàn toàn ý tưởng tạo bảng `mission_progress_logs` để tránh phức tạp và dư thừa).
  - Tái sử dụng trường `status` sẵn có của `MissionItem` cho 3 nấc Kanban (`TODO`, `IN_PROGRESS`, `COMPLETED`).
  - Tái sử dụng bảng join `mission_item_assignees` để kiểm tra phân quyền sở hữu công việc.
- **Mở rộng tối thiểu trên `MissionItem`:**
  - Thêm 3 trường tùy chọn trên entity `MissionItem`:
    + `submission_url VARCHAR(500) NULL` (link nộp bài: GitHub PR, Docs, Figma...).
    + `completion_note TEXT NULL` (ghi chú kết quả khi hoàn thành).
    + `submitted_at DATETIME NULL` (thời điểm nộp bài).
- **Nguyên tắc bảo vệ ranh giới quyền hạn (IDOR & Teamwork):**
  - **Task riêng:** Nếu TTS không có tên trong `assignees` của task $\rightarrow$ Chặn ngay với mã lỗi `403 FORBIDDEN`.
  - **Task làm chung (Pair/Team theo TM-19):** Mọi thành viên trong `assignees` đều có quyền chuyển trạng thái task chung đại diện cho nhóm.

---

## 2. Mục Tiêu Kỹ Thuật & Tuyên Ngôn Nghiệp Vụ

> **ĐỊNH NGHĨA CỐT LÕI: "THỰC TẬP SINH LÀ NGƯỜI NHẬN VIỆC (ASSIGNEE) TRỰC TIẾP THỰC THI VÀ ĐIỀU PHỐI CÔNG VIỆC TRÊN BẢNG KANBAN 3 NẤC"**

1. **Tra cứu danh sách nhiệm vụ cá nhân:** Cung cấp API cho TTS lấy các task được phân công (`GET /api/mission-items/my-missions`) hỗ trợ lọc theo trạng thái, độ ưu tiên, deadline, phân trang 0-indexed.
2. **Cung cấp Bảng Kanban 3 Cột Cá Nhân:** API `GET /api/mission-items/my-missions/kanban` trả về dữ liệu phân nhóm sẵn:
   - `todoItems` ("Chưa làm")
   - `inProgressItems` ("Đang làm")
   - `completedItems` ("Hoàn thiện")
3. **Chuyển đổi trạng thái Kanban tức thì:** Endpoint `PATCH /api/mission-items/{itemId}/status` mở rộng cho phép `ROLE_INTERN` (kèm xác thực người nhận việc), hỗ trợ gửi kèm `submissionUrl` và `completionNote` khi chuyển sang `COMPLETED`.
4. **Bắn thông báo thời gian thực đến Mentor:** Khi TTS chuyển trạng thái công việc sang `IN_PROGRESS` hoặc `COMPLETED`, tự động bắn thông báo cho Mentor phụ trách Board qua `NotificationEventDispatcher`.

---

## 3. Kế Hoạch 7 Bước Triển Khai Chi Tiết (7-Step Technical Workflow)

```text
[Bước 1: Cập nhật Entity MissionItem] ➔ [Bước 2: Xây dựng DTOs] ➔ [Bước 3: Bổ sung Repositories]
       ↓
[Bước 4: Xây dựng Service Layer] ➔ [Bước 5: Xây dựng Controller] ➔ [Bước 6: Gateway & Route]
       ↓
[Bước 7: Unit Tests & Compile Check]
```

### Bước 1: Cập nhật JPA Entity `MissionItem`
- **File:** `intern-and-program-service/.../mission/entity/MissionItem.java`
- Bổ sung các trường:
  ```java
  @Column(name = "submission_url", length = 500)
  private String submissionUrl;

  @Column(name = "completion_note", columnDefinition = "TEXT")
  private String completionNote;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;
  ```
- Tuân thủ Rule 25: Dùng Lombok an toàn (`@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`), không dùng `@Data`.

### Bước 2: Xây dựng Request & Response DTOs
- **Package:** `org.example.internservice.mission.dto.*`
- **Request DTOs (`mission/dto/request/`):**
  - `UpdateKanbanStatusRequest.java`:
    - `@NotNull(message = "Trạng thái công việc không được để trống") MissionItemStatus status;`
    - `@Pattern(regexp = "^(https?://).*", message = "Liên kết nộp bài phải là đường dẫn URL hợp lệ bắt đầu bằng http:// hoặc https://") @Size(max = 500) String submissionUrl;`
    - `@Size(max = 2000) String completionNote;`
- **Response DTOs (`mission/dto/response/`):**
  - `InternKanbanBoardResponse.java`:
    - `List<MissionItemResponse> todoItems;`
    - `List<MissionItemResponse> inProgressItems;`
    - `List<MissionItemResponse> completedItems;`
    - `int totalCount;`
    - `int todoCount;`
    - `int inProgressCount;`
    - `int completedCount;`
  - Cập nhật `MissionItemResponse.java`: Bổ sung `submissionUrl`, `completionNote`, `submittedAt`.

### Bước 3: Bổ sung Truy Vấn Trong `MissionItemRepository`
- **File:** `intern-and-program-service/.../mission/repository/MissionItemRepository.java`
- Bổ sung truy vấn phòng chống triệt để lỗi N+1 Query (Rule 22):
  ```java
  @Query("SELECT DISTINCT mi FROM MissionItem mi " +
         "JOIN FETCH mi.board b " +
         "JOIN FETCH b.program p " +
         "LEFT JOIN FETCH mi.assignees a " +
         "WHERE :internId IN (SELECT i.id FROM mi.assignees i) " +
         "ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
  List<MissionItem> findAssignedItemsByInternIdWithDetails(@Param("internId") Long internId);
  ```

### Bước 4: Xây dựng Service Layer (`InternMissionService`)
- **Nguyên tắc Anti-God-Class (Rule 17):** Tách riêng interface và implementation chuyên biệt cho Intern:
  - Interface: `mission/service/InternMissionService.java`
  - Implementation: `mission/service/impl/InternMissionServiceImpl.java`
- **Phương thức nghiệp vụ:**
  1. `PageResponse<MissionItemResponse> getMyMissions(CustomUserDetails userDetails, MissionItemStatus status, Long boardId, MissionPriority priority, String keyword, Pageable pageable);`
  2. `InternKanbanBoardResponse getMyKanbanBoard(CustomUserDetails userDetails);`
  3. `MissionItemResponse getMissionDetail(Long itemId, CustomUserDetails userDetails);`
  4. `MissionItemResponse updateKanbanStatus(Long itemId, UpdateKanbanStatusRequest request, CustomUserDetails userDetails);`
- **Logic kiểm soát an toàn:**
  - Tra cứu hồ sơ TTS qua `internProfileRepository.findByUserId(userDetails.getUserId())`.
  - Kiểm tra trạng thái hồ sơ: ném `BadRequestException` nếu `status != INTERNING && status != APPROVED`.
  - Kiểm tra phân quyền:
    ```java
    boolean isAssignee = item.getAssignees().stream()
            .anyMatch(a -> a.getId().equals(currentIntern.getId()));
    if (!isAssignee && !isMentorOrAdmin(userDetails, item)) {
        throw new AccessDeniedException("Bạn không được phân công thực hiện nhiệm vụ này, không có quyền chuyển trạng thái");
    }
    ```
  - Cập nhật trạng thái: Nếu `status == COMPLETED`, lưu `submissionUrl`, `completionNote`, `submittedAt = LocalDateTime.now()`.
  - Dispatch thông báo đến Mentor qua `NotificationEventDispatcher`:
    ```java
    notificationDispatcher.dispatch(CreateNotificationInternalRequest.builder()
            .recipientId(mentorUserId)
            .actorId(userDetails.getUserId())
            .title("Thực tập sinh đã hoàn thành nhiệm vụ")
            .content("TTS " + currentIntern.getFullName() + " đã hoàn thành nhiệm vụ: " + item.getTitle())
            .actionUrl("/mentor/mission-boards/" + item.getBoard().getId())
            .type("TASK_COMPLETED")
            .build());
    ```

### Bước 5: Xây dựng Controller Layer (`InternMissionController`)
- **File:** `mission/controller/InternMissionController.java`
- Constructor Injection thông qua `@RequiredArgsConstructor` (Rule 21).
- Endpoints chuẩn RESTful:
  - `GET /api/mission-items/my-missions`: Phân trang 0-indexed (Rule 20).
  - `GET /api/mission-items/my-missions/kanban`: Trả về dữ liệu 3 cột.
  - `GET /api/mission-items/{itemId}`: Chi tiết công việc.
  - `PATCH /api/mission-items/{itemId}/status`: Cập nhật trạng thái Kanban.
- 100% responses bọc trong `ResponseEntity<ApiResponse<T>>` (Rule 19).
- Bổ sung annotation Swagger/OpenAPI `@Operation`, `@Tag`.

### Bước 6: Kiểm tra & Đồng bộ Gateway Configuration
- **File:** `InternHub/config-repo-local/api-gateway.yml`
- Xác nhận route `mission-service` đã bao phủ `/api/mission-items/**`. Đảm bảo request từ client chuyển tiếp chính xác về `intern-and-program-service`.

### Bước 7: Unit Testing & Compile Check Bắt Buộc (Rule 27)
- **File:** `src/test/java/org/example/internservice/mission/service/InternMissionServiceTest.java`
- Các ca kiểm thử tự động với JUnit 5 + Mockito:
  - `getMyKanban_Success`: Lấy dữ liệu 3 cột Kanban thành công.
  - `updateStatus_Success_ToInProgress`: Chuyển sang `IN_PROGRESS` thành công, có dispatch notification.
  - `updateStatus_Success_ToCompleted`: Chuyển sang `COMPLETED` kèm link nộp bài thành công.
  - `updateStatus_Fail_NotAssignee`: Ném `AccessDeniedException` (HTTP 403) khi TTS không nằm trong `assignees`.
  - `updateStatus_Fail_InternTerminated`: Ném `BadRequestException` khi hồ sơ TTS bị đình chỉ.
- **Biên dịch & Chạy kiểm thử tự động:**
  ```powershell
  .\gradlew :intern-and-program-service:compileJava
  .\gradlew :intern-and-program-service:test
  ```

---

## 4. Bảng Kiểm Soát Tuân Thủ 31 Nguyên Tắc Bất Biến (Compliance Matrix)

| Nguyên Tắc | Nội Dung Ràng Buộc | Biện Pháp Đáp Ứng Trong TM-20 |
| :--- | :--- | :--- |
| **Rule 7** | Boundary Isolation (Cấm sửa Frontend) | 100% công việc chỉ thực hiện trên Backend Java Spring Boot. |
| **Rule 8** | Cấm SQL phá hoại CSDL | Chỉ bổ sung 3 cột `VARCHAR/TEXT` an toàn, không DROP/TRUNCATE. |
| **Rule 14** | Package-by-Feature triệt để | Đặt trọn vẹn trong `org.example.internservice.mission.*`. |
| **Rule 15** | Kế thừa `BaseEntity` | Thực thể `MissionItem` đã kế thừa `BaseEntity`. |
| **Rule 16** | Phân tách Request/Response DTOs | Dùng `UpdateKanbanStatusRequest`, `InternKanbanBoardResponse`. |
| **Rule 17** | Anti-God-Class (< 300 dòng) | Tách riêng `InternMissionService` và `InternMissionController`. |
| **Rule 18 & 19** | RESTful & `ApiResponse<T>` | 100% API trả về `ResponseEntity<ApiResponse<T>>`. |
| **Rule 21** | Constructor Injection an toàn | Dùng `@RequiredArgsConstructor`, cấm hoàn toàn `@Autowired` trên field. |
| **Rule 22** | Chống N+1 Query JPA | Dùng `JOIN FETCH` trong Repository khi nạp Assignees và Board. |
| **Rule 23** | Quản lý `@Transactional` rõ ràng | `@Transactional(readOnly = true)` tại Class, `@Transactional` tại method ghi. |
| **Rule 27** | Kiểm tra biên dịch bắt buộc | Chạy `.\gradlew :intern-and-program-service:compileJava` trước khi báo cáo. |
| **Rule 30** | Lưu trữ đặc tả vĩnh cửu | Đã lưu tại `docs/specs/TM-20-intern-update-task-progress-spec.md` với đầy đủ Revision History. |
| **Rule 31** | Quét dự án & Tối đa tái sử dụng | Tái sử dụng trọn vẹn cấu trúc TM-19, không tạo bảng mới thừa thãi. |

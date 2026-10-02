# Kế Hoạch Triển Khai Kỹ Thuật (Technical Implementation Plan) - TM-19

> **Tính năng:** Mentor - Quản Lý Bảng Nhiệm Vụ (MissionBoard) & Giao Việc Cho Thực Tập Sinh  
> **Jira Ticket:** [TM-19](https://robluccibn9935.atlassian.net/browse/TM-19)  
> **Tài liệu đặc tả:** [TM-19-mentor-assign-tasks-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)  
> **Trạng thái:** PENDING_APPROVAL  
> **Thay đổi cấp độ:** **L3**  
> **Service phụ trách:** `intern-and-program-service` (Port 8082), `api-gateway` (Port 8080)

---

## 1. Mục Tiêu Kỹ Thuật & Cấu Trúc Package

1. **Thiết lập quan hệ N-N giữa Program và Mentor:**
   - Tạo thực thể `ProgramMentor` trong package `org.example.internservice.program.entity`.
   - Một Program có thể có nhiều Mentor; một Mentor có thể tham gia nhiều Program.
2. **Xây dựng module MissionBoard theo chuẩn Package-by-Feature (Rule 14):**
   - Package: `org.example.internservice.mission.*`
   - Thực thể `MissionBoard`: Gắn với Program, do Mentor quản lý.
   - Thực thể `MissionItem`: Mục công việc chi tiết nằm trong Board.
   - Bảng liên kết `mission_item_assignees`: Quan hệ N-N giữa mục công việc và Thực tập sinh (`InternProfile`).
3. **Chuẩn hóa 3 trạng thái công việc chi tiết:**
   - `"chưa làm"` (`TODO`)
   - `"đang làm"` (`IN_PROGRESS`)
   - `"hoàn thiện"` (`COMPLETED`)

---

## 2. Kế Hoạch 7 Bước Triển Khai Chi Tiết

```text
[Bước 1: Entities & Enums] ➔ [Bước 2: DTOs] ➔ [Bước 3: Repositories]
       ↓
[Bước 4: Services & Business Logic] ➔ [Bước 5: Controllers] ➔ [Bước 6: Gateway Routes]
       ↓
[Bước 7: Unit Testing & Compile Check]
```

### Bước 1: Xây dựng Entities & Enums
1. **Thực thể liên kết Program - Mentor:**
   - File: `program/entity/ProgramMentor.java` kế thừa `BaseEntity`.
   - Bảng: `program_mentors` (`program_id`, `mentor_id`, `mentor_name`, `mentor_email`, `assigned_by`, `assigned_at`).
2. **Enums của Module Mission:**
   - `mission/entity/enums/MissionItemStatus.java`: `TODO`, `IN_PROGRESS`, `COMPLETED`
   - `mission/entity/enums/MissionPriority.java`: `LOW`, `MEDIUM`, `HIGH`
   - `mission/entity/enums/BoardStatus.java`: `ACTIVE`, `ARCHIVED`
3. **Thực thể `MissionBoard`:**
   - File: `mission/entity/MissionBoard.java` kế thừa `BaseEntity`.
   - Bảng: `mission_boards` (`program_id`, `mentor_id`, `mentor_name`, `title`, `description`, `status`).
4. **Thực thể `MissionItem`:**
   - File: `mission/entity/MissionItem.java` kế thừa `BaseEntity`.
   - Bảng: `mission_items` (`board_id`, `title`, `description`, `priority`, `status`, `due_date`, `order_index`).
   - Quan hệ `@ManyToMany` với `InternProfile` qua bảng join `mission_item_assignees`.

### Bước 2: Xây dựng Request & Response DTOs
1. **Request DTOs (`mission/dto/request/`):**
   - `CreateMissionBoardRequest.java`: `@NotBlank @Size(min = 3, max = 200) String title`, `@Size(max = 2000) String description`.
   - `UpdateMissionBoardRequest.java`: `title`, `description`, `BoardStatus status`.
   - `CreateMissionItemRequest.java`:
     - `@NotBlank @Size(min = 3, max = 200) String title`
     - `@Size(max = 2000) String description`
     - `MissionPriority priority` (mặc định `MEDIUM`)
     - `LocalDate dueDate`
     - `@NotEmpty @Size(min = 1) Set<Long> internIds` (bắt buộc chọn ít nhất 1 TTS trong Program)
   - `UpdateMissionItemRequest.java`: `title`, `description`, `priority`, `dueDate`, `Set<Long> internIds`.
   - `UpdateItemStatusRequest.java`: `@NotNull MissionItemStatus status`.
2. **Response DTOs (`mission/dto/response/`):**
   - `AssigneeResponse.java`: `id`, `fullName`, `internCode`, `email`.
   - `MissionItemResponse.java`: `id`, `boardId`, `title`, `description`, `priority`, `status`, `dueDate`, `List<AssigneeResponse> assignees`, `createdAt`, `updatedAt`.
   - `MissionBoardResponse.java`: Tóm tắt board kèm đếm số lượng item theo từng trạng thái (`todoCount`, `inProgressCount`, `completedCount`).
   - `MissionBoardDetailResponse.java`: Chi tiết board kèm danh sách items phân thành 3 cột hoặc danh sách đầy đủ.
   - `MentorProgramResponse.java`: Danh sách Program mà Mentor phụ trách kèm tổng số TTS.

### Bước 3: Xây dựng Spring Data JPA Repositories
1. **`ProgramMentorRepository.java`:**
   - `boolean existsByProgramIdAndMentorId(Long programId, Long mentorId);`
   - `List<ProgramMentor> findByMentorId(Long mentorId);`
   - `List<ProgramMentor> findByProgramId(Long programId);`
2. **`MissionBoardRepository.java`:**
   - `List<MissionBoard> findByProgramIdOrderByCreatedAtDesc(Long programId);`
   - `Optional<MissionBoard> findByIdAndProgramId(Long id, Long programId);`
3. **`MissionItemRepository.java`:**
   - `@Query("SELECT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.board.id = :boardId ORDER BY mi.orderIndex ASC, mi.createdAt DESC")`
     `List<MissionItem> findByBoardIdWithAssignees(@Param("boardId") Long boardId);` (Chống N+1 query)
   - `@Query("SELECT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.id = :id")`
     `Optional<MissionItem> findByIdWithAssignees(@Param("id") Long id);`

### Bước 4: Xây dựng Service Interfaces & ServiceImpls
1. **`MissionBoardService.java` & `MissionBoardServiceImpl.java`:**
   - `MissionBoardResponse createBoard(Long programId, CreateMissionBoardRequest request, CustomUserDetails userDetails);`
   - `List<MissionBoardResponse> getBoardsByProgram(Long programId, CustomUserDetails userDetails);`
   - `MissionBoardDetailResponse getBoardDetail(Long boardId, CustomUserDetails userDetails);`
   - `MissionBoardResponse updateBoard(Long boardId, UpdateMissionBoardRequest request, CustomUserDetails userDetails);`
   - `void deleteBoard(Long boardId, CustomUserDetails userDetails);`
   - Kiểm tra nghiệp vụ: Mentor phải thuộc `program_mentors` của Program đó.
2. **`MissionItemService.java` & `MissionItemServiceImpl.java`:**
   - `MissionItemResponse createItem(Long boardId, CreateMissionItemRequest request, CustomUserDetails userDetails);`
   - `MissionItemResponse updateItem(Long itemId, UpdateMissionItemRequest request, CustomUserDetails userDetails);`
   - `MissionItemResponse updateItemStatus(Long itemId, UpdateItemStatusRequest request, CustomUserDetails userDetails);`
   - `void deleteItem(Long itemId, CustomUserDetails userDetails);`
   - Kiểm tra nghiệp vụ: Tất cả `internIds` phải thuộc cùng `program_id` của Board.

### Bước 5: Xây dựng REST Controllers & Phân Quyền
1. **`MissionBoardController.java` (`/api/mission-boards`, `/api/programs/{programId}/mission-boards`):**
   - `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
   - Trả về `ResponseEntity<ApiResponse<T>>`.
2. **`MissionItemController.java` (`/api/mission-items`, `/api/mission-boards/{boardId}/items`):**
   - `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
   - Validate DTO bằng `@Valid`.
3. **`MentorProgramController.java` (`/api/mentor/programs`):**
   - `@PreAuthorize("hasRole('MENTOR')")`
   - Lấy danh sách Program mà Mentor đang phụ trách và danh sách TTS trong Program để gán việc.

### Bước 6: Cấu hình Gateway & Audit Log
1. **`config-repo-local/api-gateway.yml`:**
   - Bổ sung định tuyến Gateway:
     ```yaml
     - id: mission-service
       uri: lb://intern-and-program-service
       predicates:
         - Path=/api/mission-boards/**, /api/mission-items/**, /api/mentor/**
     ```
2. **Audit Logging:**
   - Ghi audit log các hành vi: tạo board, thêm mục công việc, chuyển trạng thái hoàn thiện.

### Bước 7: Unit Testing & Kiểm Tra Biên Dịch
1. **Viết Unit Test:**
   - `MissionBoardServiceTest.java`: Bao phủ các ca thành công và thất bại (`UT-BE-01` đến `UT-BE-08`).
2. **Biên dịch Gradle bắt buộc:**
   - Chạy lệnh kiểm tra:
     ```powershell
     .\gradlew :intern-and-program-service:compileJava
     .\gradlew :intern-and-program-service:test
     ```

---

## 3. Quản Lý Rủi Ro & Phòng Ngừa Kỹ Thuật

| STT | Rủi Ro Kỹ Thuật | Biện Pháp Phòng Ngừa Triệt Để |
| :---: | :--- | :--- |
| **1** | N+1 Query do quan hệ Many-to-Many giữa `MissionItem` và `InternProfile` | Dùng câu truy vấn `JOIN FETCH mi.assignees` trong `MissionItemRepository`. |
| **2** | Mentor gán nhầm TTS từ Program khác | Service kiểm tra `intern.getProgram().getId().equals(board.getProgram().getId())` cho từng ID trong mảng. |
| **3** | Phình to kích thước Class Service (> 300 dòng) | Tách riêng `MissionBoardService` và `MissionItemService` thành 2 service độc lập. |

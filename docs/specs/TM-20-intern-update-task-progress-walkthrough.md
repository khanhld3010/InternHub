# Walkthrough Tổng Hợp Triển Khai TM-20: Thực Tập Sinh - Chuyển Đổi Trạng Thái Nhiệm Vụ Trên Bảng Kanban

> **Mã Ticket Jira:** [TM-20](https://robluccibn9935.atlassian.net/browse/TM-20)  
> **Tiêu đề Jira:** *Intern - Chuyển đổi trạng thái nhiệm vụ Kanban (Mission Kanban Transition & Task Execution)*  
> **Tài liệu đặc tả (Spec):** [TM-20-intern-update-task-progress-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)  
> **Kế hoạch triển khai (Plan):** [TM-20-intern-update-task-progress-plan.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-plan.md)  
> **Trạng thái:** ACTIVE / IMPLEMENTED & VERIFIED  
> **Phân hệ phụ trách:** Backend (`intern-and-program-service`, `api-gateway`)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, đặc biệt Rule 7, 14, 15, 16, 17, 18, 19, 21, 22, 23, 27, 30, 31).

---

## 1. Tổng Quan Nhiệm Vụ & Nghiệp Vụ Cốt Lõi

1. **Tuyên ngôn nghiệp vụ:**
   - **Mentor ([TM-19](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)):** Lập kế hoạch, tạo Bảng nhiệm vụ (`MissionBoard`), phân rã công việc chi tiết (`MissionItem`), đặt hạn chót và **giao việc cho Thực tập sinh** (`assignees`).
   - **Thực tập sinh ([TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)):** Là **Người thực thi (Executor)** trực tiếp nhận việc, tra cứu danh sách task cá nhân và chuyển đổi thẻ công việc giữa 3 cột Kanban:
     + `TODO` ("Chưa làm"): Công việc mới được giao, tiếp nhận tài liệu và yêu cầu.
     + `IN_PROGRESS` ("Đang làm"): Bắt đầu thực hiện (kéo thẻ hoặc bấm chuyển sang `IN_PROGRESS`).
     + `COMPLETED` ("Hoàn thiện"): Hoàn thành nhiệm vụ (kéo thẻ sang `COMPLETED`), có thể đính kèm liên kết nộp bài (`submissionUrl`) và ghi chú tóm tắt (`completionNote`) để Mentor vào nghiệm thu.
2. **Loại bỏ quy trình % tiến độ (0% - 100%):**
   - Đơn giản hóa tối đa trải nghiệm người dùng, TTS không cần ước lượng con số % trừu tượng mà chỉ chuyển đổi giữa 3 nấc trạng thái Kanban rõ ràng.
3. **Nguyên tắc bảo vệ ranh giới quyền hạn:**
   - **Chặn IDOR:** TTS không có tên trong danh sách `assignees` của công việc sẽ bị chặn tuyệt đối với mã lỗi **`403 FORBIDDEN`**.
   - **Hợp tác nhóm (Pair/Team theo TM-19):** Mọi thành viên trong `assignees` của cùng 1 task đều có quyền chuyển trạng thái đại diện cho nhóm.

---

## 2. Kết Quả Triển Khai Chi Tiết (Backend)

### 2.1. Entities & Data Model (Rule 15, 31)
- **Tối đa tái sử dụng theo Rule 31:** Không tạo bảng mới phức tạp. Tái sử dụng bảng `mission_items` và cột `status` (`MissionItemStatus`) đã có từ TM-19.
- [MissionItem.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/entity/MissionItem.java): Bổ sung 3 trường tùy chọn phục vụ nộp bài:
  - `submissionUrl` (`VARCHAR(500)`)
  - `completionNote` (`TEXT`)
  - `submittedAt` (`LocalDateTime`)

### 2.2. Request & Response DTOs (Rule 16)
- [UpdateKanbanStatusRequest.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/request/UpdateKanbanStatusRequest.java): Request body chuyển trạng thái cho TTS, validate regex URL an toàn.
- [UpdateItemStatusRequest.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/request/UpdateItemStatusRequest.java): Mở rộng nhận thêm `submissionUrl` và `completionNote`.
- [InternKanbanBoardResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/InternKanbanBoardResponse.java): Phân nhóm dữ liệu 3 cột Kanban (`todoItems`, `inProgressItems`, `completedItems`) kèm số lượng.
- [MissionItemResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/MissionItemResponse.java): Bổ sung hiển thị `boardTitle`, `submissionUrl`, `completionNote`, `submittedAt`.

### 2.3. Spring Data JPA Repositories (Rule 22)
- [MissionItemRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/repository/MissionItemRepository.java):
  - Kế thừa `JpaSpecificationExecutor<MissionItem>`.
  - Bổ sung `findAssignedItemsByInternIdWithDetails(Long internId)` sử dụng `JOIN FETCH` chống triệt để lỗi N+1 Query.
  - Bổ sung `findByIdWithBoardAndAssignees(Long id)`.

### 2.4. Service Layer (Rule 17, 21, 23)
- [InternMissionService.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/InternMissionService.java): Định nghĩa nghiệp vụ tra cứu và chuyển đổi Kanban cho TTS.
- [InternMissionServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/InternMissionServiceImpl.java):
  - Tuân thủ Anti-God-Class (khoảng 180 dòng).
  - Sử dụng `@Transactional(readOnly = true)` tại class và `@Transactional` tại method ghi.
  - Tự động kích hoạt gửi thông báo thời gian thực đến Mentor qua `NotificationEventDispatcher` khi TTS chuyển trạng thái sang `IN_PROGRESS` hoặc `COMPLETED`.
- [MissionItemServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionItemServiceImpl.java): Cập nhật `mapToItemResponse` đồng bộ các trường nộp bài mới.

### 2.5. Controller Layer (Rule 18, 19)
- [InternMissionController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/controller/InternMissionController.java):
  - `GET /api/mission-items/my-missions` (& `/api/intern/mission-items`): Lấy danh sách nhiệm vụ phân trang 0-indexed (Rule 20).
  - `GET /api/mission-items/my-missions/kanban` (& `/api/intern/mission-items/kanban`): Lấy dữ liệu 3 cột Kanban cá nhân.
  - `GET /api/mission-items/{itemId}` (& `/api/intern/mission-items/{itemId}`): Xem chi tiết mục công việc.
  - `PATCH /api/mission-items/my-missions/{itemId}/status` (& `/api/intern/mission-items/{itemId}/status`): TTS chuyển trạng thái.
- [MissionItemController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/controller/MissionItemController.java):
  - Mở rộng `@PatchMapping("/api/mission-items/{itemId}/status")` cho phép cả `ROLE_INTERN`, tự động ủy quyền (delegate) xử lý an toàn.

### 2.6. API Gateway Configuration
- [api-gateway.yml](file:///d:/codegym_final_project/InternHub/config-repo-local/api-gateway.yml): Bổ sung route `/api/intern/mission-items/**` định tuyến về `intern-and-program-service`.

---

## 3. Xác Thực & Kiểm Thử Hệ Thống (Verification - Rule 27)

| Hạng mục kiểm thử | Lệnh thực thi | Kết quả | Trạng thái |
| :--- | :--- | :--- | :---: |
| **Java Compilation** | `.\gradlew.bat :intern-and-program-service:compileJava` | `BUILD SUCCESSFUL in 17s`, 0 lỗi cú pháp | ✅ PASS |
| **Unit Tests TM-20** | `.\gradlew.bat :intern-and-program-service:test --tests "org.example.internservice.mission.service.InternMissionServiceTest"` | `BUILD SUCCESSFUL in 16s`, 8/8 tests pass 100% | ✅ PASS |
| **Full Mission Module Tests** | `.\gradlew.bat :intern-and-program-service:test --tests "org.example.internservice.mission.*"` | `BUILD SUCCESSFUL in 12s`, toàn bộ tests module mission pass | ✅ PASS |

### Chi tiết 8 Test Cases trong `InternMissionServiceTest`:
1. `getMyKanbanBoard_Success`: Lấy dữ liệu 3 cột Kanban cho TTS chính xác.
2. `getMissionDetail_Success`: Xem chi tiết công việc khi là Assignee hợp lệ.
3. `getMissionDetail_Fail_NotAssignee`: Ném `AccessDeniedException` (HTTP 403) khi TTS không có tên trong `assignees`.
4. `updateKanbanStatus_Success_ToInProgress`: Chuyển task sang `IN_PROGRESS` thành công, kích hoạt gửi thông báo.
5. `updateKanbanStatus_Success_ToCompleted_WithSubmission`: Chuyển task sang `COMPLETED` kèm link nộp bài thành công, kích hoạt gửi thông báo.
6. `updateKanbanStatus_Fail_NotAssignee`: Chặn IDOR - ném `AccessDeniedException` khi TTS cố tình đổi task của người khác.
7. `updateKanbanStatus_Fail_InternTerminated`: Ném `BadRequestException` khi hồ sơ TTS bị đình chỉ.
8. `updateKanbanStatus_Fail_NullStatus`: Ném `BadRequestException` khi request hoặc status rỗng.

---

## 4. Cam Kết & Bảo Toàn Ranh Giới (Boundary Isolation)
- **Frontend Isolation (Rule 7):** Tuyệt đối không can thiệp, không sửa bất kỳ file TypeScript/React nào trong `InternHub-Frontend/`.
- **Database Safety (Rule 8):** Không chạy bất kỳ câu lệnh SQL phá hoại (`DROP`, `TRUNCATE`).
- **Clean Git (Rule 4):** Không tự ý chạy `git commit` hay `git push`. Toàn bộ thay đổi nằm trong working tree để người dùng tự kiểm tra diff.

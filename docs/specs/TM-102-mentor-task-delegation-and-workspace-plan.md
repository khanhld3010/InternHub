# Implementation Plan: TM-102 Nâng Cấp Backend Quản Lý Bảng Nhiệm Vụ & Không Gian Làm Việc Của Mentor

> **Mã công việc:** TM-102  
> **Nhánh Git:** `feature/TM-102/mentor-task-delegation-workspace`  
> **Tài liệu đặc tả:** [TM-102 Spec v1.0](file:///d:/codegym_final_project/InternHub/docs/specs/TM-102-mentor-task-delegation-and-workspace-spec.md)  
> **Phạm vi tác động:** `intern-and-program-service` (Backend Microservices - Port 8082)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) và thư mục [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (Mandatory Reuse Audit)

> [!IMPORTANT]
> **Tuân thủ triệt để Quy tắc 31 & Chỉ thị bắt buộc:** *"ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"*.

| Thành phần hệ thống | Hiện trạng quét được trong dự án | Đánh giá & Quyết định tái sử dụng | Lý do kỹ thuật & Giải trình |
| :--- | :--- | :--- | :--- |
| **Cơ sở dữ liệu & Entity** | Đã có đầy đủ các entity: `MissionBoard`, `MissionItem`, `InternGroup`, `InternProfile`, `ProgramMentor`, `InternshipProgram`. | **TÁI SỬ DỤNG 100%** - Tuyệt đối không tạo bảng MySQL mới, không sửa đổi DDL schema. | Toàn bộ nghiệp vụ bảng nhiệm vụ, chi tiết công việc, thành viên tham gia, nhóm và phân công Mentor đều đã có bảng quan hệ hoàn chỉnh. |
| **DTO Chương Trình của Mentor** | Đã có [MentorProgramResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/MentorProgramResponse.java) (chỉ có `totalInterns`, `activeInterns`). | **TÁI SỬ DỤNG & MỞ RỘNG** | Mở rộng bổ sung 5 trường: `groupCount`, `totalTaskCount`, `completedTaskCount`, `progressPercent`, `mentorCount` phục vụ Bento Grid Card. |
| **DTO Danh Sách TTS của Chương Trình** | Đã có [AssigneeResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/AssigneeResponse.java). | **TÁI SỬ DỤNG & MỞ RỘNG** | Mở rộng bổ sung 4 trường: `groupId`, `groupName`, `activeTaskCount`, `completedTaskCount` phục vụ bộ lọc nhóm và huy hiệu quá tải tải trọng (Workload badge). |
| **Truy Vấn Hiệu Năng Cao (Chống N+1 Query)** | Đang gọi các hàm `countByBoardId...` riêng lẻ theo từng board trong vòng lặp. | **NÂNG CẤP REPOSITORY BATCH QUERY** | Viết các JPQL Aggregate Queries (`GROUP BY`) lấy thống kê theo danh sách `programIds` trong O(1) query. |
| **Phân Quyền Quản Lý Nhóm** | [ProgramGroupController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/controller/ProgramGroupController.java) chỉ cho phép `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`. | **MỞ RỘNG PHÂN QUYỀN RBAC CHO MENTOR** | Bổ sung role `'MENTOR'` kèm kiểm tra quyền sở hữu kỳ thực tập tại Service layer để Mentor có thể chia nhóm, tạo nhóm cho TTS trong kỳ mình phụ trách. |
| **Đồng Bộ Cơ Chế Xác Thực Mentor** | [MissionBoardServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionBoardServiceImpl.java) đã có Smart Fallback, nhưng `MissionItemServiceImpl` chưa đồng bộ. | **ĐỒNG BỘ HÓA LOGIC CHUẨN XÁC** | Chuẩn hóa hàm `verifyMentorAccess` trong `MissionItemServiceImpl` và `InternGroupServiceImpl` để nhất quán 100%. |
| **Toàn Vẹn Thời Hạn Nhiệm Vụ** | [MissionItemServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionItemServiceImpl.java) đã có `validateDueDate`. | **TÁI SỬ DỤNG & CỦNG CỐ** | Đảm bảo chặn `dueDate < today` và `dueDate > program.endDate`. |

---

## 2. Mục Tiêu Kỹ Thuật (Technical Objectives)

1. **Hiển thị tức thời & triệt tiêu N+1 Query:** Nâng cấp endpoint `GET /api/mentor/programs` trả về đầy đủ các chỉ số tiến độ (`progressPercent`, `totalTaskCount`, `completedTaskCount`, `groupCount`, `mentorCount`) với thời gian phản hồi `< 150ms`.
2. **Minh bạch hóa phân bổ công việc (Workload Matrix Transparency):** Nâng cấp endpoint `GET /api/mentor/programs/{programId}/interns` trả về thông tin nhóm và số task đang làm (`activeTaskCount`) của từng học viên.
3. **Phân quyền tự chủ cho Mentor (Mentor Group Autonomy):** Cấp quyền cho Mentor thao tác trên nhóm thực tập (`createGroup`, `updateGroup`, `disbandGroup`, `batchApplyGroups`, `addMember`, `removeMember`) trong phạm vi chương trình phụ trách.
4. **Bảo mật truy cập chương trình đa tầng:** Áp dụng Smart Fallback kiểm tra phân công Mentor, ngăn chặn hoàn toàn việc can thiệp trái phép giữa các chương trình khác nhau.
5. **Kiểm thử đơn vị đạt chuẩn:** Viết bộ Unit Test kiểm thử các trường hợp thành công, tính toán tiến độ, kiểm tra phân quyền và điều kiện biên dữ liệu.

---

## 3. Danh Sách Tệp Tác Động (Impacted Files)

### 3.1. DTO Layer (`mission/dto/response/`):
- `[MODIFY]` [MentorProgramResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/MentorProgramResponse.java): Bổ sung 5 trường:
  - `private Integer groupCount;`
  - `private Integer totalTaskCount;`
  - `private Integer completedTaskCount;`
  - `private Double progressPercent;`
  - `private Integer mentorCount;`
- `[MODIFY]` [AssigneeResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/AssigneeResponse.java): Bổ sung 4 trường:
  - `private Long groupId;`
  - `private String groupName;`
  - `private Integer activeTaskCount;`
  - `private Integer completedTaskCount;`

### 3.2. Repository Layer:
- `[MODIFY]` [MissionItemRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/repository/MissionItemRepository.java): Bổ sung các query batch:
  - Thống kê tổng số task và số task hoàn thành gom nhóm theo chương trình:
    ```java
    @Query("SELECT mi.board.program.id, COUNT(mi), " +
           "SUM(CASE WHEN mi.status = org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1 ELSE 0 END) " +
           "FROM MissionItem mi WHERE mi.board.program.id IN :programIds GROUP BY mi.board.program.id")
    List<Object[]> countTasksByProgramIds(@Param("programIds") Collection<Long> programIds);
    ```
  - Thống kê số task active (TODO hoặc IN_PROGRESS) và completed của từng thực tập sinh trong chương trình:
    ```java
    @Query("SELECT a.id, " +
           "SUM(CASE WHEN mi.status != org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN mi.status = org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1 ELSE 0 END) " +
           "FROM MissionItem mi JOIN mi.assignees a WHERE mi.board.program.id = :programId GROUP BY a.id")
    List<Object[]> countTasksByProgramIdGroupedByIntern(@Param("programId") Long programId);
    ```
- `[MODIFY]` [InternGroupRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/repository/InternGroupRepository.java): Bổ sung batch query đếm số nhóm theo chương trình:
  ```java
  @Query("SELECT ig.program.id, COUNT(ig) FROM InternGroup ig WHERE ig.program.id IN :programIds GROUP BY ig.program.id")
  List<Object[]> countGroupsByProgramIds(@Param("programIds") Collection<Long> programIds);
  ```
- `[MODIFY]` [ProgramMentorRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/repository/ProgramMentorRepository.java): Bổ sung batch query đếm số Mentor theo chương trình:
  ```java
  @Query("SELECT pm.program.id, COUNT(pm) FROM ProgramMentor pm WHERE pm.program.id IN :programIds GROUP BY pm.program.id")
  List<Object[]> countMentorsByProgramIds(@Param("programIds") Collection<Long> programIds);
  ```

### 3.3. Service Layer:
- `[MODIFY]` [MissionBoardServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionBoardServiceImpl.java):
  - Cập nhật `getMyMentoredPrograms`: Sử dụng batch aggregate queries để tính `groupCount`, `totalTaskCount`, `completedTaskCount`, `progressPercent`, `mentorCount`.
  - Cập nhật `getProgramInterns`: Ghép nối `groupId`, `groupName` từ `InternProfile.getGroup()` và số lượng active/completed tasks từ query nhóm.
- `[MODIFY]` [MissionItemServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionItemServiceImpl.java):
  - Cập nhật `verifyMentorAccess`: Bổ sung Smart Fallback trong `verifyMentorAccess` (cho phép nếu Mentor phụ trách bất kỳ TTS nào trong kỳ).
- `[MODIFY]` [InternGroupService.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/service/InternGroupService.java) & [InternGroupServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/service/impl/InternGroupServiceImpl.java):
  - Bổ sung hàm kiểm tra quyền truy cập `verifyProgramAccess(Long programId, CustomUserDetails userDetails)` cho Mentor trước khi thực hiện thao tác tạo, sửa, xóa, chia nhóm.

### 3.4. Controller Layer:
- `[MODIFY]` [ProgramGroupController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/controller/ProgramGroupController.java):
  - Cập nhật `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")` cho các endpoints: `createGroup`, `updateGroup`, `disbandGroup`, `batchApplyGroups`, `addMember`, `removeMember`.
  - Truyền `Authentication` (hoặc trích xuất `CustomUserDetails`) vào service để kiểm tra quyền truy cập theo chương trình.

### 3.5. Testing Layer:
- `[NEW]` [MentorWorkspaceEnhancementTest.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/test/java/org/example/internservice/mission/MentorWorkspaceEnhancementTest.java):
  - Bộ Unit Test toàn diện cho các kịch bản: Thống kê O(1) Mentor Program, Nạp danh sách TTS kèm Workload & Group, Phân quyền Mentor thao tác nhóm, Xác thực Due Date và Smart Fallback.

---

## 4. Kế Hoạch Triển Khai Từng Bước (Step-by-Step Implementation Flow)

```
[Bước 1: Mở rộng DTOs] ➔ [Bước 2: Batch Aggregate Queries] ➔ [Bước 3: Nâng cấp Service & Security] 
         ↓
[Bước 4: Mở rộng Controller RBAC] ➔ [Bước 5: Unit Testing & Verification] ➔ [Bước 6: Gradle Build & Nghiệm Thu]
```

### Bước 1: Mở rộng Request / Response DTOs
1. Mở [MentorProgramResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/MentorProgramResponse.java), thêm các trường:
   - `groupCount`, `totalTaskCount`, `completedTaskCount`, `progressPercent`, `mentorCount`.
2. Mở [AssigneeResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/dto/response/AssigneeResponse.java), thêm các trường:
   - `groupId`, `groupName`, `activeTaskCount`, `completedTaskCount`.

### Bước 2: Bổ sung các câu truy vấn Aggregate Batch trong Repository
1. Thêm `countTasksByProgramIds` và `countTasksByProgramIdGroupedByIntern` vào [MissionItemRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/repository/MissionItemRepository.java).
2. Thêm `countGroupsByProgramIds` vào [InternGroupRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/repository/InternGroupRepository.java).
3. Thêm `countMentorsByProgramIds` vào [ProgramMentorRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/repository/ProgramMentorRepository.java).

### Bước 3: Nâng cấp Service Logic & Đồng Bộ Bảo Mật
1. Cập nhật [MissionBoardServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionBoardServiceImpl.java):
   - Phương thức `getMyMentoredPrograms`: Lấy danh sách `programIds`, gọi 3 batch query đếm tasks, groups, mentors, ghép nối vào Map và tính `progressPercent`.
   - Phương thức `getProgramInterns`: Gọi `countTasksByProgramIdGroupedByIntern`, map `groupId`, `groupName` từ quan hệ `intern.getGroup()` và nạp số `activeTaskCount`, `completedTaskCount`.
2. Cập nhật [MissionItemServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/service/impl/MissionItemServiceImpl.java):
   - Bổ sung Smart Fallback trong `verifyMentorAccess` (cho phép nếu Mentor phụ trách bất kỳ TTS nào trong kỳ).
3. Cập nhật [InternGroupServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/service/impl/InternGroupServiceImpl.java):
   - Thêm phương thức kiểm tra phân quyền `verifyProgramAccess` cho cả HR, ADMIN và MENTOR phụ trách.

### Bước 4: Mở rộng Controller Annotations & Phân Quyền
1. Mở [ProgramGroupController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/controller/ProgramGroupController.java):
   - Cập nhật `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")` cho các endpoints tạo, sửa, giải tán, chia nhóm tự động, thêm/gỡ học viên.
   - Truyền `CustomUserDetails` xuống service để kiểm tra quyền truy cập chi tiết.

### Bước 5: Viết Unit Tests & Xác Minh Nghiệp Vụ
1. Tạo file test `MentorWorkspaceEnhancementTest.java` kiểm thử:
   - Thống kê O(1) Mentor Program (đúng tỷ lệ %, số nhóm, số task).
   - Nạp thông tin TTS kèm Group và Active Task Count.
   - Validation Due Date không vượt quá ngày kết thúc chương trình.
   - Phân quyền Mentor truy cập chương trình hợp lệ và chặn chương trình không phụ trách.

### Bước 6: Kiểm Tra Biên Dịch & Chạy Test
1. Chạy lệnh:
   ```powershell
   .\gradlew :intern-and-program-service:compileJava
   .\gradlew :intern-and-program-service:test
   ```
2. Đảm bảo toàn bộ test case vượt qua 100%, không phát sinh lỗi biên dịch hay cảnh báo linter.

---

## 5. Kế Hoạch Rollback (Rollback Strategy)

Nếu có sự cố phát sinh trong quá trình triển khai hoặc chạy thử:
1. Do toàn bộ tính năng **không can thiệp CSDL vật lý (không có DDL Migration)**, việc rollback chỉ đơn giản là revert các file Java về commit ban đầu.
2. Các API cũ vẫn giữ nguyên tính tương thích ngược 100% nhờ việc chỉ bổ sung thêm các trường dữ liệu mới vào DTO mà không làm thay đổi các trường dữ liệu hiện hữu.

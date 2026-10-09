# Implementation Plan: Phân Công Mentor Cho Toàn Bộ Kỳ Thực Tập (TM-101 Backend)

> **Kế Hoạch Triển Khai Kỹ Thuật (Backend Implementation Plan)**  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Spring Boot 3, Java 21)  
> **Dịch vụ:** `intern-and-program-service`  
> **Mã Jira Ticket:** [TM-101](https://robluccibn9935.atlassian.net/browse/TM-101)  
> **Trạng thái:** DRAFT / IN_REVIEW  
> **Tài liệu đặc tả:** [TM-101-assign-mentor-to-program-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-101-assign-mentor-to-program-spec.md)

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (Mandatory Survey)

1. **Khảo sát Entity & Database Schema:**
   - Bảng `program_mentors` (`ProgramMentor.java`): Đã có sẵn quan hệ giữa `InternshipProgram` và `MentorProfile`.
   - Bảng `intern_profiles` (`InternProfile.java`): Đã có các trường `mentorId`, `mentorName`, `mentorEmail`, `status`.
   - Bảng `intern_mentor_assignments` (`InternMentorAssignment.java`): Đã có sẵn để lưu lịch sử phân công và trạng thái `ACTIVE`, `REPLACED`.
   - Enum `AuditAction.java`: Đã có các action phân công, bổ sung thêm `ASSIGN_MENTOR_TO_PROGRAM` nếu cần thiết.
2. **Khảo sát Service & Repository:**
   - `ProgramMentorRepository`: Có sẵn `findByProgramId(Long programId)`.
   - `InternProfileRepository`: Cần bổ sung `findByProgramIdAndStatusIn(Long programId, Collection<InternStatus> statuses)`.
   - `InternMentorAssignmentRepository`: Có sẵn `findByInternIdAndStatus(Long internId, MentorAssignmentStatus status)`. Bổ sung `findByInternIdInAndStatus` để tối ưu hóa truy vấn hàng loạt nếu cần.
   - `MissionBoardService` / `InternProfileService`: Có sẵn logic kiểm tra mentor, phát sự kiện `InternMentorAssignedEvent`. Tái sử dụng logic xác thực này.

---

## 2. Chi Tiết Các Bước Triển Khai (Step-by-Step Tasks)

### Bước 1: Khai Báo DTOs & Repository Methods
- **File tạo mới:**
  - `org/example/internservice/program/dto/request/AssignMentorToProgramRequest.java`:
    - `Long mentorId` (`@NotNull`)
    - `String notes`
  - `org/example/internservice/program/dto/response/AssignMentorToProgramResponse.java`:
    - `Long programId`, `String programName`
    - `Long mentorId`, `String mentorName`, `String mentorEmail`
    - `int totalAssignedInterns`, `int replacedMentorsCount`
    - `List<String> affectedInternCodes`, `LocalDateTime assignedAt`
- **File chỉnh sửa:**
  - `InternProfileRepository.java`: Bổ sung:
    ```java
    List<InternProfile> findByProgramIdAndStatusIn(Long programId, Collection<InternStatus> statuses);
    ```
  - `AuditAction.java`: Bổ sung `ASSIGN_MENTOR_TO_PROGRAM`.

### Bước 2: Xây Dựng / Mở Rộng Service Nghiệp Vụ
- **Interface:** Bổ sung vào `ProgramService.java`:
  ```java
  AssignMentorToProgramResponse assignMentorToProgram(Long programId, AssignMentorToProgramRequest request, String assignedBy);
  ```
- **Triển khai trong `ProgramServiceImpl.java`:**
  1. Kiểm tra tồn tại và trạng thái của `InternshipProgram`.
  2. Xác thực `MentorProfile` (phải tồn tại và ở trạng thái `ACTIVE`).
  3. Lưu hoặc cập nhật quan hệ `ProgramMentor`.
  4. Lấy tất cả `InternProfile` của chương trình có trạng thái `APPROVED` hoặc `INTERNING`.
  5. Với mỗi TTS:
     - Nếu đã có `mentorId` cũ khác với `request.getMentorId()`: Đóng assignment cũ sang `REPLACED` với lý do *"Điều phối lại Mentor toàn kỳ theo quyết định của HR"*.
     - Tạo bản ghi mới `InternMentorAssignment` trạng thái `ACTIVE`.
     - Gán `mentorId`, `mentorName`, `mentorEmail` cho TTS.
     - Kiểm tra điều kiện kép: nếu Program đang `ONGOING` và TTS đang `APPROVED`, chuyển sang `INTERNING`.
     - Bắn sự kiện `InternMentorAssignedEvent`.
  6. Lưu `saveAll(interns)`.
  7. Trả về `AssignMentorToProgramResponse`.

### Bước 3: Tự Động Kế Thừa Cho TTS Mới Nhập Sau Đó (Auto-Inheritance)
- **Trong `InternExcelImportServiceImpl.java`:**
  - Khi thực hiện `importInternsFromExcel`: Kiểm tra nếu `programMentorRepository.findByProgramId(programId)` có mentor, tự động gán mentor này cho các TTS mới import và tạo `InternMentorAssignment`.
- **Trong `InternProfileServiceImpl.java` (hoặc `ProgramServiceImpl.enrollInterns`):**
  - Khi một TTS được duyệt (`submitDecision` -> `APPROVED`), tự động kiểm tra xem chương trình có mentor không, nếu có thì gán tự động.

### Bước 4: Endpoint Controller Trong `ProgramController.java`
- Thêm REST endpoint:
  ```java
  @Operation(summary = "Phân công Mentor cho toàn bộ kỳ thực tập (HR/Admin)")
  @PostMapping("/programs/{id}/assign-mentor")
  @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
  public ResponseEntity<ApiResponse<AssignMentorToProgramResponse>> assignMentorToProgram(
          @PathVariable Long id,
          @Valid @RequestBody AssignMentorToProgramRequest request,
          Authentication authentication
  )
  ```

### Bước 5: Viết Unit Tests & Chạy Kiểm Thử Toàn Diện
- Tạo file kiểm thử `ProgramMentorAssignmentTest.java` (hoặc bổ sung trong `ProgramServiceTest.java`):
  - Phân công Mentor thành công cho Program có 5 TTS (tất cả đều nhận Mentor).
  - Phân công Mentor ghi đè chính xác các TTS đã có Mentor cũ.
  - Tự động chuyển `APPROVED` sang `INTERNING` khi Program `ONGOING`.
  - Phân công thất bại khi Program không tồn tại hoặc Mentor chưa kích hoạt (`PENDING_ACTIVATION`).
- Chạy Gradle test suite để đảm bảo 100% test pass.

---

## 3. Rủi Ro & Chiến Lược Kiểm Soát (Risk Mitigation)

| Rủi ro tiềm ẩn | Mức độ | Biện pháp kiểm soát & Giải pháp kỹ thuật |
| :--- | :---: | :--- |
| **Số lượng TTS trong kỳ lớn gây nghẽn hiệu năng khi cập nhật** | Trung bình | Sử dụng `saveAll` trên `InternProfileRepository` và tối ưu batch save thay vì gọi save từng bản ghi đơn lẻ. |
| **Xung đột nếu Mentor bị vô hiệu hóa hoặc chưa kích hoạt** | Thấp | Kiểm tra trạng thái `mentor.getStatus().equals("ACTIVE")` trước khi thực hiện phân công; báo lỗi `BadRequestException` rõ ràng. |
| **Gửi email quá tải đồng thời khi phát nhiều sự kiện** | Thấp | `InternMentorAssignedEvent` được xử lý bất đồng bộ (@Async) qua Event Listener, không chặn thread giao dịch chính của HTTP request. |

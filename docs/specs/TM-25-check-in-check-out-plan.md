# Implementation Plan: TM-25 Chấm Công Check-in / Check-out Cho Thực Tập Sinh (Backend)

Triển khai các thành phần Backend cho tính năng Chấm công (Check-in / Check-out) của Thực tập sinh trên vi dịch vụ `intern-and-program-service` (Port: 8082), tuân thủ 100% tài liệu đặc tả [TM-25-check-in-check-out-spec.md](file:///d:/Module_6/InternHub/docs/specs/TM-25-check-in-check-out-spec.md) và bộ quy chuẩn của dự án.

---

## 1. Danh Sách Tệp Tác Động (Impacted Files)

Tất cả các tệp mới thuộc package `org.example.internservice.attendance` trong `intern-and-program-service`:

### A. Entities & Enums
- `[NEW]` [AttendanceStatus.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/entity/enums/AttendanceStatus.java)
- `[NEW]` [Attendance.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/entity/Attendance.java) (Kế thừa `BaseEntity`, liên kết `internId`, unique `(internId, workDate)`)
- `[NEW]` [OfficeLocation.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/entity/OfficeLocation.java) (Kế thừa `BaseEntity`, cấu hình tọa độ gốc và bán kính 25m)

### B. Repositories
- `[NEW]` [AttendanceRepository.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/repository/AttendanceRepository.java)
- `[NEW]` [OfficeLocationRepository.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/repository/OfficeLocationRepository.java)

### C. DTOs
- `[NEW]` [CheckInRequest.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/dto/request/CheckInRequest.java) (`latitude`, `longitude`, `notes`)
- `[NEW]` [CheckOutRequest.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/dto/request/CheckOutRequest.java) (`latitude`, `longitude`, `notes`)
- `[NEW]` [AttendanceResponse.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/dto/response/AttendanceResponse.java)
- `[NEW]` [TodayAttendanceResponse.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/dto/response/TodayAttendanceResponse.java)
- `[NEW]` [MonthlyAttendanceSummaryResponse.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/dto/response/MonthlyAttendanceSummaryResponse.java)

### D. Utilities & Services
- `[NEW]` [HaversineDistanceCalculator.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/util/HaversineDistanceCalculator.java) (Tính khoảng cách không gian địa lý bằng mét)
- `[NEW]` [AttendanceService.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/service/AttendanceService.java)
- `[NEW]` [AttendanceServiceImpl.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/service/impl/AttendanceServiceImpl.java)

### E. Controller & Initializer
- `[NEW]` [AttendanceController.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/controller/AttendanceController.java) (Các API `/api/v1/attendances/...`)
- `[NEW]` [OfficeLocationDataInitializer.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/config/OfficeLocationDataInitializer.java) (Tự động khởi tạo tọa độ mặc định nếu database chưa có)

### F. Tests
- `[NEW]` [HaversineDistanceCalculatorTest.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/test/java/org/example/internservice/attendance/util/HaversineDistanceCalculatorTest.java)
- `[NEW]` [AttendanceServiceImplTest.java](file:///d:/Module_6/InternHub/intern-and-program-service/src/test/java/org/example/internservice/attendance/service/impl/AttendanceServiceImplTest.java)

---

## 2. Kế Hoạch Triển Khai Từng Bước (Proposed Changes)

### Phase 1: Entities, Enums, DTOs & Repositories
1. Tạo `AttendanceStatus` với 5 giá trị: `ON_TIME`, `LATE`, `EARLY_LEAVE`, `LATE_AND_EARLY_LEAVE`, `ABSENT`.
2. Tạo `Attendance` kế thừa `BaseEntity` với đầy đủ các trường tọa độ, khoảng cách, thời gian và unique constraint `uk_intern_work_date`.
3. Tạo `OfficeLocation` lưu thông tin tọa độ văn phòng và bán kính cho phép (mặc định 25.0m).
4. Tạo các Request và Response DTO chuẩn hóa với Bean Validation (`@NotNull`, `@DecimalMin`, `@DecimalMax`).
5. Tạo `AttendanceRepository` và `OfficeLocationRepository`.

### Phase 2: Haversine Calculator, Data Seeder & Business Logic
1. Viết `HaversineDistanceCalculator` tính khoảng cách giữa 2 cặp tọa độ (WGS84).
2. Viết `OfficeLocationDataInitializer` (`ApplicationRunner`) để tự động seed tọa độ gốc nếu bảng `office_locations` chưa có bản ghi nào.
3. Triển khai `AttendanceServiceImpl`:
   - Xác định `InternProfile` từ `userId` trong `CustomUserDetails`.
   - Lấy `workDate` theo Server timezone `Asia/Ho_Chi_Minh`.
   - `getTodayAttendance()`: Lấy bản ghi ngày hiện tại, trả về trạng thái tổng hợp phục vụ Widget Dashboard.
   - `checkIn()`:
     - Kiểm tra double-checkin (`DuplicateResourceException` nếu đã tồn tại).
     - Kiểm tra khoảng cách với văn phòng bằng Haversine. Nếu $> 25\text{m}$, ném `BadRequestException`.
     - So sánh thời gian Server hiện tại với `08:15:00` để phân loại `ON_TIME` hoặc `LATE`.
   - `checkOut()`:
     - Kiểm tra xem đã check-in chưa (ném `BadRequestException` nếu chưa).
     - Kiểm tra xem đã check-out chưa (ném `BadRequestException` nếu đã check-out).
     - Kiểm tra bán kính GPS $\le 25\text{m}$.
     - Tính `totalWorkingHours`. Nếu thời điểm check-out $< 17:30:00$, cập nhật trạng thái tương ứng (`EARLY_LEAVE` hoặc `LATE_AND_EARLY_LEAVE`).
   - `getMyHistory(month, year)`: Tổng hợp các ngày công, số lần đi muộn, về sớm và tổng giờ làm.

### Phase 3: REST Controller & Phân Quyền
1. Xây dựng `AttendanceController` tại `/api/v1/attendances`.
2. Đảm bảo mọi method trả về `ResponseEntity<ApiResponse<T>>`.
3. Kiểm tra phân quyền `@PreAuthorize("hasRole('INTERN')")`.

### Phase 4: Unit Test & Verification
1. Viết Unit Test cho `HaversineDistanceCalculator` (kiểm tra khoảng cách $\le 25\text{m}$ và $> 25\text{m}$).
2. Viết Unit Test cho `AttendanceServiceImpl` bao quát các kịch bản: Check-in đúng giờ, đi muộn, ngoài bán kính, double check-in, check-out sớm, check-out khi chưa check-in.
3. Chạy `.\gradlew :intern-and-program-service:compileJava` và `.\gradlew :intern-and-program-service:test` đảm bảo biên dịch và test pass 100%.

---

## 3. Verification Plan

### Automated Tests
```powershell
# 1. Kiểm tra biên dịch Java
.\gradlew :intern-and-program-service:compileJava

# 2. Chạy toàn bộ Unit Tests liên quan đến attendance
.\gradlew :intern-and-program-service:test --tests "org.example.internservice.attendance.*"
```

### Manual Verification Scenarios
- Kiểm tra tính toán tọa độ: Test case với tọa độ cách văn phòng $5\text{m}$ (thành công) và $50\text{m}$ (bị từ chối với HTTP 400).
- Kiểm tra logic giờ: Test case giả lập check-in lúc 08:10 (ON_TIME) vs 08:20 (LATE).

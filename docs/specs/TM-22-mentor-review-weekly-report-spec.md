# Specification: Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (TM-22)

> **Trạng thái:** DRAFT / PROPOSED (PENDING APPROVAL)  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md`  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-22](https://robluccibn9935.atlassian.net/browse/TM-22)  
> **Tiêu đề Jira:** *Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace)*  
> **Nối tiếp trực tiếp:** [TM-19 (Mentor giao nhiệm vụ)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md), [TM-20 (TTS cập nhật tiến độ Kanban)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md) & [TM-21 (TTS nộp báo cáo tuần)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)  
> **Nhánh Git dự kiến:** `feature/TM-22/mentor-review-weekly-report`  
> **Cấp độ thay đổi (Change Level):** **L3** (Mở rộng API nghiệp vụ cho `ROLE_MENTOR`, bổ sung trạng thái `REVISION_REQUESTED` và trường `revision_note` trên thực thể `InternWeeklyReport`, API đối soát song song báo cáo tuần và snapshot tasks của TTS, API yêu cầu làm lại báo cáo, cơ chế đồng bộ 2 chiều chuyển trạng thái sang `REVIEWED` khi công bố đánh giá, kiểm soát IDOR bảo mật nghiêm ngặt, bắn thông báo thời gian thực qua `NotificationEventDispatcher`).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, đặc biệt Rule 14, 15, 16, 17, 18, 19, 21, 22, 23, 26, 30, 31).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất (Tuân thủ Rule 30).

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-07 | AI Senior Pair-Programmer & User | `TM-22` | Tạo mới | Khởi tạo tài liệu đặc tả kỹ thuật Backend cho TM-22 nối tiếp trực tiếp sau TM-21, căn cứ trên Kế hoạch khảo sát hiện trạng [TM-22-pre-spec-discovery-and-action-plan.md](file:///C:/Users/admin/.gemini/antigravity-ide/brain/8646417a-8040-456e-a314-c050b7bac2bd/TM-22-pre-spec-discovery-and-action-plan.md). Chuẩn hóa 4 quyết định nghiệp vụ đã được Người dùng phê duyệt: (1) Cơ chế yêu cầu làm lại báo cáo (`REVISION_REQUESTED`), (2) Mentor có thể chủ động đánh giá linh hoạt dù TTS chưa nộp báo cáo, (3) Không gian đối soát Dual-Pane song song, (4) Cho phép cập nhật lại đánh giá trong thời gian diễn ra kỳ thực tập. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace).
- **Jira Ticket:** [TM-22](https://robluccibn9935.atlassian.net/browse/TM-22)
- **Tuyên Ngôn Nghiệp Vụ Cốt Lõi (Core Business Statement):**
  > **"ĐÁNH GIÁ VÀ PHẢN HỒI BÁO CÁO TUẦN LÀ HOẠT ĐỘNG KHÉP KÍN VÒNG LẶP HỌC TẬP (FEEDBACK LOOP). NƠI MENTOR ĐỐI CHIẾU TRỰC TIẾP GIỮA BÁO CÁO 4 TRỤ CỘT CỦA THỰC TẬP SINH VỚI KẾT QUẢ KANBAN THỰC TẾ, CHẤM ĐIỂM 4 TIÊU CHÍ RUBRICS CHUẨN HÓA, GỬI ĐỊNH HƯỚNG CHUYÊN MÔN KỊP THỜI HOẶC YÊU CẦU LÀM LẠI NẾU THIẾU MINH CHỨNG."**
  >
  > Trong chu trình quản trị đào tạo:
  > - **Mentor ([TM-19](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)):** Giao việc chi tiết cho TTS qua `MissionBoard` và `MissionItem`.
  > - **Thực tập sinh ([TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)):** Thực thi công việc hàng ngày qua bảng Kanban 3 cột (`TODO` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`).
  > - **Thực tập sinh ([TM-21](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)):** Cuối mỗi tuần, TTS tổng hợp công việc từ Kanban, đúc kết 4 trụ cột phản tư thành **Báo Cáo Tuần** gửi Mentor.
  > - **Mentor ([TM-22](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md) - Ticket Hiện Tại):** Tiếp nhận báo cáo tuần, đối soát snapshot tasks, chấm điểm 4 tiêu chí rubrics, gửi phản hồi xây dựng, giao mục tiêu tuần tới, có quyền yêu cầu làm lại báo cáo và công bố đánh giá để chốt tuần.

- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082):
     - Mở rộng thực thể `InternWeeklyReport` (thêm trạng thái `REVISION_REQUESTED` và cột `revision_note`).
     - Tái sử dụng thực thể `InternWeeklyAssessment` (lưu trữ điểm 4 tiêu chí, feedback, nextWeekGoals, status `DRAFT`/`PUBLISHED`).
     - Cung cấp API đối soát song song cho Mentor: `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}`.
     - Cung cấp API yêu cầu làm lại báo cáo: `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision`.
     - Nâng cấp API lưu/công bố đánh giá: `POST /api/interns/{internCode}/weekly-assessments` (đồng bộ trạng thái sang `REVIEWED`).
     - Nâng cấp API Triage Overview: `GET /api/mentors/my-interns/overview` (phản ánh đúng số lượng báo cáo chờ duyệt).
     - Tích hợp `NotificationEventDispatcher` bắn thông báo real-time cho TTS khi có đánh giá hoặc yêu cầu sửa đổi.
  2. `api-gateway` (Port 8080):
     - Định tuyến các endpoints `/api/mentors/**` và `/api/interns/**` về `intern-and-program-service`.

- **Target Users & Roles:**
  - **`ROLE_MENTOR` (Người Đánh Giá & Đồng Hành Trực Tiếp):**
    - Xem danh sách và hàng đợi các báo cáo tuần của TTS do mình phụ trách.
    - Xem chi tiết báo cáo 4 trụ cột và danh sách snapshot tasks của TTS theo từng tuần.
    - Chấm điểm 4 tiêu chí (1-5 sao), viết nhận xét chi tiết, giao mục tiêu tuần tới.
    - Lưu nháp đánh giá nội bộ (`DRAFT`) hoặc Công bố chính thức cho TTS (`PUBLISHED`).
    - Bấm yêu cầu làm lại báo cáo (`REVISION_REQUESTED`) kèm ghi chú nếu báo cáo không đạt yêu cầu.
    - Cập nhật lại đánh giá đã công bố trong thời gian kỳ thực tập còn hiệu lực.
  - **`ROLE_HR` / `ROLE_ADMIN` (Giám Sát & Điều Phối):**
    - Toàn quyền tra cứu và xem xét báo cáo/đánh giá tuần của bất kỳ TTS nào trong hệ thống.

- **Change Level:** **L3** (Mở rộng Enum & CSDL, thêm 2 endpoints Mentor mới, nâng cấp logic Service đồng bộ trạng thái 2 chiều, tích hợp thông báo thời gian thực).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Xóa Bỏ Tình Trạng "Đánh Giá Mù" (Zero Blind Evaluation):** Mentor được cung cấp không gian đối soát song song (Dual-Pane View), đọc trực tiếp 4 trụ cột báo cáo của TTS và danh sách snapshot tasks đối ứng ngay cạnh form chấm điểm.
2. **Khép Kín Vòng Lặp Phản Hồi Định Hướng (Actionable Feedback Loop):** Không chỉ chấm điểm số đơn thuần, Mentor cung cấp nhận xét mang tính xây dựng và giao mục tiêu cụ thể (`nextWeekGoals`) để TTS cải thiện trong tuần tiếp theo.
3. **Cơ Chế Kiểm Soát Chất Lượng Báo Cáo Linh Hoạt (Quality Gate):** Cho phép Mentor bấm "Yêu cầu chỉnh sửa lại" (`REVISION_REQUESTED`) khi phát hiện báo cáo sơ sài, thiếu minh chứng hoặc số liệu không trung thực, giúp nâng cao tính tự giác và chất lượng đào tạo.
4. **Không Gây Tắc Nghẽn Tiến Trình Thực Tập (Unblocked Evaluation Flow):** Nếu TTS chây ì không nộp báo cáo đúng hạn, Mentor vẫn có thể chủ động đánh giá tuần để ghi nhận thái độ/chuyên cần, đảm bảo hồ sơ kỳ thực tập không bị gián đoạn.
5. **Đồng Bộ Dữ Liệu Tức Thời & Bảo Mật Tuyệt Đối (Data Integrity & IDOR Defense):** Tự động đồng bộ trạng thái báo cáo sang `REVIEWED` khi Mentor công bố đánh giá; ngăn chặn triệt để mọi hành vi Mentor can thiệp vào sinh viên không thuộc quyền quản lý của mình.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope - 100% Thuộc Về Phân Hệ Mentor)

- **Xem chi tiết Báo cáo tuần & Snapshot Tasks của TTS:**
  - `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}`: Lấy toàn bộ nội dung báo cáo tuần của TTS (4 trụ cột, link tài liệu, ngày nộp, danh sách snapshot tasks) kèm theo bản ghi đánh giá hiện có của Mentor (nếu đã lưu nháp hoặc công bố).
- **Yêu cầu làm lại Báo cáo tuần (Request Revision):**
  - `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision`: Chuyển trạng thái báo cáo từ `SUBMITTED` sang `REVISION_REQUESTED`, lưu ghi chú `revisionNote`, và bắn thông báo thời gian thực đến TTS yêu cầu sửa lại.
- **Lưu Nháp & Công Bố Đánh Giá Tuần (Assessment & Publishing):**
  - `POST /api/interns/{internCode}/weekly-assessments`: Chấm điểm 4 tiêu chí rubrics (1-5 sao), tính điểm TB chuẩn, lưu nhận xét & mục tiêu tuần tới. Hỗ trợ lưu nháp (`DRAFT`) hoặc công bố (`PUBLISHED`).
  - Khi `isPublish = true`: Tự động tìm báo cáo tuần của TTS và chuyển sang `REVIEWED`, khóa cứng báo cáo với TTS và gửi thông báo real-time qua `NotificationEventDispatcher`.
  - Cho phép Mentor cập nhật lại đánh giá tuần đã công bố trong thời gian kỳ thực tập đang diễn ra.
- **Nâng cấp Mentor Triage Overview Hub:**
  - `GET /api/mentors/my-interns/overview`: Nâng cấp thuật toán đếm `needsWeeklyAssessmentCount` dựa trên số lượng TTS đã nộp báo cáo tuần (`SUBMITTED`) nhưng chưa được đánh giá công bố (`PUBLISHED`).
- **Bảo Vệ Tính Toàn Vẹn & Quyền Riêng Tư (Security & IDOR):**
  - Kiểm tra quyền sở hữu: Mentor chỉ được tra cứu và đánh giá TTS do mình trực tiếp phụ trách (`internProfile.mentorId == currentMentorId`). `HR` và `ADMIN` được phép truy cập toàn quyền.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)

- **KHÔNG SỬA ĐỔI GIAO DIỆN FRONTEND TRONG TICKET BACKEND NÀY:** Tuân thủ triệt để Rule 7 (Boundary Isolation). Giao diện Dual-Pane Frontend sẽ được đặc tả và triển khai ở spec riêng của `InternHub-Frontend`.
- **MENTOR KHÔNG TỰ VIẾT BÁO CÁO THAY CHO TTS:** Mentor chỉ đọc, nhận xét, chấm điểm hoặc yêu cầu làm lại; không sửa nội dung 4 trụ cột của TTS.
- **KHÔNG THAY THẾ ĐÁNH GIÁ GIỮA KỲ / CUỐI KỲ:** Đánh giá tuần là định kỳ thường xuyên, không thay thế phiếu đánh giá mốc kỳ `MIDTERM` / `FINAL`.
- **KHÔNG CHẠY SQL PHÁ HOẠI & KHÔNG TỰ COMMIT/PUSH GIT:** Tuân thủ Rule 8 và Rule 4.

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Lỗ hổng IDOR - Mentor A xem hoặc chấm điểm TTS của Mentor B
- **Vấn đề:** Mentor A gửi request `GET /api/mentors/my-interns/{internCode}/weekly-reports/2` nhưng `internCode` thuộc quyền quản lý của Mentor B.
- **Giải pháp:**
  - Service trích xuất `currentMentorId` và `userRole` từ `CustomUserDetails` trong Security Context.
  - Nếu `userRole == "ROLE_MENTOR"`, truy vấn `InternProfile` của `internCode`. Nếu `internProfile.getMentorId() == null || !internProfile.getMentorId().equals(currentMentorId)`, lập tức ném `AccessDeniedException("Bạn không có quyền truy cập hoặc đánh giá thực tập sinh này.")`.
  - Ngoại lệ: `ROLE_HR` và `ROLE_ADMIN` được phép bypass kiểm tra này để quản lý hệ thống.

### 4.2. Edge Case 2: Mentor yêu cầu sửa lại khi TTS chưa nộp báo cáo
- **Vấn đề:** Báo cáo tuần của TTS đang ở trạng thái `DRAFT` hoặc chưa tạo (`NOT_STARTED`), Mentor bấm API `POST .../request-revision`.
- **Giải pháp:**
  - Service kiểm tra `report.getStatus()`. Chỉ cho phép yêu cầu sửa lại khi báo cáo đang ở trạng thái `SUBMITTED`.
  - Nếu báo cáo không tồn tại hoặc `status != SUBMITTED`, ném `BadRequestException("Chỉ có thể yêu cầu chỉnh sửa đối với báo cáo tuần đã được nộp chính thức.")`.

### 4.3. Edge Case 3: Đánh giá khi TTS chưa nộp báo cáo (Mentor Proactive Assessment)
- **Vấn đề:** TTS không nộp báo cáo tuần, nhưng tuần thực tập đã kết thúc. Mentor cần ghi nhận đánh giá chuyên cần thấp để không tắc kỳ thực tập.
- **Giải pháp:**
  - Khi Mentor gọi `POST /api/interns/{internCode}/weekly-assessments`: Hệ thống không bắt buộc phải tồn tại `InternWeeklyReport`.
  - Hệ thống vẫn lưu `InternWeeklyAssessment` bình thường.
  - Nếu có tồn tại `InternWeeklyReport` ở trạng thái `SUBMITTED` hoặc `REVISION_REQUESTED`, khi Mentor publish, hệ thống tự động cập nhật báo cáo đó sang `REVIEWED`. Nếu chưa có báo cáo, hệ thống chỉ lưu assessment.

### 4.4. Edge Case 4: Mentor cập nhật lại đánh giá sau khi đã Publish
- **Vấn đề:** Mentor đã bấm publish nhưng sau đó trao đổi lại với TTS và muốn điều chỉnh lại điểm hoặc nhận xét.
- **Giải pháp:**
  - Hệ thống cho phép cập nhật lại (`Upsert` trên bản ghi assessment hiện có).
  - Cập nhật các trường điểm số, tính lại `averageScore`, cập nhật `feedback`, `nextWeekGoals` và `updatedAt`.
  - Bắn thông báo cập nhật cho TTS nếu có thay đổi quan trọng.
  - Ràng buộc: Chỉ cho phép cập nhật khi hồ sơ TTS vẫn còn trong trạng thái `INTERNING` hoặc `APPROVED` (chưa tốt nghiệp `COMPLETED` hoặc `TERMINATED`).

### 4.5. Edge Case 5: Bất đồng bộ trạng thái giữa Assessment và Report (Race Condition)
- **Vấn đề:** Mentor publish đánh giá nhưng vì lỗi kết nối nên bảng `intern_weekly_reports` không chuyển sang `REVIEWED`, dẫn đến TTS vẫn sửa được báo cáo.
- **Giải pháp:**
  - Đặt toàn bộ logic lưu `InternWeeklyAssessment` và cập nhật `InternWeeklyReport.status` trong cùng một `@Transactional` của Spring. Nếu một trong hai thao tác thất bại, toàn bộ giao dịch sẽ rollback ngay lập tức.

### 4.6. Edge Case 6: Lỗi N+1 Query JPA khi tải Báo Cáo Tuần kèm Snapshot Tasks
- **Vấn đề:** Khi Mentor mở màn hình đối soát tuần, Hibernate thực hiện query báo cáo tuần rồi query riêng lẻ từng snapshot task.
- **Giải pháp:**
  - Sử dụng phương thức `reportRepository.findByInternCodeAndWeekNumberWithTasks(internCode, weekNumber)` đã được tối ưu hóa với `LEFT JOIN FETCH r.tasks` trong TM-21.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng Của Mentor)

- **FR-1 (Tra cứu Chi tiết Báo Cáo Tuần Đối Soát Song Song):**
  - Cung cấp API cho Mentor tra cứu toàn diện dữ liệu của một tuần cụ thể:
    - Thông tin TTS: Họ tên, mã TTS, vị trí thực tập, tên chương trình.
    - Báo cáo tuần của TTS: 4 trụ cột (`completedTasksSummary`, `unfinishedTasksSummary`, `difficultiesAndChallenges`, `learningsAndKnowledge`), kế hoạch tuần tới (`nextWeekPlan`), link tài liệu đính kèm, thời điểm nộp (`submittedAt`), ghi chú yêu cầu sửa gần nhất (`revisionNote` nếu có).
    - Danh sách Snapshot Tasks: Tiêu đề, trạng thái tại thời điểm nộp, link sản phẩm/PR, ghi chú hoàn thành.
    - Bản ghi Đánh giá của Mentor hiện tại (nếu có): Điểm 4 tiêu chí, điểm TB, nhận xét, mục tiêu tuần tới, trạng thái (`DRAFT` / `PUBLISHED`).
  - Nếu TTS chưa nộp báo cáo cho tuần đó, API vẫn trả về thông tin tuần kèm bản ghi đánh giá của Mentor (với `report = null`).

- **FR-2 (Chấm Điểm & Đánh Giá 4 Tiêu Chí Rubrics Chuẩn):**
  - Mentor nhập điểm 4 tiêu chí (thang điểm 1 đến 5 nguyên dương):
    1. `technicalScore`: Kỹ năng chuyên môn & Kỹ thuật.
    2. `attitudeScore`: Thái độ & Tác phong làm việc.
    3. `teamworkScore`: Giao tiếp & Tinh thần đồng đội.
    4. `productivityScore`: Tiến độ hoàn thành công việc.
  - Hệ thống tự động tính điểm trung bình chuẩn:  
    $$\text{averageScore} = \frac{\text{technicalScore} + \text{attitudeScore} + \text{teamworkScore} + \text{productivityScore}}{4}$$  
    (Làm tròn 1 chữ số thập phân theo quy tắc `HALF_UP`).
  - Mentor nhập nội dung nhận xét bắt buộc (`feedback`) và mục tiêu tuần tới (`nextWeekGoals`).

- **FR-3 (Lưu Nháp Đánh Giá - Mentor Draft):**
  - Mentor có thể lưu dở dang đánh giá với `isPublish = false` (`status = DRAFT`).
  - Đánh giá dạng nháp chỉ Mentor (hoặc HR/Admin) nhìn thấy. TTS không nhìn thấy điểm số và nhận xét này. Báo cáo của TTS vẫn giữ nguyên trạng thái `SUBMITTED`.

- **FR-4 (Công Bố Đánh Giá - Publish Assessment & Khóa Báo Cáo):**
  - Mentor bấm công bố với `isPublish = true` (`status = PUBLISHED`), ghi nhận `publishedAt = now()`.
  - Hệ thống tự động chuyển trạng thái `InternWeeklyReport` của tuần đó sang `REVIEWED`. Báo cáo tuần bị khóa vĩnh viễn đối với TTS (Read-only).
  - Tự động kích hoạt sự kiện thông báo thời gian thực đến tài khoản TTS:  
    *"Mentor {mentorName} đã công bố đánh giá Tuần {weekNumber} cho bạn (Điểm TB: {averageScore}/5.0). Hãy xem phản hồi và mục tiêu tuần tới!"*

- **FR-5 (Yêu Cầu Làm Lại Báo Cáo - Request Revision):**
  - Khi báo cáo của TTS không đạt yêu cầu, Mentor bấm yêu cầu chỉnh sửa lại kèm lý do (`revisionNote` bắt buộc không để trống).
  - Hệ thống chuyển `InternWeeklyReport.status` sang `REVISION_REQUESTED`, lưu `revisionNote`.
  - Báo cáo tuần mở khóa cho phép TTS chỉnh sửa và bấm nộp lại.
  - Kích hoạt sự kiện gửi thông báo đến TTS:  
    *"Mentor {mentorName} đã yêu cầu bạn chỉnh sửa lại Báo cáo Tuần {weekNumber}. Lý do: {revisionNote}."*

- **FR-6 (Nâng Cấp Hàng Đợi Triage Overview Cho Mentor):**
  - Trả về tổng quan danh sách TTS phụ trách kèm trạng thái tuần hiện tại.
  - Cập nhật bộ đếm `needsWeeklyAssessmentCount`: Đếm số lượng TTS có báo cáo tuần hiện tại đang ở trạng thái `SUBMITTED` nhưng chưa có đánh giá `PUBLISHED`.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Ràng Buộc Thẩm Quyền Phân Công Mentor):** Mentor chỉ được phép xem báo cáo và thực hiện đánh giá đối với các TTS mà mình được phân công phụ trách (`internProfile.mentorId == currentMentorId`). `ROLE_HR` và `ROLE_ADMIN` có toàn quyền xem và can thiệp.
- **BR-2 (Ràng Buộc Thang Điểm Rubrics):** Điểm cho từng tiêu chí (`technicalScore`, `attitudeScore`, `teamworkScore`, `productivityScore`) phải là số nguyên dương trong đoạn $[1, 5]$.
- **BR-3 (Quy Tắc Đồng Bộ Trạng Thái Khóa Báo Cáo):** Khi `InternWeeklyAssessment` chuyển sang `PUBLISHED`, `InternWeeklyReport.status` bắt buộc chuyển sang `REVIEWED`. Báo cáo của TTS bị khóa cứng (Read-only).
- **BR-4 (Quy Tắc Mở Khóa Khi Yêu Cầu Sửa Đổi):** Khi báo cáo chuyển sang `REVISION_REQUESTED`, TTS được phép cập nhật nội dung và nộp lại (`POST .../submit`). Khi TTS nộp lại, trạng thái tự động trở về `SUBMITTED`.
- **BR-5 (Tính Linh Hoạt Khi TTS Chưa Nộp Báo Cáo):** Mentor được phép chủ động đánh giá một tuần thực tập ngay cả khi TTS chưa nộp báo cáo (`NOT_SUBMITTED` hoặc `DRAFT`).
- **BR-6 (Ràng Buộc Cập Nhật Đánh Giá Trong Kỳ):** Mentor được phép cập nhật lại điểm số và nhận xét của một tuần đã công bố (`PUBLISHED`) chừng nào hồ sơ TTS vẫn còn trong kỳ thực tập (`INTERNING` hoặc `APPROVED`).
- **BR-7 (Bắn Thông Báo Tự Động Hai Chiều):** Mọi hành vi công bố đánh giá (`PUBLISHED`) hoặc yêu cầu sửa đổi (`REVISION_REQUESTED`) đều kích hoạt bắn notification thời gian thực qua `NotificationEventDispatcher`.

---

## 7. Data Model (Mô Hình Dữ Liệu)

> [!NOTE]
> **Tuân thủ triệt để Rule 31 (Reuse First, Zero Unnecessary Redundancy):**
> - Tái sử dụng 100% bảng `intern_weekly_assessments`.
> - Tái sử dụng bảng `intern_weekly_reports` và `intern_weekly_report_tasks`.
> - Bổ sung giá trị `REVISION_REQUESTED` vào enum `WeeklyReportStatus`.
> - Bổ sung cột `revision_note` trên bảng `intern_weekly_reports`.

### 7.1. Cập Nhật CSDL MySQL (Migration Script)

```sql
-- 1. Bổ sung cột revision_note vào bảng intern_weekly_reports (nếu chưa có)
ALTER TABLE intern_weekly_reports 
ADD COLUMN IF NOT EXISTS revision_note TEXT NULL COMMENT 'Ghi chú lý do yêu cầu TTS chỉnh sửa lại báo cáo' 
AFTER report_attachment_url;

-- 2. Cập nhật comment cho cột status của intern_weekly_reports
ALTER TABLE intern_weekly_reports 
MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' 
COMMENT 'DRAFT, SUBMITTED, REVISION_REQUESTED, REVIEWED';
```

### 7.2. Cập Nhật JPA Entity Mapping

#### 1. Cập nhật `InternWeeklyReport.java`

```java
package org.example.internservice.intern.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "intern_weekly_reports",
    indexes = {
        @Index(name = "idx_weekly_report_intern", columnList = "intern_code"),
        @Index(name = "idx_weekly_report_mentor_status", columnList = "mentor_id, status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_weekly_report_intern_week", columnNames = {"intern_code", "week_number"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReport extends BaseEntity {

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "completed_tasks_summary", nullable = false, columnDefinition = "TEXT")
    private String completedTasksSummary;

    @Column(name = "unfinished_tasks_summary", columnDefinition = "TEXT")
    private String unfinishedTasksSummary;

    @Column(name = "difficulties_and_challenges", columnDefinition = "TEXT")
    private String difficultiesAndChallenges;

    @Column(name = "learnings_and_knowledge", columnDefinition = "TEXT")
    private String learningsAndKnowledge;

    @Column(name = "next_week_plan", columnDefinition = "TEXT")
    private String nextWeekPlan;

    @Column(name = "report_attachment_url", length = 500)
    private String reportAttachmentUrl;

    @Column(name = "revision_note", columnDefinition = "TEXT")
    private String revisionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WeeklyReportStatus status = WeeklyReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InternWeeklyReportTask> tasks = new ArrayList<>();

    public void addTask(InternWeeklyReportTask task) {
        tasks.add(task);
        task.setReport(this);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public enum WeeklyReportStatus {
        DRAFT,
        SUBMITTED,
        REVISION_REQUESTED,
        REVIEWED
    }
}
```

#### 2. Thực thể `InternWeeklyAssessment.java` (Tái sử dụng 100%)

Thực thể đã sẵn sàng tại `org.example.internservice.intern.entity.InternWeeklyAssessment.java` với các trường:
- `internCode`: String
- `mentorId`: Long
- `mentorName`: String
- `weekNumber`: Integer
- `assessmentDate`: LocalDate
- `technicalScore`: Integer (1-5)
- `attitudeScore`: Integer (1-5)
- `teamworkScore`: Integer (1-5)
- `productivityScore`: Integer (1-5)
- `averageScore`: BigDecimal (scale 1)
- `feedback`: String (TEXT)
- `nextWeekGoals`: String (TEXT)
- `status`: AssessmentStatus (`DRAFT`, `PUBLISHED`)
- `publishedAt`: LocalDateTime

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

> [!NOTE]
> Tuân thủ Rule 18 (RESTful chuẩn hóa), Rule 19 (Đóng gói 100% trong `ResponseEntity<ApiResponse<T>>`), Rule 16 (Phân tách Request & Response DTOs), Rule 9 (Kiểm tra RBAC chuẩn qua `@PreAuthorize`).

### 8.1. Danh Mục Toàn Bộ Endpoints Phân Hệ Mentor (TM-22)

| STT | HTTP Method | Endpoint | Quyền (PreAuthorize) | Trách nhiệm nghiệp vụ |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `GET` | `/api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` | `hasAnyRole('MENTOR', 'HR', 'ADMIN')` | **Xem chi tiết đối soát tuần:** Trả về báo cáo của TTS, snapshot tasks và đánh giá hiện tại của Mentor. Kiểm tra IDOR. |
| **2** | `POST` | `/api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision` | `hasAnyRole('MENTOR', 'HR', 'ADMIN')` | **Yêu cầu làm lại báo cáo:** Chuyển `status` sang `REVISION_REQUESTED`, ghi `revisionNote`, bắn notification cho TTS. |
| **3** | `POST` | `/api/interns/{internCode}/weekly-assessments` | `hasAnyRole('MENTOR', 'HR', 'ADMIN')` | **Lưu nháp hoặc Công bố đánh giá:** Chấm điểm 4 tiêu chí rubrics, tính điểm TB; khi publish tự động đổi báo cáo sang `REVIEWED`. |
| **4** | `GET` | `/api/interns/{internCode}/weekly-assessments` | `hasAnyRole('INTERN', 'MENTOR', 'HR', 'ADMIN')` | **Lịch sử đánh giá:** Xem lịch sử các tuần đã đánh giá của TTS. |
| **5** | `GET` | `/api/mentors/my-interns/overview` | `hasAnyRole('MENTOR', 'HR', 'ADMIN')` | **Mentor Triage Hub:** Thống kê tiến độ TTS và danh sách báo cáo tuần cần đánh giá. |

---

### 8.2. Chi Tiết Payloads Mẫu Chuẩn Hóa

#### 1. Xem chi tiết đối soát báo cáo tuần: `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}`

**Response Success (200 OK) - Trường hợp TTS đã nộp báo cáo:**

```json
{
  "code": 200,
  "success": true,
  "message": "Lấy chi tiết báo cáo tuần đối soát thành công",
  "data": {
    "internCode": "TTS-2026-001",
    "internName": "Nguyễn Văn An",
    "programName": "Chương trình Kỹ sư Cầu nối Java Spring 2026",
    "appliedPosition": "Backend Java Developer",
    "weekNumber": 3,
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "report": {
      "id": 15,
      "reportDate": "2026-10-07",
      "status": "SUBMITTED",
      "statusDisplayName": "Đã nộp, chờ đánh giá",
      "submittedAt": "2026-10-07T17:30:00",
      "completedTasksSummary": "- Hoàn thành task thiết kế Entity và Migration cho TM-20 Kanban\n- Đã viết unit test đạt độ phủ 100%",
      "unfinishedTasksSummary": "- Task Redis Cache đang nghiên cứu do lỗi serialization",
      "difficultiesAndChallenges": "Gặp khó khăn khi cấu hình Redis Cluster trên Docker Windows",
      "learningsAndKnowledge": "Hiểu sâu hơn về Spring Data Redis và kiến trúc Cache-Aside",
      "nextWeekPlan": "Hoàn tất module Cache và tích hợp với Service chính",
      "reportAttachmentUrl": "https://docs.google.com/document/d/xyz-sample-report",
      "revisionNote": null,
      "tasks": [
        {
          "id": 41,
          "missionItemId": 101,
          "taskTitle": "Thiết kế Entity và Migration cho TM-20 Kanban",
          "taskStatus": "COMPLETED",
          "submissionUrl": "https://github.com/codegym/repo/pull/45",
          "note": "Đã hoàn thành và test pass 8/8 unit tests",
          "isCompleted": true
        },
        {
          "id": 42,
          "missionItemId": 102,
          "taskTitle": "Tối ưu hóa Distributed Cache với Redis",
          "taskStatus": "IN_PROGRESS",
          "submissionUrl": null,
          "note": "Còn vướng lỗi kết nối serialization",
          "isCompleted": false
        }
      ]
    },
    "assessment": {
      "id": 8,
      "mentorId": 12,
      "mentorName": "Trần Văn Mentor",
      "technicalScore": 4,
      "attitudeScore": 5,
      "teamworkScore": 5,
      "productivityScore": 4,
      "averageScore": 4.5,
      "feedback": "Tiếp thu kiến trúc nhanh, chủ động fix bug auth tốt.",
      "nextWeekGoals": "Giải quyết dứt điểm lỗi serialization Redis, chuẩn bị demo.",
      "status": "DRAFT",
      "publishedAt": null
    }
  }
}
```

---

#### 2. Yêu cầu làm lại báo cáo: `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision`

**Request Body (`RequestReportRevisionRequest`):**

```json
{
  "revisionNote": "Báo cáo còn thiếu link PR minh chứng cho task Kanban số 101 và phần khó khăn ghi quá chung chung. Vui lòng bổ sung chi tiết trước 12:00 ngày mai."
}
```

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Đã gửi yêu cầu chỉnh sửa lại báo cáo tuần thành công cho Thực tập sinh",
  "data": {
    "internCode": "TTS-2026-001",
    "weekNumber": 3,
    "status": "REVISION_REQUESTED",
    "statusDisplayName": "Yêu cầu chỉnh sửa lại",
    "revisionNote": "Báo cáo còn thiếu link PR minh chứng cho task Kanban số 101 và phần khó khăn ghi quá chung chung. Vui lòng bổ sung chi tiết trước 12:00 ngày mai."
  }
}
```

---

#### 3. Lưu nháp hoặc Công bố đánh giá tuần: `POST /api/interns/{internCode}/weekly-assessments`

**Request Body (`WeeklyAssessmentRequest`):**

```json
{
  "weekNumber": 3,
  "technicalScore": 5,
  "attitudeScore": 5,
  "teamworkScore": 4,
  "productivityScore": 4,
  "feedback": "Em tiếp thu kiến trúc rất nhanh, chủ động giải quyết vấn đề và hỗ trợ đồng đội xuất sắc. Hoàn thành task đúng và vượt tiến độ đề ra.",
  "nextWeekGoals": "Nghiên cứu sâu hơn về GenericJackson2JsonRedisSerializer, tối ưu hóa latency và chuẩn bị bài thuyết trình kỹ thuật nội bộ.",
  "isPublish": true
}
```

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Lưu đánh giá tuần thành công",
  "data": {
    "id": 8,
    "internCode": "TTS-2026-001",
    "mentorId": 12,
    "mentorName": "Trần Văn Mentor",
    "weekNumber": 3,
    "assessmentDate": "2026-10-07",
    "technicalScore": 5,
    "attitudeScore": 5,
    "teamworkScore": 4,
    "productivityScore": 4,
    "averageScore": 4.5,
    "feedback": "Em tiếp thu kiến trúc rất nhanh, chủ động giải quyết vấn đề và hỗ trợ đồng đội xuất sắc. Hoàn thành task đúng và vượt tiến độ đề ra.",
    "nextWeekGoals": "Nghiên cứu sâu hơn về GenericJackson2JsonRedisSerializer, tối ưu hóa latency và chuẩn bị bài thuyết trình kỹ thuật nội bộ.",
    "status": "PUBLISHED",
    "publishedAt": "2026-10-07T18:45:00",
    "createdAt": "2026-10-07T18:30:00",
    "updatedAt": "2026-10-07T18:45:00"
  }
}
```

---

## 9. End-to-End Sequence & Data Flow (Luồng Tương Tác Trực Quan Phân Hệ TM-22)

```text
┌────────────────┐                     ┌───────────────────────────┐                 ┌────────────────┐
│ Mentor Hướng Dẫn│                     │ intern-and-program-service│                 │ Thực Tập Sinh  │
└───────┬────────┘                     └─────────────┬─────────────┘                 └───────┬────────┘
        │                                            │                                       │
        │ 1. Mở xem chi tiết đối soát tuần X         │                                       │
        ├───────────────────────────────────────────►│                                       │
        │ GET /my-interns/{code}/weekly-reports/{w}  │ (Kiểm tra IDOR phân công)             │
        │                                            │ (JOIN FETCH Report + Tasks)           │
        │◄───────────────────────────────────────────┤                                       │
        │ Trả về Dual-Pane Data:                     │                                       │
        │ [Báo cáo TTS + Tasks] vs [Form Đánh giá]   │                                       │
        │                                            │                                       │
        │ ─── KỊCH BẢN A: Báo cáo chưa đạt ───────── │                                       │
        │ 2A. Bấm Yêu cầu chỉnh sửa lại              │                                       │
        ├───────────────────────────────────────────►│                                       │
        │ POST .../request-revision                  │ Đổi status ➔ REVISION_REQUESTED       │
        │                                            │ Lưu revisionNote                      │
        │◄───────────────────────────────────────────┤ Dispatch REPORT_REVISION_REQUESTED    │
        │ Yêu cầu sửa thành công                     ├────────► Gửi Realtime Notification ───┼──► Nhận thông báo:
        │                                            │          qua notification-service     │    "Cần sửa báo cáo!"
        │                                            │                                       │    (Mở khóa báo cáo)
        │                                            │                                       │
        │ ─── KỊCH BẢN B: Báo cáo đạt yêu cầu ────── │                                       │
        │ 2B. Chấm 4 Rubrics & Gửi đánh giá          │                                       │
        ├───────────────────────────────────────────►│                                       │
        │ POST /api/interns/{code}/weekly-assessments│ 1. Lưu Assessment (PUBLISHED)         │
        │ (isPublish = true)                         │ 2. Đổi InternWeeklyReport ➔ REVIEWED  │
        │                                            │ 3. Khóa cứng báo cáo (Read-only)      │
        │◄───────────────────────────────────────────┤ Dispatch WEEKLY_ASSESSMENT_PUBLISHED  │
        │ Đánh giá công bố thành công                ├────────► Gửi Realtime Notification ───┼──► Nhận thông báo:
        │                                            │          qua notification-service     │    "Điểm tuần: 4.5⭐"
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Bảo Mật Chống Lỗ Hổng IDOR Tuyệt Đối (Rule 18, 9):**
   - Mọi request của `ROLE_MENTOR` đều phải kiểm tra quyền phụ trách trên `InternProfile`. Nghiêm cấm tin cậy tham số client gửi lên.
2. **Phòng Chống Triệt Để Lỗi N+1 Query JPA (Rule 22):**
   - Tải chi tiết báo cáo tuần kèm snapshot tasks bằng query `JOIN FETCH` duy nhất: `findByInternCodeAndWeekNumberWithTasks`.
3. **Quản Lý Giao Dịch Đồng Bộ An Toàn (`@Transactional`) (Rule 23):**
   - Toàn bộ chuỗi thao tác: lưu đánh giá, tính điểm trung bình, cập nhật trạng thái `InternWeeklyReport` sang `REVIEWED` và dispatch notification phải nằm trong cùng một transaction.
4. **Tiêu Chuẩn Anti-God-Class (Rule 17):**
   - Phân tách rõ ràng giữa `WeeklyAssessmentService` và `InternWeeklyReportService`. Sử dụng helper methods hoặc mapper chuyên trách.
5. **Constructor Injection An Toàn (Rule 21):**
   - 100% sử dụng `@RequiredArgsConstructor` từ Lombok, không dùng field injection `@Autowired`.
6. **Boundary Isolation (Rule 7):**
   - Tuyệt đối không can thiệp sửa đổi file nào trong `InternHub-Frontend/` trong phạm vi ticket Backend TM-22 này.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Đối Soát Song Song Báo Cáo Tuần):** Mentor phụ trách truy cập `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` lấy được đầy đủ 4 trụ cột báo cáo của TTS, danh sách snapshot tasks đối ứng và đánh giá hiện tại của Mentor.
- [ ] **AC-2 (Chặn IDOR Nghiêm Ngặt):** Mentor A cố tình truy vấn hoặc đánh giá TTS của Mentor B sẽ nhận ngay lỗi `403 FORBIDDEN`. `HR` và `ADMIN` được phép truy cập thành công.
- [ ] **AC-3 (Yêu Cầu Làm Lại Báo Cáo):** Mentor gửi yêu cầu sửa đổi thành công (`POST .../request-revision`): trạng thái báo cáo chuyển sang `REVISION_REQUESTED`, lưu `revisionNote`, TTS nhận thông báo real-time và được mở khóa để sửa/nộp lại.
- [ ] **AC-4 (Chấm Điểm Rubrics & Tính Điểm TB):** Điểm 4 tiêu chí được validate nghiêm ngặt trong đoạn $[1, 5]$. Điểm trung bình được tính tự động chính xác làm tròn 1 chữ số thập phân (`scale = 1`, `RoundingMode.HALF_UP`).
- [ ] **AC-5 (Đồng Bộ Khóa Báo Cáo Khi Publish):** Khi Mentor lưu với `isPublish = true`: `InternWeeklyAssessment.status` là `PUBLISHED`, `InternWeeklyReport.status` tự động chuyển sang `REVIEWED`, TTS bị khóa không thể sửa tiếp.
- [ ] **AC-6 (Mentor Chủ Động Đánh Giá Khi Chưa Có Báo Cáo):** Mentor vẫn có thể chấm điểm và công bố đánh giá cho tuần thực tập ngay cả khi TTS chưa nộp báo cáo (`NOT_STARTED` hoặc `DRAFT`).
- [ ] **AC-7 (Cho Phép Cập Nhật Đánh Giá Trong Kỳ):** Mentor có thể cập nhật lại điểm và nhận xét của một tuần đã công bố khi kỳ thực tập vẫn đang diễn ra.
- [ ] **AC-8 (Mentor Triage Hub Chuẩn Xác):** `GET /api/mentors/my-interns/overview` phản ánh chính xác số lượng TTS đã nộp báo cáo tuần đang chờ đánh giá.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Unit Tests (`WeeklyAssessmentServiceTest`)

- [ ] **UT-BE-01:** `getWeeklyReportForMentor_Success`: Mentor lấy chi tiết báo cáo đối soát của TTS phụ trách thành công (có cả report, tasks và assessment).
- [ ] **UT-BE-02:** `getWeeklyReportForMentor_Fail_IDOR`: Ném `AccessDeniedException` khi Mentor cố tình xem báo cáo của TTS không thuộc quyền quản lý của mình.
- [ ] **UT-BE-03:** `requestRevision_Success`: Mentor yêu cầu sửa lại báo cáo thành công, đổi status sang `REVISION_REQUESTED`, lưu `revisionNote` và bắn event notification.
- [ ] **UT-BE-04:** `requestRevision_Fail_NotSubmitted`: Ném `BadRequestException` khi yêu cầu sửa lại báo cáo chưa được nộp (`DRAFT` hoặc không tồn tại).
- [ ] **UT-BE-05:** `saveAssessment_Publish_SyncReportStatus`: Khi `isPublish = true`, kiểm tra `InternWeeklyAssessment` có status `PUBLISHED` và `InternWeeklyReport` đổi sang `REVIEWED`.
- [ ] **UT-BE-06:** `saveAssessment_Success_WithoutReport`: Mentor chấm điểm thành công cho tuần mà TTS chưa nộp báo cáo.
- [ ] **UT-BE-07:** `saveAssessment_UpdatePublishedAssessment`: Mentor cập nhật lại đánh giá đã publish thành công, tính lại điểm trung bình chính xác.

### 12.2. Integration Tests (`WeeklyAssessmentControllerIT`)

- [ ] **IT-BE-01:** Kiểm thử qua MockMvc: Mentor đăng nhập gọi `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` thành công (HTTP 200).
- [ ] **IT-BE-02:** Kiểm thử qua MockMvc: Mentor gọi `POST .../request-revision` thành công (HTTP 200).
- [ ] **IT-BE-03:** Kiểm thử qua MockMvc: Chặn Mentor không phân công (HTTP 403).
- [ ] **IT-BE-04:** Kiểm thử qua MockMvc: Chặn Intern gọi API của Mentor (HTTP 403).

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai TM-22)

### 13.1. Entity & Enum Updates
- [ ] Cập nhật `intern/entity/InternWeeklyReport.java`:
  - Bổ sung `REVISION_REQUESTED` vào enum `WeeklyReportStatus`.
  - Bổ sung trường `@Column(name = "revision_note", columnDefinition = "TEXT") private String revisionNote;`.

### 13.2. DTOs
- [ ] `intern/dto/request/RequestReportRevisionRequest.java` (Request body yêu cầu làm lại báo cáo)
- [ ] `intern/dto/response/MentorWeeklyReportReviewResponse.java` (Response chi tiết đối soát Dual-Pane cho Mentor)
- [ ] `intern/dto/response/ReportRevisionResponse.java` (Response xác nhận yêu cầu sửa đổi)

### 13.3. Service Layer
- [ ] Cập nhật `intern/service/WeeklyAssessmentService.java`:
  - Khai báo method `getWeeklyReportForMentor(String internCode, Integer weekNumber, Long mentorId, String role)`.
  - Khai báo method `requestReportRevision(String internCode, Integer weekNumber, RequestReportRevisionRequest request, Long mentorId, String mentorName, String role)`.
  - Cập nhật logic `saveAssessment` đồng bộ `InternWeeklyReport.status = REVIEWED` khi publish.
- [ ] Cập nhật `intern/service/impl/WeeklyAssessmentServiceImpl.java`:
  - Triển khai kiểm tra IDOR an toàn.
  - Tích hợp gọi `reportRepository.findByInternCodeAndWeekNumberWithTasks`.
  - Bắn sự kiện notification `REPORT_REVISION_REQUESTED` và `WEEKLY_ASSESSMENT_PUBLISHED`.

### 13.4. Controller Layer
- [ ] Cập nhật `intern/controller/WeeklyAssessmentController.java`:
  - Thêm endpoint `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}`.
  - Thêm endpoint `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision`.

### 13.5. Automated Tests
- [ ] `src/test/java/org/example/internservice/intern/service/WeeklyAssessmentServiceTest.java` (Unit tests bao phủ toàn bộ các kịch bản AC-1 đến AC-8).

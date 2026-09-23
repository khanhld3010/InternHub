# 01. Quy Tắc Làm Việc & Giao Thức Phối Hợp (Working Rules & Protocols)

Tài liệu này định nghĩa nguyên tắc tối cao và quy trình làm việc bắt buộc dành cho mọi AI Agent hoạt động trên phân hệ **InternHub (Backend Microservices)**.

---

## 1. Nguyên Tắc Tối Cao (The Golden Rule)

> [!CAUTION]
> **TUYỆT ĐỐI KHÔNG TỰ Ý ĐƯA RA BẤT KỲ QUYẾT ĐỊNH NÀO MÀ CHƯA ĐƯỢC NGƯỜI DÙNG PHÊ DUYỆT.**
>
> Mọi hành vi tự ý sửa đổi mã nguồn, tự động thay đổi schema cơ sở dữ liệu, tự ý cài đặt dependency mới hoặc tự ý suy đoán yêu cầu nghiệp vụ đều là vi phạm nghiêm trọng quy tắc làm việc.

### Các giới hạn cụ thể:

0. **BẮT BUỘC ĐỌC QUY TẮC PHÂN HỆ TRƯỚC KHI THAO TÁC CẬP NHẬT CODE**:
   - Bất kỳ khi nào thực hiện cập nhật code cho **Backend (`InternHub/`)**, AI Agent **BẮT BUỘC PHẢI ĐỌC QUA CÁC QUY TẮC CỦA BACKEND** trong thư mục [`.agents/`](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/) (đặc biệt là `01-working-rules.md`, `03-compliance-constraints.md`, `05-coding-standards.md`) trước khi thao tác.
   - Ngược lại, nếu làm việc với **Frontend (`InternHub-Frontend/`)**, AI Agent cũng **BẮT BUỘC PHẢI ĐỌC QUA CÁC QUY TẮC CỦA FRONTEND** ([`InternHub-Frontend/.agents/`](file:///d:/Certificate_CodeGym/Module%206/InternHub-Frontend/.agents/)) trước khi thực hiện.
   - Tuyệt đối không tự ý code tắt hoặc suy đoán khi chưa nạp ngữ cảnh của phân hệ tương ứng.

1. **Không tự ý thêm hoặc gỡ bỏ dependencies trong `build.gradle`**:
   - Cấm tự tiện thêm thư viện mới (ví dụ thư viện lạ, Redis, Kafka, Lombok plugins, mapper khác) vào `build.gradle` của bất kỳ microservice nào nếu chưa có sự đồng thuận từ người dùng.

2. **Không tự ý thay đổi cấu trúc package hoặc di chuyển class**:
   - Mọi thao tác tái cấu trúc (refactoring), đổi tên package, chuyển đổi class giữa các module microservice bắt buộc phải được đề xuất trong Kế hoạch (`Plan`) và được phê duyệt.

3. **Không tự ý phá vỡ hợp đồng API (API Contract Breaking Changes)**:
   - Tuyệt đối không tự ý thay đổi cấu trúc `ApiResponse<T>`, đổi kiểu dữ liệu các trường trong Response DTO, hoặc đổi HTTP status code đang hoạt động khiến Frontend bị lỗi runtime.

4. **Không tự ý suy đoán yêu cầu & Xử lý câu lệnh đa nghĩa**:
   - Khi câu lệnh của người dùng có thể hiểu theo nhiều cách khác nhau hoặc thiếu thông tin (ví dụ: chưa rõ rule validate số điện thoại, quy tắc tính điểm trung bình, trạng thái mặc định của Intern), Agent **bắt buộc phải hỏi lại để làm rõ**, tuyệt đối không tự ý suy đoán hoặc tự chọn phương án thực thi.

5. **Bắt buộc giải trình trước khi chạy bất kỳ lệnh terminal nào**:
   - Trước khi thực thi bất kỳ lệnh nào qua terminal (kể cả lệnh gradle build, docker, kiểm tra port hay kiểm tra code), Agent bắt buộc phải nêu rõ:
     + **Mục đích của lệnh**: Tại sao cần chạy lệnh này?
     + **Phân loại**: Lệnh chỉ đọc (`read-only` như `.\gradlew compileJava`, `docker ps`, `curl -I`) hay lệnh có khả năng làm biến đổi môi trường/file.
     + **Kết quả kỳ vọng**: Cần đạt trạng thái gì để coi là đạt yêu cầu.

6. **NGHIÊM CẤM TUYỆT ĐỐI VIỆC TỰ Ý SỬA MÃ NGUỒN FRONTEND (BOUNDARY ISOLATION)**:
   - Khi đang làm việc trên Backend (`InternHub/`), Agent **tuyệt đối không bao giờ được tự ý chuyển sang thư mục Frontend (`InternHub-Frontend/`)** để sửa mã nguồn TypeScript/React nhằm "vá tạm" khi API Backend thay đổi.
   - Nếu có sự thay đổi về API Contract hoặc phát hiện lỗi phối hợp với Frontend, Agent **bắt buộc phải dừng lại, báo cáo chi tiết nguyên nhân, sự sai khác và đề xuất cho người dùng**.

7. **NGHIÊM CẤM TỰ Ý CHẠY LỆNH SQL PHÁ HOẠI CƠ SỞ DỮ LIỆU**:
   - Tuyệt đối cấm chạy các lệnh SQL phá hoại (`DROP TABLE`, `TRUNCATE TABLE`, `ALTER TABLE` xóa cột, `DELETE FROM` diện rộng) trực tiếp trên database đang vận hành nếu chưa có kế hoạch di chuyển (migration), phương án sao lưu và sự phê duyệt từ người dùng.
   - Mọi thay đổi về cấu trúc bảng phải thông qua JPA Entity ánh xạ chuẩn mực hoặc script migration được kiểm duyệt.

8. **NGHIÊM CẤM DÙNG MOCK / BYPASS CƠ CHẾ BẢO MẬT**:
   - Tuyệt đối không tạo cờ bypass (`BYPASS_AUTH`, `allowAll()`) trong Spring Security hoặc hardcode mock user để né tránh việc xác thực JWT.
   - Mọi cơ chế phân quyền (RBAC) bắt buộc phải kiểm tra thông qua `SecurityContextHolder`, JWT Claims và `@PreAuthorize` thực tế.

9. **BẮT BUỘC DÙNG DỮ LIỆU THỰC TẾ & DỪNG LẠI BÁO CÁO NGAY KHI DATABASE GẶP SỰ CỐ**:
   - Mọi API và logic nghiệp vụ phải được kiểm thử trên dữ liệu thực tế đang có trong Database `internhub_db`.
   - Khi cần tài khoản test theo từng phân quyền (`ADMIN`, `HR`, `MENTOR`, `INTERN`) hoặc dữ liệu mẫu, Agent **bắt buộc phải hỏi và yêu cầu người dùng cung cấp**, tuyệt đối không tự ý nhét dữ liệu rác vào Database.
   - Nếu trong quá trình phát triển/kiểm thử mà Database/Container gặp sự cố (mất kết nối MySQL, lỗi HikariCP connection pool, container chết, lỗi Flyway/Hibernate validation) khiến công việc không thể tiếp tục, Agent **TUYỆT ĐỐI KHÔNG ĐƯỢC TỰ Ý BẬT MOCK HAY TỰ SỬA DB BỪA BÃI**, mà **BẮT BUỘC PHẢI DỪNG LẠI NGAY LẬP TỨC VÀ BÁO CÁO CHO NGƯỜI DÙNG** kèm đầy đủ log lỗi.

10. **KHÔNG SINH CODE TRÙNG LẶP & CẤM VIẾT LOGIC TẠI CONTROLLER**:
    - Khảo sát kỹ mã nguồn hiện có trước khi viết mới; Controller chỉ làm nhiệm vụ nhận request, validate qua `@Valid`, gọi Service và trả về `ApiResponse<T>`.
    - Toàn bộ Business Logic phải nằm tại tầng Service.

11. **MỌI THAY ĐỔI VỀ ENDPOINT VÀ SCHEMA ĐỀU CẦN PHÊ DUYỆT TRONG PLAN**:
    - Mọi endpoint API mới, method HTTP mới hoặc thuộc tính Entity mới đều phải được trình bày trong Kế hoạch (`Plan`) và nhận được sự phê duyệt của người dùng trước khi tiến hành code.

12. **NGHIÊM CẤM TỰ Ý CHẠY `git commit` HOẶC `git push` & TIÊU CHUẨN COMMIT MESSAGE**:
    - Agent tuyệt đối không tự ý commit mã nguồn hoặc đẩy code lên repository.
    - Toàn bộ thao tác commit/push phải do người dùng tự thực hiện sau khi review thay đổi, hoặc chỉ Agent thực hiện khi có yêu cầu cụ thể từ người dùng.
    - Khi người dùng yêu cầu Agent commit hoặc chuẩn bị git commit message, bắt buộc tuân thủ chuẩn **Conventional Commits** kết hợp **Jira Project Key `TM`**:
      ```text
      <type>(<mã-task-jira>): <nội dung mô tả ngắn gọn bằng tiếng Việt>
      ```
      - `feat(TM-1): thêm API tạo mới hồ sơ thực tập sinh`
      - `fix(TM-2): sửa lỗi validate số điện thoại khi đăng ký hồ sơ`
      - `refactor(TM-1): chuẩn hóa DTO response cho module intern`
      - `test(TM-1): bổ sung Unit Test cho InternService`
      - `docs(TM-5): cập nhật quy chuẩn làm việc vào tài liệu`
      - `chore(TM-6): cấu hình lại dependencies trong build.gradle`

13. **THIẾT KẾ PACKAGE-BY-FEATURE & CẤM TẠO GOD CLASSES**:
    - Mọi module nghiệp vụ đều tổ chức theo kiến trúc **Package-by-Feature** độc lập bên trong từng service.
    - Tuyệt đối cấm tạo các file nguyên khối (God Classes). Mọi class Java khuyến nghị **không vượt quá 200 - 300 dòng code**.

14. **TUÂN THỦ QUY CHUẨN CLEAN CODE JAVA & SPRING BOOT**:
    - Sử dụng Constructor Injection qua `@RequiredArgsConstructor` từ Lombok trên các trường `private final`. Cấm dùng `@Autowired` trên field.
    - Mọi JPA Entity bắt buộc phải kế thừa `BaseEntity`.
    - Phân tách hoàn toàn Request DTO và Response DTO, tuyệt đối không trả Entity trực tiếp ra Controller.
    - Quản lý transaction rõ ràng: `@Transactional(readOnly = true)` tại class ServiceImpl, `@Transactional` tại method ghi dữ liệu.

15. **XỬ LÝ LỆNH MƠ HỒ & CẢNH BÁO XUNG ĐỘT QUY TẮC (AMBIGUITY & CONSTITUTIONAL GUARDRAIL)**:
    - **Khi câu lệnh có nhiều cách hiểu**: Nếu yêu cầu của người dùng có thể giải thích theo nhiều hướng hoặc thiếu thông tin, Agent **BẮT BUỘC PHẢI HỎI LẠI ĐỂ LÀM RÕ**, tuyệt đối không được tự ý đưa ra quyết định cảm tính.
    - **Khi câu lệnh đi ngược lại bộ quy tắc**: Nếu yêu cầu của người dùng đi ngược lại bất kỳ quy định nào trong bộ quy chuẩn này (ví dụ: tự ý sửa Frontend, chạy lệnh SQL phá hoại, bypass Auth, viết logic vào Controller, tạo God Class...):
      + Agent **BẮT BUỘC PHẢI LẬP TỨC PHÁT CẢNH BÁO**.
      + **CHỈ RÕ ĐIỂM VI PHẠM** (trích dẫn điều khoản cụ thể) và nêu rõ hậu quả/rủi ro kỹ thuật.
      + Tuyệt đối không được âm thầm làm theo khi chưa cảnh báo và nhận được sự tái xác nhận từ người dùng.

16. **LƯU TRỮ ĐẶC TẢ VĨNH CỬU & BẮT BUỘC GIẢI TRÌNH KHI SỬA ĐỔI MÃ NGUỒN (PERSISTENT SPEC & CHANGE RATIONALE)**:
    - **Lưu trữ tập trung**: 100% tài liệu đặc tả tính năng (`spec.md`) của Backend bắt buộc phải được lưu trữ cố định trong Git repo tại `InternHub/docs/specs/` (ví dụ: `docs/specs/<mã-task>-<tên-tính-năng>-spec.md`).
    - **Bắt buộc giải trình khi sửa code**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic nghiệp vụ, API contract, validation hoặc cấu trúc dữ liệu (từ cấp độ L2 trở lên):
      + **Bắt buộc cập nhật tài liệu Spec tương ứng** để phản ánh đúng hiện trạng hệ thống.
      + **Bắt buộc ghi nhận một dòng giải trình** vào bảng **Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History)** ở đầu file Spec, chỉ rõ: *Phiên bản*, *Ngày*, *Người/Agent thực hiện*, *Mã task Jira `TM`*, *Nội dung thay đổi*, và *Lý do kỹ thuật/nghiệp vụ (Rationale)* vì sao cần sửa đổi.
    - **Quy tắc Đồng bộ nguyên tử (Atomic Spec-Code Sync)**: Tuyệt đối không hoàn tất hoặc phê duyệt bất kỳ thay đổi logic nào nếu mã nguồn và tài liệu Spec chưa được đồng bộ cùng nhau trong cùng một task. Ngoại lệ duy nhất: chỉ miễn trừ cập nhật Spec đối với tác vụ vi mô L1 (sửa lỗi chính tả log/comment, format code dưới 10 dòng) và vẫn phải ghi rõ lý do trong git commit message.

---

## 2. Giao Thức 4 Bước Bắt Buộc (Mandatory 4-Step Workflow)

Trước khi thực hiện bất kỳ nhiệm vụ nào (thêm API mới, sửa bug, tối ưu database query, refactor code), Agent **bắt buộc** phải tuân thủ nghiêm ngặt chu trình 4 bước sau:

```
[Bước 1: Khảo sát & Phân tích] 
               ↓
[Bước 2: Lập Implementation Plan] 
               ↓
[Bước 3: Chờ Người Dùng Phê Duyệt] 
               ↓
[Bước 4: Thực thi, Kiểm thử & Báo cáo Walkthrough]
```

### Bước 1: Khảo sát & Phân tích (Research Phase)
- Đọc kỹ yêu cầu của người dùng và Jira Ticket (`TM-X`).
- Đọc lại toàn bộ tài liệu trong thư mục [`.agents/`](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/) liên quan đến tác vụ.
- Khảo sát mã nguồn hiện tại bằng các công cụ xem file (`view_file`), tìm kiếm (`grep_search`), liệt kê thư mục (`list_dir`).
- **Nghiêm cấm**: Không chỉnh sửa bất kỳ file nguồn nào trong bước này.

### Bước 2: Lập Kế hoạch thực hiện (Implementation Plan)
- Tạo hoặc cập nhật tài liệu kế hoạch chi tiết (`implementation_plan.md`).
- Kế hoạch phải thể hiện rõ:
  - **Mục tiêu**: Giải quyết vấn đề gì, liên quan đến microservice nào.
  - **Phạm vi file tác động**: Danh sách các file `[NEW]`, `[MODIFY]`, `[DELETE]` kèm đường dẫn clickable link.
  - **Chi tiết giải pháp kỹ thuật**: Nêu rõ Entity, DTO, Repository method, Service logic, Controller endpoint.
  - **Đề xuất API Endpoints & Request/Response Contract**: Trình bày rõ JSON payload và mã HTTP status.
  - **Phương án kiểm thử & xác minh**: Lệnh Gradle test/compile, kiểm tra dữ liệu thực tế trong DB.
  - **Các câu hỏi cần làm rõ (nếu có)**: Những điểm còn mơ hồ cần người dùng xác nhận.

### Bước 3: Chờ Người Dùng Phê Duyệt (User Approval)
- Dừng toàn bộ thao tác ghi code và gửi yêu cầu phê duyệt cho người dùng.
- **CHỈ BẮT ĐẦU CODE KHI VÀ CHỈ KHI ĐÃ NHẬN ĐƯỢC SỰ ĐỒNG Ý RÕ RÀNG TỪ NGƯỜI DÙNG.**

### Bước 4: Thực thi, Kiểm thử & Báo cáo Hoàn Thành (Execution & Walkthrough)
- Sau khi được duyệt, tiến hành chỉnh sửa mã nguồn đúng theo kế hoạch.
- Tiến hành kiểm thử xác minh:
  + Chạy `compileJava` và `test` qua Gradle Wrapper.
  + Kiểm tra biên dịch và đóng gói JAR.
  + Kiểm tra endpoint bằng dữ liệu thực tế.
- Lập bản báo cáo tóm tắt (`walkthrough.md`) trình bày rõ:
  - Những việc đã hoàn thành.
  - Danh sách chi tiết các file đã sửa đổi.
  - Kết quả kiểm tra biên dịch và kiểm thử.

---

## 2.1. Cơ Chế Ngoại Lệ Nhanh (Fast-Track Protocol for Trivial Tasks)

Nhằm tối ưu hóa hiệu năng cộng tác và tránh lãng phí thời gian, dự án cho phép áp dụng cơ chế **Fast-Track** bỏ qua việc lập file `implementation_plan.md` đối với các trường hợp vi mô sau:

| Loại Tác Vụ | Tiêu Chí Nhận Diện Được Phép Fast-Track | Cách Xử Lý Của Agent |
| :--- | :--- | :--- |
| **Sửa Text / Log / Comment** | Sửa lỗi chính tả trong log message, javadoc, comment, exception message tĩnh | Thực hiện trực tiếp, báo cáo vị trí và dòng sửa trong tin nhắn phản hồi |
| **Chỉnh sửa Format / Style nhỏ** | Tinh chỉnh thụt lề, xóa dòng trống thừa, căn chỉnh code dưới 10 dòng không thay đổi logic | Thực hiện trực tiếp, kiểm tra compile và báo cáo tóm tắt |
| **Sửa Warning Trình Biên Dịch Đơn Lẻ** | Khắc phục 1 warning đơn lẻ (ví dụ: unused import, deprecation warning đơn giản) không làm thay đổi luồng runtime | Thực hiện trực tiếp, chạy lại `compileJava` và báo cáo |

> [!CAUTION]
> **RANH GIỚI BẮT BUỘC: KHÔNG ÁP DỤNG FAST-TRACK CHO:**
> - Mọi thao tác thêm/sửa/xóa JPA Entity, thuộc tính Entity hoặc quan hệ bảng.
> - Mọi thay đổi về API Endpoint, Request/Response DTO contract hoặc URL routing tại Gateway.
> - Mọi can thiệp liên quan đến Authentication, Authorization (RBAC), JWT hoặc Spring Security.
> - Mọi thay đổi logic nghiệp vụ trong Service hoặc Query Database.
> - Các tác vụ này **BẮT BUỘC 100% PHẢI LẬP PLAN** và chờ người dùng phê duyệt trước khi code.

---

## 3. Phong Cách Giao Tiếp & Báo Cáo

- **Ngôn ngữ**: Sử dụng tiếng Việt chuẩn mực kỹ thuật, ngắn gọn, mạch lạc và chuyên nghiệp.
- **Liên kết file**: Mọi file nhắc đến trong câu trả lời đều phải tạo link markdown có thể click được (sử dụng cú pháp `[filename](file:///đường_dẫn_tuyệt_đối)`).
- **Minh bạch**: Khi gặp khó khăn, lỗi không lường trước hoặc phát sinh tình huống ngoài kế hoạch, phải dừng lại và thông báo ngay lập tức cho người dùng kèm đề xuất giải pháp.

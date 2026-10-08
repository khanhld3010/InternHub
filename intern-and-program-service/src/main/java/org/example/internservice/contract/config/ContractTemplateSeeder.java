package org.example.internservice.contract.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.contract.entity.ContractTemplate;
import org.example.internservice.contract.entity.ContractTemplateVersion;
import org.example.internservice.contract.entity.enums.TemplateVersionStatus;
import org.example.internservice.contract.repository.ContractTemplateRepository;
import org.example.internservice.contract.repository.ContractTemplateVersionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ContractTemplateSeeder implements CommandLineRunner {

    private final ContractTemplateRepository templateRepository;
    private final ContractTemplateVersionRepository versionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (templateRepository.count() == 0) {
            log.info("==> [ContractTemplateSeeder] Khởi tạo dữ liệu mẫu hợp đồng mặc định ban đầu...");

            ContractTemplate standardTemplate = ContractTemplate.builder()
                    .code("TPL_STANDARD_INTERN")
                    .title("Mẫu Hợp Đồng Thực Tập Sinh Tiêu Chuẩn")
                    .description("Biểu mẫu thỏa thuận hợp đồng thực tập chuẩn doanh nghiệp với đầy đủ điều khoản bảo mật, phụ cấp và thời hạn.")
                    .isActive(true)
                    .createdBy("SYSTEM_INITIALIZER")
                    .build();

            standardTemplate = templateRepository.save(standardTemplate);

            String bodyTemplate = """
                <div style="font-family: 'Times New Roman', Times, serif; color: #111; line-height: 1.6;">
                  <div style="text-align: center; margin-bottom: 24px;">
                    <h3 style="margin: 0; text-transform: uppercase; font-size: 15px; font-weight: bold;">CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</h3>
                    <p style="margin: 2px 0 0; font-size: 14px; text-decoration: underline; font-weight: bold;">Độc lập - Tự do - Hạnh phúc</p>
                  </div>

                  <h2 style="text-align: center; font-size: 18px; font-weight: bold; margin-bottom: 20px; text-transform: uppercase;">
                    THỎA THUẬN HỢP ĐỒNG THỰC TẬP
                  </h2>

                  <p>Hôm nay, ngày {{currentDay}} tháng {{currentMonth}} năm {{currentYear}}, tại văn phòng Công ty, chúng tôi gồm có:</p>

                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">BÊN A: BÊN TIẾP NHẬN THỰC TẬP (CÔNG TY)</h4>
                  <ul style="list-style: none; padding-left: 0; margin: 0 0 12px;">
                    <li>- Đại diện: <strong>Phòng Quản Trị Nhân Sự</strong></li>
                    <li>- Chương trình: <strong>{{programTitle}}</strong></li>
                    <li>- Bộ phận tiếp nhận: <strong>{{department}}</strong></li>
                    <li>- Người hướng dẫn trực tiếp: <strong>{{supervisorName}}</strong></li>
                  </ul>

                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">BÊN B: THỰC TẬP SINH</h4>
                  <ul style="list-style: none; padding-left: 0; margin: 0 0 12px;">
                    <li>- Họ và tên: <strong>{{internFullName}}</strong></li>
                    <li>- Email liên hệ: <strong>{{internEmail}}</strong></li>
                    <li>- Số điện thoại: <strong>{{internPhone}}</strong></li>
                    <li>- Trường đào tạo: <strong>{{university}}</strong> (Chuyên ngành: {{major}})</li>
                  </ul>

                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">ĐIỀU 1: VỊ TRÍ VÀ THỜI GIAN THỰC TẬP</h4>
                  <p>- Vị trí thực tập: <strong>{{position}}</strong></p>
                  <p>- Thời hạn: Từ ngày <strong>{{startDate}}</strong> đến hết ngày <strong>{{endDate}}</strong>.</p>

                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">ĐIỀU 2: PHỤ CẤP VÀ CHẾ ĐỘ</h4>
                  <p>- Mức phụ cấp hỗ trợ hàng tháng: <strong>{{formattedAllowance}} VND / tháng</strong>.</p>
                  <p>- Mức hỗ trợ trên căn cứ theo kết quả chấm công và đánh giá chuyên cần trong kỳ thực tập.</p>

                  {{#customTermsSection}}
                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">ĐIỀU 3: ĐIỀU KHOẢN BỔ SUNG RIÊNG</h4>
                  <p style="white-space: pre-line;">{{customTerms}}</p>
                  {{/customTermsSection}}

                  <h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">ĐIỀU 4: CAM KẾT CHUNG</h4>
                  <p>Hai bên cam kết tuân thủ nghiêm túc nội quy công ty, bảo mật thông tin tài sản doanh nghiệp và các điều khoản đã thỏa thuận.</p>

                  <div style="margin-top: 40px; display: flex; justify-content: space-between; text-align: center;">
                    <div style="width: 45%;">
                      <p style="font-weight: bold; margin-bottom: 60px;">ĐẠI DIỆN BÊN A</p>
                      <p>(Ký, ghi rõ họ tên)</p>
                    </div>
                    <div style="width: 45%;">
                      <p style="font-weight: bold; margin-bottom: 60px;">ĐẠI DIỆN BÊN B</p>
                      <p><strong>{{internFullName}}</strong></p>
                    </div>
                  </div>
                </div>
                """;

            ContractTemplateVersion version1 = ContractTemplateVersion.builder()
                    .contractTemplate(standardTemplate)
                    .versionNumber(1)
                    .contentTemplate(bodyTemplate)
                    .status(TemplateVersionStatus.ACTIVE)
                    .createdBy("SYSTEM_INITIALIZER")
                    .build();

            versionRepository.save(version1);

            log.info("==> [ContractTemplateSeeder] Khởi tạo thành công Mẫu hợp đồng ID: {} (Version 1)", standardTemplate.getId());
        }
    }
}

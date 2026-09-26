package org.example.reportingservice.email.template;

import org.example.reportingservice.email.dto.request.SendInternDecisionEmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class EmailTemplateBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Value("${app.mail.company-name:InternHub Technology}")
    private String companyName;

    @Value("${app.mail.support-email:support@internhub.com}")
    private String supportEmail;

    @Value("${app.mail.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public String buildApprovedEmail(SendInternDecisionEmailRequest req) {
        String internName = req.getFullName();
        String position = req.getAppliedPosition() != null ? req.getAppliedPosition() : "Thực tập sinh";
        String department = req.getDepartment() != null && !req.getDepartment().isBlank() 
                ? req.getDepartment() : "Bộ phận Công nghệ / Phòng ban liên quan";
        String mentor = req.getMentorName() != null && !req.getMentorName().isBlank()
                ? req.getMentorName() : "Sẽ được thông báo trong ngày onboarding";
        String startDate = req.getStartDate() != null ? req.getStartDate().format(DATE_FORMATTER) : "Sẽ được thông báo sau";

        String onboardingLink = req.getOnboardingToken() != null && !req.getOnboardingToken().isBlank()
                ? frontendUrl + "/onboarding/activate?token=" + req.getOnboardingToken()
                : frontendUrl + "/login";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Chúc mừng! Hồ sơ được duyệt</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 20px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;\">\n" +
                "  <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 600px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);\">\n" +
                "    <!-- Header -->\n" +
                "    <tr>\n" +
                "      <td style=\"background-color: #4f46e5; padding: 24px 32px;\">\n" +
                "        <h1 style=\"color: #ffffff; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">InternHub</h1>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Body Content -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 32px;\">\n" +
                "        <!-- Badge -->\n" +
                "        <div style=\"display: inline-block; background-color: #ecfdf5; color: #059669; font-weight: 700; font-size: 13px; padding: 6px 14px; border-radius: 9999px; margin-bottom: 20px;\">\n" +
                "          ✓ HỒ SƠ ĐÃ ĐƯỢC DUYỆT\n" +
                "        </div>\n" +
                "        \n" +
                "        <!-- Greeting -->\n" +
                "        <h2 style=\"margin: 0 0 16px 0; font-size: 24px; font-weight: 800; color: #0f172a;\">\n" +
                "          Chúc mừng, " + escapeHtml(internName) + "! 🎉\n" +
                "        </h2>\n" +
                "        <p style=\"font-size: 15px; line-height: 1.6; color: #334155; margin: 0 0 24px 0;\">\n" +
                "          Chúng tôi rất vui thông báo rằng hồ sơ ứng tuyển vị trí <strong>" + escapeHtml(position) + "</strong> tại <strong>" + escapeHtml(companyName) + "</strong> của bạn đã được duyệt. Chào mừng bạn gia nhập đội ngũ!\n" +
                "        </p>\n" +
                "        \n" +
                "        <!-- Details Card -->\n" +
                "        <div style=\"background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 20px; margin-bottom: 28px;\">\n" +
                "          <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"font-size: 14px;\">\n" +
                "            <tr>\n" +
                "              <td style=\"padding: 8px 0; color: #64748b;\">Vị trí</td>\n" +
                "              <td style=\"padding: 8px 0; text-align: right; font-weight: 600; color: #0f172a;\">" + escapeHtml(position) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"padding: 8px 0; color: #64748b;\">Phòng ban</td>\n" +
                "              <td style=\"padding: 8px 0; text-align: right; font-weight: 600; color: #0f172a;\">" + escapeHtml(department) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"padding: 8px 0; color: #64748b;\">Mentor phụ trách</td>\n" +
                "              <td style=\"padding: 8px 0; text-align: right; font-weight: 600; color: #0f172a;\">" + escapeHtml(mentor) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"padding: 8px 0; color: #64748b;\">Ngày bắt đầu dự kiến</td>\n" +
                "              <td style=\"padding: 8px 0; text-align: right; font-weight: 600; color: #0f172a;\">" + escapeHtml(startDate) + "</td>\n" +
                "            </tr>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "        \n" +
                "        <!-- CTA Button -->\n" +
                "        <div style=\"text-align: center; margin-bottom: 28px;\">\n" +
                "          <a href=\"" + onboardingLink + "\" style=\"display: inline-block; background-color: #4f46e5; color: #ffffff; text-decoration: none; font-weight: 700; font-size: 15px; padding: 14px 28px; border-radius: 8px; box-shadow: 0 2px 4px rgba(79, 70, 229, 0.3);\">\n" +
                "            Xem thông tin onboarding &rarr;\n" +
                "          </a>\n" +
                "        </div>\n" +
                "        \n" +
                "        <p style=\"font-size: 14px; line-height: 1.6; color: #475569; margin: 0 0 12px 0;\">\n" +
                "          Trước ngày bắt đầu, bạn sẽ nhận thêm hướng dẫn chuẩn bị hồ sơ, tài khoản hệ thống và lịch onboarding chi tiết qua email riêng.\n" +
                "        </p>\n" +
                "        <p style=\"font-size: 14px; line-height: 1.6; color: #475569; margin: 0;\">\n" +
                "          Nếu có bất kỳ câu hỏi nào, đừng ngại liên hệ chúng tôi qua <a href=\"mailto:" + escapeHtml(supportEmail) + "\" style=\"color: #4f46e5; text-decoration: underline;\">" + escapeHtml(supportEmail) + "</a>.\n" +
                "        </p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Footer -->\n" +
                "    <tr>\n" +
                "      <td style=\"background-color: #f8fafc; border-top: 1px solid #f1f5f9; padding: 20px 32px; text-align: center; font-size: 12px; color: #94a3b8; line-height: 1.6;\">\n" +
                "        Email này được gửi tự động bởi hệ thống InternHub thay mặt " + escapeHtml(companyName) + ".<br>\n" +
                "        Mọi thắc mắc vui lòng liên hệ " + escapeHtml(supportEmail) + "\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";
    }

    public String buildRejectedEmail(SendInternDecisionEmailRequest req) {
        String internName = req.getFullName();
        String position = (req.getAppliedPosition() != null && !req.getAppliedPosition().isBlank()) 
                ? req.getAppliedPosition() : "Thực tập sinh";
        String jobsLink = frontendUrl + "/jobs";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Thông báo kết quả tuyển dụng</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 20px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;\">\n" +
                "  <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 600px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);\">\n" +
                "    <!-- Header -->\n" +
                "    <tr>\n" +
                "      <td style=\"background-color: #0f172a; padding: 24px 32px;\">\n" +
                "        <h1 style=\"color: #ffffff; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">InternHub</h1>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Body Content -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 32px;\">\n" +
                "        <!-- Greeting -->\n" +
                "        <h2 style=\"margin: 0 0 16px 0; font-size: 22px; font-weight: 800; color: #0f172a;\">\n" +
                "          Xin chào " + escapeHtml(internName) + ",\n" +
                "        </h2>\n" +
                "        <p style=\"font-size: 15px; line-height: 1.6; color: #334155; margin: 0 0 16px 0;\">\n" +
                "          Cảm ơn bạn đã dành thời gian ứng tuyển vị trí <strong>" + escapeHtml(position) + "</strong> tại <strong>" + escapeHtml(companyName) + "</strong>, cũng như đã quan tâm và tin tưởng chương trình thực tập của chúng tôi.\n" +
                "        </p>\n" +
                "        <p style=\"font-size: 15px; line-height: 1.6; color: #334155; margin: 0 0 20px 0;\">\n" +
                "          Sau khi xem xét kỹ hồ sơ, chúng tôi rất tiếc phải thông báo rằng <strong>lần này bạn chưa phù hợp</strong> với vị trí đang tuyển. Đây là một quyết định không dễ dàng, vì chúng tôi nhận được rất nhiều hồ sơ chất lượng cho đợt tuyển này.\n" +
                "        </p>\n" +
                "        <p style=\"font-size: 14px; line-height: 1.6; color: #475569; margin: 0 0 28px 0;\">\n" +
                "          Chúng tôi thực sự trân trọng những gì bạn đã thể hiện qua hồ sơ, và mong bạn tiếp tục theo dõi cũng như ứng tuyển vào các đợt tuyển dụng sắp tới của " + escapeHtml(companyName) + ".\n" +
                "        </p>\n" +
                "        \n" +
                "        <!-- CTA Button -->\n" +
                "        <div style=\"text-align: center; margin-bottom: 28px;\">\n" +
                "          <a href=\"" + jobsLink + "\" style=\"display: inline-block; background-color: #ffffff; color: #0f172a; text-decoration: none; font-weight: 700; font-size: 14px; padding: 12px 24px; border-radius: 8px; border: 1px solid #cbd5e1; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);\">\n" +
                "            Xem các vị trí đang tuyển &rarr;\n" +
                "          </a>\n" +
                "        </div>\n" +
                "        \n" +
                "        <p style=\"font-size: 14px; line-height: 1.6; color: #475569; margin: 0 0 8px 0;\">\n" +
                "          Chúc bạn nhiều may mắn và thành công trên chặng đường phía trước.\n" +
                "        </p>\n" +
                "        <p style=\"font-size: 14px; line-height: 1.6; color: #475569; margin: 0;\">\n" +
                "          Nếu có bất kỳ câu hỏi nào, vui lòng liên hệ chúng tôi qua <a href=\"mailto:" + escapeHtml(supportEmail) + "\" style=\"color: #4f46e5; text-decoration: underline;\">" + escapeHtml(supportEmail) + "</a>.\n" +
                "        </p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Footer -->\n" +
                "    <tr>\n" +
                "      <td style=\"background-color: #f8fafc; border-top: 1px solid #f1f5f9; padding: 20px 32px; text-align: center; font-size: 12px; color: #94a3b8; line-height: 1.6;\">\n" +
                "        Email này được gửi tự động bởi hệ thống InternHub thay mặt " + escapeHtml(companyName) + ".<br>\n" +
                "        Mọi thắc mắc vui lòng liên hệ " + escapeHtml(supportEmail) + "\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";
    }

    public String buildInternMentorAssignedEmail(String internName, String mentorName, String mentorEmail, String programName) {
        return "<!DOCTYPE html><html><body style=\"font-family: sans-serif; background-color: #f8fafc; padding: 20px;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 8px; border: 1px solid #e2e8f0;\">" +
                "<h2 style=\"color: #0f172a;\">Thông Báo Người Hướng Dẫn Kỹ Thuật (Mentor)</h2>" +
                "<p>Xin chào <strong>" + escapeHtml(internName) + "</strong>,</p>" +
                "<p>Ban Nhân sự xin thông báo bạn đã được phân công Người hướng dẫn (Mentor) chính thức thuộc chương trình <strong>" + escapeHtml(programName) + "</strong>.</p>" +
                "<div style=\"background: #f1f5f9; padding: 16px; border-radius: 6px; margin: 16px 0;\">" +
                "<p style=\"margin: 4px 0;\"><strong>Họ và tên Mentor:</strong> " + escapeHtml(mentorName) + "</p>" +
                "<p style=\"margin: 4px 0;\"><strong>Email liên hệ:</strong> " + escapeHtml(mentorEmail) + "</p>" +
                "</div>" +
                "<p>Mentor sẽ trực tiếp hướng dẫn, giao việc và đánh giá kết quả thực tập của bạn. Hãy chủ động liên hệ với Mentor để chuẩn bị cho kỳ thực tập nhé!</p>" +
                "<p style=\"color: #64748b; font-size: 13px; margin-top: 24px;\">Trân trọng,<br>" + escapeHtml(companyName) + "</p>" +
                "</div></body></html>";
    }

    public String buildMentorNewAssignedEmail(String mentorName, String internName, String internEmail, String internCode, String programName, String position, String notes) {
        return "<!DOCTYPE html><html><body style=\"font-family: sans-serif; background-color: #f8fafc; padding: 20px;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 8px; border: 1px solid #e2e8f0;\">" +
                "<h2 style=\"color: #0f172a;\">Thông Báo Tiếp Nhận Thực Tập Sinh Mới</h2>" +
                "<p>Xin chào <strong>" + escapeHtml(mentorName) + "</strong>,</p>" +
                "<p>Ban Nhân sự vừa phân công bạn phụ trách hướng dẫn thực tập sinh sau đây:</p>" +
                "<div style=\"background: #f1f5f9; padding: 16px; border-radius: 6px; margin: 16px 0;\">" +
                "<p style=\"margin: 4px 0;\"><strong>Họ và tên TTS:</strong> " + escapeHtml(internName) + " (" + escapeHtml(internCode) + ")</p>" +
                "<p style=\"margin: 4px 0;\"><strong>Email TTS:</strong> " + escapeHtml(internEmail) + "</p>" +
                "<p style=\"margin: 4px 0;\"><strong>Chương trình:</strong> " + escapeHtml(programName) + "</p>" +
                "<p style=\"margin: 4px 0;\"><strong>Vị trí ứng tuyển:</strong> " + escapeHtml(position) + "</p>" +
                (notes != null && !notes.isBlank() ? "<p style=\"margin: 4px 0;\"><strong>Ghi chú từ HR:</strong> " + escapeHtml(notes) + "</p>" : "") +
                "</div>" +
                "<p>Vui lòng đăng nhập vào hệ thống InternHub tại <a href=\"" + frontendUrl + "/mentor/dashboard\" style=\"color: #4f46e5;\">Mentor Portal</a> để xem chi tiết hồ sơ và chuẩn bị kế hoạch đào tạo.</p>" +
                "<p style=\"color: #64748b; font-size: 13px; margin-top: 24px;\">Trân trọng,<br>" + escapeHtml(companyName) + "</p>" +
                "</div></body></html>";
    }

    public String buildMentorOldHandoverEmail(String oldMentorName, String internName, String internCode, String newMentorName, String reason) {
        return "<!DOCTYPE html><html><body style=\"font-family: sans-serif; background-color: #f8fafc; padding: 20px;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 8px; border: 1px solid #e2e8f0;\">" +
                "<h2 style=\"color: #0f172a;\">Thông Báo Bàn Giao Hướng Dẫn Thực Tập Sinh</h2>" +
                "<p>Xin chào <strong>" + escapeHtml(oldMentorName) + "</strong>,</p>" +
                "<p>Ban Nhân sự xin thông báo thực tập sinh <strong>" + escapeHtml(internName) + "</strong> (" + escapeHtml(internCode) + ") đã được điều chuyển sang người hướng dẫn mới (" + escapeHtml(newMentorName) + ").</p>" +
                (reason != null && !reason.isBlank() ? "<div style=\"background: #fff1f2; border-left: 3px solid #f43f5e; padding: 12px; margin: 16px 0; color: #9f1239;\"><strong>Lý do thay đổi:</strong> " + escapeHtml(reason) + "</div>" : "") +
                "<p>Cảm ơn bạn đã đồng hành và hỗ trợ thực tập sinh trong thời gian qua.</p>" +
                "<p style=\"color: #64748b; font-size: 13px; margin-top: 24px;\">Trân trọng,<br>" + escapeHtml(companyName) + "</p>" +
                "</div></body></html>";
    }

    public String buildMentorRevokedEmail(String oldMentorName, String internName, String internCode, String reason) {
        return "<!DOCTYPE html><html><body style=\"font-family: sans-serif; background-color: #f8fafc; padding: 20px;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 8px; border: 1px solid #e2e8f0;\">" +
                "<h2 style=\"color: #0f172a;\">Thông Báo Kết Thúc Phụ Trách Thực Tập Sinh</h2>" +
                "<p>Xin chào <strong>" + escapeHtml(oldMentorName) + "</strong>,</p>" +
                "<p>Ban Nhân sự xin thông báo bạn đã được thu hồi phân công hướng dẫn đối với thực tập sinh <strong>" + escapeHtml(internName) + "</strong> (" + escapeHtml(internCode) + ").</p>" +
                (reason != null && !reason.isBlank() ? "<div style=\"background: #fff1f2; border-left: 3px solid #f43f5e; padding: 12px; margin: 16px 0; color: #9f1239;\"><strong>Lý do:</strong> " + escapeHtml(reason) + "</div>" : "") +
                "<p style=\"color: #64748b; font-size: 13px; margin-top: 24px;\">Trân trọng,<br>" + escapeHtml(companyName) + "</p>" +
                "</div></body></html>";
    }

    public String buildInternMentorRevokedEmail(String internName, String oldMentorName, String reason) {
        return "<!DOCTYPE html><html><body style=\"font-family: sans-serif; background-color: #f8fafc; padding: 20px;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 8px; border: 1px solid #e2e8f0;\">" +
                "<h2 style=\"color: #0f172a;\">Thông Báo Thay Đổi Người Hướng Dẫn (Mentor)</h2>" +
                "<p>Xin chào <strong>" + escapeHtml(internName) + "</strong>,</p>" +
                "<p>Ban Nhân sự xin thông báo: Người hướng dẫn <strong>" + escapeHtml(oldMentorName != null && !oldMentorName.isBlank() ? oldMentorName : "hiện tại") + "</strong> đã kết thúc phụ trách hướng dẫn bạn.</p>" +
                (reason != null && !reason.isBlank() ? "<div style=\"background: #fff1f2; border-left: 3px solid #f43f5e; padding: 12px; margin: 16px 0; color: #9f1239;\"><strong>Lý do thay đổi:</strong> " + escapeHtml(reason) + "</div>" : "") +
                "<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 14px; margin: 16px 0;\">" +
                "<p style=\"margin: 0; color: #334155; font-size: 14px;\">Ban Nhân sự đang tiến hành sắp xếp Người hướng dẫn mới phù hợp cho bạn trong thời gian sớm nhất. Lịch trình và thông tin Mentor mới sẽ được cập nhật trực tiếp trên hệ thống InternHub.</p>" +
                "</div>" +
                "<p style=\"color: #64748b; font-size: 13px; margin-top: 24px;\">Trân trọng,<br>" + escapeHtml(companyName) + "</p>" +
                "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

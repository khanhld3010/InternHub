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

    /**
     * Layout email chuẩn dùng chung cho toàn bộ hệ thống InternHub.
     * Sử dụng bảng màu Design System chuẩn:
     * - Text 1: #12141C
     * - Text 2: #5B6072
     * - Primary: #4F46E5
     * - Background: #F6F7FA
     * - Border: #E7E9EF
     * Có preheader ẩn chống rác preview trên email client, table layout bulletproof.
     */
    private String wrapEmail(String preheaderText, String badgeLabel, String badgeColor,
                              String badgeBg, String bodyHtml) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>" + escapeHtml(companyName) + "</title>\n" +
                "</head>\n" +
                "<body style=\"margin:0; padding:0; background-color:#F6F7FA; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">\n" +
                "  <!-- Preheader text for mail client preview -->\n" +
                "  <div style=\"display:none; max-height:0; overflow:hidden; opacity:0; mso-hide:all;\">" +
                escapeHtml(preheaderText) + "&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;</div>\n" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F6F7FA; padding: 24px 0;\">\n" +
                "    <tr><td align=\"center\" style=\"padding:12px 16px;\">\n" +
                "      <table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:600px; max-width:600px; background-color:#FFFFFF; border-radius:12px; overflow:hidden; border:1px solid #E7E9EF; box-shadow:0 4px 12px rgba(18,20,28,0.04);\">\n" +
                "        <!-- Brand Header -->\n" +
                "        <tr><td style=\"padding:24px 40px; background-color:#4F46E5;\">\n" +
                "          <span style=\"font-size:20px; font-weight:800; color:#FFFFFF; letter-spacing:-0.03em;\">InternHub</span>\n" +
                "        </td></tr>\n" +
                "        <!-- Main Content -->\n" +
                "        <tr><td style=\"padding:36px 40px 24px 40px;\">\n" +
                (badgeLabel != null && !badgeLabel.isBlank() ?
                        "          <div style=\"display:inline-block; background-color:" + badgeBg + "; color:" + badgeColor + "; font-size:12px; font-weight:700; padding:5px 12px; border-radius:999px; margin-bottom:20px; letter-spacing:0.02em;\">" + escapeHtml(badgeLabel) + "</div>\n"
                        : "") +
                bodyHtml +
                "        </td></tr>\n" +
                "        <!-- Brand Footer -->\n" +
                "        <tr><td style=\"padding:20px 40px; background-color:#FAFBFC; border-top:1px solid #F0F1F5;\">\n" +
                "          <p style=\"margin:0; font-size:12px; line-height:18px; color:#9599A8; text-align:center;\">Email này được gửi tự động bởi hệ thống InternHub thay mặt " + escapeHtml(companyName) + ".<br>Mọi thắc mắc vui lòng liên hệ <a href=\"mailto:" + escapeHtml(supportEmail) + "\" style=\"color:#4F46E5; text-decoration:none;\">" + escapeHtml(supportEmail) + "</a>.</p>\n" +
                "        </td></tr>\n" +
                "      </table>\n" +
                "    </td></tr>\n" +
                "  </table>\n" +
                "</body></html>";
    }

    /**
     * Bulletproof CTA button tương thích tốt với mọi client (bao gồm Outlook Desktop).
     */
    private String buildButton(String url, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0;\">\n" +
                "  <tr><td style=\"border-radius:8px; background-color:#4F46E5; box-shadow:0 2px 4px rgba(79,70,229,0.3);\">\n" +
                "    <a href=\"" + url + "\" target=\"_blank\" style=\"display:inline-block; padding:12px 28px; font-size:14px; font-weight:700; color:#FFFFFF; text-decoration:none; border-radius:8px;\">" +
                escapeHtml(label) + " →</a>\n" +
                "  </td></tr>\n" +
                "</table>";
    }

    /**
     * 1. Template: Kết quả duyệt tiếp nhận thực tập sinh (APPROVED)
     */
    public String buildApprovedEmail(SendInternDecisionEmailRequest req) {
        String internName = req.getFullName();
        String position = req.getAppliedPosition() != null ? req.getAppliedPosition() : "Thực tập sinh";
        String department = req.getDepartment() != null && !req.getDepartment().isBlank()
                ? req.getDepartment() : "Bộ phận Công nghệ / Phòng ban chuyên môn";
        String mentor = req.getMentorName() != null && !req.getMentorName().isBlank()
                ? req.getMentorName() : "Sẽ được thông báo trong ngày Onboarding";
        String startDate = req.getStartDate() != null ? req.getStartDate().format(DATE_FORMATTER) : "Sẽ được thông báo sau";

        String onboardingLink = req.getOnboardingToken() != null && !req.getOnboardingToken().isBlank()
                ? frontendUrl + "/onboarding/activate?token=" + req.getOnboardingToken()
                : frontendUrl + "/login";

        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Chúc mừng, " + escapeHtml(internName) + "! 🎉</h1>" +
                "<p style=\"margin:0 0 20px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Chúng tôi rất vui thông báo rằng hồ sơ ứng tuyển vị trí <strong style=\"color:#12141C;\">" + escapeHtml(position) + "</strong> tại <strong style=\"color:#12141C;\">" + escapeHtml(companyName) + "</strong> của bạn đã được tiếp nhận chính thức. Chào mừng bạn gia nhập đội ngũ!" +
                "</p>" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F6F7FA; border:1px solid #E7E9EF; border-radius:10px; margin:20px 0;\">" +
                "<tr><td style=\"padding:18px 24px;\">" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Vị trí:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(position) + "</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Phòng ban:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(department) + "</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Mentor hướng dẫn:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(mentor) + "</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Ngày bắt đầu dự kiến:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(startDate) + "</td></tr>" +
                "  </table>" +
                "</td></tr></table>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">Vui lòng hoàn tất kích hoạt tài khoản hệ thống để bắt đầu hành trình onboarding:</p>" +
                buildButton(onboardingLink, "Thiết Lập Tài Khoản & Onboarding") +
                "<p style=\"margin:16px 0 0 0; font-size:13px; line-height:20px; color:#5B6072;\">" +
                "Trước ngày bắt đầu, bạn sẽ nhận thêm hướng dẫn chuẩn bị hồ sơ và lịch làm việc chi tiết. Nếu cần hỗ trợ, vui lòng phản hồi email này." +
                "</p>";

        return wrapEmail(
                "Chúc mừng! Hồ sơ ứng tuyển của bạn tại " + companyName + " đã được duyệt",
                "✓ HỒ SƠ ĐÃ ĐƯỢC DUYỆT", "#0E9F6E", "#E8F9F1",
                body
        );
    }

    /**
     * 2. Template: Kết quả từ chối hồ sơ thực tập (REJECTED)
     */
    public String buildRejectedEmail(SendInternDecisionEmailRequest req) {
        String internName = req.getFullName();
        String position = (req.getAppliedPosition() != null && !req.getAppliedPosition().isBlank())
                ? req.getAppliedPosition() : "Thực tập sinh";
        String jobsLink = frontendUrl + "/jobs";

        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(internName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Cảm ơn bạn đã dành thời gian ứng tuyển vị trí <strong style=\"color:#12141C;\">" + escapeHtml(position) + "</strong> tại <strong style=\"color:#12141C;\">" + escapeHtml(companyName) + "</strong> cũng như đã quan tâm đến chương trình phát triển tài năng trẻ của chúng tôi." +
                "</p>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Sau khi xem xét kỹ lưỡng hồ sơ, chúng tôi rất tiếc phải thông báo hiện tại bạn chưa phù hợp với chỉ tiêu đợt tuyển dụng này. Đây là quyết định nhiều cân nhắc do số lượng hồ sơ ứng tuyển rất lớn." +
                "</p>" +
                "<p style=\"margin:0 0 20px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Thông tin của bạn đã được lưu trữ trong cơ sở dữ liệu nhân tài. Chúng tôi sẽ chủ động liên hệ lại khi có cơ hội thực tập phù hợp hơn trong các đợt tuyển dụng tiếp theo." +
                "</p>" +
                buildButton(jobsLink, "Xem Các Cơ Hội Khác") +
                "<p style=\"margin:16px 0 0 0; font-size:14px; line-height:22px; color:#5B6072;\">Chúc bạn luôn vững tin và gặt hái nhiều thành công trên con đường sự nghiệp!</p>";

        return wrapEmail(
                "Thông báo kết quả ứng tuyển vị trí " + position + " tại " + companyName,
                "THÔNG BÁO TUYỂN DỤNG", "#5B6072", "#F0F1F5",
                body
        );
    }

    /**
     * 3. Template: Thông báo tới Thực tập sinh khi được phân công Mentor (TM-16)
     */
    public String buildInternMentorAssignedEmail(String internName, String mentorName, String mentorEmail, String programName) {
        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(internName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Bạn đã được phân công Người hướng dẫn (Mentor) chính thức thuộc chương trình <strong style=\"color:#12141C;\">" + escapeHtml(programName) + "</strong>." +
                "</p>" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#EEF0FF; border:1px solid #D8DCFF; border-radius:12px; margin:20px 0;\">" +
                "<tr><td style=\"padding:20px 24px;\">" +
                "  <p style=\"margin:0 0 4px 0; font-size:12px; color:#5B6072; font-weight:600; text-transform:uppercase;\">Người hướng dẫn trực tiếp</p>" +
                "  <p style=\"margin:0 0 14px 0; font-size:16px; color:#12141C; font-weight:700;\">" + escapeHtml(mentorName) + "</p>" +
                "  <p style=\"margin:0 0 4px 0; font-size:12px; color:#5B6072; font-weight:600; text-transform:uppercase;\">Email liên hệ</p>" +
                "  <p style=\"margin:0; font-size:14px; color:#4F46E5; font-weight:600;\">" + escapeHtml(mentorEmail) + "</p>" +
                "</td></tr></table>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Mentor sẽ trực tiếp hướng dẫn kỹ thuật, giao bài tập và đánh giá kết quả thực tập. Bạn hãy chủ động liên hệ để chuẩn bị hành trang tốt nhất nhé!" +
                "</p>" +
                buildButton(frontendUrl + "/intern/dashboard", "Xem Trang Thực Tập Của Tôi");

        return wrapEmail(
                "Bạn đã có Mentor hướng dẫn: " + mentorName,
                "✓ ĐÃ CÓ MENTOR", "#0E9F6E", "#E8F9F1",
                body
        );
    }

    /**
     * 4. Template: Thông báo tới Mentor khi được giao thêm TTS mới (TM-16)
     */
    public String buildMentorNewAssignedEmail(String mentorName, String internName, String internEmail, String internCode, String programName, String position, String notes) {
        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(mentorName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Ban Nhân sự vừa phân công bạn phụ trách hướng dẫn thực tập sinh mới sau đây:" +
                "</p>" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F6F7FA; border:1px solid #E7E9EF; border-radius:10px; margin:20px 0;\">" +
                "<tr><td style=\"padding:18px 24px;\">" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Thực tập sinh:</td><td align=\"right\" style=\"padding:6px 0; font-weight:700; color:#12141C;\">" + escapeHtml(internName) + " (" + escapeHtml(internCode) + ")</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Email liên hệ:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(internEmail) + "</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Chương trình:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(programName) + "</td></tr>" +
                "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Vị trí:</td><td align=\"right\" style=\"padding:6px 0; font-weight:600; color:#12141C;\">" + escapeHtml(position) + "</td></tr>" +
                (notes != null && !notes.isBlank() ?
                        "    <tr><td style=\"padding:6px 0; color:#5B6072;\">Ghi chú HR:</td><td align=\"right\" style=\"padding:6px 0; color:#12141C;\">" + escapeHtml(notes) + "</td></tr>"
                        : "") +
                "  </table>" +
                "</td></tr></table>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Vui lòng truy cập Mentor Portal để xem chi tiết thông tin và chuẩn bị kế hoạch hướng dẫn:" +
                "</p>" +
                buildButton(frontendUrl + "/mentor/dashboard", "Mở Mentor Portal");

        return wrapEmail(
                "Phân công TTS mới: " + internName + " (" + internCode + ")",
                "PHÂN CÔNG MỚI", "#7C5CFC", "#F1EDFF",
                body
        );
    }

    /**
     * 5. Template: Thông báo tới Mentor cũ khi điều chuyển TTS sang Mentor khác (TM-16)
     */
    public String buildMentorOldHandoverEmail(String oldMentorName, String internName, String internCode, String newMentorName, String reason) {
        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(oldMentorName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Ban Nhân sự xin thông báo thực tập sinh <strong style=\"color:#12141C;\">" + escapeHtml(internName) + "</strong> (" + escapeHtml(internCode) + ") đã được điều chuyển sang người hướng dẫn mới (<strong style=\"color:#12141C;\">" + escapeHtml(newMentorName) + "</strong>)." +
                "</p>" +
                (reason != null && !reason.isBlank() ?
                        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FCEAE9; border:1px solid #F8CCC8; border-radius:8px; margin:16px 0;\">" +
                        "<tr><td style=\"padding:14px 18px;\"><p style=\"margin:0; font-size:13px; color:#E1493F;\"><strong>Lý do thay đổi:</strong> " + escapeHtml(reason) + "</p></td></tr></table>"
                        : "") +
                "<p style=\"margin:16px 0 0 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Chân thành cảm ơn những đóng góp và sự đồng hành của bạn cùng thực tập sinh trong thời gian qua." +
                "</p>";

        return wrapEmail(
                "Thông báo bàn giao TTS " + internName,
                "ĐIỀU CHUYỂN MENTOR", "#5B6072", "#F0F1F5",
                body
        );
    }

    /**
     * 6. Template: Thông báo tới Mentor khi thu hồi hoàn toàn phân công (TM-16)
     */
    public String buildMentorRevokedEmail(String oldMentorName, String internName, String internCode, String reason) {
        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(oldMentorName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Ban Nhân sự xin thông báo bạn đã được thu hồi phân công hướng dẫn đối với thực tập sinh <strong style=\"color:#12141C;\">" + escapeHtml(internName) + "</strong> (" + escapeHtml(internCode) + ")." +
                "</p>" +
                (reason != null && !reason.isBlank() ?
                        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FCEAE9; border:1px solid #F8CCC8; border-radius:8px; margin:16px 0;\">" +
                        "<tr><td style=\"padding:14px 18px;\"><p style=\"margin:0; font-size:13px; color:#E1493F;\"><strong>Lý do:</strong> " + escapeHtml(reason) + "</p></td></tr></table>"
                        : "") +
                "<p style=\"margin:16px 0 0 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Cảm ơn bạn đã luôn nhiệt huyết và hỗ trợ thực tập sinh trong suốt quá trình hướng dẫn." +
                "</p>";

        return wrapEmail(
                "Thông báo kết thúc phụ trách " + internName,
                "KẾT THÚC PHỤ TRÁCH", "#E1493F", "#FCEAE9",
                body
        );
    }

    /**
     * 7. Template: Thông báo tới Thực tập sinh khi Mentor của họ bị thu hồi (TM-16)
     */
    public String buildInternMentorRevokedEmail(String internName, String oldMentorName, String reason) {
        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Xin chào " + escapeHtml(internName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Ban Nhân sự xin thông báo: Người hướng dẫn <strong style=\"color:#12141C;\">" +
                escapeHtml(oldMentorName != null && !oldMentorName.isBlank() ? oldMentorName : "hiện tại") +
                "</strong> đã kết thúc phụ trách hướng dẫn bạn." +
                "</p>" +
                (reason != null && !reason.isBlank() ?
                        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FCEAE9; border:1px solid #F8CCC8; border-radius:8px; margin:16px 0;\">" +
                        "<tr><td style=\"padding:14px 18px;\"><p style=\"margin:0; font-size:13px; color:#E1493F;\"><strong>Lý do:</strong> " + escapeHtml(reason) + "</p></td></tr></table>"
                        : "") +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F6F7FA; border:1px solid #E7E9EF; border-radius:8px; margin:16px 0;\">" +
                "<tr><td style=\"padding:16px 20px;\">" +
                "  <p style=\"margin:0; color:#12141C; font-size:13px; line-height:20px;\">" +
                "    Ban Nhân sự đang tiến hành sắp xếp Người hướng dẫn mới phù hợp cho bạn trong thời gian sớm nhất. Lịch trình và thông tin Mentor mới sẽ được tự động cập nhật trên hệ thống InternHub." +
                "  </p>" +
                "</td></tr></table>" +
                buildButton(frontendUrl + "/intern/dashboard", "Kiểm Tra Trạng Thái Cá Nhân");

        return wrapEmail(
                "Thông báo thay đổi Người hướng dẫn (Mentor)",
                "ĐANG ĐIỀU PHỐI MENTOR", "#D97706", "#FEF3C7",
                body
        );
    }

    /**
     * 8. Template: Thư mời Onboarding kích hoạt tài khoản dành cho Mentor mới (TM-29)
     */
    public String buildMentorOnboardingEmail(String fullName, String departmentName, String onboardingToken) {
        String activationUrl = frontendUrl + "/onboarding/activate?token=" + onboardingToken + "&role=MENTOR";

        String body =
                "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:30px; font-weight:800; color:#12141C;\">Kính chào anh/chị " + escapeHtml(fullName) + ",</h1>" +
                "<p style=\"margin:0 0 16px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Ban Nhân sự trân trọng kính mời anh/chị tham gia vào đội ngũ <strong style=\"color:#12141C;\">Người Hướng Dẫn Kỹ Thuật (Mentor)</strong> trực thuộc <strong style=\"color:#12141C;\">" +
                escapeHtml(departmentName != null && !departmentName.isBlank() ? departmentName : "Công ty") +
                "</strong> trên nền tảng quản lý thực tập <strong style=\"color:#12141C;\">InternHub</strong>." +
                "</p>" +
                "<p style=\"margin:0 0 20px 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Để bắt đầu tiếp nhận phân công và đồng hành cùng các bạn thực tập sinh, anh/chị vui lòng nhấn nút bên dưới để thiết lập mật khẩu cá nhân và kích hoạt tài khoản hệ thống:" +
                "</p>" +
                buildButton(activationUrl, "Thiết Lập Mật Khẩu & Kích Hoạt") +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F6F7FA; border:1px solid #E7E9EF; border-radius:8px; margin:20px 0;\">" +
                "<tr><td style=\"padding:16px 20px;\">" +
                "  <p style=\"margin:0 0 6px 0; font-size:13px; font-weight:700; color:#12141C;\">Lưu ý an toàn & bảo mật:</p>" +
                "  <p style=\"margin:0; font-size:13px; line-height:20px; color:#5B6072;\">" +
                "    • Liên kết kích hoạt này có hiệu lực trong vòng <strong>7 ngày</strong>.<br>" +
                "    • Tuyệt đối không chia sẻ liên kết này với người khác để bảo vệ an toàn thông tin nội bộ." +
                "  </p>" +
                "</td></tr></table>" +
                "<p style=\"margin:16px 0 0 0; font-size:14px; line-height:22px; color:#5B6072;\">" +
                "Trân trọng cảm ơn sự đóng góp của anh/chị cho sự nghiệp phát triển thế hệ kỹ sư trẻ!<br>" +
                "<strong style=\"color:#12141C;\">Ban Quản Trị & Nhân Sự " + escapeHtml(companyName) + "</strong>" +
                "</p>";

        return wrapEmail(
                "Thư mời gia nhập đội ngũ Mentor tại " + companyName,
                "★ THƯ MỜI GIA NHẬP MENTOR", "#7C5CFC", "#F1EDFF",
                body
        );
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

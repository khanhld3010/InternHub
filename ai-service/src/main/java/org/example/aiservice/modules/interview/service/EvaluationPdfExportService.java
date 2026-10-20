package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.aiservice.modules.interview.dto.response.EvaluationDossierResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationPdfExportService {

    private final HrInterviewEvaluationService hrEvaluationService;

    public byte[] generateEvaluationPdf(Long sessionId) {
        EvaluationDossierResponse dossier = hrEvaluationService.getEvaluationDossier(sessionId);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont fontBold;
            PDFont fontRegular;

            // Nạp font Arial TTF từ resources để hỗ trợ đầy đủ ký tự tiếng Việt Unicode
            try {
                ClassPathResource fontRegularRes = new ClassPathResource("fonts/arial.ttf");
                ClassPathResource fontBoldRes = new ClassPathResource("fonts/arialbd.ttf");
                if (fontRegularRes.exists() && fontBoldRes.exists()) {
                    try (InputStream isReg = fontRegularRes.getInputStream();
                         InputStream isBold = fontBoldRes.getInputStream()) {
                        fontRegular = PDType0Font.load(document, isReg);
                        fontBold = PDType0Font.load(document, isBold);
                    }
                } else {
                    fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                    fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                }
            } catch (Exception ex) {
                log.warn("Không thể load font Arial TTF, fallback sang Helvetica: {}", ex.getMessage());
                fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            }

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;

                // Header
                contentStream.beginText();
                contentStream.setFont(fontBold, 16);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("INTERNHUB - AI INTERVIEW EVALUATION DOSSIER");
                contentStream.endText();
                y -= 30;

                // Subheader Info
                contentStream.beginText();
                contentStream.setFont(fontRegular, 10);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("Ứng viên: " + sanitizeText(dossier.getCandidateName()) + " (" + sanitizeText(dossier.getCandidateEmail()) + ")");
                contentStream.newLineAtOffset(0, -16);
                contentStream.showText("Đợt phỏng vấn: " + sanitizeText(dossier.getScheduleTitle()) + " | Phiên bản chấm: v" + dossier.getVersion());
                contentStream.newLineAtOffset(0, -16);
                contentStream.showText("Điểm AI: " + dossier.getTotalScore() + "/100 | Đề xuất tuyển dụng: " + dossier.getAiRecommendation());
                contentStream.newLineAtOffset(0, -16);
                contentStream.showText("Điểm HR chốt: " + (dossier.getHrFinalTotalScore() != null ? dossier.getHrFinalTotalScore() : dossier.getTotalScore()) + "/100 | Trạng thái HR: " + dossier.getHrReview().getStatus());
                contentStream.newLineAtOffset(0, -16);
                contentStream.showText("Giám sát phòng thi: " + dossier.getProctoringStatus() + " (Rời tab: " + dossier.getAntiCheatInfo().getBlurCount() + " lần, Dán văn bản: " + dossier.getAntiCheatInfo().getPasteCount() + " lần)");
                contentStream.endText();
                y -= 105;

                // Section: AI Summary
                contentStream.beginText();
                contentStream.setFont(fontBold, 13);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("1. AI EVALUATION SUMMARY");
                contentStream.endText();
                y -= 20;

                contentStream.beginText();
                contentStream.setFont(fontRegular, 10);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("Strengths: " + truncate(sanitizeText(dossier.getStrengthsSummary()), 90));
                contentStream.newLineAtOffset(0, -16);
                contentStream.showText("Weaknesses: " + truncate(sanitizeText(dossier.getWeaknessesSummary()), 90));
                contentStream.endText();
                y -= 50;

                // Section: Question Breakdown
                contentStream.beginText();
                contentStream.setFont(fontBold, 13);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("2. QUESTION BREAKDOWN & RUBRIC RATINGS");
                contentStream.endText();
                y -= 25;

                for (int i = 0; i < dossier.getAnswers().size() && y > 100; i++) {
                    EvaluationDossierResponse.AnswerEvaluationDetail a = dossier.getAnswers().get(i);
                    contentStream.beginText();
                    contentStream.setFont(fontBold, 10);
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText(String.format("Q%d (ID: %d): Score %.1f/100 (Weight: %.1f) - %s",
                            i + 1, a.getQuestionId(), a.getScore(), a.getScoreWeight(), a.getCategory()));
                    contentStream.newLineAtOffset(0, -14);
                    contentStream.setFont(fontRegular, 9);
                    contentStream.showText(String.format("Rubric: Accuracy=%.0f, Optimization=%.0f, Articulation=%.0f, EdgeCases=%.0f",
                            a.getRubric().getTechnicalAccuracy(),
                            a.getRubric().getOptimization(),
                            a.getRubric().getArticulation(),
                            a.getRubric().getEdgeCases()));
                    contentStream.newLineAtOffset(0, -14);
                    contentStream.showText("Rationale: " + truncate(sanitizeText(a.getRationale()), 100));
                    contentStream.endText();
                    y -= 50;
                }

                // Footer note
                contentStream.beginText();
                contentStream.setFont(fontRegular, 9);
                contentStream.newLineAtOffset(margin, 40);
                contentStream.showText("Generated by InternHub AI Interview Grader Engine. Evaluated with Gemini 1.5 Pro.");
                contentStream.endText();
            }

            document.save(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Lỗi khi render PDF báo cáo phỏng vấn: {}", e.getMessage());
            throw new RuntimeException("Không thể xuất file PDF kết quả đánh giá: " + e.getMessage(), e);
        }
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        // Loại bỏ ký tự điều khiển hoặc unicode không chuẩn để tránh crash PDFBox Type1
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

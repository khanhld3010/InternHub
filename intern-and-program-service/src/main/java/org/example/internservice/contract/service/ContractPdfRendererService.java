package org.example.internservice.contract.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class ContractPdfRendererService {

    /**
     * Chuyển đổi Canonical HTML cùng chữ ký điện tử sang file PDF chuẩn A4 (mã hóa byte[])
     */
    public byte[] renderContractPdf(String canonicalHtml, String signatureDataUrl, String internFullName) {
        try {
            log.info("Bắt đầu kết xuất PDF hợp đồng cho TTS: {}", internFullName);
            
            // 1. Nhúng chữ ký Canvas vào HTML
            String enrichedHtml = injectSignatureIntoHtml(canonicalHtml, signatureDataUrl, internFullName);

            // 2. Chuyển đổi sang tài liệu XHTML hợp lệ thông qua Jsoup
            Document doc = Jsoup.parse(enrichedHtml, "UTF-8");
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
            doc.outputSettings().charset(StandardCharsets.UTF_8);

            // 3. Đảm bảo thẻ style và page setup khổ A4
            ensurePrintStyles(doc);

            String xhtml = doc.html();

            // 4. Render qua Flying Saucer (XHTML -> PDF)
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();

            // Nạp Font TTF Unicode (Times New Roman) trực tiếp từ Classpath Resources
            try {
                org.xhtmlrenderer.pdf.ITextFontResolver fontResolver = renderer.getFontResolver();
                String[] fontFiles = {"times.ttf", "timesbd.ttf", "timesi.ttf", "timesbi.ttf"};
                for (String fontFile : fontFiles) {
                    var fontUrl = getClass().getClassLoader().getResource("fonts/" + fontFile);
                    if (fontUrl != null) {
                        fontResolver.addFont(fontUrl.toExternalForm(), com.lowagie.text.pdf.BaseFont.IDENTITY_H, com.lowagie.text.pdf.BaseFont.EMBEDDED);
                        log.info("Đã nạp font Unicode PDF thành công: {}", fontFile);
                    }
                }
            } catch (Exception fontEx) {
                log.warn("Cảnh báo: Không thể nạp font Unicode TTF từ resources/fonts: {}", fontEx.getMessage());
            }
            
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(outputStream);
            renderer.finishPDF();

            byte[] pdfBytes = outputStream.toByteArray();
            log.info("Kết xuất PDF hợp đồng thành công! Kích thước: {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            log.error("Lỗi khi kết xuất PDF hợp đồng: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file PDF hợp đồng từ bản ghi điện tử", e);
        }
    }

    private String injectSignatureIntoHtml(String html, String signatureDataUrl, String internFullName) {
        if (signatureDataUrl == null || signatureDataUrl.isBlank()) {
            return html;
        }

        // Tạo thẻ img chứa chữ ký số Base64
        String signatureImgTag = String.format(
                "<div style=\"margin-top: 8px; margin-bottom: 8px;\">" +
                "<img src=\"%s\" style=\"max-height: 70px; max-width: 180px; object-fit: contain; display: block; margin: 0 auto;\" alt=\"Chữ ký điện tử TTS\" />" +
                "</div>",
                signatureDataUrl
        );

        // Con dấu điện tử doanh nghiệp bên A (Digital Seal)
        String companyDigitalStamp = 
                "<div style=\"margin-top: 8px; margin-bottom: 8px; border: 1.5px dashed #059669; padding: 4px; display: inline-block; border-radius: 4px; background-color: #f0fdf4;\">" +
                "<div style=\"font-size: 11px; font-weight: bold; color: #047857; text-transform: uppercase;\">ĐÃ KÝ ĐIỆN TỬ BỞI CÔNG TY</div>" +
                "<div style=\"font-size: 9px; color: #065f46;\">InternHub Enterprise Verified</div>" +
                "</div>";

        String modifiedHtml = html;

        // Ghép chữ ký TTS vào vùng Bên B
        if (modifiedHtml.contains("ĐẠI DIỆN BÊN B")) {
            // Thay thế khoảng trống margin-bottom: 60px hoặc chèn trước tên TTS
            modifiedHtml = modifiedHtml.replace(
                    "<p style=\"font-weight: bold; margin-bottom: 60px;\">ĐẠI DIỆN BÊN B</p>",
                    "<p style=\"font-weight: bold; margin-bottom: 5px;\">ĐẠI DIỆN BÊN B</p>" + signatureImgTag
            );
        }

        // Ghép con dấu bên A
        if (modifiedHtml.contains("ĐẠI DIỆN BÊN A")) {
            modifiedHtml = modifiedHtml.replace(
                    "<p style=\"font-weight: bold; margin-bottom: 60px;\">ĐẠI DIỆN BÊN A</p>",
                    "<p style=\"font-weight: bold; margin-bottom: 5px;\">ĐẠI DIỆN BÊN A</p>" + companyDigitalStamp
            );
        }

        return modifiedHtml;
    }

    private void ensurePrintStyles(Document doc) {
        if (doc.head() != null) {
            doc.head().append(
                    "<style type=\"text/css\">" +
                    "@page { size: A4; margin: 20mm 15mm 20mm 15mm; }" +
                    "body { font-family: 'Times New Roman', serif; font-size: 13pt; line-height: 1.5; color: #111827; }" +
                    "table { width: 100%; border-collapse: collapse; }" +
                    "h2, h3, h4 { page-break-after: avoid; }" +
                    "p, div { word-wrap: break-word; }" +
                    "</style>"
            );
        }
    }
}

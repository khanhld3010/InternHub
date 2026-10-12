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

    /**
     * Nhúng con dấu mộc đỏ Bên A và chữ ký vẽ điện tử Bên B trực tiếp vào HTML (hỗ trợ cả preview và render PDF)
     */
    public String injectSignatureIntoHtml(String html, String signatureDataUrl, String internFullName) {
        if (html == null || html.isBlank()) {
            return html;
        }

        try {
            Document doc = Jsoup.parse(html, "UTF-8");

            // 1. Tạo con dấu mộc đỏ Bên A chuẩn doanh nghiệp (Red Stamp Badge)
            String companyRedStampHtml = """
                <div style="margin: 6px auto; width: 130px; height: 130px; border: 2.5px solid #dc2626; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: #dc2626; transform: rotate(-5deg); background-color: rgba(254, 242, 242, 0.6); box-sizing: border-box; padding: 6px;">
                    <div style="font-size: 8px; font-weight: bold; letter-spacing: 0.5px; text-transform: uppercase;">CÔNG TY CP INTERNHUB</div>
                    <div style="font-size: 16px; margin: 2px 0;">★ ★ ★</div>
                    <div style="font-size: 9px; font-weight: bold; border-top: 1px dashed #dc2626; border-bottom: 1px dashed #dc2626; padding: 2px 0; width: 90%;">ĐÃ KÝ ĐIỆN TỬ</div>
                    <div style="font-size: 7.5px; margin-top: 2px; color: #b91c1c;">HỆ THỐNG XÁC THỰC</div>
                </div>
            """;

            // 2. Chèn con dấu Bên A: Tìm thẻ chứa 'ĐẠI DIỆN BÊN A'
            var benAElements = doc.getElementsContainingOwnText("ĐẠI DIỆN BÊN A");
            if (!benAElements.isEmpty()) {
                var benAElement = benAElements.first();
                // Xóa hoặc giảm bớt margin trống
                benAElement.attr("style", "font-weight: bold; margin-bottom: 4px;");
                // Kiểm tra xem đã có stamp chưa để tránh duplicate
                if (benAElement.parent() != null && benAElement.parent().select(".company-stamp").isEmpty()) {
                    benAElement.after("<div class=\"company-stamp\" style=\"display: block; text-align: center;\">" + companyRedStampHtml + "</div>");
                }
            }

            // 3. Chèn chữ ký TTS Bên B (nếu có dữ liệu nét vẽ)
            if (signatureDataUrl != null && !signatureDataUrl.isBlank()) {
                String signatureImgTag = String.format(
                        "<div class=\"intern-signature-box\" style=\"margin-top: 6px; margin-bottom: 6px; text-align: center;\">" +
                        "<img src=\"%s\" style=\"max-height: 70px; max-width: 170px; object-fit: contain; display: block; margin: 0 auto;\" alt=\"Chữ ký điện tử TTS\" />" +
                        "</div>",
                        signatureDataUrl
                );

                var benBElements = doc.getElementsContainingOwnText("ĐẠI DIỆN BÊN B");
                if (!benBElements.isEmpty()) {
                    var benBElement = benBElements.first();
                    benBElement.attr("style", "font-weight: bold; margin-bottom: 4px;");
                    if (benBElement.parent() != null && benBElement.parent().select(".intern-signature-box").isEmpty()) {
                        benBElement.after(signatureImgTag);
                    }
                }
            }

            return doc.body().html();
        } catch (Exception e) {
            log.warn("Lỗi khi dùng Jsoup inject con dấu/chữ ký, giữ nguyên HTML gốc: {}", e.getMessage());
            return html;
        }
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

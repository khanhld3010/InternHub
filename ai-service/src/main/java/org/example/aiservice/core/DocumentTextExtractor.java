package org.example.aiservice.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Component
public class DocumentTextExtractor {

    public String extractText(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            log.warn("Nội dung file rỗng cho: {}", fileName);
            return "";
        }

        String lowerName = (fileName != null) ? fileName.toLowerCase() : "";
        try {
            if (lowerName.endsWith(".pdf")) {
                return extractPdfText(fileBytes);
            } else if (lowerName.endsWith(".docx")) {
                return extractDocxText(fileBytes);
            } else if (lowerName.endsWith(".doc")) {
                return extractDocText(fileBytes);
            } else {
                log.warn("Định dạng file không hỗ trợ trích xuất văn bản trực tiếp: {}", fileName);
                return "";
            }
        } catch (Exception e) {
            log.error("Lỗi khi bóc tách văn bản từ file {}: {}", fileName, e.getMessage(), e);
            return "";
        }
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawText = stripper.getText(document);
            return cleanExtractedText(rawText);
        }
    }

    private String extractDocxText(byte[] docxBytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(docxBytes);
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return cleanExtractedText(extractor.getText());
        }
    }

    private String extractDocText(byte[] docBytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(docBytes);
             org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(is);
             org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(doc)) {
            return cleanExtractedText(extractor.getText());
        }
    }

    private String cleanExtractedText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n]+", "\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}

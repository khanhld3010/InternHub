package org.example.aiservice.core.extractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTextExtractorTest {

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DocumentTextExtractor();
    }

    @Test
    @DisplayName("Xử lý an toàn khi file rỗng hoặc null")
    void testExtractTextWithEmptyBytes() {
        String resultNull = extractor.extractText(null, "cv.pdf");
        assertEquals("", resultNull);

        String resultEmpty = extractor.extractText(new byte[0], "cv.pdf");
        assertEquals("", resultEmpty);
    }

    @Test
    @DisplayName("Bỏ qua các định dạng không được hỗ trợ mà không văng lỗi")
    void testExtractTextWithUnsupportedFormat() {
        byte[] dummyBytes = "Dummy content".getBytes();
        String result = extractor.extractText(dummyBytes, "cv.txt");
        assertEquals("", result);
    }
}

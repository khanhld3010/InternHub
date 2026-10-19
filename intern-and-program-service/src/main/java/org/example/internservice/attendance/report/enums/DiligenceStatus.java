package org.example.internservice.attendance.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phân loại mức độ chuyên cần và kỷ luật đào tạo của Thực tập sinh (TM-26).
 */
@Getter
@RequiredArgsConstructor
public enum DiligenceStatus {

    EXCELLENT("Xuất sắc", "Kỷ luật chuẩn mực, đúng giờ tuyệt đối"),
    GOOD("Tốt", "Chuyên cần tốt, vi phạm không đáng kể"),
    WARNING("Cảnh báo", "Có biểu hiện đi muộn hoặc vắng mặt cần chấn chỉnh"),
    CRITICAL("Báo động", "Vi phạm kỷ luật nghiêm trọng, cần xử lý");

    private final String title;
    private final String description;
}

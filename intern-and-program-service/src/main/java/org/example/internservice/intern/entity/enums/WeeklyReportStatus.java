package org.example.internservice.intern.entity.enums;

public enum WeeklyReportStatus {
    DRAFT,               // Bản nháp đang soạn, TTS có thể sửa tự do
    SUBMITTED,           // Đã nộp cho Mentor, chờ xem xét
    REVISION_REQUESTED,  // Mentor yêu cầu chỉnh sửa lại nội dung
    REVIEWED             // Mentor đã hoàn tất công bố đánh giá tuần
}

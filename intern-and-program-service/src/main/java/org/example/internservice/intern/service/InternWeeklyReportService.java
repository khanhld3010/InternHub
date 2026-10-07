package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.SaveWeeklyReportRequest;
import org.example.internservice.intern.dto.response.SuggestedKanbanTasksResponse;
import org.example.internservice.intern.dto.response.WeeklyReportDetailResponse;
import org.example.internservice.intern.dto.response.WeeklyReportTimelineResponse;

public interface InternWeeklyReportService {

    /**
     * Lấy danh sách timeline toàn bộ các tuần thực tập của Thực tập sinh đang đăng nhập
     */
    WeeklyReportTimelineResponse getTimeline(Long userId);

    /**
     * Lấy gợi ý danh sách các nhiệm vụ từ Kanban TM-20 của tuần chỉ định
     */
    SuggestedKanbanTasksResponse getSuggestedKanbanTasks(Long userId, Integer weekNumber);

    /**
     * Tạo mới hoặc cập nhật (lưu nháp) báo cáo tuần
     */
    WeeklyReportDetailResponse saveOrUpdateReport(Long userId, SaveWeeklyReportRequest request);

    /**
     * Nộp chính thức báo cáo tuần và gửi thông báo cho Mentor phụ trách
     */
    WeeklyReportDetailResponse submitReport(Long userId, Integer weekNumber);

    /**
     * Xem chi tiết báo cáo của một tuần cụ thể
     */
    WeeklyReportDetailResponse getMyReportDetail(Long userId, Integer weekNumber);
}

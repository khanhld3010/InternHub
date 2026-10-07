package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.request.SaveWeeklyReportRequest;
import org.example.internservice.intern.dto.response.SuggestedKanbanTasksResponse;
import org.example.internservice.intern.dto.response.WeeklyReportDetailResponse;
import org.example.internservice.intern.dto.response.WeeklyReportTimelineResponse;
import org.example.internservice.intern.service.InternWeeklyReportService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interns/my-weekly-reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Intern Weekly Report Controller", description = "Quản lý và nộp báo cáo tuần cho Thực tập sinh (TM-21)")
public class InternWeeklyReportController {

    private final InternWeeklyReportService weeklyReportService;

    @Operation(summary = "Lấy danh sách timeline toàn bộ các tuần thực tập của TTS")
    @GetMapping
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<WeeklyReportTimelineResponse>> getTimeline(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WeeklyReportTimelineResponse response = weeklyReportService.getTimeline(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách báo cáo tuần thành công", response));
    }

    @Operation(summary = "Xem chi tiết báo cáo của một tuần cụ thể")
    @GetMapping("/{weekNumber}")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<WeeklyReportDetailResponse>> getReportDetail(
            @PathVariable Integer weekNumber,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WeeklyReportDetailResponse response = weeklyReportService.getMyReportDetail(userId, weekNumber);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết báo cáo tuần thành công", response));
    }

    @Operation(summary = "Lấy danh sách gợi ý nhiệm vụ từ bảng Kanban TM-20 của tuần đó")
    @GetMapping("/{weekNumber}/kanban-tasks")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<SuggestedKanbanTasksResponse>> getSuggestedKanbanTasks(
            @PathVariable Integer weekNumber,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        SuggestedKanbanTasksResponse response = weeklyReportService.getSuggestedKanbanTasks(userId, weekNumber);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách nhiệm vụ Kanban gợi ý thành công", response));
    }

    @Operation(summary = "Tạo mới hoặc lưu nháp báo cáo tuần")
    @PostMapping
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<WeeklyReportDetailResponse>> createOrSaveReport(
            @Valid @RequestBody SaveWeeklyReportRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WeeklyReportDetailResponse response = weeklyReportService.saveOrUpdateReport(userId, request);
        String msg = Boolean.TRUE.equals(request.getIsSubmit()) ? "Nộp báo cáo tuần thành công" : "Lưu nháp báo cáo tuần thành công";
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), msg, response));
    }

    @Operation(summary = "Chỉnh sửa nội dung báo cáo tuần (khi Mentor chưa công bố đánh giá)")
    @PutMapping("/{weekNumber}")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<WeeklyReportDetailResponse>> updateReport(
            @PathVariable Integer weekNumber,
            @Valid @RequestBody SaveWeeklyReportRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        request.setWeekNumber(weekNumber);
        WeeklyReportDetailResponse response = weeklyReportService.saveOrUpdateReport(userId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật báo cáo tuần thành công", response));
    }

    @Operation(summary = "Nộp chính thức báo cáo tuần và gửi thông báo cho Mentor")
    @PostMapping("/{weekNumber}/submit")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<WeeklyReportDetailResponse>> submitReport(
            @PathVariable Integer weekNumber,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        WeeklyReportDetailResponse response = weeklyReportService.submitReport(userId, weekNumber);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Nộp báo cáo tuần thành công. Thông báo đã được gửi đến Mentor phụ trách.", response));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Yêu cầu xác thực tài khoản hợp lệ");
    }
}

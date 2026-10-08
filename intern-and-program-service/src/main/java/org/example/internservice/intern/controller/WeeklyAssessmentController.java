package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.request.RequestReportRevisionRequest;
import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorTriageOverviewResponse;
import org.example.internservice.intern.dto.response.MentorWeeklyReportReviewResponse;
import org.example.internservice.intern.dto.response.ReportRevisionResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;
import org.example.internservice.intern.service.WeeklyAssessmentService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Weekly Assessment Controller", description = "Đánh giá tuần & Mentor Triage Hub (TM-22)")
public class WeeklyAssessmentController {

    private final WeeklyAssessmentService weeklyAssessmentService;

    @Operation(summary = "Tạo / Cập nhật đánh giá tuần cho Thực tập sinh (Mentor)")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    @PostMapping("/interns/{internCode}/weekly-assessments")
    public ResponseEntity<ApiResponse<WeeklyAssessmentResponse>> saveAssessment(
            @PathVariable String internCode,
            @Valid @RequestBody WeeklyAssessmentRequest request,
            Authentication authentication
    ) {
        Long mentorId = null;
        String mentorName = null;
        String role = null;

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            mentorId = userDetails.getUserId();
            mentorName = userDetails.getUsername();
            role = userDetails.getRole();
        }

        WeeklyAssessmentResponse response = weeklyAssessmentService.saveAssessment(internCode, request, mentorId, mentorName, role);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lưu đánh giá tuần thành công", response));
    }

    @Operation(summary = "Xem chi tiết đối soát báo cáo tuần của Thực tập sinh (Dual-Pane Review)")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    @GetMapping("/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}")
    public ResponseEntity<ApiResponse<MentorWeeklyReportReviewResponse>> getWeeklyReportForMentor(
            @PathVariable String internCode,
            @PathVariable Integer weekNumber,
            Authentication authentication
    ) {
        Long mentorId = null;
        String role = null;

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            mentorId = userDetails.getUserId();
            role = userDetails.getRole();
        }

        MentorWeeklyReportReviewResponse response = weeklyAssessmentService.getWeeklyReportForMentor(internCode, weekNumber, mentorId, role);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết báo cáo tuần đối soát thành công", response));
    }

    @Operation(summary = "Yêu cầu Thực tập sinh chỉnh sửa lại báo cáo tuần")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    @PostMapping("/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision")
    public ResponseEntity<ApiResponse<ReportRevisionResponse>> requestReportRevision(
            @PathVariable String internCode,
            @PathVariable Integer weekNumber,
            @Valid @RequestBody RequestReportRevisionRequest request,
            Authentication authentication
    ) {
        Long mentorId = null;
        String mentorName = null;
        String role = null;

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            mentorId = userDetails.getUserId();
            mentorName = userDetails.getUsername();
            role = userDetails.getRole();
        }

        ReportRevisionResponse response = weeklyAssessmentService.requestReportRevision(internCode, weekNumber, request, mentorId, mentorName, role);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Đã gửi yêu cầu chỉnh sửa lại báo cáo tuần thành công cho Thực tập sinh", response));
    }

    @Operation(summary = "Lấy lịch sử đánh giá tuần của Thực tập sinh")
    @GetMapping("/interns/{internCode}/weekly-assessments")
    public ResponseEntity<ApiResponse<List<WeeklyAssessmentResponse>>> getAssessmentHistory(
            @PathVariable String internCode,
            Authentication authentication
    ) {
        String role = "INTERN";
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            role = userDetails.getRole();
        }

        List<WeeklyAssessmentResponse> response = weeklyAssessmentService.getAssessmentHistory(internCode, role);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy lịch sử đánh giá tuần thành công", response));
    }

    @Operation(summary = "Thống kê nhanh danh sách TTS cho Mentor (Scale Triage Hub)")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    @GetMapping("/mentors/my-interns/overview")
    public ResponseEntity<ApiResponse<MentorTriageOverviewResponse>> getMentorTriageOverview(
            Authentication authentication
    ) {
        Long mentorId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            mentorId = userDetails.getUserId();
        }

        MentorTriageOverviewResponse response = weeklyAssessmentService.getMentorTriageOverview(mentorId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy tổng quan tiến độ TTS thành công", response));
    }
}

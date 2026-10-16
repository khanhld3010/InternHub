package org.example.internservice.attendance.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.InternAttendanceDetailReportResponse;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.attendance.report.service.AttendanceReportService;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.UnauthorizedException;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attendances/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance Report Controller", description = "Báo cáo đi làm và nghỉ phép quản lý chuyên cần cho HR (TM-26)")
public class AttendanceReportController {

    private final AttendanceReportService attendanceReportService;

    @Operation(summary = "Lấy báo cáo Dashboard KPI tổng quan chuyên cần toàn công ty")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<AttendanceSummaryKpiResponse>> getAttendanceSummaryKpi(
            @Valid AttendanceReportFilterRequest filter,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} gọi xem Dashboard KPI chuyên cần", userDetails.getUsername());
        AttendanceSummaryKpiResponse response = attendanceReportService.getAttendanceSummaryKpi(filter, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy báo cáo tổng quan chuyên cần thành công", response));
    }

    @Operation(summary = "Lấy danh sách bảng tổng hợp chuyên cần của các thực tập sinh (Phân trang 0-indexed)")
    @GetMapping("/interns")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<PageResponse<InternDiligenceReportResponse>>> getInternDiligenceReports(
            @Valid AttendanceReportFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} gọi xem danh sách báo cáo chuyên cần (page={}, size={})", userDetails.getUsername(), pageable.getPageNumber(), pageable.getPageSize());
        PageResponse<InternDiligenceReportResponse> response = attendanceReportService.getInternDiligenceReports(filter, pageable, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách báo cáo chuyên cần thành công", response));
    }

    @Operation(summary = "Lấy chi tiết bảng công và ngày nghỉ phép theo từng ngày của 1 thực tập sinh")
    @GetMapping("/interns/{internId}/details")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<InternAttendanceDetailReportResponse>> getInternAttendanceDetailReport(
            @PathVariable("internId") Long internId,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} gọi xem chi tiết bảng công của internId={}", userDetails.getUsername(), internId);
        InternAttendanceDetailReportResponse response = attendanceReportService.getInternAttendanceDetailReport(internId, month, year, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết bảng công của thực tập sinh thành công", response));
    }

    @Operation(summary = "Xuất báo cáo chuyên cần ra file CSV chuẩn UTF-8 BOM hiển thị tiếng Việt trên Excel")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<byte[]> exportAttendanceReportCsv(
            @Valid AttendanceReportFilterRequest filter,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} yêu cầu xuất báo cáo chuyên cần ra file CSV", userDetails.getUsername());
        byte[] csvData = attendanceReportService.exportAttendanceReportCsv(filter, userDetails);

        int month = filter.getMonth() != null ? filter.getMonth() : LocalDate.now().getMonthValue();
        int year = filter.getYear() != null ? filter.getYear() : LocalDate.now().getYear();
        String filename = String.format("BaoCaoChuyenCan_InternHub_Thang%02d_%d.csv", month, year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new UnauthorizedException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}

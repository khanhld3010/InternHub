package org.example.internservice.attendance.report.service;

import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.InternAttendanceDetailReportResponse;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;

/**
 * Interface định nghĩa các nghiệp vụ báo cáo đi làm, nghỉ phép và quản lý chuyên cần (TM-26).
 */
public interface AttendanceReportService {

    /**
     * Lấy dữ liệu Dashboard KPI tổng quan toàn công ty hoặc theo bộ lọc.
     */
    AttendanceSummaryKpiResponse getAttendanceSummaryKpi(AttendanceReportFilterRequest filter, CustomUserDetails userDetails);

    /**
     * Lấy danh sách tổng hợp chuyên cần của các Thực tập sinh theo phân trang và bộ lọc đa chiều.
     */
    PageResponse<InternDiligenceReportResponse> getInternDiligenceReports(AttendanceReportFilterRequest filter, Pageable pageable, CustomUserDetails userDetails);

    /**
     * Tra cứu chi tiết bảng công từng ngày và đơn nghỉ phép của 1 Thực tập sinh cụ thể trong tháng.
     */
    InternAttendanceDetailReportResponse getInternAttendanceDetailReport(Long internId, Integer month, Integer year, CustomUserDetails userDetails);

    /**
     * Xuất dữ liệu báo cáo chuyên cần sang file CSV chuẩn UTF-8 BOM.
     */
    byte[] exportAttendanceReportCsv(AttendanceReportFilterRequest filter, CustomUserDetails userDetails);
}

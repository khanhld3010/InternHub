package org.example.internservice.attendance.report.service.impl;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.DailyAttendanceDetailItem;
import org.example.internservice.attendance.report.dto.response.InternAttendanceDetailReportResponse;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.attendance.report.service.AttendanceReportService;
import org.example.internservice.attendance.report.util.AttendanceCsvExporter;
import org.example.internservice.attendance.report.util.AttendanceReportAggregator;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.MentorAccessHelper;
import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ tổng hợp báo cáo chuyên cần và nghỉ phép (TM-26).
 * Phân bổ tính toán sang AttendanceReportAggregator để tuân thủ Anti-God-Class (Rule 17).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AttendanceReportServiceImpl implements AttendanceReportService {

    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final InternProfileRepository internProfileRepository;
    private final AttendanceReportAggregator reportAggregator;
    private final AttendanceCsvExporter csvExporter;
    private final MentorAccessHelper mentorAccessHelper;

    @Override
    public AttendanceSummaryKpiResponse getAttendanceSummaryKpi(AttendanceReportFilterRequest filter, CustomUserDetails userDetails) {
        LocalDate[] range = resolveDateRange(filter);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];

        Specification<InternProfile> spec = buildInternSpecification(filter, userDetails);
        List<InternProfile> interns = internProfileRepository.findAll(spec);
        if (interns.isEmpty()) {
            return reportAggregator.buildEmptyKpiResponse(filter, startDate, endDate);
        }

        List<Long> internIds = interns.stream().map(InternProfile::getId).toList();
        List<Attendance> attendances = attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(internIds, startDate, endDate);
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(internIds, startDate, endDate);

        Map<Long, List<Attendance>> attendanceMap = attendances.stream().collect(Collectors.groupingBy(Attendance::getInternId));
        Map<Long, List<LeaveRequest>> leaveMap = leaves.stream().collect(Collectors.groupingBy(l -> l.getIntern().getId()));

        List<InternDiligenceReportResponse> reports = interns.stream()
                .map(i -> reportAggregator.calculateInternDiligence(
                        i, startDate, endDate,
                        attendanceMap.getOrDefault(i.getId(), List.of()),
                        leaveMap.getOrDefault(i.getId(), List.of()),
                        resolveDepartmentName(i),
                        resolveProgramTitle(i)))
                .toList();

        return reportAggregator.aggregateKpi(filter, startDate, endDate, reports, leaves);
    }

    @Override
    public PageResponse<InternDiligenceReportResponse> getInternDiligenceReports(AttendanceReportFilterRequest filter, Pageable pageable, CustomUserDetails userDetails) {
        LocalDate[] range = resolveDateRange(filter);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];

        Specification<InternProfile> spec = buildInternSpecification(filter, userDetails);

        if (filter.getDiligenceStatus() == null) {
            Page<InternProfile> internPage = internProfileRepository.findAll(spec, pageable);
            if (internPage.isEmpty()) {
                return PageResponse.from(new PageImpl<>(List.of(), pageable, 0));
            }

            List<Long> internIds = internPage.getContent().stream().map(InternProfile::getId).toList();
            List<Attendance> attendances = attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(internIds, startDate, endDate);
            List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(internIds, startDate, endDate);

            Map<Long, List<Attendance>> attendanceMap = attendances.stream().collect(Collectors.groupingBy(Attendance::getInternId));
            Map<Long, List<LeaveRequest>> leaveMap = leaves.stream().collect(Collectors.groupingBy(l -> l.getIntern().getId()));

            List<InternDiligenceReportResponse> reports = internPage.getContent().stream()
                    .map(i -> reportAggregator.calculateInternDiligence(
                            i, startDate, endDate,
                            attendanceMap.getOrDefault(i.getId(), List.of()),
                            leaveMap.getOrDefault(i.getId(), List.of()),
                            resolveDepartmentName(i),
                            resolveProgramTitle(i)))
                    .toList();

            Page<InternDiligenceReportResponse> reportPage = new PageImpl<>(reports, pageable, internPage.getTotalElements());
            return PageResponse.from(reportPage);
        }

        // Khi có lọc theo diligenceStatus: thực hiện tính toán trên tập ứng viên và phân trang bộ nhớ chính xác
        List<InternProfile> allInterns = internProfileRepository.findAll(spec);
        if (allInterns.isEmpty()) {
            return PageResponse.from(new PageImpl<>(List.of(), pageable, 0));
        }

        List<Long> internIds = allInterns.stream().map(InternProfile::getId).toList();
        List<Attendance> attendances = attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(internIds, startDate, endDate);
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(internIds, startDate, endDate);

        Map<Long, List<Attendance>> attendanceMap = attendances.stream().collect(Collectors.groupingBy(Attendance::getInternId));
        Map<Long, List<LeaveRequest>> leaveMap = leaves.stream().collect(Collectors.groupingBy(l -> l.getIntern().getId()));

        List<InternDiligenceReportResponse> filteredReports = allInterns.stream()
                .map(i -> reportAggregator.calculateInternDiligence(
                        i, startDate, endDate,
                        attendanceMap.getOrDefault(i.getId(), List.of()),
                        leaveMap.getOrDefault(i.getId(), List.of()),
                        resolveDepartmentName(i),
                        resolveProgramTitle(i)))
                .filter(r -> r.getDiligenceStatus() == filter.getDiligenceStatus())
                .toList();

        int pageSize = pageable.isPaged() ? pageable.getPageSize() : (filteredReports.isEmpty() ? 1 : filteredReports.size());
        int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int fromIndex = pageNumber * pageSize;

        List<InternDiligenceReportResponse> pageContent;
        if (fromIndex >= filteredReports.size()) {
            pageContent = List.of();
        } else {
            int toIndex = Math.min(fromIndex + pageSize, filteredReports.size());
            pageContent = filteredReports.subList(fromIndex, toIndex);
        }

        Page<InternDiligenceReportResponse> reportPage = new PageImpl<>(pageContent, pageable, filteredReports.size());
        return PageResponse.from(reportPage);
    }

    @Override
    public InternAttendanceDetailReportResponse getInternAttendanceDetailReport(Long internId, Integer month, Integer year, CustomUserDetails userDetails) {
        InternProfile intern = internProfileRepository.findById(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thực tập sinh với ID: " + internId));

        validateMentorAccess(intern, userDetails);

        LocalDate now = LocalDate.now();
        int qMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();
        int qYear = (year != null && year >= 2000) ? year : now.getYear();

        LocalDate startDate = LocalDate.of(qYear, qMonth, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Attendance> attendances = attendanceRepository.findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(internId, startDate, endDate);
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesByInternIdAndDateRange(internId, startDate, endDate);

        Map<LocalDate, Attendance> attendanceByDate = attendances.stream().collect(Collectors.toMap(Attendance::getWorkDate, a -> a, (a1, a2) -> a1));

        InternDiligenceReportResponse summary = reportAggregator.calculateInternDiligence(
                intern, startDate, endDate, attendances, leaves, resolveDepartmentName(intern), resolveProgramTitle(intern));
        List<DailyAttendanceDetailItem> dailyItems = reportAggregator.buildDailyDetails(intern, startDate, endDate, attendanceByDate, leaves);

        return InternAttendanceDetailReportResponse.builder()
                .internId(intern.getId())
                .internCode(intern.getInternCode())
                .fullName(intern.getFullName())
                .email(intern.getEmail())
                .departmentName(resolveDepartmentName(intern))
                .programTitle(resolveProgramTitle(intern))
                .mentorName(intern.getMentorName())
                .month(qMonth)
                .year(qYear)
                .summary(InternAttendanceDetailReportResponse.InternDiligenceSummary.builder()
                        .expectedWorkDays(summary.getExpectedWorkDays())
                        .actualWorkDays(summary.getActualWorkDays())
                        .onTimeCount(summary.getOnTimeCount())
                        .lateCount(summary.getLateCount())
                        .earlyLeaveCount(summary.getEarlyLeaveCount())
                        .totalWorkingHours(summary.getTotalWorkingHours())
                        .approvedLeaveDays(summary.getApprovedLeaveDays())
                        .unexcusedAbsenceDays(summary.getUnexcusedAbsenceDays())
                        .diligenceRate(summary.getDiligenceRate())
                        .diligenceStatus(summary.getDiligenceStatus())
                        .build())
                .dailyDetails(dailyItems)
                .build();
    }

    @Override
    public byte[] exportAttendanceReportCsv(AttendanceReportFilterRequest filter, CustomUserDetails userDetails) {
        LocalDate[] range = resolveDateRange(filter);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];

        Specification<InternProfile> spec = buildInternSpecification(filter, userDetails);
        List<InternProfile> interns = internProfileRepository.findAll(spec);

        if (interns.isEmpty()) {
            return csvExporter.exportToCsv(List.of());
        }

        List<Long> internIds = interns.stream().map(InternProfile::getId).toList();
        List<Attendance> attendances = attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(internIds, startDate, endDate);
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(internIds, startDate, endDate);

        Map<Long, List<Attendance>> attendanceMap = attendances.stream().collect(Collectors.groupingBy(Attendance::getInternId));
        Map<Long, List<LeaveRequest>> leaveMap = leaves.stream().collect(Collectors.groupingBy(l -> l.getIntern().getId()));

        List<InternDiligenceReportResponse> reports = interns.stream()
                .map(i -> reportAggregator.calculateInternDiligence(
                        i, startDate, endDate,
                        attendanceMap.getOrDefault(i.getId(), List.of()),
                        leaveMap.getOrDefault(i.getId(), List.of()),
                        resolveDepartmentName(i),
                        resolveProgramTitle(i)))
                .filter(r -> filter.getDiligenceStatus() == null || r.getDiligenceStatus() == filter.getDiligenceStatus())
                .toList();

        return csvExporter.exportToCsv(reports);
    }

    private LocalDate[] resolveDateRange(AttendanceReportFilterRequest filter) {
        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            if (filter.getEndDate().isBefore(filter.getStartDate())) {
                throw new BadRequestException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
            }
            return new LocalDate[]{filter.getStartDate(), filter.getEndDate()};
        }
        LocalDate now = LocalDate.now();
        int month = (filter.getMonth() != null && filter.getMonth() >= 1 && filter.getMonth() <= 12) ? filter.getMonth() : now.getMonthValue();
        int year = (filter.getYear() != null && filter.getYear() >= 2000) ? filter.getYear() : now.getYear();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new LocalDate[]{start, end};
    }

    private Specification<InternProfile> buildInternSpecification(AttendanceReportFilterRequest filter, CustomUserDetails userDetails) {
        Specification<InternProfile> spec = (root, query, cb) -> cb.conjunction();

        boolean isMentor = userDetails != null && userDetails.getAuthorities().stream().anyMatch(a -> "ROLE_MENTOR".equals(a.getAuthority()));
        boolean isHrOrAdmin = userDetails != null && userDetails.getAuthorities().stream().anyMatch(a -> "ROLE_HR".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

        if (isMentor && !isHrOrAdmin && userDetails != null) {
            Set<Long> managedInternIds = mentorAccessHelper.getManagedInternIds(userDetails);
            if (managedInternIds.isEmpty()) {
                spec = spec.and((root, query, cb) -> cb.disjunction());
            } else {
                spec = spec.and((root, query, cb) -> root.get("id").in(managedInternIds));
            }
        } else if (filter.getMentorId() != null) {
            Set<Long> managedInternIds = mentorAccessHelper.getManagedInternIds(filter.getMentorId());
            if (managedInternIds.isEmpty()) {
                spec = spec.and((root, query, cb) -> cb.disjunction());
            } else {
                spec = spec.and((root, query, cb) -> root.get("id").in(managedInternIds));
            }
        }

        if (filter.getDepartmentId() != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.join("program", JoinType.LEFT).join("department", JoinType.LEFT).get("id"), filter.getDepartmentId()),
                    cb.equal(root.get("desiredDepartmentId"), filter.getDepartmentId())
            ));
        }

        if (filter.getProgramId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("program", JoinType.LEFT).get("id"), filter.getProgramId()));
        }

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            String kw = "%" + filter.getKeyword().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), kw),
                    cb.like(cb.lower(root.get("internCode")), kw),
                    cb.like(cb.lower(root.get("email")), kw)
            ));
        }

        spec = spec.and((root, query, cb) -> root.get("status").in(List.of(InternStatus.APPROVED, InternStatus.INTERNING, InternStatus.ON_HOLD, InternStatus.COMPLETED)));

        // Tránh N+1 query: chỉ fetch join khi truy vấn lấy dữ liệu (không áp dụng cho truy vấn đếm count)
        spec = spec.and((root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("program", JoinType.LEFT).fetch("department", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        });

        return spec;
    }

    private void validateMentorAccess(InternProfile intern, CustomUserDetails userDetails) {
        if (userDetails == null) return;
        boolean isMentor = userDetails.getAuthorities().stream().anyMatch(a -> "ROLE_MENTOR".equals(a.getAuthority()));
        boolean isHrOrAdmin = userDetails.getAuthorities().stream().anyMatch(a -> "ROLE_HR".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

        if (isMentor && !isHrOrAdmin && !mentorAccessHelper.isInternManagedByMentor(intern, userDetails)) {
            throw new AccessDeniedException("Bạn không có quyền xem báo cáo của thực tập sinh này");
        }
    }

    private String resolveDepartmentName(InternProfile intern) {
        if (intern.getProgram() != null && intern.getProgram().getDepartment() != null) {
            return intern.getProgram().getDepartment().getName();
        }
        if (intern.getDesiredDepartmentName() != null && !intern.getDesiredDepartmentName().isBlank()) {
            return intern.getDesiredDepartmentName();
        }
        return "Chưa phân ban";
    }

    private String resolveProgramTitle(InternProfile intern) {
        return intern.getProgram() != null ? intern.getProgram().getName() : "Chưa đăng ký chương trình";
    }
}

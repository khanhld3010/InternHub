package org.example.internservice.attendance.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckInRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.attendance.repository.OfficeLocationRepository;
import org.example.internservice.attendance.service.AttendanceService;
import org.example.internservice.attendance.service.CheckInQrTokenManager;
import org.example.internservice.attendance.util.FrontendUrlResolver;
import org.example.internservice.attendance.util.HaversineDistanceCalculator;
import org.example.internservice.attendance.util.QrCodeGenerator;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AttendanceServiceImpl implements AttendanceService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime LATE_THRESHOLD = LocalTime.of(8, 15, 0);
    public static final LocalTime STANDARD_END_TIME = LocalTime.of(17, 30, 0);

    private final AttendanceRepository attendanceRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final InternProfileRepository internProfileRepository;
    private final HaversineDistanceCalculator haversineCalculator;
    private final CheckInQrTokenManager qrTokenManager;
    private final QrCodeGenerator qrCodeGenerator;
    private final FrontendUrlResolver frontendUrlResolver;

    @Override
    public TodayAttendanceResponse getTodayAttendance(Long userId) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        OfficeLocation office = getActiveOfficeLocation();

        return attendanceRepository.findByInternIdAndWorkDate(intern.getId(), today)
                .map(att -> TodayAttendanceResponse.builder()
                        .workDate(att.getWorkDate())
                        .hasCheckedIn(true)
                        .hasCheckedOut(att.getCheckOutTime() != null)
                        .checkInTime(att.getCheckInTime())
                        .checkOutTime(att.getCheckOutTime())
                        .totalWorkingHours(att.getTotalWorkingHours())
                        .status(att.getStatus())
                        .officeName(office.getName())
                        .allowedRadiusMeters(office.getAllowedRadiusMeters())
                        .build())
                .orElseGet(() -> TodayAttendanceResponse.builder()
                        .workDate(today)
                        .hasCheckedIn(false)
                        .hasCheckedOut(false)
                        .checkInTime(null)
                        .checkOutTime(null)
                        .totalWorkingHours(null)
                        .status(null)
                        .officeName(office.getName())
                        .allowedRadiusMeters(office.getAllowedRadiusMeters())
                        .build());
    }

    @Override
    public CheckInQrResponse initiateCheckIn(Long userId, CheckInInitiateRequest request) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        if (attendanceRepository.existsByInternIdAndWorkDate(intern.getId(), today)) {
            throw new DuplicateResourceException("Bạn đã thực hiện check-in cho ngày hôm nay rồi");
        }

        OfficeLocation office = getActiveOfficeLocation();
        double distance = haversineCalculator.calculateDistance(
                request.getLatitude(), request.getLongitude(),
                office.getLatitude(), office.getLongitude()
        );

        if (distance > office.getAllowedRadiusMeters()) {
            throw new BadRequestException(String.format(
                    "Vị trí của bạn cách văn phòng %.1fm, vượt quá bán kính cho phép (%.1fm)",
                    distance, office.getAllowedRadiusMeters()
            ));
        }

        CheckInQrTokenManager.CheckInSession session = qrTokenManager.createSession(
                intern.getId(),
                userId,
                today,
                request.getLatitude(),
                request.getLongitude(),
                distance
        );

        String baseUrl = frontendUrlResolver.resolveBaseUrl(request.getClientBaseUrl());
        String confirmationUrl = frontendUrlResolver.buildConfirmationUrl(baseUrl, session.getToken());
        String qrCodeDataUrl = qrCodeGenerator.generateQrCodeDataUrl(confirmationUrl);

        return CheckInQrResponse.builder()
                .qrToken(session.getToken())
                .confirmationUrl(confirmationUrl)
                .qrCodeDataUrl(qrCodeDataUrl)
                .expiresInSeconds(CheckInQrTokenManager.TTL_SECONDS)
                .expiresAt(session.getExpiresAt())
                .distance(Math.round(distance * 10.0) / 10.0)
                .officeName(office.getName())
                .message("Khoảng cách hợp lệ. Mã QR xác thực phiên điểm danh đã được tạo thành công.")
                .build();
    }

    @Override
    @Transactional
    public AttendanceResponse confirmCheckIn(Long userId, CheckInConfirmRequest request) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        if (attendanceRepository.existsByInternIdAndWorkDate(intern.getId(), today)) {
            throw new DuplicateResourceException("Bạn đã thực hiện check-in cho ngày hôm nay rồi");
        }

        CheckInQrTokenManager.CheckInSession session = qrTokenManager.validateAndConsume(request.getQrToken(), intern.getId());

        AttendanceStatus status = now.toLocalTime().isAfter(LATE_THRESHOLD)
                ? AttendanceStatus.LATE
                : AttendanceStatus.ON_TIME;

        Attendance attendance = Attendance.builder()
                .internId(intern.getId())
                .workDate(today)
                .checkInTime(now)
                .checkInLatitude(session.getLatitude())
                .checkInLongitude(session.getLongitude())
                .checkInDistance(session.getDistance())
                .status(status)
                .notes(request.getNotes())
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Intern ID {} xác nhận check-in thành công qua mã QR lúc {} (Khoảng cách: {}m, Trạng thái: {})",
                intern.getId(), now, session.getDistance(), status);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AttendanceResponse checkIn(Long userId, CheckInRequest request) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        if (attendanceRepository.existsByInternIdAndWorkDate(intern.getId(), today)) {
            throw new DuplicateResourceException("Bạn đã thực hiện check-in cho ngày hôm nay rồi");
        }

        OfficeLocation office = getActiveOfficeLocation();
        double distance = haversineCalculator.calculateDistance(
                request.getLatitude(), request.getLongitude(),
                office.getLatitude(), office.getLongitude()
        );

        if (distance > office.getAllowedRadiusMeters()) {
            throw new BadRequestException(String.format(
                    "Vị trí của bạn cách văn phòng %.1fm, vượt quá bán kính cho phép (%.1fm)",
                    distance, office.getAllowedRadiusMeters()
            ));
        }

        AttendanceStatus status = now.toLocalTime().isAfter(LATE_THRESHOLD)
                ? AttendanceStatus.LATE
                : AttendanceStatus.ON_TIME;

        Attendance attendance = Attendance.builder()
                .internId(intern.getId())
                .workDate(today)
                .checkInTime(now)
                .checkInLatitude(request.getLatitude())
                .checkInLongitude(request.getLongitude())
                .checkInDistance(distance)
                .status(status)
                .notes(request.getNotes())
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Intern ID {} check-in thành công lúc {} (Khoảng cách: {}m, Trạng thái: {})",
                intern.getId(), now, distance, status);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AttendanceResponse checkOut(Long userId, CheckOutRequest request) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        Attendance attendance = attendanceRepository.findByInternIdAndWorkDate(intern.getId(), today)
                .orElseThrow(() -> new BadRequestException("Bạn chưa thực hiện check-in cho ngày hôm nay, không thể check-out"));

        if (attendance.getCheckOutTime() != null) {
            throw new BadRequestException("Bạn đã hoàn thành check-out cho ngày hôm nay rồi");
        }

        OfficeLocation office = getActiveOfficeLocation();
        double distance = haversineCalculator.calculateDistance(
                request.getLatitude(), request.getLongitude(),
                office.getLatitude(), office.getLongitude()
        );

        if (distance > office.getAllowedRadiusMeters()) {
            throw new BadRequestException(String.format(
                    "Vị trí của bạn cách văn phòng %.1fm, vượt quá bán kính cho phép (%.1fm)",
                    distance, office.getAllowedRadiusMeters()
            ));
        }

        long workingMinutes = Math.max(0, Duration.between(attendance.getCheckInTime(), now).toMinutes());
        double totalWorkingHours = Math.round((workingMinutes / 60.0) * 100.0) / 100.0;

        if (now.toLocalTime().isBefore(STANDARD_END_TIME)) {
            if (attendance.getStatus() == AttendanceStatus.LATE) {
                attendance.setStatus(AttendanceStatus.LATE_AND_EARLY_LEAVE);
            } else if (attendance.getStatus() == AttendanceStatus.ON_TIME) {
                attendance.setStatus(AttendanceStatus.EARLY_LEAVE);
            }
        }

        attendance.setCheckOutTime(now);
        attendance.setCheckOutLatitude(request.getLatitude());
        attendance.setCheckOutLongitude(request.getLongitude());
        attendance.setCheckOutDistance(distance);
        attendance.setTotalWorkingHours(totalWorkingHours);

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String combinedNotes = (attendance.getNotes() != null && !attendance.getNotes().isBlank())
                    ? attendance.getNotes() + " | Check-out: " + request.getNotes()
                    : request.getNotes();
            attendance.setNotes(combinedNotes);
        }

        Attendance updated = attendanceRepository.save(attendance);
        log.info("Intern ID {} check-out thành công lúc {} (Khoảng cách: {}m, Tổng giờ: {}, Trạng thái: {})",
                intern.getId(), now, distance, totalWorkingHours, updated.getStatus());

        return mapToResponse(updated);
    }

    @Override
    public MonthlyAttendanceSummaryResponse getMyHistory(Long userId, Integer month, Integer year) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate now = LocalDate.now(VIETNAM_ZONE);

        int queryMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();
        int queryYear = (year != null && year >= 2000) ? year : now.getYear();

        LocalDate startDate = LocalDate.of(queryYear, queryMonth, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Attendance> attendances = attendanceRepository
                .findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(intern.getId(), startDate, endDate);

        long onTimeCount = attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ON_TIME)
                .count();

        long lateCount = attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.LATE || a.getStatus() == AttendanceStatus.LATE_AND_EARLY_LEAVE)
                .count();

        long earlyLeaveCount = attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.EARLY_LEAVE || a.getStatus() == AttendanceStatus.LATE_AND_EARLY_LEAVE)
                .count();

        double totalHours = attendances.stream()
                .mapToDouble(a -> a.getTotalWorkingHours() != null ? a.getTotalWorkingHours() : 0.0)
                .sum();
        totalHours = Math.round(totalHours * 100.0) / 100.0;

        List<AttendanceResponse> responseList = attendances.stream()
                .map(this::mapToResponse)
                .toList();

        return MonthlyAttendanceSummaryResponse.builder()
                .month(queryMonth)
                .year(queryYear)
                .totalWorkingDays(attendances.size())
                .onTimeDays(onTimeCount)
                .lateDays(lateCount)
                .earlyLeaveDays(earlyLeaveCount)
                .totalWorkingHours(totalHours)
                .attendances(responseList)
                .build();
    }

    private InternProfile getValidInternProfile(Long userId) {
        InternProfile intern = internProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh liên kết với tài khoản này"));

        if (intern.getStatus() == InternStatus.TERMINATED) {
            throw new BadRequestException("Hồ sơ thực tập sinh đã bị chấm dứt, không thể thực hiện thao tác chấm công");
        }

        return intern;
    }

    private OfficeLocation getActiveOfficeLocation() {
        return officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new BadRequestException("Hệ thống chưa thiết lập vị trí văn phòng đang hoạt động để chấm công"));
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .internId(attendance.getInternId())
                .workDate(attendance.getWorkDate())
                .checkInTime(attendance.getCheckInTime())
                .checkOutTime(attendance.getCheckOutTime())
                .checkInDistance(attendance.getCheckInDistance())
                .checkOutDistance(attendance.getCheckOutDistance())
                .totalWorkingHours(attendance.getTotalWorkingHours())
                .status(attendance.getStatus())
                .notes(attendance.getNotes())
                .build();
    }
}

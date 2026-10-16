package org.example.internservice.attendance.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.request.QuickCheckOutRequest;
import org.example.internservice.attendance.dto.request.ReviewAttendanceRequest;
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
import org.example.internservice.attendance.service.TrustLevelEvaluator;
import org.example.internservice.attendance.service.TrustLevelEvaluator.EvaluationResult;
import org.example.internservice.attendance.util.FrontendUrlResolver;
import org.example.internservice.attendance.util.HaversineDistanceCalculator;
import org.example.internservice.attendance.util.QrCodeGenerator;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.repository.LeaveRequestRepository;
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
    public static final LocalTime AFTERNOON_LATE_THRESHOLD = LocalTime.of(13, 30, 0);
    public static final LocalTime STANDARD_END_TIME = LocalTime.of(17, 30, 0);
    public static final LocalTime MORNING_END_TIME = LocalTime.of(12, 0, 0);

    private final AttendanceRepository attendanceRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final InternProfileRepository internProfileRepository;
    private final HaversineDistanceCalculator haversineCalculator;
    private final CheckInQrTokenManager qrTokenManager;
    private final QrCodeGenerator qrCodeGenerator;
    private final FrontendUrlResolver frontendUrlResolver;
    private final org.example.internservice.attendance.service.TrustLevelEvaluator trustLevelEvaluator;
    private final org.example.internservice.attendance.repository.AttendanceAuditLogRepository auditLogRepository;
    private final org.example.internservice.attendance.repository.InternDeviceRepository internDeviceRepository;
    private final org.example.internservice.attendance.service.SseEmitterRegistry sseEmitterRegistry;
    private final LeaveRequestRepository leaveRequestRepository;

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
        Double distance = null;
        if (request.getLatitude() != null && request.getLongitude() != null) {
            distance = haversineCalculator.calculateDistance(
                    request.getLatitude(), request.getLongitude(),
                    office.getLatitude(), office.getLongitude()
            );
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
                .distance(distance != null ? Math.round(distance * 10.0) / 10.0 : null)
                .officeName(office.getName())
                .message("Mã QR xác thực phiên điểm danh đã được tạo thành công.")
                .build();
    }

    @Override
    @Transactional
    public AttendanceResponse confirmCheckIn(Long userId, CheckInConfirmRequest request, String clientIp) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        if (attendanceRepository.existsByInternIdAndWorkDate(intern.getId(), today)) {
            throw new DuplicateResourceException("Bạn đã thực hiện check-in cho ngày hôm nay rồi");
        }

        CheckInQrTokenManager.CheckInSession session = qrTokenManager.validateAndConsume(request.getQrToken(), intern.getId());
        OfficeLocation office = getActiveOfficeLocation();

        var evalResult = trustLevelEvaluator.evaluate(
                intern.getId(),
                request.getDeviceFingerprint(),
                clientIp,
                request.getLatitude() != null ? request.getLatitude() : session.getLatitude(),
                request.getLongitude() != null ? request.getLongitude() : session.getLongitude(),
                request.getAccuracyMeters(),
                office
        );

        boolean hasMorningLeave = leaveRequestRepository.findApprovedLeaveByInternAndDate(intern.getId(), today)
                .map(l -> l.getDurationType() == LeaveDurationType.MORNING)
                .orElse(false);

        LocalTime lateLimit = hasMorningLeave ? AFTERNOON_LATE_THRESHOLD : LATE_THRESHOLD;
        AttendanceStatus status = now.toLocalTime().isAfter(lateLimit)
                ? AttendanceStatus.LATE
                : AttendanceStatus.ON_TIME;

        Attendance attendance = Attendance.builder()
                .internId(intern.getId())
                .workDate(today)
                .checkInTime(now)
                .checkInLatitude(request.getLatitude() != null ? request.getLatitude() : session.getLatitude())
                .checkInLongitude(request.getLongitude() != null ? request.getLongitude() : session.getLongitude())
                .checkInDistance(evalResult.getCalculatedDistance() != null ? evalResult.getCalculatedDistance() : session.getDistance())
                .status(status)
                .reviewStatus(evalResult.getReviewStatus())
                .notes(request.getNotes())
                .build();

        Attendance saved = attendanceRepository.save(attendance);

        saveAuditLog(
                saved.getId(),
                intern.getId(),
                "CHECK_IN",
                now,
                request.getDeviceFingerprint(),
                request.getDeviceModel(),
                clientIp,
                request.getLatitude(),
                request.getLongitude(),
                request.getAccuracyMeters(),
                evalResult
        );

        recordDeviceUsage(intern.getId(), request.getDeviceFingerprint(), request.getDeviceModel(), now);

        String auditFlags = evalResult.getAuditFlags() != null ? String.join(",", evalResult.getAuditFlags()) : "";
        AttendanceResponse response = mapToResponse(saved, evalResult.getTrustLevel() != null ? evalResult.getTrustLevel().name() : null, auditFlags);
        sseEmitterRegistry.emitCheckInSuccess(request.getQrToken(), response);

        log.info("Intern ID {} xác nhận check-in thành công qua mã QR lúc {} (TrustLevel: {}, Review: {})",
                intern.getId(), now, evalResult.getTrustLevel(), evalResult.getReviewStatus());

        return response;
    }

    @Override
    @Transactional
    public AttendanceResponse quickCheckOut(Long userId, org.example.internservice.attendance.dto.request.QuickCheckOutRequest request, String clientIp) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        Attendance attendance = attendanceRepository.findByInternIdAndWorkDate(intern.getId(), today)
                .orElseThrow(() -> new BadRequestException("Bạn chưa thực hiện check-in cho ngày hôm nay, không thể check-out"));

        if (attendance.getCheckOutTime() != null) {
            throw new BadRequestException("Bạn đã hoàn thành check-out cho ngày hôm nay rồi");
        }

        OfficeLocation office = getActiveOfficeLocation();
        var evalResult = trustLevelEvaluator.evaluate(
                intern.getId(),
                request.getDeviceFingerprint(),
                clientIp,
                request.getLatitude(),
                request.getLongitude(),
                request.getAccuracyMeters(),
                office
        );

        if (evalResult.getTrustLevel() == org.example.internservice.attendance.entity.enums.TrustLevel.LOW) {
            throw new BadRequestException("Vị trí của bạn đang ở ngoài khu vực văn phòng. Vui lòng quét mã QR trên laptop để thực hiện check-out.");
        }

        long workingMinutes = Math.max(0, Duration.between(attendance.getCheckInTime(), now).toMinutes());
        double totalWorkingHours = Math.round((workingMinutes / 60.0) * 100.0) / 100.0;

        boolean hasAfternoonLeave = leaveRequestRepository.findApprovedLeaveByInternAndDate(intern.getId(), today)
                .map(l -> l.getDurationType() == LeaveDurationType.AFTERNOON)
                .orElse(false);

        LocalTime endLimit = hasAfternoonLeave ? MORNING_END_TIME : STANDARD_END_TIME;
        if (now.toLocalTime().isBefore(endLimit)) {
            if (attendance.getStatus() == AttendanceStatus.LATE) {
                attendance.setStatus(AttendanceStatus.LATE_AND_EARLY_LEAVE);
            } else if (attendance.getStatus() == AttendanceStatus.ON_TIME) {
                attendance.setStatus(AttendanceStatus.EARLY_LEAVE);
            }
        }

        attendance.setCheckOutTime(now);
        attendance.setCheckOutLatitude(request.getLatitude());
        attendance.setCheckOutLongitude(request.getLongitude());
        attendance.setCheckOutDistance(evalResult.getCalculatedDistance());
        attendance.setTotalWorkingHours(totalWorkingHours);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            attendance.setNotes(attendance.getNotes() == null ? request.getNotes() : attendance.getNotes() + " | " + request.getNotes());
        }

        Attendance saved = attendanceRepository.save(attendance);

        saveAuditLog(
                saved.getId(),
                intern.getId(),
                "QUICK_CHECK_OUT",
                now,
                request.getDeviceFingerprint(),
                request.getDeviceModel(),
                clientIp,
                request.getLatitude(),
                request.getLongitude(),
                request.getAccuracyMeters(),
                evalResult
        );

        log.info("Intern ID {} tan ca nhanh 1-chạm thành công lúc {} (Tổng giờ: {}h)", intern.getId(), now, totalWorkingHours);
        return mapToResponse(saved, evalResult.getTrustLevel().name(), String.join(",", evalResult.getAuditFlags()));
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

        boolean hasAfternoonLeave = leaveRequestRepository.findApprovedLeaveByInternAndDate(intern.getId(), today)
                .map(l -> l.getDurationType() == LeaveDurationType.AFTERNOON)
                .orElse(false);

        LocalTime endLimit = hasAfternoonLeave ? MORNING_END_TIME : STANDARD_END_TIME;
        if (now.toLocalTime().isBefore(endLimit)) {
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

    @Override
    @Transactional
    public AttendanceResponse reviewAttendance(Long attendanceId, ReviewAttendanceRequest request, String reviewerUsername) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi chấm công có ID: " + attendanceId));

        attendance.setReviewStatus(request.getReviewStatus());
        attendance.setReviewedBy(reviewerUsername != null ? reviewerUsername : "HR_ADMIN");
        attendance.setReviewedAt(LocalDateTime.now(VIETNAM_ZONE));
        if (request.getReviewNote() != null && !request.getReviewNote().isBlank()) {
            attendance.setReviewNote(request.getReviewNote());
        }

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Bản ghi chấm công ID {} đã được cập nhật reviewStatus thành {} bởi {}",
                attendanceId, request.getReviewStatus(), reviewerUsername);

        return mapToResponse(saved);
    }

    private void saveAuditLog(
            Long attendanceId,
            Long internId,
            String actionType,
            LocalDateTime actionTime,
            String deviceFingerprint,
            String deviceModel,
            String clientIp,
            Double latitude,
            Double longitude,
            Double accuracyMeters,
            org.example.internservice.attendance.service.TrustLevelEvaluator.EvaluationResult evalResult
    ) {
        org.example.internservice.attendance.entity.AttendanceAuditLog auditLog = org.example.internservice.attendance.entity.AttendanceAuditLog.builder()
                .attendanceId(attendanceId)
                .internId(internId)
                .actionType(actionType)
                .actionTime(actionTime)
                .deviceFingerprint(deviceFingerprint != null ? deviceFingerprint : "UNKNOWN")
                .deviceModel(deviceModel)
                .clientIp(clientIp != null ? clientIp : "UNKNOWN")
                .isCompanyIp(evalResult.isCompanyIp())
                .latitude(latitude)
                .longitude(longitude)
                .accuracyMeters(accuracyMeters)
                .distanceMeters(evalResult.getCalculatedDistance())
                .trustLevel(evalResult.getTrustLevel())
                .auditFlags(evalResult.getAuditFlags() != null ? String.join(",", evalResult.getAuditFlags()) : "")
                .build();
        auditLogRepository.save(auditLog);
    }

    private void recordDeviceUsage(Long internId, String deviceFingerprint, String deviceModel, LocalDateTime actionTime) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return;
        }

        var existingDeviceOpt = internDeviceRepository.findByInternIdAndDeviceFingerprint(internId, deviceFingerprint);
        if (existingDeviceOpt.isPresent()) {
            var device = existingDeviceOpt.get();
            device.setLastUsedAt(actionTime);
            device.setValidCheckinCount(device.getValidCheckinCount() + 1);
            if (device.getValidCheckinCount() >= 3 && device.getTrustStatus() == org.example.internservice.attendance.entity.enums.DeviceTrustStatus.PENDING_PAIRING) {
                device.setTrustStatus(org.example.internservice.attendance.entity.enums.DeviceTrustStatus.PRIMARY_TRUSTED);
            }
            internDeviceRepository.save(device);
        } else {
            var newDevice = org.example.internservice.attendance.entity.InternDevice.builder()
                    .internId(internId)
                    .deviceFingerprint(deviceFingerprint)
                    .deviceModel(deviceModel)
                    .trustStatus(org.example.internservice.attendance.entity.enums.DeviceTrustStatus.PENDING_PAIRING)
                    .validCheckinCount(1)
                    .lastUsedAt(actionTime)
                    .build();
            internDeviceRepository.save(newDevice);
        }
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
        return mapToResponse(attendance, null, null);
    }

    private AttendanceResponse mapToResponse(Attendance attendance, String trustLevel, String auditFlags) {
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
                .reviewStatus(attendance.getReviewStatus() != null ? attendance.getReviewStatus().name() : "AUTO_APPROVED")
                .trustLevel(trustLevel)
                .auditFlags(auditFlags)
                .notes(attendance.getNotes())
                .build();
    }
}


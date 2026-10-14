package org.example.internservice.meeting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.meeting.client.MeetingEmailClient;
import org.example.internservice.meeting.client.MeetingEmailClient.SendMeetingEmailPayload;
import org.example.internservice.meeting.dto.request.CreateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.request.UpdateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.response.MeetingScheduleResponse;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingStatus;
import org.example.internservice.meeting.repository.MeetingScheduleRepository;
import org.example.internservice.meeting.service.MeetingScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingScheduleServiceImpl implements MeetingScheduleService {

    private final MeetingScheduleRepository meetingScheduleRepository;
    private final InternProfileRepository internProfileRepository;
    private final MeetingEmailClient meetingEmailClient;
    private final NotificationEventDispatcher notificationEventDispatcher;
    private final org.example.internservice.meeting.provider.MeetingProviderResolver meetingProviderResolver;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");

    @Override
    @Transactional
    public MeetingScheduleResponse createMeeting(Long creatorId, String creatorName, String creatorRole, CreateMeetingScheduleRequest request) {
        log.info("Tao lich hop boi creatorId={}, internProfileId={}", creatorId, request.getInternProfileId());

        // 1. Validation nghiệp vụ thời gian
        validateMeetingTimes(request.getStartTime(), request.getEndTime());

        // 2. Validation an toàn đường dẫn URL nếu là hình thức ONLINE và người dùng nhập thủ công
        validateLocationOrUrl(request.getMeetingType(), request.getLocationOrUrl());

        InternProfile internProfile = internProfileRepository.findById(request.getInternProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ho so thuc tap sinh voi ID: " + request.getInternProfileId()));

        Long internUserId = internProfile.getUserId() != null ? internProfile.getUserId() : internProfile.getId();

        MeetingSchedule meeting = MeetingSchedule.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .meetingType(request.getMeetingType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .creatorId(creatorId)
                .creatorName(creatorName != null ? creatorName : "Nguoi huong dan / HR")
                .creatorRole(creatorRole != null ? creatorRole : "MENTOR")
                .internProfileId(internProfile.getId())
                .internUserId(internUserId)
                .internName(internProfile.getFullName())
                .internEmail(internProfile.getEmail())
                .status(MeetingStatus.SCHEDULED)
                .sequenceNumber(0)
                .reminderSent(false)
                .build();

        // Điều phối tạo phòng họp theo nguyên tắc "Link tay thắng tất cả -> Google -> Jitsi"
        org.example.internservice.meeting.provider.MeetingRoomInfo roomInfo =
                meetingProviderResolver.resolveAndCreate(meeting, request.getLocationOrUrl());

        meeting.setProvider(roomInfo.getProvider());
        meeting.setJoinUrl(roomInfo.getJoinUrl());
        meeting.setExternalEventId(roomInfo.getExternalEventId());
        meeting.setLocationOrUrl(roomInfo.getJoinUrl() != null ? roomInfo.getJoinUrl() : request.getLocationOrUrl());

        MeetingSchedule saved = meetingScheduleRepository.save(meeting);
        log.info("Luu thanh cong cuoc hop ID={}, provider={}, joinUrl={}", saved.getId(), saved.getProvider(), saved.getJoinUrl());

        // 1. Kich hoat gui Email tu dong (Bat dong bo)
        dispatchMeetingEmail(saved, "SCHEDULED");

        // 2. Ban thong bao Realtime WebSocket STOMP
        dispatchMeetingNotification(saved, "MEETING_SCHEDULED",
                "Lịch họp mới: " + saved.getTitle(),
                String.format("Bạn có lịch hẹn cuộc họp lúc %s do %s tạo.",
                        saved.getStartTime().format(TIME_FORMAT), saved.getCreatorName()));

        return mapToResponse(saved);
    }


    @Override
    @Transactional
    public MeetingScheduleResponse updateMeeting(Long meetingId, Long updaterId, String updaterRole, UpdateMeetingScheduleRequest request) {
        log.info("Cap nhat lich hop meetingId={}, updaterId={}", meetingId, updaterId);

        MeetingSchedule meeting = meetingScheduleRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay cuoc hop voi ID: " + meetingId));

        // Kiểm tra quyền sở hữu (Chống lỗi BOLA / IDOR): Chỉ người tạo hoặc HR / ADMIN mới được cập nhật
        checkMeetingOwnership(meeting, updaterId, updaterRole, "chỉnh sửa");

        // Validation nghiệp vụ thời gian
        validateMeetingTimes(request.getStartTime(), request.getEndTime());

        // Validation URL scheme
        validateLocationOrUrl(request.getMeetingType(), request.getLocationOrUrl());

        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        meeting.setMeetingType(request.getMeetingType());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());
        meeting.setStatus(MeetingStatus.UPDATED);
        meeting.setSequenceNumber(meeting.getSequenceNumber() != null ? meeting.getSequenceNumber() + 1 : 1);
        meeting.setReminderSent(false); // Reset reminder nếu thời gian thay đổi

        if (request.getLocationOrUrl() != null && !request.getLocationOrUrl().isBlank()) {
            org.example.internservice.meeting.provider.MeetingRoomInfo roomInfo =
                    meetingProviderResolver.resolveAndCreate(meeting, request.getLocationOrUrl());
            meeting.setProvider(roomInfo.getProvider());
            meeting.setJoinUrl(roomInfo.getJoinUrl());
            meeting.setExternalEventId(roomInfo.getExternalEventId());
            meeting.setLocationOrUrl(roomInfo.getJoinUrl() != null ? roomInfo.getJoinUrl() : request.getLocationOrUrl());
        }

        MeetingSchedule updated = meetingScheduleRepository.save(meeting);

        // 1. Kich hoat gui Email thong bao cap nhat
        dispatchMeetingEmail(updated, "UPDATED");

        // 2. Ban thong bao Realtime WebSocket STOMP
        dispatchMeetingNotification(updated, "MEETING_UPDATED",
                "Cập nhật lịch họp: " + updated.getTitle(),
                String.format("Lịch họp đã được dời/cập nhật lúc %s. Vui lòng kiểm tra lại thời gian.",
                        updated.getStartTime().format(TIME_FORMAT)));

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public MeetingScheduleResponse cancelMeeting(Long meetingId, Long cancellerId, String cancellerRole) {
        log.info("Huy lich hop meetingId={}, cancellerId={}", meetingId, cancellerId);

        MeetingSchedule meeting = meetingScheduleRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay cuoc hop voi ID: " + meetingId));

        // Kiểm tra quyền sở hữu (Chống lỗi BOLA / IDOR): Chỉ người tạo hoặc HR / ADMIN mới được hủy
        checkMeetingOwnership(meeting, cancellerId, cancellerRole, "hủy");

        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setSequenceNumber(meeting.getSequenceNumber() != null ? meeting.getSequenceNumber() + 1 : 1);
        MeetingSchedule cancelled = meetingScheduleRepository.save(meeting);

        // 1. Kich hoat gui Email thong bao huy hop
        dispatchMeetingEmail(cancelled, "CANCELLED");

        // 2. Ban thong bao Realtime WebSocket STOMP
        dispatchMeetingNotification(cancelled, "MEETING_CANCELLED",
                "Hủy lịch họp: " + cancelled.getTitle(),
                String.format("Cuộc họp dự kiến lúc %s đã bị hủy bởi người tổ chức.",
                        cancelled.getStartTime().format(TIME_FORMAT)));

        return mapToResponse(cancelled);
    }

    private void checkMeetingOwnership(MeetingSchedule meeting, Long userId, String role, String actionName) {
        boolean isOwner = meeting.getCreatorId() != null && meeting.getCreatorId().equals(userId);
        boolean isHrOrAdmin = "HR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        if (!isOwner && !isHrOrAdmin) {
            log.warn("IDOR violation: userId={} (role={}) co tinh {} cuoc hop ID={} do userId={} tao",
                    userId, role, actionName, meeting.getId(), meeting.getCreatorId());
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền " + actionName + " cuộc họp này.");
        }
    }

    private void validateMeetingTimes(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        if (startTime == null) {
            throw new org.example.internservice.exception.BadRequestException("Thời gian bắt đầu không được để trống");
        }
        if (startTime.isBefore(java.time.LocalDateTime.now().minusMinutes(2))) {
            throw new org.example.internservice.exception.BadRequestException("Thời gian bắt đầu không được nằm trong quá khứ");
        }
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new org.example.internservice.exception.BadRequestException("Thời gian kết thúc phải diễn ra sau thời gian bắt đầu");
        }
    }

    private void validateLocationOrUrl(MeetingSchedule.MeetingType meetingType, String locationOrUrl) {
        if (locationOrUrl == null || locationOrUrl.isBlank()) {
            return;
        }
        String trimmed = locationOrUrl.trim();
        if (meetingType == MeetingSchedule.MeetingType.ONLINE) {
            // Nếu người dùng nhập đường link cho phòng họp trực tuyến
            // Bắt buộc phải là schema http:// hoặc https:// hợp lệ, triệt tiêu javascript:, data:, file: ...
            if (trimmed.contains("://") && !trimmed.toLowerCase().startsWith("http://") && !trimmed.toLowerCase().startsWith("https://")) {
                throw new org.example.internservice.exception.BadRequestException("Đường link phòng họp trực tuyến phải bắt đầu bằng http:// hoặc https://");
            }
            if (trimmed.toLowerCase().startsWith("javascript:") || trimmed.toLowerCase().startsWith("data:")) {
                throw new org.example.internservice.exception.BadRequestException("Đường link phòng họp không hợp lệ hoặc chứa mã độc");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingScheduleResponse> getMyMeetings(Long userId, String role) {
        if ("INTERN".equalsIgnoreCase(role)) {
            return meetingScheduleRepository.findByInternUserIdOrderByStartTimeAsc(userId)
                    .stream().map(this::mapToResponse).collect(Collectors.toList());
        }
        return meetingScheduleRepository.findByCreatorIdOrderByStartTimeAsc(userId)
                    .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingScheduleResponse> getMeetingsByIntern(Long internUserId) {
        return meetingScheduleRepository.findByInternUserIdOrderByStartTimeAsc(internUserId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingScheduleResponse getMeetingById(Long meetingId) {
        return meetingScheduleRepository.findById(meetingId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay cuoc hop voi ID: " + meetingId));
    }

    private void dispatchMeetingEmail(MeetingSchedule meeting, String actionType) {
        SendMeetingEmailPayload payload = SendMeetingEmailPayload.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .meetingId(meeting.getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .meetingType(meeting.getMeetingType().name())
                .locationOrUrl(meeting.getLocationOrUrl())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .organizerName(meeting.getCreatorName())
                .organizerRole(meeting.getCreatorRole())
                .internName(meeting.getInternName())
                .internEmail(meeting.getInternEmail())
                .actionType(actionType)
                .provider(meeting.getProvider() != null ? meeting.getProvider().name() : null)
                .sequenceNumber(meeting.getSequenceNumber())
                .build();

        meetingEmailClient.sendMeetingEmailAsync(payload);
    }

    private void dispatchMeetingNotification(MeetingSchedule meeting, String type, String title, String content) {
        CreateNotificationInternalRequest notifRequest = CreateNotificationInternalRequest.builder()
                .recipientId(meeting.getInternUserId())
                .actorId(meeting.getCreatorId())
                .title(title)
                .content(content)
                .type(type)
                .referenceType("MEETING")
                .referenceId(String.valueOf(meeting.getId()))
                .actionUrl("/intern/dashboard")
                .build();

        notificationEventDispatcher.dispatch(notifRequest);
    }

    private MeetingScheduleResponse mapToResponse(MeetingSchedule meeting) {
        return MeetingScheduleResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .meetingType(meeting.getMeetingType())
                .locationOrUrl(meeting.getLocationOrUrl())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .creatorId(meeting.getCreatorId())
                .creatorName(meeting.getCreatorName())
                .creatorRole(meeting.getCreatorRole())
                .internProfileId(meeting.getInternProfileId())
                .internUserId(meeting.getInternUserId())
                .internName(meeting.getInternName())
                .internEmail(meeting.getInternEmail())
                .status(meeting.getStatus())
                .provider(meeting.getProvider() != null ? meeting.getProvider().name() : null)
                .joinUrl(meeting.getJoinUrl())
                .externalEventId(meeting.getExternalEventId())
                .sequenceNumber(meeting.getSequenceNumber())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }
}

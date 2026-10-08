package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.ApplyInternRequest;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternDecisionRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.event.InternDecisionProcessedEvent;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.specification.InternProfileSpecification;
import org.example.internservice.intern.service.InternProfileService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import org.example.internservice.exception.RateLimitException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InternProfileServiceImpl implements InternProfileService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "fullName", "internCode", "university", "major", "appliedPosition", "status"
    );
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final InternProfileRepository internProfileRepository;
    private final org.example.internservice.program.repository.InternshipProgramRepository programRepository;
    private final org.example.internservice.intern.repository.InternMentorAssignmentRepository internMentorAssignmentRepository;
    private final org.example.internservice.program.repository.DepartmentRepository departmentRepository;
    private final org.example.internservice.program.repository.ProgramMentorRepository programMentorRepository;
    private final org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;
    private final org.example.internservice.intern.repository.MentorProfileRepository mentorProfileRepository;
    private final org.example.internservice.intern.repository.InternWeeklyAssessmentRepository weeklyAssessmentRepository;
    private final org.example.internservice.intern.repository.InternEvaluationRepository evaluationRepository;
    private final org.example.internservice.intern.client.IntegrationEmailClient integrationEmailClient;
    private final org.example.internservice.intern.service.OnboardingTokenService onboardingTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final org.example.internservice.intern.client.NotificationEventDispatcher notificationDispatcher;

    @Override
    @Transactional
    public InternResponse createIntern(CreateInternRequest request) {
        log.info("Bat dau tao ho so thuc tap sinh voi email: {}", request.getEmail());

        if (internProfileRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' đã tồn tại trong hệ thống");
        }

        if (internProfileRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Số điện thoại '" + request.getPhone() + "' đã tồn tại trong hệ thống");
        }

        String internCode = generateInternCode();

        InternProfile internProfile = InternProfile.builder()
                .internCode(internCode)
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .address(request.getAddress())
                .university(request.getUniversity().trim())
                .major(request.getMajor().trim())
                .academicYear(request.getAcademicYear())
                .appliedPosition(request.getAppliedPosition().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(InternStatus.PENDING)
                .notes(request.getNotes())
                .build();

        InternProfile savedProfile = internProfileRepository.save(internProfile);
        log.info("Tao thanh cong ho so thuc tap sinh voi ID: {}, Code: {}", savedProfile.getId(), savedProfile.getInternCode());

        return mapToResponse(savedProfile);
    }

    @Override
    @Transactional
    public InternResponse applyOnline(ApplyInternRequest request) {
        log.info("Bắt đầu xử lý nộp hồ sơ ứng tuyển trực tuyến: email={}, userId={}, programId={}", 
                request.getEmail(), request.getUserId(), request.getProgramId());

        if (request.getProgramId() == null) {
            throw new BadRequestException("Vui lòng chọn chương trình thực tập ứng tuyển");
        }

        org.example.internservice.program.entity.InternshipProgram program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + request.getProgramId()));

        if (program.getStatus() != org.example.internservice.program.entity.enums.ProgramStatus.PLANNING 
                && program.getStatus() != org.example.internservice.program.entity.enums.ProgramStatus.OPEN) {
            throw new BadRequestException("Chương trình thực tập không ở trạng thái mở nhận hồ sơ (" + program.getStatus().getDisplayName() + ")");
        }

        if (!Boolean.TRUE.equals(program.getIsRecruitmentOpen())) {
            throw new BadRequestException("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");
        }

        // 1. Kiểm tra nếu có userId, kiểm tra xem tài khoản này đã có hồ sơ đang xử lý trong chương trình này chưa
        if (request.getUserId() != null) {
            boolean hasActiveApplication = internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(
                    request.getUserId(),
                    program.getId(),
                    List.of(InternStatus.PENDING, InternStatus.APPROVED, InternStatus.INTERNING)
            );
            if (hasActiveApplication) {
                log.warn("Nộp hồ sơ thất bại: Tài khoản ID={} đã có hồ sơ đang chờ xét duyệt hoặc đang thực tập trong chương trình ID={}", 
                        request.getUserId(), program.getId());
                throw new BadRequestException("Bạn đã có một hồ sơ đang chờ xét duyệt hoặc đang thực tập trong chương trình này");
            }
        }

        // 2. Kiểm tra nếu email đã có hồ sơ đang xử lý trong cùng chương trình
        boolean hasEmailInProgram = internProfileRepository.existsByEmailAndProgramIdAndStatusIn(
                request.getEmail().trim().toLowerCase(),
                program.getId(),
                List.of(InternStatus.PENDING, InternStatus.APPROVED, InternStatus.INTERNING)
        );
        if (hasEmailInProgram) {
            log.warn("Nộp hồ sơ thất bại: Email {} đã có hồ sơ đang xử lý trong chương trình ID={}", request.getEmail(), program.getId());
            throw new BadRequestException("Hồ sơ với email '" + request.getEmail() + "' đang chờ xét duyệt hoặc đang thực tập trong chương trình này");
        }

        // 3. Kiểm tra nếu số điện thoại đã có hồ sơ đang xử lý trong cùng chương trình
        boolean hasPhoneInProgram = internProfileRepository.existsByPhoneAndProgramIdAndStatusIn(
                request.getPhone().trim(),
                program.getId(),
                List.of(InternStatus.PENDING, InternStatus.APPROVED, InternStatus.INTERNING)
        );
        if (hasPhoneInProgram) {
            log.warn("Nộp hồ sơ thất bại: Số điện thoại {} đã có hồ sơ đang xử lý trong chương trình ID={}", request.getPhone(), program.getId());
            throw new BadRequestException("Hồ sơ với số điện thoại '" + request.getPhone() + "' đang chờ xét duyệt hoặc đang thực tập trong chương trình này");
        }

        // 4. Kế thừa ngày bắt đầu / kết thúc từ chương trình nếu ứng viên để trống
        LocalDate effectiveStartDate = request.getStartDate() != null ? request.getStartDate() : program.getStartDate();
        LocalDate effectiveEndDate = request.getEndDate() != null ? request.getEndDate() : program.getEndDate();

        if (effectiveStartDate != null && effectiveEndDate != null && effectiveEndDate.isBefore(effectiveStartDate)) {
            throw new BadRequestException("Ngày kết thúc thực tập không thể trước ngày bắt đầu");
        }

        // 5. Tự động sinh mã thực tập sinh
        String internCode = generateInternCode();

        // 6. Tạo entity InternProfile liên kết với chương trình
        InternProfile profile = InternProfile.builder()
                .userId(request.getUserId())
                .internCode(internCode)
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .university(request.getUniversity().trim())
                .major(request.getMajor().trim())
                .academicYear(request.getAcademicYear() != null ? request.getAcademicYear().trim() : null)
                .appliedPosition(request.getAppliedPosition().trim())
                .startDate(effectiveStartDate)
                .endDate(effectiveEndDate)
                .program(program)
                .status(InternStatus.PENDING)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        InternProfile savedProfile = internProfileRepository.save(profile);
        log.info("Nộp hồ sơ thành công cho ứng viên: internCode={}, ID={}, programId={}", 
                internCode, savedProfile.getId(), program.getId());

        // Bắn thông báo real-time tới HR và ADMIN
        List<Long> hrUserIds = identityServiceClient.findUserIdsByRole("HR");
        if (hrUserIds.isEmpty()) {
            hrUserIds = identityServiceClient.findUserIdsByRole("ADMIN");
        }
        notificationDispatcher.dispatchToMultiple(hrUserIds, hrId -> org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                .recipientId(hrId)
                .actorId(savedProfile.getUserId())
                .title("Đơn ứng tuyển mới")
                .content(String.format("Ứng viên %s vừa nộp hồ sơ vào vị trí %s (%s).",
                        savedProfile.getFullName(), savedProfile.getAppliedPosition(), program.getName()))
                .type("APPLICATION_SUBMITTED")
                .referenceType("APPLICATION")
                .referenceId(String.valueOf(savedProfile.getId()))
                .actionUrl("/hr/interns/" + savedProfile.getId())
                .build());

        return mapToResponse(savedProfile);
    }

    @Override
    @Transactional
    public InternResponse updateIntern(Long id, UpdateInternRequest request) {
        log.info("Bat dau cap nhat ho so thuc tap sinh voi ID: {}", id);

        InternProfile profile = internProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id));

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc thực tập không thể trước ngày bắt đầu");
        }

        validateStatusTransition(profile.getStatus(), request.getStatus());

        if (internProfileRepository.existsByEmailAndIdNot(request.getEmail().trim().toLowerCase(), id)) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' đã tồn tại trong hệ thống");
        }

        if (internProfileRepository.existsByPhoneAndIdNot(request.getPhone().trim(), id)) {
            throw new DuplicateResourceException("Số điện thoại '" + request.getPhone() + "' đã tồn tại trong hệ thống");
        }

        profile.updateInformation(
                request.getFullName().trim(),
                request.getEmail().trim().toLowerCase(),
                request.getPhone().trim(),
                request.getDateOfBirth(),
                request.getGender(),
                request.getAddress(),
                request.getUniversity().trim(),
                request.getMajor().trim(),
                request.getAcademicYear(),
                request.getAppliedPosition().trim(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                request.getNotes()
        );

        InternProfile updatedProfile = internProfileRepository.save(profile);
        log.info("Cap nhat thanh cong ho so thuc tap sinh voi ID: {}, Code: {}", updatedProfile.getId(), updatedProfile.getInternCode());

        return mapToResponse(updatedProfile);
    }

    @Override
    @Transactional
    public InternResponse processDecision(Long id, InternDecisionRequest request, String reviewerUsername) {
        log.info("Bat dau xu ly quyet dinh {} cho ho so ID: {} boi user: {}", request.getDecision(), id, reviewerUsername);

        InternProfile profile = internProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id));

        if (!request.isValidDecision()) {
            throw new BadRequestException("Quyết định xét duyệt không hợp lệ. Chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        if (request.getDecision() == InternStatus.REJECTED && !request.hasValidRejectionReason()) {
            throw new BadRequestException("Lý do từ chối là bắt buộc và phải có ít nhất 5 ký tự khi từ chối hồ sơ");
        }

        if (request.getDecision() == InternStatus.APPROVED) {
            Long targetProgramId = request.getProgramId() != null 
                    ? request.getProgramId() 
                    : (profile.getProgram() != null ? profile.getProgram().getId() : null);

            if (targetProgramId == null) {
                throw new BadRequestException("Vui lòng chọn chương trình thực tập tiếp nhận khi duyệt hồ sơ");
            }
            org.example.internservice.program.entity.InternshipProgram program = programRepository.findByIdWithLock(targetProgramId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + targetProgramId));

            if (program.getStatus() != org.example.internservice.program.entity.enums.ProgramStatus.PLANNING 
                    && program.getStatus() != org.example.internservice.program.entity.enums.ProgramStatus.OPEN) {
                throw new BadRequestException("Chương trình thực tập không ở trạng thái nhận hồ sơ (" + program.getStatus().getDisplayName() + ")");
            }

            if (!Boolean.TRUE.equals(program.getIsRecruitmentOpen())) {
                throw new BadRequestException("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");
            }

            long currentActive = internProfileRepository.countByProgramIdAndStatusIn(
                    program.getId(), 
                    List.of(InternStatus.APPROVED, InternStatus.INTERNING, InternStatus.COMPLETED)
            );
            if (currentActive >= program.getMaxInterns()) {
                throw new BadRequestException("Chương trình đã đạt giới hạn chỉ tiêu tiếp nhận (" + program.getMaxInterns() + " TTS)");
            }

            profile.setProgram(program);
            profile.setNeedsReassignment(false);
            profile.setReassignmentReason(null);
        }

        profile.applyDecision(request.getDecision(), request.getTrimmedRejectionReason(), reviewerUsername);

        InternProfile savedProfile = internProfileRepository.save(profile);
        log.info("Xu ly quyet dinh {} thanh cong cho ho so ID: {}", savedProfile.getStatus(), savedProfile.getId());

        eventPublisher.publishEvent(new InternDecisionProcessedEvent(this, savedProfile));

        return mapToResponse(savedProfile);
    }

    @Override
    @Transactional
    public InternResponse resendDecisionEmail(Long id, String reviewerUsername) {
        log.info("HR {} yeu cau gui lai email cho ho so ID: {}", reviewerUsername, id);

        InternProfile profile = internProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id));

        if (profile.getStatus() != InternStatus.APPROVED && profile.getStatus() != InternStatus.REJECTED) {
            throw new BadRequestException("Chỉ có thể gửi lại email cho hồ sơ đã được DUYỆT hoặc TỪ CHỐI");
        }

        // 1. Kiểm tra Cooldown (45 giây)
        if (profile.getLastEmailSentAt() != null) {
            long secondsSinceLastSent = ChronoUnit.SECONDS.between(profile.getLastEmailSentAt(), LocalDateTime.now());
            if (secondsSinceLastSent < 45) {
                long retryAfter = 45 - secondsSinceLastSent;
                throw new RateLimitException("Email vừa được gửi cách đây " + secondsSinceLastSent + " giây. Vui lòng đợi trước khi gửi lại.", retryAfter);
            }
        }

        // 2. Kiểm tra giới hạn 5 lần resend/ngày
        if (profile.getEmailRetryCount() != null && profile.getEmailRetryCount() >= 5) {
            throw new BadRequestException("Không thể gửi lại quá 5 lần. Vui lòng kiểm tra lại địa chỉ email của thực tập sinh.");
        }

        // Đánh dấu lại trạng thái PENDING và tăng retry count
        profile.markEmailPending();
        InternProfile saved = internProfileRepository.save(profile);

        // Bắn lại event gửi mail
        eventPublisher.publishEvent(new InternDecisionProcessedEvent(this, saved));
        log.info("Da phat su kien gui lai email thanh cong cho ho so ID: {}", id);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void updateEmailStatus(Long id, String status, String errorMessage) {
        log.info("Nhan callback cap nhat emailStatus cho ho so ID: {}, status: {}", id, status);
        internProfileRepository.findById(id).ifPresent(profile -> {
            profile.updateEmailStatus(status);
            internProfileRepository.save(profile);
            log.info("Cap nhat emailStatus thanh cong cho ho so ID: {} sang {}", id, status);
        });
    }

    private void validateStatusTransition(InternStatus currentStatus, InternStatus newStatus) {
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Không thể chuyển đổi trạng thái từ " + currentStatus + " sang " + newStatus);
        }
    }

    private synchronized String generateInternCode() {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

        long count = internProfileRepository.countByCreatedAtBetween(startOfMonth, endOfMonth) + 1;
        return String.format("INT-%s-%04d", yearMonth, count);
    }

    private InternResponse mapToResponse(InternProfile profile) {
        return InternResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .internCode(profile.getInternCode())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .address(profile.getAddress())
                .university(profile.getUniversity())
                .major(profile.getMajor())
                .academicYear(profile.getAcademicYear())
                .appliedPosition(profile.getAppliedPosition())
                .startDate(profile.getStartDate() != null ? profile.getStartDate() : (profile.getProgram() != null ? profile.getProgram().getStartDate() : null))
                .endDate(profile.getEndDate() != null ? profile.getEndDate() : (profile.getProgram() != null ? profile.getProgram().getEndDate() : null))
                .status(profile.getStatus())
                .notes(profile.getNotes())
                .rejectionReason(profile.getRejectionReason())
                .reviewedBy(profile.getReviewedBy())
                .reviewedAt(profile.getReviewedAt())
                .emailStatus(profile.getEmailStatus())
                .emailSentAt(profile.getEmailSentAt())
                .emailRetryCount(profile.getEmailRetryCount())
                .lastEmailSentAt(profile.getLastEmailSentAt())
                .programId(profile.getProgram() != null ? profile.getProgram().getId() : null)
                .programCode(profile.getProgram() != null ? profile.getProgram().getProgramCode() : null)
                .programName(profile.getProgram() != null ? profile.getProgram().getName() : null)
                .candidateType(profile.getCandidateType())
                .desiredDepartmentId(profile.getDesiredDepartmentId())
                .desiredDepartmentName(profile.getDesiredDepartmentName())
                .mentorId(profile.getMentorId())
                .mentorName(profile.getMentorName())
                .mentorEmail(profile.getMentorEmail())
                .needsReassignment(profile.getNeedsReassignment())
                .reassignmentReason(profile.getReassignmentReason())
                .needsMentorReassignment(profile.getNeedsMentorReassignment())
                .mentorReassignmentReason(profile.getMentorReassignmentReason())
                .groupId(profile.getGroup() != null ? profile.getGroup().getId() : null)
                .groupName(profile.getGroup() != null ? profile.getGroup().getName() : null)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public InternResponse assignMentor(Long id, org.example.internservice.intern.dto.request.AssignMentorRequest request, String assignedBy) {
        log.info("HR {} thực hiện phân công Mentor cho TTS ID: {}", assignedBy, id);
        InternProfile intern = internProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id));

        // 1. Chặn nếu chưa có program hoặc program đang chờ điều phối lại
        if (Boolean.TRUE.equals(intern.getNeedsReassignment()) || intern.getProgram() == null) {
            throw new IllegalArgumentException("Không thể phân công Mentor cho thực tập sinh đang chờ điều phối chương trình thực tập. Vui lòng xếp chương trình mới trước.");
        }

        // 2. Chỉ cho phép gán mentor khi ở trạng thái APPROVED hoặc INTERNING
        if (intern.getStatus() != InternStatus.APPROVED && intern.getStatus() != InternStatus.INTERNING) {
            throw new IllegalStateException("Chỉ có thể phân công Mentor cho hồ sơ đã được duyệt (APPROVED) hoặc đang thực tập (INTERNING). Trạng thái hiện tại: " + intern.getStatus());
        }

        // 3. Tìm thông tin Mentor từ MentorProfile hoặc Identity Service
        org.example.internservice.intern.entity.MentorProfile mentorProfile = mentorProfileRepository.findById(request.getMentorId())
                .or(() -> mentorProfileRepository.findByUserId(request.getMentorId()))
                .orElse(null);

        String mentorFullName;
        String mentorEmail;

        if (mentorProfile != null) {
            if (!"ACTIVE".equalsIgnoreCase(mentorProfile.getStatus())) {
                throw new BadRequestException("Người hướng dẫn này chưa kích hoạt tài khoản. Vui lòng yêu cầu Mentor kích hoạt trước khi phân công.");
            }
            mentorFullName = mentorProfile.getFullName();
            mentorEmail = mentorProfile.getEmail();
        } else {
            // Fallback sang Identity Service cho cac user mentor tao truoc do
            List<Map<String, Object>> users = identityServiceClient.getAllUsers();
            Map<String, Object> mentorUser = users.stream()
                    .filter(u -> {
                        Object uid = u.get("id");
                        return uid != null && Long.valueOf(uid.toString()).equals(request.getMentorId());
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Mentor với ID: " + request.getMentorId()));

            String mentorRole = mentorUser.get("role") != null ? mentorUser.get("role").toString() : "";
            String mentorStatus = mentorUser.get("status") != null ? mentorUser.get("status").toString() : "";
            boolean isMentorRole = "MENTOR".equalsIgnoreCase(mentorRole)
                    || "ROLE_MENTOR".equalsIgnoreCase(mentorRole)
                    || mentorRole.toUpperCase().contains("MENTOR");
            if (!isMentorRole) {
                throw new IllegalArgumentException("Người dùng được chọn không có vai trò MENTOR");
            }
            if (!mentorStatus.isBlank() && !"ACTIVE".equalsIgnoreCase(mentorStatus)) {
                throw new BadRequestException("Tài khoản Mentor đang bị vô hiệu hóa hoặc chưa kích hoạt");
            }

            mentorFullName = mentorUser.get("fullName") != null ? mentorUser.get("fullName").toString() : ("Mentor " + request.getMentorId());
            mentorEmail = mentorUser.get("email") != null ? mentorUser.get("email").toString() : ("mentor" + request.getMentorId() + "@internhub.vn");
            String mentorPhone = mentorUser.get("phone") != null ? mentorUser.get("phone").toString()
                    : (mentorUser.get("phoneNumber") != null ? mentorUser.get("phoneNumber").toString() : ("09" + (System.currentTimeMillis() % 100000000)));

            // Tự động tạo MentorProfile trong intern-and-program-service nếu chưa có
            try {
                org.example.internservice.program.entity.Department dept = (intern.getProgram() != null && intern.getProgram().getDepartment() != null)
                        ? intern.getProgram().getDepartment()
                        : departmentRepository.findAll().stream().findFirst().orElse(null);

                mentorProfile = org.example.internservice.intern.entity.MentorProfile.builder()
                        .userId(request.getMentorId())
                        .fullName(mentorFullName)
                        .email(mentorEmail)
                        .phone(mentorPhone)
                        .department(dept)
                        .status("ACTIVE")
                        .build();
                mentorProfile = mentorProfileRepository.save(mentorProfile);
                log.info("Tự động tạo mới MentorProfile ID={} cho Mentor userId={}", mentorProfile.getId(), request.getMentorId());
            } catch (Exception e) {
                log.warn("Lỗi khi tự tạo MentorProfile: {}", e.getMessage());
                mentorProfile = mentorProfileRepository.findByEmail(mentorEmail).orElse(null);
            }
        }

        if (mentorFullName == null || mentorFullName.isBlank()) {
            mentorFullName = "Mentor " + request.getMentorId();
        }
        if (mentorEmail == null || mentorEmail.isBlank()) {
            mentorEmail = "mentor" + request.getMentorId() + "@internhub.vn";
        }

        Long oldMentorId = intern.getMentorId();
        String oldMentorName = intern.getMentorName();
        String oldMentorEmail = intern.getMentorEmail();
        boolean isReplacing = oldMentorId != null;

        // 4. Xử lý trường hợp Thay thế Mentor cũ
        if (isReplacing) {
            if (request.getReplaceReason() == null || request.getReplaceReason().trim().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng nhập lý do thay đổi người hướng dẫn");
            }

            // Đóng bản ghi phân công cũ sang REPLACED
            internMentorAssignmentRepository.findByInternIdAndStatus(intern.getId(), org.example.internservice.intern.entity.enums.MentorAssignmentStatus.ACTIVE)
                    .ifPresent(oldAssignment -> {
                        oldAssignment.setStatus(org.example.internservice.intern.entity.enums.MentorAssignmentStatus.REPLACED);
                        oldAssignment.setRevokedAt(LocalDateTime.now());
                        oldAssignment.setRevocationReason(request.getReplaceReason().trim());
                        internMentorAssignmentRepository.save(oldAssignment);
                    });
        }

        // 5. Tạo bản ghi phân công mới ACTIVE
        org.example.internservice.intern.entity.InternMentorAssignment newAssignment = org.example.internservice.intern.entity.InternMentorAssignment.builder()
                .intern(intern)
                .mentorId(request.getMentorId())
                .mentorName(mentorFullName)
                .mentorEmail(mentorEmail)
                .assignedBy(assignedBy)
                .assignedAt(LocalDateTime.now())
                .status(org.example.internservice.intern.entity.enums.MentorAssignmentStatus.ACTIVE)
                .notes(request.getNotes())
                .build();
        internMentorAssignmentRepository.save(newAssignment);

        // 6. Cập nhật hồ sơ InternProfile
        intern.setMentorId(request.getMentorId());
        intern.setMentorName(mentorFullName);
        intern.setMentorEmail(mentorEmail);
        intern.setNeedsMentorReassignment(false);
        intern.setMentorReassignmentReason(null);

        // 7. Tự động đồng bộ ProgramMentor để Mentor nhìn thấy Program trong Mission Board
        if (intern.getProgram() != null && mentorProfile != null) {
            try {
                boolean existsInProgram = programMentorRepository.existsByProgramIdAndMentorIdentifier(intern.getProgram().getId(), mentorProfile.getId())
                        || programMentorRepository.existsByProgramIdAndMentorIdentifier(intern.getProgram().getId(), request.getMentorId());
                if (!existsInProgram) {
                    org.example.internservice.program.entity.ProgramMentor pm = org.example.internservice.program.entity.ProgramMentor.builder()
                            .program(intern.getProgram())
                            .mentor(mentorProfile)
                            .assignedBy(assignedBy)
                            .assignedAt(LocalDateTime.now())
                            .build();
                    programMentorRepository.save(pm);
                    log.info("Tự động đồng bộ Mentor ID={} vào Program ID={}", mentorProfile.getId(), intern.getProgram().getId());
                }
            } catch (Exception e) {
                log.warn("Không thể lưu ProgramMentor khi phân công mentor: {}", e.getMessage());
            }
        }

        // 8. Cơ chế điều kiện kép: Chuyển APPROVED sang INTERNING nếu Program đã ONGOING
        if (intern.getStatus() == InternStatus.APPROVED && intern.getProgram() != null && intern.getProgram().getStatus() == org.example.internservice.program.entity.enums.ProgramStatus.ONGOING) {
            intern.setStatus(InternStatus.INTERNING);
            log.info("TTS {} ({}) thỏa mãn điều kiện kép -> Tự động chuyển APPROVED -> INTERNING", intern.getFullName(), intern.getInternCode());
        }

        InternProfile saved = internProfileRepository.save(intern);

        // Bắn sự kiện gửi email 3 chiều (Intern, New Mentor, Old Mentor)
        try {
            eventPublisher.publishEvent(new org.example.internservice.intern.event.InternMentorAssignedEvent(
                    this,
                    saved.getId(),
                    saved.getInternCode(),
                    saved.getFullName(),
                    saved.getEmail(),
                    saved.getProgram() != null ? saved.getProgram().getName() : "Chương trình thực tập",
                    saved.getAppliedPosition(),
                    isReplacing ? "REPLACED" : "ASSIGNED",
                    request.getMentorId(),
                    mentorFullName,
                    mentorEmail,
                    oldMentorId,
                    oldMentorName,
                    oldMentorEmail,
                    request.getReplaceReason(),
                    request.getNotes(),
                    assignedBy
            ));
        } catch (Exception e) {
            log.warn("Loi phat su kien gui email mentor: {}", e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public InternResponse revokeMentor(Long id, org.example.internservice.intern.dto.request.RevokeMentorRequest request, String revokedBy) {
        log.info("HR {} thực hiện thu hồi Mentor của TTS ID: {}", revokedBy, id);
        InternProfile intern = internProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với ID: " + id));

        if (intern.getMentorId() == null) {
            throw new IllegalStateException("Thực tập sinh hiện tại chưa được phân công Mentor để thu hồi");
        }

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do thu hồi người hướng dẫn không được để trống");
        }

        Long oldMentorId = intern.getMentorId();
        String oldMentorName = intern.getMentorName();
        String oldMentorEmail = intern.getMentorEmail();

        // Chuyển bản ghi assignment sang REVOKED
        internMentorAssignmentRepository.findByInternIdAndStatus(intern.getId(), org.example.internservice.intern.entity.enums.MentorAssignmentStatus.ACTIVE)
                .ifPresent(assignment -> {
                    assignment.setStatus(org.example.internservice.intern.entity.enums.MentorAssignmentStatus.REVOKED);
                    assignment.setRevokedAt(LocalDateTime.now());
                    assignment.setRevocationReason(request.getReason().trim());
                    internMentorAssignmentRepository.save(assignment);
                });

        intern.setMentorId(null);
        intern.setMentorName(null);
        intern.setMentorEmail(null);
        intern.setNeedsMentorReassignment(true);
        intern.setMentorReassignmentReason(request.getReason().trim());

        InternProfile saved = internProfileRepository.save(intern);

        // Bắn sự kiện gửi email thu hồi cho Mentor cũ và TTS
        try {
            eventPublisher.publishEvent(new org.example.internservice.intern.event.InternMentorAssignedEvent(
                    this,
                    saved.getId(),
                    saved.getInternCode(),
                    saved.getFullName(),
                    saved.getEmail(),
                    saved.getProgram() != null ? saved.getProgram().getName() : "Chương trình thực tập",
                    saved.getAppliedPosition(),
                    "REVOKED",
                    null,
                    null,
                    null,
                    oldMentorId,
                    oldMentorName,
                    oldMentorEmail,
                    request.getReason(),
                    null,
                    revokedBy
            ));
        } catch (Exception e) {
            log.warn("Loi phat su kien gui email thu hoi mentor: {}", e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Override
    public List<org.example.internservice.intern.dto.response.MentorAssignmentResponse> getMentorHistory(Long id) {
        return internMentorAssignmentRepository.findByInternIdOrderByAssignedAtDesc(id).stream()
                .map(a -> org.example.internservice.intern.dto.response.MentorAssignmentResponse.builder()
                        .id(a.getId())
                        .internId(a.getIntern() != null ? a.getIntern().getId() : null)
                        .mentorId(a.getMentorId())
                        .mentorName(a.getMentorName())
                        .mentorEmail(a.getMentorEmail())
                        .assignedBy(a.getAssignedBy())
                        .assignedAt(a.getAssignedAt())
                        .status(a.getStatus())
                        .notes(a.getNotes())
                        .revokedAt(a.getRevokedAt())
                        .revocationReason(a.getRevocationReason())
                        .build())
                .toList();
    }

    @Override
    public List<org.example.internservice.intern.dto.response.MentorOptionResponse> getAvailableMentors() {
        log.info("Lấy danh sách Mentor từ bảng mentor_profiles và fallback Identity");
        List<org.example.internservice.intern.entity.MentorProfile> mentorProfiles = mentorProfileRepository.findAllWithDepartment();

        // Map tu mentor_profiles entity truoc (chinh xac 100% phong ban)
        List<org.example.internservice.intern.dto.response.MentorOptionResponse> result = new ArrayList<>();
        Set<String> processedEmails = new java.util.HashSet<>();

        for (org.example.internservice.intern.entity.MentorProfile m : mentorProfiles) {
            long interningCount = internProfileRepository.countByMentorIdAndStatus(m.getId(), InternStatus.INTERNING);
            long approvedCount = internProfileRepository.countByMentorIdAndStatus(m.getId(), InternStatus.APPROVED);
            long activeCount = interningCount + approvedCount;

            result.add(org.example.internservice.intern.dto.response.MentorOptionResponse.builder()
                    .id(m.getId())
                    .fullName(m.getFullName())
                    .email(m.getEmail())
                    .phone(m.getPhone())
                    .departmentId(m.getDepartment() != null ? m.getDepartment().getId() : null)
                    .departmentName(m.getDepartment() != null ? m.getDepartment().getName() : "Chưa phân ban")
                    .departmentCode(m.getDepartment() != null ? m.getDepartment().getCode() : null)
                    .status(m.getStatus())
                    .activeInternCount(activeCount)
                    .interningCount(interningCount)
                    .assignedPendingStartCount(approvedCount)
                    .build());
            processedEmails.add(m.getEmail().toLowerCase());
        }

        // Fallback cho cac tai khoan MENTOR co san tren Identity chua co record trong mentor_profiles
        List<Map<String, Object>> users = identityServiceClient.getAllUsers();
        for (Map<String, Object> u : users) {
            String email = u.get("email") != null ? u.get("email").toString().toLowerCase() : "";
            if (!processedEmails.contains(email) && "MENTOR".equalsIgnoreCase((String) u.get("role"))) {
                Long mentorId = Long.valueOf(u.get("id").toString());
                long interningCount = internProfileRepository.countByMentorIdAndStatus(mentorId, InternStatus.INTERNING);
                long approvedCount = internProfileRepository.countByMentorIdAndStatus(mentorId, InternStatus.APPROVED);

                result.add(org.example.internservice.intern.dto.response.MentorOptionResponse.builder()
                        .id(mentorId)
                        .fullName((String) u.get("fullName"))
                        .email((String) u.get("email"))
                        .phone((String) u.get("phone"))
                        .departmentId(1L)
                        .departmentName("Trung tâm Phát triển Phần mềm")
                        .departmentCode("IT-DEV")
                        .status((String) u.get("status"))
                        .activeInternCount(interningCount + approvedCount)
                        .interningCount(interningCount)
                        .assignedPendingStartCount(approvedCount)
                        .build());
            }
        }

        return result;
    }

    @Override
    @Transactional
    public org.example.internservice.intern.dto.response.MentorOptionResponse createMentor(org.example.internservice.intern.dto.request.CreateMentorRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();
        log.info("HR tạo mới Mentor: fullName={}, email={}, deptId={}", request.getFullName(), email, request.getDepartmentId());

        if (mentorProfileRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email này đã được sử dụng cho một người hướng dẫn khác");
        }
        if (mentorProfileRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Số điện thoại này đã được sử dụng cho một người hướng dẫn khác");
        }

        org.example.internservice.program.entity.Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + request.getDepartmentId()));

        org.example.internservice.intern.entity.MentorProfile mentor = org.example.internservice.intern.entity.MentorProfile.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(phone)
                .department(department)
                .status("PENDING_ACTIVATION")
                .lastInvitationSentAt(LocalDateTime.now())
                .invitationRetryCount(0)
                .build();

        org.example.internservice.intern.entity.MentorProfile savedMentor = mentorProfileRepository.save(mentor);
        log.info("Lưu thành công MentorProfile ID: {}, status: PENDING_ACTIVATION", savedMentor.getId());

        // Sinh token kích hoạt an toàn cho Mentor
        String activationToken = onboardingTokenService.generateOnboardingToken(
                savedMentor.getId(),
                savedMentor.getEmail(),
                savedMentor.getFullName(),
                "MENTOR"
        );

        // Bắn request sang Reporting Service để gửi email mời kích hoạt (BẮT BUỘC)
        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("mentorProfileId", savedMentor.getId());
        emailPayload.put("email", savedMentor.getEmail());
        emailPayload.put("fullName", savedMentor.getFullName());
        emailPayload.put("departmentName", department.getName());
        emailPayload.put("onboardingToken", activationToken);

        integrationEmailClient.sendMentorOnboardingEmail(emailPayload);

        return org.example.internservice.intern.dto.response.MentorOptionResponse.builder()
                .id(savedMentor.getId())
                .fullName(savedMentor.getFullName())
                .email(savedMentor.getEmail())
                .phone(savedMentor.getPhone())
                .departmentId(department.getId())
                .departmentName(department.getName())
                .departmentCode(department.getCode())
                .status(savedMentor.getStatus())
                .activeInternCount(0L)
                .interningCount(0L)
                .assignedPendingStartCount(0L)
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> resendMentorInvitation(Long mentorId) {
        log.info("Yêu cầu gửi lại email kích hoạt cho Mentor ID: {}", mentorId);

        org.example.internservice.intern.entity.MentorProfile mentor = mentorProfileRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người hướng dẫn với ID: " + mentorId));

        if ("ACTIVE".equalsIgnoreCase(mentor.getStatus())) {
            throw new BadRequestException("Tài khoản người hướng dẫn đã được kích hoạt thành công, không thể gửi lại thư mời.");
        }

        // 1. Kiểm tra Cooldown 45 giây (tái sử dụng từ TM-12)
        if (mentor.getLastInvitationSentAt() != null) {
            long secondsSinceLastSent = ChronoUnit.SECONDS.between(mentor.getLastInvitationSentAt(), LocalDateTime.now());
            if (secondsSinceLastSent < 45) {
                long retryAfter = 45 - secondsSinceLastSent;
                throw new RateLimitException("Thư mời vừa được gửi cách đây ít phút. Vui lòng đợi trước khi gửi lại.", retryAfter);
            }
        }

        // 2. Giới hạn tối đa 5 lần gửi lại trong ngày
        if (mentor.getInvitationRetryCount() != null && mentor.getInvitationRetryCount() >= 5) {
            throw new BadRequestException("Đã vượt quá số lần gửi lại tối đa trong ngày (5 lần). Vui lòng liên hệ trực tiếp với Mentor.");
        }

        mentor.setLastInvitationSentAt(LocalDateTime.now());
        mentor.setInvitationRetryCount((mentor.getInvitationRetryCount() != null ? mentor.getInvitationRetryCount() : 0) + 1);
        mentorProfileRepository.save(mentor);

        // Sinh token mới và gửi email
        String activationToken = onboardingTokenService.generateOnboardingToken(
                mentor.getId(),
                mentor.getEmail(),
                mentor.getFullName(),
                "MENTOR"
        );

        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("mentorProfileId", mentor.getId());
        emailPayload.put("email", mentor.getEmail());
        emailPayload.put("fullName", mentor.getFullName());
        emailPayload.put("departmentName", mentor.getDepartment() != null ? mentor.getDepartment().getName() : "Công ty");
        emailPayload.put("onboardingToken", activationToken);

        integrationEmailClient.sendMentorOnboardingEmail(emailPayload);

        Map<String, Object> result = new HashMap<>();
        result.put("mentorId", mentor.getId());
        result.put("lastInvitationSentAt", mentor.getLastInvitationSentAt());
        result.put("retryCount", mentor.getInvitationRetryCount());
        result.put("message", "Đã gửi lại email thư mời kích hoạt tài khoản cho Mentor");
        return result;
    }

    @Override
    public PageResponse<InternResponse> searchInterns(InternFilterRequest request, Pageable pageable) {
        log.info("Tim kiem va loc ho so thuc tap sinh");
        Pageable sanitizedPageable = sanitizePageable(pageable);

        // Bảo mật cấp API: Tự động ép lọc theo tập hợp mentorIds nếu người gọi là ROLE_MENTOR
        List<Long> enforceMentorIds = null;
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof org.example.internservice.security.CustomUserDetails userDetails) {
            boolean isMentor = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_MENTOR") || a.getAuthority().equalsIgnoreCase("MENTOR"));
            if (isMentor) {
                final List<Long> mentorIds = new ArrayList<>();
                mentorIds.add(userDetails.getUserId());
                mentorProfileRepository.findByUserId(userDetails.getUserId())
                        .ifPresent(mp -> mentorIds.add(mp.getId()));
                if (userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
                    mentorProfileRepository.findByEmail(userDetails.getUsername())
                            .ifPresent(mp -> {
                                if (!mentorIds.contains(mp.getId())) {
                                    mentorIds.add(mp.getId());
                                }
                            });
                }
                log.info("Phát hiện tài khoản Mentor [user={}, mentorIds={}]. Tự động giới hạn dữ liệu chỉ hiển thị TTS do mentor này phụ trách.",
                        userDetails.getUsername(), mentorIds);
                enforceMentorIds = mentorIds;
            }
        }

        Specification<InternProfile> spec = InternProfileSpecification.getSpecification(request, enforceMentorIds);
        Page<InternProfile> internPage = internProfileRepository.findAll(spec, sanitizedPageable);
        return PageResponse.from(internPage, this::mapToResponse);

    }

    private Pageable sanitizePageable(Pageable pageable) {
        int pageNumber = (pageable != null && pageable.getPageNumber() >= 0) ? pageable.getPageNumber() : 0;
        int pageSize = DEFAULT_PAGE_SIZE;

        if (pageable != null) {
            if (pageable.getPageSize() > MAX_PAGE_SIZE) {
                pageSize = MAX_PAGE_SIZE;
            } else if (pageable.getPageSize() > 0) {
                pageSize = pageable.getPageSize();
            }
        }

        Sort validSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable != null && pageable.getSort().isSorted()) {
            List<Sort.Order> validOrders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    validOrders.add(order);
                }
            }
            if (!validOrders.isEmpty()) {
                validSort = Sort.by(validOrders);
            }
        }

        return PageRequest.of(pageNumber, pageSize, validSort);
    }

    @Override
    public InternResponse getMyProfileByUserId(Long userId) {
        log.info("Lấy thông tin hồ sơ cho userId: {}", userId);
        return internProfileRepository.findByUserId(userId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh liên kết với tài khoản người dùng ID: " + userId));
    }

    @Override
    public List<InternResponse> getInternsByMentorId(Long mentorId) {
        log.info("Lấy danh sách TTS được phân công cho Mentor ID: {}", mentorId);
        return internProfileRepository.findByMentorId(mentorId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeInternFromProgram(Long programId, Long internId) {
        log.info("Yêu cầu gỡ thực tập sinh ID: {} khỏi chương trình ID: {}", internId, programId);

        InternProfile intern = internProfileRepository.findById(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với ID: " + internId));

        if (intern.getProgram() == null || !intern.getProgram().getId().equals(programId)) {
            throw new BadRequestException("Thực tập sinh ID " + internId + " không thuộc chương trình ID " + programId);
        }

        // 3-Layer Defense Guards:
        // 1. Kiểm tra Mentor
        if (intern.getMentorId() != null) {
            throw new BadRequestException("Thực tập sinh đã được gán Mentor hướng dẫn. Vui lòng dùng chức năng 'Chuyển chương trình' hoặc 'Chấm dứt thực tập' thay vì gỡ trực tiếp.");
        }

        // 2. Kiểm tra Đánh giá quá trình
        boolean hasWeekly = !weeklyAssessmentRepository.findByInternCodeOrderByWeekNumberDesc(intern.getInternCode()).isEmpty();
        boolean hasEvaluation = !evaluationRepository.findByInternCode(intern.getInternCode()).isEmpty();
        if (hasWeekly || hasEvaluation) {
            throw new BadRequestException("Thực tập sinh đã có dữ liệu đánh giá quá trình. Vui lòng dùng chức năng 'Chuyển chương trình' hoặc 'Chấm dứt thực tập' thay vì gỡ trực tiếp.");
        }

        // 3. Kiểm tra Trạng thái hợp lệ (chỉ cho phép khi PENDING hoặc APPROVED)
        if (intern.getStatus() != InternStatus.PENDING && intern.getStatus() != InternStatus.APPROVED) {
            throw new BadRequestException("Không thể gỡ trực tiếp thực tập sinh ở trạng thái " + intern.getStatus().name() + ". Vui lòng dùng chức năng 'Chuyển chương trình' hoặc 'Chấm dứt thực tập'.");
        }

        // Reset liên kết chương trình và nhóm
        intern.setProgram(null);
        intern.setGroup(null);
        internProfileRepository.save(intern);
        log.info("Đã gỡ thành công thực tập sinh ID: {} khỏi chương trình ID: {}", internId, programId);
    }
}


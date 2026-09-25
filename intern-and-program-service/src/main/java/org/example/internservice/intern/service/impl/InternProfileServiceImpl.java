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
import java.util.List;
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
    private final ApplicationEventPublisher eventPublisher;

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
        log.info("Bắt đầu xử lý nộp hồ sơ ứng tuyển trực tuyến: email={}, userId={}", request.getEmail(), request.getUserId());

        // 1. Kiểm tra nếu có userId, kiểm tra xem tài khoản này đã có hồ sơ đang xử lý chưa
        if (request.getUserId() != null) {
            boolean hasActiveApplication = internProfileRepository.existsByUserIdAndStatusIn(
                    request.getUserId(),
                    List.of(InternStatus.PENDING, InternStatus.APPROVED, InternStatus.INTERNING)
            );
            if (hasActiveApplication) {
                log.warn("Nộp hồ sơ thất bại: Tài khoản ID={} đã có hồ sơ đang chờ xét duyệt hoặc đang thực tập", request.getUserId());
                throw new BadRequestException("Bạn đã có một hồ sơ đang chờ xét duyệt hoặc đang trong quá trình thực tập");
            }
        }

        // 2. Kiểm tra nếu email đã có hồ sơ đang xử lý
        boolean hasEmailInProcess = internProfileRepository.existsByEmailAndStatusIn(
                request.getEmail().trim(),
                List.of(InternStatus.PENDING, InternStatus.APPROVED, InternStatus.INTERNING)
        );
        if (hasEmailInProcess) {
            log.warn("Nộp hồ sơ thất bại: Email {} đã có hồ sơ đang xử lý", request.getEmail());
            throw new BadRequestException("Hồ sơ với email '" + request.getEmail() + "' đang chờ xét duyệt hoặc đang trong quá trình thực tập");
        }

        // 3. Kiểm tra tính duy nhất của email và phone
        if (internProfileRepository.existsByEmail(request.getEmail().trim())) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' đã tồn tại trong hệ thống");
        }

        if (internProfileRepository.existsByPhone(request.getPhone().trim())) {
            throw new DuplicateResourceException("Số điện thoại '" + request.getPhone() + "' đã tồn tại trong hệ thống");
        }

        // 4. Validate ngày kết thúc nếu có
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc thực tập không thể trước ngày bắt đầu");
        }

        // 5. Tự động sinh mã thực tập sinh
        String internCode = generateInternCode();

        // 6. Tạo entity InternProfile
        InternProfile profile = InternProfile.builder()
                .userId(request.getUserId())
                .internCode(internCode)
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim())
                .phone(request.getPhone().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .university(request.getUniversity().trim())
                .major(request.getMajor().trim())
                .academicYear(request.getAcademicYear() != null ? request.getAcademicYear().trim() : null)
                .appliedPosition(request.getAppliedPosition().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(InternStatus.PENDING)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        InternProfile savedProfile = internProfileRepository.save(profile);
        log.info("Nộp hồ sơ thành công cho ứng viên: internCode={}, ID={}", internCode, savedProfile.getId());

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
            if (request.getProgramId() == null) {
                throw new BadRequestException("Vui lòng chọn chương trình thực tập tiếp nhận khi duyệt hồ sơ");
            }
            org.example.internservice.program.entity.InternshipProgram program = programRepository.findByIdWithLock(request.getProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + request.getProgramId()));

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
                .startDate(profile.getStartDate())
                .endDate(profile.getEndDate())
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
                .needsReassignment(profile.getNeedsReassignment())
                .reassignmentReason(profile.getReassignmentReason())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    @Override
    public PageResponse<InternResponse> searchInterns(InternFilterRequest request, Pageable pageable) {
        log.info("Tim kiem va loc ho so thuc tap sinh");
        Pageable sanitizedPageable = sanitizePageable(pageable);
        Specification<InternProfile> spec = InternProfileSpecification.getSpecification(request);
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
}


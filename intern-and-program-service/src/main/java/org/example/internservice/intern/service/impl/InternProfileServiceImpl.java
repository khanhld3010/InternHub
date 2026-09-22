package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.specification.InternProfileSpecification;
import org.example.internservice.intern.service.InternProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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


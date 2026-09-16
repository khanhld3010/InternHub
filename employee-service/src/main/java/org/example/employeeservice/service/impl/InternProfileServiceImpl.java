package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.request.CreateInternRequest;
import org.example.employeeservice.dto.response.InternResponse;
import org.example.employeeservice.entity.InternProfile;
import org.example.employeeservice.entity.enums.InternStatus;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.repository.InternProfileRepository;
import org.example.employeeservice.service.InternProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternProfileServiceImpl implements InternProfileService {

    private final InternProfileRepository internProfileRepository;

    @Override
    @Transactional
    public InternResponse createIntern(CreateInternRequest request) {
        log.info("Bắt đầu tiếp nhận tạo mới hồ sơ thực tập sinh: email={}, phone={}", request.getEmail(), request.getPhone());

        // 1. Kiểm tra trùng lặp email
        if (internProfileRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' đã tồn tại trong hệ thống");
        }

        // 2. Kiểm tra trùng lặp số điện thoại
        if (internProfileRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Số điện thoại '" + request.getPhone() + "' đã tồn tại trong hệ thống");
        }

        // 3. Tự động sinh mã thực tập sinh: INT-YYYYMM-XXXX
        String internCode = generateUniqueInternCode();

        // 4. Map DTO sang Entity
        InternProfile profile = InternProfile.builder()
                .internCode(internCode)
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .university(request.getUniversity().trim())
                .major(request.getMajor().trim())
                .academicYear(request.getAcademicYear() != null ? request.getAcademicYear().trim() : null)
                .gpa(request.getGpa())
                .appliedPosition(request.getAppliedPosition().trim())
                .status(InternStatus.PENDING)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        // 5. Lưu vào CSDL
        InternProfile savedProfile = internProfileRepository.save(profile);
        log.info("Đã tạo thành công hồ sơ thực tập sinh với mã: {}", savedProfile.getInternCode());

        // 6. Map Entity sang Response DTO
        return mapToResponse(savedProfile);
    }

    private String generateUniqueInternCode() {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "INT-" + yearMonth + "-";

        long currentCount = internProfileRepository.countByInternCodeStartingWith(prefix);
        long nextIndex = currentCount + 1;
        String candidateCode = String.format("%s%04d", prefix, nextIndex);

        while (internProfileRepository.existsByInternCode(candidateCode)) {
            nextIndex++;
            candidateCode = String.format("%s%04d", prefix, nextIndex);
        }

        return candidateCode;
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
                .university(profile.getUniversity())
                .major(profile.getMajor())
                .academicYear(profile.getAcademicYear())
                .gpa(profile.getGpa())
                .appliedPosition(profile.getAppliedPosition())
                .status(profile.getStatus())
                .startDate(profile.getStartDate())
                .endDate(profile.getEndDate())
                .address(profile.getAddress())
                .notes(profile.getNotes())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}

package org.example.internservice.program.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.dto.request.ChangeProgramStatusRequest;
import org.example.internservice.program.dto.request.CreateProgramRequest;
import org.example.internservice.program.dto.request.ProgramFilterRequest;
import org.example.internservice.program.dto.request.UpdateProgramRequest;
import org.example.internservice.program.dto.response.DepartmentResponse;
import org.example.internservice.program.dto.response.ProgramDetailResponse;
import org.example.internservice.program.dto.response.ProgramSummaryResponse;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramCodeSequence;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.DepartmentRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramCodeSequenceRepository;
import org.example.internservice.program.service.InternshipProgramService;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipProgramServiceImpl implements InternshipProgramService {

    private final InternshipProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramCodeSequenceRepository sequenceRepository;
    private final InternProfileRepository internProfileRepository;

    private static final List<InternStatus> ACTIVE_INTERN_STATUSES = List.of(
            InternStatus.APPROVED, InternStatus.INTERNING, InternStatus.COMPLETED
    );

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findByStatus("ACTIVE").stream()
                .map(DepartmentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = {DataIntegrityViolationException.class, CannotAcquireLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    public ProgramDetailResponse createProgram(CreateProgramRequest request, String createdBy) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại với ID: " + request.getDepartmentId()));

        boolean isHistorical = Boolean.TRUE.equals(request.getIsHistorical());
        LocalDate today = LocalDate.now();

        if (isHistorical) {
            if (!request.getEndDate().isAfter(request.getStartDate())) {
                throw new BadRequestException("Ngày kết thúc phải lớn hơn ngày bắt đầu");
            }
        } else {
            if (request.getStartDate().isBefore(today)) {
                throw new BadRequestException("Ngày bắt đầu chương trình không được nằm trong quá khứ");
            }
            if (!request.getEndDate().isAfter(request.getStartDate())) {
                throw new BadRequestException("Ngày kết thúc phải lớn hơn ngày bắt đầu");
            }
            long daysBetween = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            if (daysBetween < 27) {
                throw new BadRequestException("Thời lượng chương trình thực tập tối thiểu phải từ 4 tuần trở lên (ít nhất 28 ngày)");
            }
            if (request.getMaxInterns() == null || request.getMaxInterns() <= 0) {
                throw new BadRequestException("Chỉ tiêu tiếp nhận tối đa phải lớn hơn 0");
            }
        }

        String programCode = generateUniqueProgramCode(request.getStartDate());

        InternshipProgram program = InternshipProgram.builder()
                .programCode(programCode)
                .name(request.getName().trim())
                .department(department)
                .description(request.getDescription())
                .maxInterns(isHistorical ? (request.getCurrentInterns() != null ? request.getCurrentInterns() : 0) : request.getMaxInterns())
                .currentInterns(isHistorical ? (request.getCurrentInterns() != null ? request.getCurrentInterns() : 0) : 0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isRecruitmentOpen(!isHistorical)
                .isHistorical(isHistorical)
                .status(isHistorical ? ProgramStatus.COMPLETED : ProgramStatus.PLANNING)
                .createdBy(createdBy)
                .build();

        InternshipProgram saved = programRepository.save(program);
        long activeCount = isHistorical ? saved.getCurrentInterns() : 0L;
        log.info("Chương trình thực tập mới được tạo: mã={}, trạng thái={}", saved.getProgramCode(), saved.getStatus());
        return ProgramDetailResponse.fromEntity(saved, activeCount);
    }

    private String generateUniqueProgramCode(LocalDate date) {
        String yearMonth = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        ProgramCodeSequence seqEntity = sequenceRepository.findByYearMonthWithLock(yearMonth)
                .orElseGet(() -> ProgramCodeSequence.builder()
                        .yearMonth(yearMonth)
                        .currentSeq(0L)
                        .updatedAt(LocalDateTime.now())
                        .build());

        long nextSeq = seqEntity.getCurrentSeq() + 1;
        seqEntity.setCurrentSeq(nextSeq);
        seqEntity.setUpdatedAt(LocalDateTime.now());
        sequenceRepository.save(seqEntity);

        return String.format("PRG-%s-%04d", yearMonth, nextSeq);
    }

    @Override
    @Transactional
    public ProgramDetailResponse updateProgram(Long id, UpdateProgramRequest request) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));

        if (program.getStatus() == ProgramStatus.COMPLETED || program.getStatus() == ProgramStatus.CANCELLED) {
            throw new BadRequestException("Không thể chỉnh sửa chương trình đã ở trạng thái " + program.getStatus().getDisplayName());
        }

        if (program.getStatus() == ProgramStatus.ONGOING) {
            if (request.getStartDate() != null && !request.getStartDate().isEqual(program.getStartDate())) {
                throw new BadRequestException("Không thể thay đổi ngày bắt đầu khi chương trình thực tập đang diễn ra (ONGOING)");
            }
        }

        if (!request.getEndDate().isAfter(request.getStartDate() != null ? request.getStartDate() : program.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc phải lớn hơn ngày bắt đầu");
        }

        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(id, ACTIVE_INTERN_STATUSES);
        if (request.getMaxInterns() != null && request.getMaxInterns() < activeCount) {
            throw new BadRequestException("Chỉ tiêu tối đa (" + request.getMaxInterns() + ") không được nhỏ hơn số lượng TTS hiện có (" + activeCount + ")");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại với ID: " + request.getDepartmentId()));

        program.setName(request.getName().trim());
        program.setDepartment(department);
        program.setDescription(request.getDescription());
        if (request.getMaxInterns() != null) {
            program.setMaxInterns(request.getMaxInterns());
        }
        if (program.getStatus() != ProgramStatus.ONGOING && request.getStartDate() != null) {
            program.setStartDate(request.getStartDate());
        }
        program.setEndDate(request.getEndDate());

        InternshipProgram saved = programRepository.save(program);
        return ProgramDetailResponse.fromEntity(saved, activeCount);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProgramDetailResponse> getPrograms(ProgramFilterRequest filter, Pageable pageable) {
        Specification<InternshipProgram> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("programCode")), pattern)
                ));
            }
            if (filter.getDepartmentId() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("department").get("id"), filter.getDepartmentId()));
            }
            if (filter.getStatus() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getIsRecruitmentOpen() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("isRecruitmentOpen"), filter.getIsRecruitmentOpen()));
            }
            return predicates;
        };

        Page<InternshipProgram> page = programRepository.findAll(spec, pageable);
        return PageResponse.from(page, program -> {
            long activeCount = internProfileRepository.countByProgramIdAndStatusIn(program.getId(), ACTIVE_INTERN_STATUSES);
            return ProgramDetailResponse.fromEntity(program, activeCount);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDetailResponse getProgramDetailById(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));
        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(id, ACTIVE_INTERN_STATUSES);
        return ProgramDetailResponse.fromEntity(program, activeCount);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramSummaryResponse getProgramSummaryById(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));
        return ProgramSummaryResponse.fromEntity(program);
    }

    @Override
    @Transactional
    public ProgramDetailResponse changeStatus(Long id, ChangeProgramStatusRequest request) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));

        ProgramStatus current = program.getStatus();
        ProgramStatus target = request.getTargetStatus();

        if (!current.canTransitionTo(target)) {
            throw new BadRequestException("Không thể chuyển trạng thái chương trình từ " + current.getDisplayName() + " sang " + target.getDisplayName());
        }

        if (target == ProgramStatus.CANCELLED) {
            if (request.getCancellationReason() == null || request.getCancellationReason().isBlank()) {
                throw new BadRequestException("Lý do hủy chương trình là bắt buộc");
            }
            program.setCancellationReason(request.getCancellationReason().trim());
            program.setIsRecruitmentOpen(false);

            List<InternProfile> linkedInterns = internProfileRepository.findByProgramId(id);
            for (InternProfile intern : linkedInterns) {
                if (intern.getStatus() == InternStatus.APPROVED || intern.getStatus() == InternStatus.INTERNING) {
                    intern.setNeedsReassignment(true);
                    intern.setReassignmentReason("Chương trình " + program.getProgramCode() + " đã bị hủy: " + request.getCancellationReason().trim());
                    internProfileRepository.save(intern);
                }
            }
        }

        program.setStatus(target);
        InternshipProgram saved = programRepository.save(program);
        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(id, ACTIVE_INTERN_STATUSES);
        return ProgramDetailResponse.fromEntity(saved, activeCount);
    }

    @Override
    @Transactional
    public ProgramDetailResponse toggleRecruitment(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));

        if (program.getStatus() == ProgramStatus.COMPLETED || program.getStatus() == ProgramStatus.CANCELLED) {
            throw new BadRequestException("Không thể thay đổi trạng thái nhận hồ sơ của chương trình đã đóng hoặc đã hủy");
        }

        program.setIsRecruitmentOpen(!Boolean.TRUE.equals(program.getIsRecruitmentOpen()));
        InternshipProgram saved = programRepository.save(program);
        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(id, ACTIVE_INTERN_STATUSES);
        return ProgramDetailResponse.fromEntity(saved, activeCount);
    }

    @Override
    @Transactional
    public void deleteProgram(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));

        if (program.getStatus() != ProgramStatus.PLANNING) {
            throw new BadRequestException("Chỉ có thể xóa chương trình đang ở trạng thái Kế hoạch (PLANNING). Với các chương trình khác, vui lòng chuyển trạng thái sang CANCELLED.");
        }

        long totalLinkedInterns = internProfileRepository.countByProgramId(id);
        if (totalLinkedInterns > 0) {
            throw new BadRequestException("Không thể xóa chương trình đã có " + totalLinkedInterns + " hồ sơ ứng viên gắn kèm. Hãy chuyển trạng thái sang CANCELLED.");
        }

        programRepository.delete(program);
        log.info("Chương trình thực tập ID={} đã được xóa vật lý thành công", id);
    }
}

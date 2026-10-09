package org.example.internservice.program.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.dto.request.ChangeProgramStatusRequest;
import org.example.internservice.program.dto.request.CreateProgramRequest;
import org.example.internservice.program.dto.request.EnrollInternsRequest;
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

import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.event.InternMentorAssignedEvent;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.program.dto.request.AssignMentorToProgramRequest;
import org.example.internservice.program.dto.response.AssignMentorToProgramResponse;
import org.example.internservice.program.entity.ProgramMentor;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.system.audit.entity.AuditAction;
import org.example.internservice.system.audit.entity.AuditModule;
import org.example.internservice.system.audit.entity.AuditStatus;
import org.example.internservice.system.audit.event.AuditLogEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipProgramServiceImpl implements InternshipProgramService {

    private final InternshipProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramCodeSequenceRepository sequenceRepository;
    private final InternProfileRepository internProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final InternMentorAssignmentRepository internMentorAssignmentRepository;
    private final IdentityServiceClient identityServiceClient;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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
            long pendingCount = internProfileRepository.countByProgramIdAndStatusIn(program.getId(), List.of(InternStatus.PENDING));
            return ProgramDetailResponse.fromEntity(program, activeCount, pendingCount);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDetailResponse getProgramDetailById(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));
        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(id, ACTIVE_INTERN_STATUSES);
        long pendingCount = internProfileRepository.countByProgramIdAndStatusIn(id, List.of(InternStatus.PENDING));
        return ProgramDetailResponse.fromEntity(program, activeCount, pendingCount);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramSummaryResponse getProgramSummaryById(Long id) {
        InternshipProgram program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + id));
        long pendingCount = internProfileRepository.countByProgramIdAndStatusIn(id, List.of(InternStatus.PENDING));
        return ProgramSummaryResponse.fromEntity(program, pendingCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramSummaryResponse> getOpenPrograms() {
        log.info("Lấy danh sách các chương trình thực tập đang mở tuyển cho ứng viên");
        List<ProgramStatus> openStatuses = List.of(ProgramStatus.PLANNING, ProgramStatus.OPEN);
        List<InternshipProgram> openPrograms = programRepository.findOpenProgramsWithDepartment(openStatuses);
        return openPrograms.stream()
                .map(program -> {
                    long pendingCount = internProfileRepository.countByProgramIdAndStatusIn(program.getId(), List.of(InternStatus.PENDING));
                    return ProgramSummaryResponse.fromEntity(program, pendingCount);
                })
                .toList();
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

    @Override
    @Transactional(readOnly = true)
    public org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse getCapacityOverview() {
        log.info("Tính toán tổng quan năng lực tiếp nhận các phòng ban (Department Capacity Hub)");
        List<Department> departments = departmentRepository.findByStatus("ACTIVE");
        List<org.example.internservice.intern.entity.MentorProfile> allMentors = mentorProfileRepository.findAllWithDepartment();

        List<org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.DepartmentCapacityItem> deptItems = new java.util.ArrayList<>();

        int totalCompanyQuota = 0;
        int totalCompanyActiveInterns = 0;
        int totalCompanyActiveMentors = allMentors.size();

        for (Department dept : departments) {
            // Danh sách mentor thuộc phòng ban
            List<org.example.internservice.intern.entity.MentorProfile> deptMentors = allMentors.stream()
                    .filter(m -> m.getDepartment() != null && m.getDepartment().getId().equals(dept.getId()))
                    .toList();

            List<org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.MentorRosterItem> roster = new java.util.ArrayList<>();
            for (org.example.internservice.intern.entity.MentorProfile m : deptMentors) {
                long mInterns = internProfileRepository.countByMentorIdAndStatus(m.getId(), InternStatus.INTERNING)
                        + internProfileRepository.countByMentorIdAndStatus(m.getId(), InternStatus.APPROVED);
                String workloadStatus = mInterns >= 5 ? "OVERLOAD" : mInterns >= 3 ? "STANDARD" : "AVAILABLE";
                roster.add(org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.MentorRosterItem.builder()
                        .mentorId(m.getId())
                        .mentorName(m.getFullName())
                        .email(m.getEmail())
                        .avatarUrl(null)
                        .activeInternCount((int) mInterns)
                        .workloadStatus(workloadStatus)
                        .build());
            }

            // Đếm số TTS đang học hoặc được duyệt trong các chương trình của phòng ban này
            List<InternshipProgram> deptPrograms = programRepository.findAll((root, query, cb) ->
                    cb.equal(root.get("department").get("id"), dept.getId()));

            int activeInternsInDept = 0;
            int activeProgramsCount = 0;
            for (InternshipProgram prog : deptPrograms) {
                if (prog.getStatus() == ProgramStatus.ONGOING || prog.getStatus() == ProgramStatus.OPEN) {
                    activeProgramsCount++;
                }
                activeInternsInDept += (int) internProfileRepository.countByProgramIdAndStatusIn(
                        prog.getId(), List.of(InternStatus.APPROVED, InternStatus.INTERNING));
            }

            int quota = dept.getPlannedCapacityQuota() != null ? dept.getPlannedCapacityQuota() : 10;
            double utilizationRate = quota > 0 ? Math.round(((double) activeInternsInDept / quota) * 1000.0) / 10.0 : 0.0;

            totalCompanyQuota += quota;
            totalCompanyActiveInterns += activeInternsInDept;

            deptItems.add(org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.DepartmentCapacityItem.builder()
                    .departmentId(dept.getId())
                    .departmentCode(dept.getCode())
                    .departmentName(dept.getName())
                    .description(dept.getDescription())
                    .leadMentorName(dept.getLeadMentorName())
                    .plannedCapacityQuota(quota)
                    .activeInternCount(activeInternsInDept)
                    .activeMentorCount(deptMentors.size())
                    .utilizationRate(utilizationRate)
                    .qualityScoreAvg(4.8) // Chuẩn hóa baseline benchmark
                    .activeProgramsCount(activeProgramsCount)
                    .mentors(roster)
                    .build());
        }

        double companyOverallUtilization = totalCompanyQuota > 0
                ? Math.round(((double) totalCompanyActiveInterns / totalCompanyQuota) * 1000.0) / 10.0
                : 0.0;

        org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.CompanySummary summary =
                org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.CompanySummary.builder()
                        .totalDepartments(departments.size())
                        .totalActiveInterns(totalCompanyActiveInterns)
                        .totalPlannedQuota(totalCompanyQuota)
                        .totalActiveMentors(totalCompanyActiveMentors)
                        .overallUtilizationRate(companyOverallUtilization)
                        .build();

        return org.example.internservice.program.dto.response.DepartmentCapacityOverviewResponse.builder()
                .companySummary(summary)
                .departments(deptItems)
                .build();
    }

    @Override
    @Transactional
    public void updateDepartmentQuota(Long departmentId, Integer plannedCapacityQuota) {
        if (plannedCapacityQuota == null || plannedCapacityQuota <= 0) {
            throw new BadRequestException("Chỉ tiêu năng lực tiếp nhận (quota) phải lớn hơn 0");
        }
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + departmentId));
        dept.setPlannedCapacityQuota(plannedCapacityQuota);
        departmentRepository.save(dept);
        log.info("Cập nhật chỉ tiêu năng lực phòng ban ID={} lên mức {}", departmentId, plannedCapacityQuota);
    }

    @Override
    @Transactional
    public ProgramDetailResponse enrollInterns(Long programId, org.example.internservice.program.dto.request.EnrollInternsRequest request, String reviewerUsername) {
        log.info("HR {} thực hiện tiếp nhận danh sách TTS vào chương trình ID: {}", reviewerUsername, programId);

        InternshipProgram program = programRepository.findByIdWithLock(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId));

        if (program.getStatus() != ProgramStatus.PLANNING && program.getStatus() != ProgramStatus.OPEN) {
            throw new BadRequestException("Chương trình thực tập không ở trạng thái nhận hồ sơ (" + program.getStatus().getDisplayName() + ")");
        }

        if (!Boolean.TRUE.equals(program.getIsRecruitmentOpen())) {
            throw new BadRequestException("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");
        }

        List<Long> internIds = request.getInternIds();
        if (internIds == null || internIds.isEmpty()) {
            throw new BadRequestException("Danh sách ID thực tập sinh không được để trống");
        }

        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_INTERN_STATUSES);
        if (activeCount + internIds.size() > program.getMaxInterns()) {
            throw new BadRequestException(String.format("Không thể tiếp nhận %d TTS. Chương trình chỉ còn %d chỉ tiêu trống (%d/%d).",
                    internIds.size(), Math.max(0, program.getMaxInterns() - activeCount), activeCount, program.getMaxInterns()));
        }

        List<InternProfile> interns = internProfileRepository.findAllById(internIds);
        if (interns.size() != internIds.size()) {
            throw new ResourceNotFoundException("Một số hồ sơ thực tập sinh không tồn tại trong hệ thống");
        }

        LocalDateTime now = LocalDateTime.now();
        List<ProgramMentor> programMentors = programMentorRepository.findByProgramId(programId);
        MentorProfile defaultMentor = programMentors.isEmpty() ? null : programMentors.get(programMentors.size() - 1).getMentor();
        List<InternMentorAssignment> newAssignments = new ArrayList<>();

        for (InternProfile intern : interns) {
            intern.setProgram(program);
            intern.setStatus(InternStatus.APPROVED);
            intern.setReviewedBy(reviewerUsername);
            intern.setReviewedAt(now);
            intern.setNeedsReassignment(false);
            intern.setReassignmentReason(null);

            if (defaultMentor != null) {
                intern.setMentorId(defaultMentor.getId());
                intern.setMentorName(defaultMentor.getFullName());
                intern.setMentorEmail(defaultMentor.getEmail());
                if (program.getStatus() == ProgramStatus.ONGOING) {
                    intern.setStatus(InternStatus.INTERNING);
                }
                newAssignments.add(InternMentorAssignment.builder()
                        .intern(intern)
                        .mentorId(defaultMentor.getId())
                        .mentorName(defaultMentor.getFullName())
                        .mentorEmail(defaultMentor.getEmail())
                        .assignedBy(reviewerUsername)
                        .assignedAt(now)
                        .status(MentorAssignmentStatus.ACTIVE)
                        .notes("Tự động kế thừa Mentor của chương trình: " + program.getName())
                        .build());
            }
        }

        if (!newAssignments.isEmpty()) {
            internMentorAssignmentRepository.saveAll(newAssignments);
        }
        internProfileRepository.saveAll(interns);

        if (eventPublisher != null) {
            for (InternProfile intern : interns) {
                try {
                    eventPublisher.publishEvent(new org.example.internservice.intern.event.InternDecisionProcessedEvent(this, intern));
                } catch (Exception e) {
                    log.warn("Không thể phát sự kiện duyệt hồ sơ cho TTS ID={}: {}", intern.getId(), e.getMessage());
                }
            }
        }

        long newActiveCount = internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_INTERN_STATUSES);
        long pendingCount = internProfileRepository.countByProgramIdAndStatusIn(programId, List.of(InternStatus.PENDING));
        program.setCurrentInterns((int) newActiveCount);
        InternshipProgram saved = programRepository.save(program);

        return ProgramDetailResponse.fromEntity(saved, newActiveCount, pendingCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<org.example.internservice.intern.dto.response.InternResponse> getProgramInterns(Long programId) {
        if (!programRepository.existsById(programId)) {
            throw new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId);
        }
        return internProfileRepository.findByProgramId(programId).stream()
                .map(org.example.internservice.intern.dto.response.InternResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public AssignMentorToProgramResponse assignMentorToProgram(
            Long programId,
            AssignMentorToProgramRequest request,
            String assignedBy
    ) {
        log.info("HR {} thực hiện phân công Mentor ID={} cho toàn bộ kỳ thực tập ID={}", assignedBy, request.getMentorId(), programId);

        InternshipProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId));

        Long targetMentorId = request.getMentorId();

        // 1. Tìm thông tin Mentor từ MentorProfile hoặc fallback sang Identity Service
        MentorProfile mentorProfile = mentorProfileRepository.findById(targetMentorId)
                .or(() -> mentorProfileRepository.findByUserId(targetMentorId))
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
            List<Map<String, Object>> users = identityServiceClient.getAllUsers();
            Map<String, Object> mentorUser = users.stream()
                    .filter(u -> {
                        Object uid = u.get("id");
                        return uid != null && Long.valueOf(uid.toString()).equals(targetMentorId);
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Mentor với ID: " + targetMentorId));

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

            mentorFullName = mentorUser.get("fullName") != null ? mentorUser.get("fullName").toString() : ("Mentor " + targetMentorId);
            mentorEmail = mentorUser.get("email") != null ? mentorUser.get("email").toString() : ("mentor" + targetMentorId + "@internhub.vn");
            String mentorPhone = mentorUser.get("phone") != null ? mentorUser.get("phone").toString()
                    : (mentorUser.get("phoneNumber") != null ? mentorUser.get("phoneNumber").toString() : ("09" + (System.currentTimeMillis() % 100000000)));

            try {
                Department dept = program.getDepartment() != null
                        ? program.getDepartment()
                        : departmentRepository.findAll().stream().findFirst().orElse(null);

                mentorProfile = MentorProfile.builder()
                        .userId(targetMentorId)
                        .fullName(mentorFullName)
                        .email(mentorEmail)
                        .phone(mentorPhone)
                        .department(dept)
                        .status("ACTIVE")
                        .build();
                mentorProfile = mentorProfileRepository.save(mentorProfile);
                log.info("Tự động tạo mới MentorProfile ID={} cho Mentor userId={}", mentorProfile.getId(), targetMentorId);
            } catch (Exception e) {
                log.warn("Lỗi khi tự tạo MentorProfile: {}", e.getMessage());
                mentorProfile = mentorProfileRepository.findByEmail(mentorEmail).orElse(null);
            }
        }

        if (mentorFullName == null || mentorFullName.isBlank()) {
            mentorFullName = "Mentor " + targetMentorId;
        }
        if (mentorEmail == null || mentorEmail.isBlank()) {
            mentorEmail = "mentor" + targetMentorId + "@internhub.vn";
        }

        // 2. Gán ProgramMentor để đồng bộ quan hệ chương trình và mentor
        if (mentorProfile != null) {
            boolean existsInProgram = programMentorRepository.existsByProgramIdAndMentorIdentifier(program.getId(), mentorProfile.getId())
                    || (mentorProfile.getUserId() != null && programMentorRepository.existsByProgramIdAndMentorIdentifier(program.getId(), mentorProfile.getUserId()));
            if (!existsInProgram) {
                ProgramMentor pm = ProgramMentor.builder()
                        .program(program)
                        .mentor(mentorProfile)
                        .assignedBy(assignedBy)
                        .assignedAt(LocalDateTime.now())
                        .build();
                programMentorRepository.save(pm);
                log.info("Gán Mentor ID={} vào Program ID={}", mentorProfile.getId(), program.getId());
            }
        }

        // 3. Tìm toàn bộ TTS có trạng thái APPROVED hoặc INTERNING thuộc chương trình
        List<InternProfile> interns = internProfileRepository.findByProgramIdAndStatusIn(
                programId,
                List.of(InternStatus.APPROVED, InternStatus.INTERNING)
        );

        int replacedCount = 0;
        List<String> affectedInternCodes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (!interns.isEmpty()) {
            List<Long> internIds = interns.stream().map(InternProfile::getId).toList();
            List<InternMentorAssignment> activeAssignments = internMentorAssignmentRepository.findByInternIdInAndStatus(
                    internIds,
                    MentorAssignmentStatus.ACTIVE
            );
            Map<Long, InternMentorAssignment> activeAssignmentsMap = activeAssignments.stream()
                    .collect(Collectors.toMap(a -> a.getIntern().getId(), a -> a, (first, second) -> first));

            List<InternMentorAssignment> assignmentsToUpdate = new ArrayList<>();
            List<InternMentorAssignment> assignmentsToCreate = new ArrayList<>();

            for (InternProfile intern : interns) {
                affectedInternCodes.add(intern.getInternCode());
                Long oldMentorId = intern.getMentorId();
                String oldMentorName = intern.getMentorName();
                String oldMentorEmail = intern.getMentorEmail();
                boolean isReplacing = oldMentorId != null && !oldMentorId.equals(targetMentorId);

                if (isReplacing) {
                    replacedCount++;
                    InternMentorAssignment oldAssignment = activeAssignmentsMap.get(intern.getId());
                    if (oldAssignment != null) {
                        oldAssignment.setStatus(MentorAssignmentStatus.REPLACED);
                        oldAssignment.setRevokedAt(now);
                        oldAssignment.setRevocationReason("Thay thế người hướng dẫn theo kỳ thực tập (" + program.getName() + ")");
                        assignmentsToUpdate.add(oldAssignment);
                    }
                }

                // Cập nhật thông tin Mentor trên hồ sơ TTS
                intern.setMentorId(mentorProfile != null ? mentorProfile.getId() : targetMentorId);
                intern.setMentorName(mentorFullName);
                intern.setMentorEmail(mentorEmail);
                intern.setNeedsMentorReassignment(false);
                intern.setMentorReassignmentReason(null);

                // Cơ chế điều kiện kép: Chuyển APPROVED sang INTERNING nếu Program đã ONGOING
                if (intern.getStatus() == InternStatus.APPROVED && program.getStatus() == ProgramStatus.ONGOING) {
                    intern.setStatus(InternStatus.INTERNING);
                    log.info("TTS {} ({}) chuyển APPROVED -> INTERNING theo kỳ {}", intern.getFullName(), intern.getInternCode(), program.getName());
                }

                // Tạo mới bản ghi phân công ACTIVE
                InternMentorAssignment newAssignment = InternMentorAssignment.builder()
                        .intern(intern)
                        .mentorId(mentorProfile != null ? mentorProfile.getId() : targetMentorId)
                        .mentorName(mentorFullName)
                        .mentorEmail(mentorEmail)
                        .assignedBy(assignedBy)
                        .assignedAt(now)
                        .status(MentorAssignmentStatus.ACTIVE)
                        .notes(request.getNotes() != null && !request.getNotes().isBlank()
                                ? request.getNotes()
                                : ("Gán Mentor theo kỳ thực tập: " + program.getName()))
                        .build();
                assignmentsToCreate.add(newAssignment);

                // Gửi event email 3 chiều
                if (eventPublisher != null) {
                    try {
                        eventPublisher.publishEvent(new InternMentorAssignedEvent(
                                this,
                                intern.getId(),
                                intern.getInternCode(),
                                intern.getFullName(),
                                intern.getEmail(),
                                program.getName(),
                                intern.getAppliedPosition(),
                                isReplacing ? "REPLACED" : "ASSIGNED",
                                mentorProfile != null ? mentorProfile.getId() : targetMentorId,
                                mentorFullName,
                                mentorEmail,
                                oldMentorId,
                                oldMentorName,
                                oldMentorEmail,
                                "Gán Mentor cho toàn bộ kỳ thực tập",
                                request.getNotes(),
                                assignedBy
                        ));
                    } catch (Exception e) {
                        log.warn("Không thể phát sự kiện phân công mentor cho TTS {}: {}", intern.getId(), e.getMessage());
                    }
                }
            }

            if (!assignmentsToUpdate.isEmpty()) {
                internMentorAssignmentRepository.saveAll(assignmentsToUpdate);
            }
            if (!assignmentsToCreate.isEmpty()) {
                internMentorAssignmentRepository.saveAll(assignmentsToCreate);
            }
            internProfileRepository.saveAll(interns);
        }

        // Bắn AuditLogEvent
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(AuditLogEvent.builder()
                        .username(assignedBy)
                        .action(AuditAction.ASSIGN_MENTOR_TO_PROGRAM)
                        .module(AuditModule.INTERN)
                        .description(String.format("Phân công Mentor %s cho kỳ %s (%d TTS, %d thay thế)",
                                mentorFullName, program.getName(), interns.size(), replacedCount))
                        .status(AuditStatus.SUCCESS)
                        .build());
            } catch (Exception e) {
                log.warn("Không thể ghi log audit phân công mentor cho kỳ: {}", e.getMessage());
            }
        }

        return AssignMentorToProgramResponse.builder()
                .programId(program.getId())
                .programName(program.getName())
                .mentorId(mentorProfile != null ? mentorProfile.getId() : targetMentorId)
                .mentorName(mentorFullName)
                .mentorEmail(mentorEmail)
                .totalAssignedInterns(interns.size())
                .replacedMentorsCount(replacedCount)
                .affectedInternCodes(affectedInternCodes)
                .assignedAt(now)
                .build();
    }
}

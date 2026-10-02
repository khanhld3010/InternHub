package org.example.internservice.mission.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.dto.response.MentorOptionResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.mission.dto.request.CreateMissionBoardRequest;
import org.example.internservice.mission.dto.request.UpdateMissionBoardRequest;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.MentorProgramResponse;
import org.example.internservice.mission.dto.response.MissionBoardDetailResponse;
import org.example.internservice.mission.dto.response.MissionBoardResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.MissionBoardService;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramMentor;
import org.example.internservice.program.repository.DepartmentRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MissionBoardServiceImpl implements MissionBoardService {

    private final MissionBoardRepository missionBoardRepository;
    private final MissionItemRepository missionItemRepository;
    private final InternshipProgramRepository programRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final InternProfileRepository internProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final IdentityServiceClient identityServiceClient;

    @Override
    @Transactional
    public MissionBoardResponse createBoard(Long programId, CreateMissionBoardRequest request, CustomUserDetails userDetails) {
        log.info("Mentor {} tạo MissionBoard cho programId={}", userDetails.getUsername(), programId);
        InternshipProgram program = findProgramOrThrow(programId);
        MentorProfile mentor = resolveMentorProfile(userDetails);
        verifyMentorAccessToProgram(programId, mentor.getId(), userDetails);

        MissionBoard board = MissionBoard.builder()
                .program(program)
                .mentor(mentor)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(BoardStatus.ACTIVE)
                .build();

        MissionBoard saved = missionBoardRepository.save(board);
        return mapToBoardResponse(saved, 0, 0, 0, 0);
    }

    @Override
    public List<MissionBoardResponse> getBoardsByProgram(Long programId, CustomUserDetails userDetails) {
        findProgramOrThrow(programId);
        verifyMentorAccessToProgram(programId, resolveMentorIdNullable(userDetails), userDetails);

        List<MissionBoard> boards = missionBoardRepository.findByProgramIdWithDetails(programId);
        return boards.stream().map(b -> {
            int todo = (int) missionItemRepository.countByBoardIdAndStatus(b.getId(), MissionItemStatus.TODO);
            int inProgress = (int) missionItemRepository.countByBoardIdAndStatus(b.getId(), MissionItemStatus.IN_PROGRESS);
            int completed = (int) missionItemRepository.countByBoardIdAndStatus(b.getId(), MissionItemStatus.COMPLETED);
            return mapToBoardResponse(b, todo + inProgress + completed, todo, inProgress, completed);
        }).toList();
    }

    @Override
    public MissionBoardDetailResponse getBoardDetail(Long boardId, CustomUserDetails userDetails) {
        MissionBoard board = missionBoardRepository.findByIdWithDetails(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng nhiệm vụ với id: " + boardId));
        verifyMentorAccessToProgram(board.getProgram().getId(), resolveMentorIdNullable(userDetails), userDetails);

        List<MissionItem> items = missionItemRepository.findByBoardIdWithAssignees(boardId);
        List<MissionItemResponse> todoList = new ArrayList<>();
        List<MissionItemResponse> inProgressList = new ArrayList<>();
        List<MissionItemResponse> completedList = new ArrayList<>();

        for (MissionItem item : items) {
            MissionItemResponse itemResp = mapToItemResponse(item);
            if (item.getStatus() == MissionItemStatus.TODO) {
                todoList.add(itemResp);
            } else if (item.getStatus() == MissionItemStatus.IN_PROGRESS) {
                inProgressList.add(itemResp);
            } else if (item.getStatus() == MissionItemStatus.COMPLETED) {
                completedList.add(itemResp);
            }
        }

        return MissionBoardDetailResponse.builder()
                .id(board.getId())
                .programId(board.getProgram().getId())
                .programName(board.getProgram().getName())
                .mentorId(board.getMentor().getId())
                .mentorName(board.getMentor().getFullName())
                .title(board.getTitle())
                .description(board.getDescription())
                .status(board.getStatus())
                .statusDisplayName(board.getStatus().getDisplayName())
                .totalItems(items.size())
                .todoCount(todoList.size())
                .inProgressCount(inProgressList.size())
                .completedCount(completedList.size())
                .todoItems(todoList)
                .inProgressItems(inProgressList)
                .completedItems(completedList)
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public MissionBoardResponse updateBoard(Long boardId, UpdateMissionBoardRequest request, CustomUserDetails userDetails) {
        MissionBoard board = missionBoardRepository.findByIdWithDetails(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng nhiệm vụ với id: " + boardId));
        verifyMentorAccessToProgram(board.getProgram().getId(), resolveMentorIdNullable(userDetails), userDetails);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            board.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            board.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            board.setStatus(request.getStatus());
        }

        MissionBoard updated = missionBoardRepository.save(board);
        int total = (int) missionItemRepository.countByBoardId(boardId);
        int todo = (int) missionItemRepository.countByBoardIdAndStatus(boardId, MissionItemStatus.TODO);
        int inProgress = (int) missionItemRepository.countByBoardIdAndStatus(boardId, MissionItemStatus.IN_PROGRESS);
        int completed = (int) missionItemRepository.countByBoardIdAndStatus(boardId, MissionItemStatus.COMPLETED);
        return mapToBoardResponse(updated, total, todo, inProgress, completed);
    }

    @Override
    @Transactional
    public void deleteBoard(Long boardId, CustomUserDetails userDetails) {
        MissionBoard board = missionBoardRepository.findByIdWithDetails(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng nhiệm vụ với id: " + boardId));
        verifyMentorAccessToProgram(board.getProgram().getId(), resolveMentorIdNullable(userDetails), userDetails);
        missionBoardRepository.delete(board);
        log.info("Xóa MissionBoard id={} bởi user={}", boardId, userDetails.getUsername());
    }

    @Override
    public List<MentorProgramResponse> getMyMentoredPrograms(CustomUserDetails userDetails) {
        Long identifier = resolveMentorIdNullable(userDetails);
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        if (identifier == null && userId == null) {
            return List.of();
        }

        Map<Long, InternshipProgram> programMap = new LinkedHashMap<>();

        // 1. Tìm từ bảng phân công program_mentors
        if (identifier != null) {
            List<ProgramMentor> programMentors = programMentorRepository.findByMentorIdentifierWithProgram(identifier);
            for (ProgramMentor pm : programMentors) {
                if (pm.getProgram() != null) {
                    programMap.put(pm.getProgram().getId(), pm.getProgram());
                }
            }
        }
        if (userId != null && !userId.equals(identifier)) {
            List<ProgramMentor> programMentors = programMentorRepository.findByMentorIdentifierWithProgram(userId);
            for (ProgramMentor pm : programMentors) {
                if (pm.getProgram() != null) {
                    programMap.put(pm.getProgram().getId(), pm.getProgram());
                }
            }
        }

        // 2. Smart fallback: Tìm từ các InternProfile được gán trực tiếp cho Mentor này
        List<InternProfile> assignedInterns = new ArrayList<>();
        if (identifier != null) {
            assignedInterns.addAll(internProfileRepository.findByMentorId(identifier));
        }
        if (userId != null && !userId.equals(identifier)) {
            assignedInterns.addAll(internProfileRepository.findByMentorId(userId));
        }
        for (InternProfile intern : assignedInterns) {
            if (intern.getProgram() != null && !programMap.containsKey(intern.getProgram().getId())) {
                programMap.put(intern.getProgram().getId(), intern.getProgram());
            }
        }

        return programMap.values().stream().map(p -> {
            int totalInterns = internProfileRepository.findByProgramId(p.getId()).size();
            int activeInterns = (int) internProfileRepository.countByProgramIdAndStatusIn(p.getId(), List.of(InternStatus.INTERNING));
            return MentorProgramResponse.builder()
                    .programId(p.getId())
                    .programCode(p.getProgramCode())
                    .name(p.getName())
                    .departmentId(p.getDepartment() != null ? p.getDepartment().getId() : null)
                    .departmentName(p.getDepartment() != null ? p.getDepartment().getName() : null)
                    .status(p.getStatus())
                    .startDate(p.getStartDate())
                    .endDate(p.getEndDate())
                    .totalInterns(totalInterns)
                    .activeInterns(activeInterns)
                    .build();
        }).toList();
    }

    @Override
    public List<AssigneeResponse> getProgramInterns(Long programId, CustomUserDetails userDetails) {
        findProgramOrThrow(programId);
        verifyMentorAccessToProgram(programId, resolveMentorIdNullable(userDetails), userDetails);

        List<InternProfile> interns = internProfileRepository.findByProgramId(programId);
        return interns.stream()
                .filter(i -> i.getStatus() == InternStatus.INTERNING || i.getStatus() == InternStatus.APPROVED)
                .map(this::mapToAssigneeResponse)
                .toList();
    }

    @Override
    @Transactional
    public void addMentorToProgram(Long programId, Long mentorId, String assignedBy) {
        InternshipProgram program = findProgramOrThrow(programId);

        MentorProfile mentor = mentorProfileRepository.findById(mentorId)
                .or(() -> mentorProfileRepository.findByUserId(mentorId))
                .orElse(null);

        if (mentor == null) {
            List<Map<String, Object>> users = identityServiceClient.getAllUsers();
            Map<String, Object> mentorUser = users.stream()
                    .filter(u -> {
                        Object uid = u.get("id");
                        return uid != null && Long.valueOf(uid.toString()).equals(mentorId);
                    })
                    .findFirst()
                    .orElse(null);

            if (mentorUser != null) {
                String fullName = mentorUser.get("fullName") != null ? mentorUser.get("fullName").toString() : ("Mentor " + mentorId);
                String email = mentorUser.get("email") != null ? mentorUser.get("email").toString() : ("mentor" + mentorId + "@internhub.vn");
                String phone = mentorUser.get("phone") != null ? mentorUser.get("phone").toString()
                        : (mentorUser.get("phoneNumber") != null ? mentorUser.get("phoneNumber").toString() : ("09" + (System.currentTimeMillis() % 100000000)));
                Department dept = program.getDepartment() != null ? program.getDepartment()
                        : departmentRepository.findAll().stream().findFirst().orElse(null);

                mentor = MentorProfile.builder()
                        .userId(mentorId)
                        .fullName(fullName)
                        .email(email)
                        .phone(phone)
                        .department(dept)
                        .status("ACTIVE")
                        .build();
                try {
                    mentor = mentorProfileRepository.save(mentor);
                } catch (Exception e) {
                    mentor = mentorProfileRepository.findByEmail(email).orElse(null);
                }
            }
        }

        if (mentor == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin Mentor với ID: " + mentorId);
        }

        boolean exists = programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentor.getId())
                || programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorId);
        if (!exists) {
            ProgramMentor pm = ProgramMentor.builder()
                    .program(program)
                    .mentor(mentor)
                    .assignedBy(assignedBy != null ? assignedBy : "HR")
                    .assignedAt(LocalDateTime.now())
                    .build();
            programMentorRepository.save(pm);
            log.info("HR {} đã thêm Mentor {} (ID={}) vào Program ID={}", assignedBy, mentor.getFullName(), mentor.getId(), programId);
        }
    }

    @Override
    @Transactional
    public void removeMentorFromProgram(Long programId, Long mentorId) {
        findProgramOrThrow(programId);
        List<ProgramMentor> pms = programMentorRepository.findByProgramId(programId);
        for (ProgramMentor pm : pms) {
            if ((pm.getMentor() != null && pm.getMentor().getId().equals(mentorId))
                    || (pm.getMentor() != null && pm.getMentor().getUserId() != null && pm.getMentor().getUserId().equals(mentorId))) {
                programMentorRepository.delete(pm);
                log.info("Đã gỡ Mentor ID={} khỏi Program ID={}", mentorId, programId);
                return;
            }
        }
    }

    @Override
    public List<MentorOptionResponse> getMentorsByProgram(Long programId) {
        findProgramOrThrow(programId);
        List<ProgramMentor> pms = programMentorRepository.findByProgramId(programId);
        return pms.stream().map(pm -> {
            MentorProfile m = pm.getMentor();
            return MentorOptionResponse.builder()
                    .id(m.getId())
                    .fullName(m.getFullName())
                    .email(m.getEmail())
                    .phone(m.getPhone())
                    .departmentId(m.getDepartment() != null ? m.getDepartment().getId() : null)
                    .departmentName(m.getDepartment() != null ? m.getDepartment().getName() : null)
                    .departmentCode(m.getDepartment() != null ? m.getDepartment().getCode() : null)
                    .status(m.getStatus())
                    .build();
        }).toList();
    }

    private InternshipProgram findProgramOrThrow(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với id: " + programId));
    }

    private MentorProfile resolveMentorProfile(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getUserId() != null) {
            Optional<MentorProfile> byUserId = mentorProfileRepository.findByUserId(userDetails.getUserId());
            if (byUserId.isPresent()) {
                return byUserId.get();
            }
        }
        if (userDetails != null && userDetails.getUsername() != null) {
            Optional<MentorProfile> byEmail = mentorProfileRepository.findByEmail(userDetails.getUsername());
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        List<MentorProfile> allMentors = mentorProfileRepository.findAll();
        if (!allMentors.isEmpty()) {
            return allMentors.get(0);
        }
        Department defaultDept = departmentRepository.findAll().stream().findFirst().orElse(null);
        MentorProfile created = MentorProfile.builder()
                .userId(userDetails != null ? userDetails.getUserId() : 1L)
                .fullName(userDetails != null ? userDetails.getUsername() : "Mentor User")
                .email(userDetails != null ? (userDetails.getUsername() + "@internhub.vn") : "mentor@internhub.vn")
                .phone("09" + (System.currentTimeMillis() % 100000000))
                .department(defaultDept)
                .status("ACTIVE")
                .build();
        return mentorProfileRepository.save(created);
    }

    private Long resolveMentorIdNullable(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            return null;
        }
        return mentorProfileRepository.findByUserId(userDetails.getUserId())
                .map(MentorProfile::getId)
                .orElse(userDetails.getUserId());
    }

    private void verifyMentorAccessToProgram(Long programId, Long mentorId, CustomUserDetails userDetails) {
        boolean isHrOrAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isHrOrAdmin) {
            return;
        }
        if (mentorId != null) {
            boolean existsInProgramMentor = programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorId);
            if (existsInProgramMentor) {
                return;
            }
            Long userId = userDetails != null ? userDetails.getUserId() : null;
            if (userId != null && programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, userId)) {
                return;
            }
            // Smart Fallback: Cho phép nếu có bất kỳ InternProfile nào trong Program này được phân công cho Mentor
            boolean hasInternInProgram = internProfileRepository.findByProgramId(programId).stream()
                    .anyMatch(i -> (i.getMentorId() != null && (i.getMentorId().equals(mentorId) || (userId != null && i.getMentorId().equals(userId)))));
            if (hasInternInProgram) {
                return;
            }
            throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
        }
    }

    private MissionBoardResponse mapToBoardResponse(MissionBoard b, int total, int todo, int inProgress, int completed) {
        return MissionBoardResponse.builder()
                .id(b.getId())
                .programId(b.getProgram().getId())
                .programName(b.getProgram().getName())
                .mentorId(b.getMentor().getId())
                .mentorName(b.getMentor().getFullName())
                .title(b.getTitle())
                .description(b.getDescription())
                .status(b.getStatus())
                .statusDisplayName(b.getStatus().getDisplayName())
                .totalItems(total)
                .todoCount(todo)
                .inProgressCount(inProgress)
                .completedCount(completed)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private MissionItemResponse mapToItemResponse(MissionItem item) {
        boolean isOverdue = item.getStatus() != MissionItemStatus.COMPLETED
                && item.getDueDate() != null
                && item.getDueDate().isBefore(LocalDate.now());

        List<AssigneeResponse> assignees = (item.getAssignees() == null) ? List.of() :
                item.getAssignees().stream().map(this::mapToAssigneeResponse).toList();

        return MissionItemResponse.builder()
                .id(item.getId())
                .boardId(item.getBoard().getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .priority(item.getPriority())
                .priorityDisplayName(item.getPriority().getDisplayName())
                .status(item.getStatus())
                .statusDisplayName(item.getStatus().getDisplayName())
                .dueDate(item.getDueDate())
                .isOverdue(isOverdue)
                .orderIndex(item.getOrderIndex())
                .assignees(assignees)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private AssigneeResponse mapToAssigneeResponse(InternProfile intern) {
        return AssigneeResponse.builder()
                .id(intern.getId())
                .userId(intern.getUserId())
                .internCode(intern.getInternCode())
                .fullName(intern.getFullName())
                .email(intern.getEmail())
                .phone(intern.getPhone())
                .appliedPosition(intern.getAppliedPosition())
                .build();
    }
}

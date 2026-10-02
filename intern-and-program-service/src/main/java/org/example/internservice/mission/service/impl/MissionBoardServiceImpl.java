package org.example.internservice.mission.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
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
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramMentor;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        if (identifier == null) {
            return List.of();
        }
        List<ProgramMentor> programMentors = programMentorRepository.findByMentorIdentifierWithProgram(identifier);
        return programMentors.stream().map(pm -> {
            InternshipProgram p = pm.getProgram();
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

    private InternshipProgram findProgramOrThrow(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với id: " + programId));
    }

    private MentorProfile resolveMentorProfile(CustomUserDetails userDetails) {
        return mentorProfileRepository.findByUserId(userDetails.getUserId())
                .orElseGet(() -> mentorProfileRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new BadRequestException("Không tìm thấy hồ sơ Mentor tương ứng với tài khoản đăng nhập")));
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
        if (mentorId != null && !programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorId)) {
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

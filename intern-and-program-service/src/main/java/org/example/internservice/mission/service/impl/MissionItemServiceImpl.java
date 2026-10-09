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
import org.example.internservice.mission.dto.request.CreateMissionItemRequest;
import org.example.internservice.mission.dto.request.UpdateItemStatusRequest;
import org.example.internservice.mission.dto.request.UpdateMissionItemRequest;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.MissionItemService;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MissionItemServiceImpl implements MissionItemService {

    private static final String NOT_FOUND_MSG_PREFIX = "Không tìm thấy mục công việc với id: ";

    private final MissionItemRepository missionItemRepository;
    private final MissionBoardRepository missionBoardRepository;
    private final InternProfileRepository internProfileRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Override
    @Transactional
    public MissionItemResponse createItem(Long boardId, CreateMissionItemRequest request, CustomUserDetails userDetails) {
        MissionBoard board = missionBoardRepository.findByIdWithDetails(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng nhiệm vụ với id: " + boardId));
        verifyMentorAccess(board.getProgram().getId(), userDetails);
        validateDueDate(request.getDueDate(), board);

        Set<InternProfile> assignees = resolveAndValidateAssignees(request.getInternIds(), board.getProgram().getId());

        MissionItem item = MissionItem.builder()
                .board(board)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : MissionPriority.MEDIUM)
                .status(MissionItemStatus.TODO)
                .dueDate(request.getDueDate())
                .assignees(assignees)
                .build();

        MissionItem saved = missionItemRepository.save(item);
        log.info("Tạo MissionItem id={} trong boardId={} cho {} TTS", saved.getId(), boardId, assignees.size());
        return mapToItemResponse(saved);
    }

    @Override
    @Transactional
    public MissionItemResponse updateItem(Long itemId, UpdateMissionItemRequest request, CustomUserDetails userDetails) {
        MissionItem item = missionItemRepository.findByIdWithAssignees(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MSG_PREFIX + itemId));
        verifyMentorAccess(item.getBoard().getProgram().getId(), userDetails);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            item.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            item.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            validateDueDate(request.getDueDate(), item.getBoard());
            item.setDueDate(request.getDueDate());
        }
        if (request.getInternIds() != null) {
            if (request.getInternIds().isEmpty()) {
                throw new BadRequestException("Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc");
            }
            Set<InternProfile> assignees = resolveAndValidateAssignees(request.getInternIds(), item.getBoard().getProgram().getId());
            item.setAssignees(assignees);
        }

        MissionItem updated = missionItemRepository.save(item);
        return mapToItemResponse(updated);
    }

    @Override
    @Transactional
    public MissionItemResponse updateItemStatus(Long itemId, UpdateItemStatusRequest request, CustomUserDetails userDetails) {
        MissionItem item = missionItemRepository.findByIdWithAssignees(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MSG_PREFIX + itemId));
        verifyMentorAccess(item.getBoard().getProgram().getId(), userDetails);

        if (request.getStatus() == null) {
            throw new BadRequestException("Trạng thái công việc không hợp lệ");
        }

        item.setStatus(request.getStatus());
        MissionItem updated = missionItemRepository.save(item);
        log.info("Cập nhật trạng thái MissionItem id={} sang {}", itemId, request.getStatus());
        return mapToItemResponse(updated);
    }

    @Override
    @Transactional
    public void deleteItem(Long itemId, CustomUserDetails userDetails) {
        MissionItem item = missionItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MSG_PREFIX + itemId));
        verifyMentorAccess(item.getBoard().getProgram().getId(), userDetails);

        missionItemRepository.delete(item);
        log.info("Xóa MissionItem id={} bởi user={}", itemId, userDetails.getUsername());
    }

    private void validateDueDate(LocalDate dueDate, MissionBoard board) {
        if (dueDate == null) {
            return;
        }
        if (dueDate.isBefore(LocalDate.now(ZoneId.systemDefault()))) {
            throw new BadRequestException("Hạn chót công việc không được ở trong quá khứ");
        }
        if (board.getProgram().getEndDate() != null && dueDate.isAfter(board.getProgram().getEndDate())) {
            throw new BadRequestException("Hạn chót công việc không được vượt quá ngày kết thúc chương trình (" + board.getProgram().getEndDate() + ")");
        }
    }

    private Set<InternProfile> resolveAndValidateAssignees(Set<Long> internIds, Long programId) {
        if (internIds == null || internIds.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc");
        }
        List<InternProfile> interns = internProfileRepository.findAllById(internIds);
        if (interns.size() != internIds.size()) {
            throw new BadRequestException("Một số thực tập sinh không tồn tại trong hệ thống");
        }

        for (InternProfile intern : interns) {
            if (intern.getProgram() == null || !intern.getProgram().getId().equals(programId)) {
                throw new BadRequestException("Thực tập sinh [" + intern.getFullName() + " - " + intern.getInternCode() + "] không thuộc chương trình đào tạo của bảng nhiệm vụ này");
            }
            if (intern.getStatus() != null && intern.getStatus() != InternStatus.INTERNING && intern.getStatus() != InternStatus.APPROVED) {
                throw new BadRequestException("Thực tập sinh [" + intern.getFullName() + " - " + intern.getInternCode() + "] đang ở trạng thái " + intern.getStatus() + ", không thể giao việc");
            }
        }
        return new HashSet<>(interns);
    }

    private void verifyMentorAccess(Long programId, CustomUserDetails userDetails) {
        boolean isHrOrAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isHrOrAdmin) {
            return;
        }

        Long mentorIdentifier = (userDetails != null && userDetails.getUserId() != null)
                ? mentorProfileRepository.findByUserId(userDetails.getUserId()).map(MentorProfile::getId).orElse(userDetails.getUserId())
                : null;
        Long userId = userDetails != null ? userDetails.getUserId() : null;

        if (mentorIdentifier != null) {
            if (programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorIdentifier)) {
                return;
            }
            if (userId != null && programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, userId)) {
                return;
            }
            // Smart Fallback: Cho phép nếu có bất kỳ InternProfile nào trong Program này được phân công cho Mentor
            boolean hasInternInProgram = internProfileRepository.findByProgramId(programId).stream()
                    .anyMatch(i -> (i.getMentorId() != null && (i.getMentorId().equals(mentorIdentifier) || (userId != null && i.getMentorId().equals(userId)))));
            if (hasInternInProgram) {
                return;
            }
            throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
        }
    }

    private MissionItemResponse mapToItemResponse(MissionItem item) {
        boolean isOverdue = item.getStatus() != MissionItemStatus.COMPLETED
                && item.getDueDate() != null
                && item.getDueDate().isBefore(LocalDate.now(ZoneId.systemDefault()));

        List<AssigneeResponse> assignees = (item.getAssignees() == null) ? List.of() :
                item.getAssignees().stream().map(intern -> AssigneeResponse.builder()
                        .id(intern.getId())
                        .userId(intern.getUserId())
                        .internCode(intern.getInternCode())
                        .fullName(intern.getFullName())
                        .email(intern.getEmail())
                        .phone(intern.getPhone())
                        .appliedPosition(intern.getAppliedPosition())
                        .build()).toList();

        return MissionItemResponse.builder()
                .id(item.getId())
                .boardId(item.getBoard() != null ? item.getBoard().getId() : null)
                .boardTitle(item.getBoard() != null ? item.getBoard().getTitle() : null)
                .title(item.getTitle())
                .description(item.getDescription())
                .priority(item.getPriority())
                .priorityDisplayName(item.getPriority().getDisplayName())
                .status(item.getStatus())
                .statusDisplayName(item.getStatus().getDisplayName())
                .dueDate(item.getDueDate())
                .isOverdue(isOverdue)
                .orderIndex(item.getOrderIndex())
                .submissionUrl(item.getSubmissionUrl())
                .completionNote(item.getCompletionNote())
                .submittedAt(item.getSubmittedAt())
                .assignees(assignees)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}

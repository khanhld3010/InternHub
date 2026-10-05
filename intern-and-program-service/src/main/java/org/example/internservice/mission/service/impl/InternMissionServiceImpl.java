package org.example.internservice.mission.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.mission.dto.request.UpdateKanbanStatusRequest;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.InternKanbanBoardResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.InternMissionService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InternMissionServiceImpl implements InternMissionService {

    private final MissionItemRepository missionItemRepository;
    private final InternProfileRepository internProfileRepository;
    private final NotificationEventDispatcher notificationEventDispatcher;

    @Override
    public PageResponse<MissionItemResponse> getMyMissions(
            CustomUserDetails userDetails,
            MissionItemStatus status,
            Long boardId,
            MissionPriority priority,
            String keyword,
            Pageable pageable
    ) {
        InternProfile currentIntern = resolveCurrentIntern(userDetails);

        Specification<MissionItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<MissionItem, InternProfile> assigneesJoin = root.join("assignees", JoinType.INNER);
            predicates.add(cb.equal(assigneesJoin.get("id"), currentIntern.getId()));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (boardId != null) {
                predicates.add(cb.equal(root.get("board").get("id"), boardId));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }

            if (query != null && Long.class != query.getResultType()) {
                root.fetch("board", JoinType.LEFT);
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MissionItem> page = missionItemRepository.findAll(spec, pageable);
        return PageResponse.from(page, this::mapToItemResponse);
    }

    @Override
    public InternKanbanBoardResponse getMyKanbanBoard(CustomUserDetails userDetails) {
        InternProfile currentIntern = resolveCurrentIntern(userDetails);
        List<MissionItem> items = missionItemRepository.findAssignedItemsByInternIdWithDetails(currentIntern.getId());

        List<MissionItemResponse> todoItems = new ArrayList<>();
        List<MissionItemResponse> inProgressItems = new ArrayList<>();
        List<MissionItemResponse> completedItems = new ArrayList<>();

        for (MissionItem item : items) {
            MissionItemResponse dto = mapToItemResponse(item);
            if (item.getStatus() == MissionItemStatus.TODO) {
                todoItems.add(dto);
            } else if (item.getStatus() == MissionItemStatus.IN_PROGRESS) {
                inProgressItems.add(dto);
            } else if (item.getStatus() == MissionItemStatus.COMPLETED) {
                completedItems.add(dto);
            }
        }

        return InternKanbanBoardResponse.builder()
                .todoItems(todoItems)
                .inProgressItems(inProgressItems)
                .completedItems(completedItems)
                .totalCount(items.size())
                .todoCount(todoItems.size())
                .inProgressCount(inProgressItems.size())
                .completedCount(completedItems.size())
                .build();
    }

    @Override
    public MissionItemResponse getMissionDetail(Long itemId, CustomUserDetails userDetails) {
        MissionItem item = missionItemRepository.findByIdWithBoardAndAssignees(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục công việc với id: " + itemId));

        InternProfile currentIntern = resolveCurrentIntern(userDetails);
        verifyAssigneeOwnership(item, currentIntern, userDetails);

        return mapToItemResponse(item);
    }

    @Override
    @Transactional
    public MissionItemResponse updateKanbanStatus(Long itemId, UpdateKanbanStatusRequest request, CustomUserDetails userDetails) {
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Trạng thái công việc không được để trống");
        }

        MissionItem item = missionItemRepository.findByIdWithBoardAndAssignees(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục công việc với id: " + itemId));

        InternProfile currentIntern = resolveCurrentIntern(userDetails);
        verifyInternStatus(currentIntern);
        verifyAssigneeOwnership(item, currentIntern, userDetails);

        MissionItemStatus oldStatus = item.getStatus();
        MissionItemStatus newStatus = request.getStatus();

        item.setStatus(newStatus);
        if (newStatus == MissionItemStatus.COMPLETED) {
            if (request.getSubmissionUrl() != null && !request.getSubmissionUrl().isBlank()) {
                item.setSubmissionUrl(request.getSubmissionUrl().trim());
            }
            if (request.getCompletionNote() != null) {
                item.setCompletionNote(request.getCompletionNote().trim());
            }
            item.setSubmittedAt(LocalDateTime.now(ZoneId.systemDefault()));
        } else {
            if (request.getSubmissionUrl() != null) {
                item.setSubmissionUrl(request.getSubmissionUrl().trim());
            }
            if (request.getCompletionNote() != null) {
                item.setCompletionNote(request.getCompletionNote().trim());
            }
        }

        MissionItem updated = missionItemRepository.save(item);
        log.info("Intern id={} chuyển trạng thái MissionItem id={} từ {} sang {}",
                currentIntern.getId(), itemId, oldStatus, newStatus);

        dispatchMentorNotification(updated, currentIntern, oldStatus, newStatus, userDetails);

        return mapToItemResponse(updated);
    }

    private InternProfile resolveCurrentIntern(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new AccessDeniedException("Yêu cầu thông tin đăng nhập hợp lệ");
        }
        return internProfileRepository.findByUserId(userDetails.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh cho tài khoản hiện tại"));
    }

    private void verifyInternStatus(InternProfile intern) {
        if (intern.getStatus() != InternStatus.INTERNING && intern.getStatus() != InternStatus.APPROVED) {
            throw new BadRequestException("Hồ sơ thực tập sinh đang ở trạng thái [" + intern.getStatus() + "], không thể thực hiện công việc");
        }
    }

    private void verifyAssigneeOwnership(MissionItem item, InternProfile currentIntern, CustomUserDetails userDetails) {
        boolean isHrOrAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isHrOrAdmin) {
            return;
        }

        boolean isAssignee = item.getAssignees() != null && item.getAssignees().stream()
                .anyMatch(a -> a.getId().equals(currentIntern.getId()));

        if (!isAssignee) {
            throw new AccessDeniedException("Bạn không được phân công thực hiện nhiệm vụ này, không có quyền chuyển trạng thái");
        }
    }

    private void dispatchMentorNotification(
            MissionItem item,
            InternProfile intern,
            MissionItemStatus oldStatus,
            MissionItemStatus newStatus,
            CustomUserDetails userDetails
    ) {
        if (item.getBoard() == null || item.getBoard().getMentor() == null) {
            return;
        }
        Long mentorUserId = item.getBoard().getMentor().getUserId();
        if (mentorUserId == null) {
            return;
        }

        String title;
        String content;
        String type;

        if (newStatus == MissionItemStatus.COMPLETED) {
            title = "Thực tập sinh đã hoàn thành nhiệm vụ";
            content = "Thực tập sinh " + intern.getFullName() + " đã hoàn thành nhiệm vụ: \"" + item.getTitle() + "\". Vui lòng nghiệm thu.";
            type = "TASK_COMPLETED";
        } else if (newStatus == MissionItemStatus.IN_PROGRESS && oldStatus == MissionItemStatus.TODO) {
            title = "Thực tập sinh bắt đầu thực hiện nhiệm vụ";
            content = "Thực tập sinh " + intern.getFullName() + " đã bắt đầu thực hiện nhiệm vụ: \"" + item.getTitle() + "\".";
            type = "TASK_IN_PROGRESS";
        } else {
            title = "Cập nhật trạng thái nhiệm vụ";
            content = "Thực tập sinh " + intern.getFullName() + " đã chuyển nhiệm vụ \"" + item.getTitle() + "\" sang trạng thái: " + newStatus.getDisplayName() + ".";
            type = "TASK_UPDATED";
        }

        try {
            notificationEventDispatcher.dispatch(CreateNotificationInternalRequest.builder()
                    .recipientId(mentorUserId)
                    .actorId(userDetails != null ? userDetails.getUserId() : null)
                    .title(title)
                    .content(content)
                    .type(type)
                    .referenceType("MISSION_ITEM")
                    .referenceId(String.valueOf(item.getId()))
                    .actionUrl("/mentor/mission-boards/" + item.getBoard().getId())
                    .build());
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo cho Mentor userId={}: {}", mentorUserId, e.getMessage());
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
                .priorityDisplayName(item.getPriority() != null ? item.getPriority().getDisplayName() : null)
                .status(item.getStatus())
                .statusDisplayName(item.getStatus() != null ? item.getStatus().getDisplayName() : null)
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

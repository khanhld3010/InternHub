package org.example.internservice.program.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.program.dto.request.BatchApplyGroupsRequest;
import org.example.internservice.program.dto.request.CreateGroupRequest;
import org.example.internservice.program.dto.response.InternGroupResponse;
import org.example.internservice.program.entity.InternGroup;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.repository.InternGroupRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.program.service.InternGroupService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InternGroupServiceImpl implements InternGroupService {

    private final InternGroupRepository internGroupRepository;
    private final InternshipProgramRepository programRepository;
    private final InternProfileRepository internProfileRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Override
    public List<InternGroupResponse> getGroupsByProgramId(Long programId) {
        log.info("Lấy danh sách nhóm thực tập cho chương trình ID: {}", programId);
        verifyAccess(programId);
        List<InternGroup> groups = internGroupRepository.findByProgramId(programId);
        return groups.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public InternGroupResponse createGroup(Long programId, CreateGroupRequest request) {
        log.info("Tạo nhóm mới '{}' cho chương trình ID: {}", request.getName(), programId);
        verifyAccess(programId);
        InternshipProgram program = getProgramOrThrow(programId);

        if (internGroupRepository.existsByProgramIdAndName(programId, request.getName().trim())) {
            throw new BadRequestException("Tên nhóm '" + request.getName().trim() + "' đã tồn tại trong chương trình này");
        }

        InternGroup group = InternGroup.builder()
                .program(program)
                .name(request.getName().trim())
                .maxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 4)
                .mentorId(request.getMentorId())
                .build();

        InternGroup saved = internGroupRepository.save(group);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public InternGroupResponse updateGroup(Long programId, Long groupId, CreateGroupRequest request) {
        log.info("Cập nhật thông tin nhóm ID: {} trong chương trình ID: {}", groupId, programId);
        verifyAccess(programId);
        getProgramOrThrow(programId);
        InternGroup group = internGroupRepository.findByIdAndProgramId(groupId, programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + groupId + " trong chương trình này"));

        String trimmedName = request.getName().trim();
        if (!group.getName().equalsIgnoreCase(trimmedName) && internGroupRepository.existsByProgramIdAndName(programId, trimmedName)) {
            throw new BadRequestException("Tên nhóm '" + trimmedName + "' đã tồn tại trong chương trình này");
        }

        group.setName(trimmedName);
        if (request.getMaxMembers() != null) {
            group.setMaxMembers(request.getMaxMembers());
        }
        group.setMentorId(request.getMentorId());

        InternGroup updated = internGroupRepository.save(group);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void disbandGroup(Long programId, Long groupId) {
        log.info("Giải tán nhóm ID: {} trong chương trình ID: {}", groupId, programId);
        verifyAccess(programId);
        getProgramOrThrow(programId);
        InternGroup group = internGroupRepository.findByIdAndProgramId(groupId, programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + groupId + " trong chương trình này"));

        // 1. Gỡ toàn bộ intern ra khỏi nhóm (reset group = null, vẫn ở trong program)
        List<InternProfile> members = internProfileRepository.findByGroupId(groupId);
        for (InternProfile member : members) {
            member.setGroup(null);
        }
        internProfileRepository.saveAll(members);

        // 2. Xóa bản ghi nhóm
        internGroupRepository.delete(group);
        log.info("Giải tán thành công nhóm ID: {}", groupId);
    }

    @Override
    @Transactional
    public List<InternGroupResponse> batchApplyGroups(Long programId, BatchApplyGroupsRequest request) {
        log.info("Bắt đầu áp dụng chia nhóm hàng loạt cho chương trình ID: {}", programId);
        verifyAccess(programId);
        InternshipProgram program = getProgramOrThrow(programId);

        // Validate payload
        if (request.getGroups() == null || request.getGroups().isEmpty()) {
            throw new BadRequestException("Danh sách nhóm chia tự động không được để trống");
        }

        // 1. Validate IDOR: Toàn bộ internId phải thuộc đúng programId và không trùng lặp
        Set<Long> encounteredInternIds = new HashSet<>();
        Set<String> encounteredGroupNames = new HashSet<>();

        for (BatchApplyGroupsRequest.GroupBatchItem item : request.getGroups()) {
            String gName = item.getName() != null ? item.getName().trim() : "";
            if (gName.isEmpty()) {
                throw new BadRequestException("Tên nhóm không được để trống");
            }
            if (!encounteredGroupNames.add(gName.toLowerCase())) {
                throw new BadRequestException("Tên nhóm '" + gName + "' bị trùng lặp trong danh sách áp dụng");
            }
            if (internGroupRepository.existsByProgramIdAndName(programId, gName)) {
                throw new BadRequestException("Tên nhóm '" + gName + "' đã tồn tại trong chương trình này");
            }

            if (item.getInternIds() != null) {
                for (Long internId : item.getInternIds()) {
                    if (!encounteredInternIds.add(internId)) {
                        throw new BadRequestException("Thực tập sinh ID " + internId + " bị xếp trùng lặp vào nhiều nhóm");
                    }
                    InternProfile intern = internProfileRepository.findById(internId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với ID: " + internId));
                    if (intern.getProgram() == null || !intern.getProgram().getId().equals(programId)) {
                        throw new BadRequestException("Thực tập sinh ID " + internId + " không thuộc chương trình ID " + programId);
                    }
                }
            }
        }

        // 2. Tạo các nhóm và liên kết intern
        List<InternGroupResponse> responses = new ArrayList<>();
        for (BatchApplyGroupsRequest.GroupBatchItem item : request.getGroups()) {
            InternGroup group = InternGroup.builder()
                    .program(program)
                    .name(item.getName().trim())
                    .maxMembers(item.getMaxMembers() != null && item.getMaxMembers() > 0 ? item.getMaxMembers() : 4)
                    .build();
            InternGroup savedGroup = internGroupRepository.save(group);

            if (item.getInternIds() != null && !item.getInternIds().isEmpty()) {
                List<InternProfile> groupMembers = internProfileRepository.findAllById(item.getInternIds());
                for (InternProfile member : groupMembers) {
                    member.setGroup(savedGroup);
                }
                internProfileRepository.saveAll(groupMembers);
            }

            responses.add(mapToResponse(savedGroup));
        }

        log.info("Áp dụng thành công {} nhóm cho chương trình ID: {}", responses.size(), programId);
        return responses;
    }

    @Override
    @Transactional
    public void addMemberToGroup(Long programId, Long groupId, Long internId) {
        log.info("Thêm thực tập sinh ID: {} vào nhóm ID: {} của chương trình ID: {}", internId, groupId, programId);
        verifyAccess(programId);
        getProgramOrThrow(programId);
        InternGroup group = internGroupRepository.findByIdAndProgramId(groupId, programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + groupId + " trong chương trình này"));

        InternProfile intern = internProfileRepository.findById(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với ID: " + internId));

        if (intern.getProgram() == null || !intern.getProgram().getId().equals(programId)) {
            throw new BadRequestException("Thực tập sinh ID " + internId + " không thuộc chương trình ID " + programId);
        }

        intern.setGroup(group);
        internProfileRepository.save(intern);
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(Long programId, Long groupId, Long internId) {
        log.info("Gỡ thực tập sinh ID: {} khỏi nhóm ID: {} của chương trình ID: {}", internId, groupId, programId);
        verifyAccess(programId);
        getProgramOrThrow(programId);
        internGroupRepository.findByIdAndProgramId(groupId, programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + groupId + " trong chương trình này"));

        InternProfile intern = internProfileRepository.findById(internId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với ID: " + internId));

        if (intern.getGroup() != null && intern.getGroup().getId().equals(groupId)) {
            intern.setGroup(null);
            internProfileRepository.save(intern);
        }
    }

    private void verifyAccess(Long programId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        boolean isHrOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isHrOrAdmin) {
            return;
        }
        CustomUserDetails userDetails = (auth.getPrincipal() instanceof CustomUserDetails ud) ? ud : null;
        if (userDetails != null) {
            Long mentorIdentifier = (userDetails.getUserId() != null)
                    ? mentorProfileRepository.findByUserId(userDetails.getUserId()).map(MentorProfile::getId).orElse(userDetails.getUserId())
                    : null;
            Long userId = userDetails.getUserId();
            if (mentorIdentifier != null) {
                if (programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, mentorIdentifier)) {
                    return;
                }
                if (userId != null && programMentorRepository.existsByProgramIdAndMentorIdentifier(programId, userId)) {
                    return;
                }
                boolean hasIntern = internProfileRepository.findByProgramId(programId).stream()
                        .anyMatch(i -> (i.getMentorId() != null && (i.getMentorId().equals(mentorIdentifier) || (userId != null && i.getMentorId().equals(userId)))));
                if (hasIntern) {
                    return;
                }
                throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
            }
        }
    }

    private InternshipProgram getProgramOrThrow(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId));
    }

    private InternGroupResponse mapToResponse(InternGroup group) {
        List<InternProfile> members = internProfileRepository.findByGroupId(group.getId());
        List<InternGroupResponse.GroupMemberResponse> memberResponses = members.stream()
                .map(m -> InternGroupResponse.GroupMemberResponse.builder()
                        .id(m.getId())
                        .internCode(m.getInternCode())
                        .fullName(m.getFullName())
                        .appliedPosition(m.getAppliedPosition())
                        .email(m.getEmail())
                        .phone(m.getPhone())
                        .build())
                .toList();

        return InternGroupResponse.builder()
                .id(group.getId())
                .programId(group.getProgram().getId())
                .name(group.getName())
                .maxMembers(group.getMaxMembers())
                .memberCount(members.size())
                .mentorId(group.getMentorId())
                .mentorName(group.getMentorName())
                .members(memberResponses)
                .build();
    }
}

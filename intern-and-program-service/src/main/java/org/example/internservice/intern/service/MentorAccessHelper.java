package org.example.internservice.intern.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Helper chuyên trách phân giải định danh Mentor hai chiều (IAM userId <-> MentorProfile.id)
 * và tổng hợp chính xác danh sách thực tập sinh do Mentor phụ trách (Rule 17, 31).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MentorAccessHelper {

    private final MentorProfileRepository mentorProfileRepository;
    private final InternProfileRepository internProfileRepository;
    private final InternMentorAssignmentRepository internMentorAssignmentRepository;

    /**
     * Phân giải tập hợp các ID định danh ứng viên cho Mentor từ CustomUserDetails.
     * Bao gồm cả IAM userId và MentorProfile.id (nếu có hồ sơ).
     */
    public Set<Long> resolveMentorCandidateIds(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            return Collections.emptySet();
        }
        return resolveMentorCandidateIds(userDetails.getUserId());
    }

    /**
     * Phân giải tập hợp các ID định danh ứng viên cho Mentor từ một mã ID bất kỳ (userId hoặc profileId).
     */
    public Set<Long> resolveMentorCandidateIds(Long mentorIdentifier) {
        if (mentorIdentifier == null) {
            return Collections.emptySet();
        }
        Set<Long> candidateIds = new HashSet<>();
        candidateIds.add(mentorIdentifier);

        // Trường hợp mentorIdentifier là userId -> tìm thêm profile.id
        mentorProfileRepository.findByUserId(mentorIdentifier)
                .ifPresent(profile -> candidateIds.add(profile.getId()));

        // Trường hợp mentorIdentifier là profile.id -> tìm thêm profile.userId
        mentorProfileRepository.findById(mentorIdentifier)
                .ifPresent(profile -> {
                    if (profile.getUserId() != null) {
                        candidateIds.add(profile.getUserId());
                    }
                });

        return candidateIds;
    }

    /**
     * Lấy toàn bộ danh sách ID của các Thực tập sinh do Mentor phụ trách từ CustomUserDetails.
     */
    public Set<Long> getManagedInternIds(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            return Collections.emptySet();
        }
        return getManagedInternIds(userDetails.getUserId());
    }

    /**
     * Lấy toàn bộ danh sách ID của các Thực tập sinh do Mentor phụ trách từ mã định danh Mentor.
     */
    public Set<Long> getManagedInternIds(Long mentorIdentifier) {
        if (mentorIdentifier == null) {
            return Collections.emptySet();
        }
        Set<Long> candidateMentorIds = resolveMentorCandidateIds(mentorIdentifier);
        return getManagedInternIdsByMentorIds(candidateMentorIds);
    }

    /**
     * Kiểm tra xem một thực tập sinh có thuộc quyền quản lý của Mentor hay không.
     */
    public boolean isInternManagedByMentor(Long internId, CustomUserDetails userDetails) {
        if (internId == null || userDetails == null || userDetails.getUserId() == null) {
            return false;
        }
        Set<Long> managedInternIds = getManagedInternIds(userDetails);
        return managedInternIds.contains(internId);
    }

    /**
     * Kiểm tra xem một thực thể InternProfile có thuộc quyền quản lý của Mentor hay không.
     */
    public boolean isInternManagedByMentor(InternProfile intern, CustomUserDetails userDetails) {
        if (intern == null || userDetails == null || userDetails.getUserId() == null) {
            return false;
        }
        Set<Long> candidateMentorIds = resolveMentorCandidateIds(userDetails);
        if (intern.getMentorId() != null && candidateMentorIds.contains(intern.getMentorId())) {
            return true;
        }
        return isInternManagedByMentor(intern.getId(), userDetails);
    }

    /**
     * Lấy chính xác IAM userId của Mentor phụ trách TTS để phục vụ bắn thông báo WebSocket/In-App.
     */
    public Long getMentorNotificationRecipientId(InternProfile intern) {
        if (intern == null) {
            return null;
        }
        Long mentorId = intern.getMentorId();
        if (mentorId == null) {
            Optional<InternMentorAssignment> activeOpt = internMentorAssignmentRepository.findByInternIdAndStatus(intern.getId(), MentorAssignmentStatus.ACTIVE);
            if (activeOpt.isPresent()) {
                mentorId = activeOpt.get().getMentorId();
            }
        }
        if (mentorId == null) {
            return null;
        }

        // Nếu mentorId là profile.id -> phân giải sang userId để gửi notification
        Optional<MentorProfile> profileOpt = mentorProfileRepository.findById(mentorId);
        if (profileOpt.isPresent() && profileOpt.get().getUserId() != null) {
            return profileOpt.get().getUserId();
        }
        return mentorId;
    }

    private Set<Long> getManagedInternIdsByMentorIds(Set<Long> candidateMentorIds) {
        if (candidateMentorIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> internIds = new HashSet<>();

        // 1. Quét từ bảng intern_profiles theo mentor_id
        List<InternProfile> interns = internProfileRepository.findByMentorIdIn(candidateMentorIds);
        for (InternProfile ip : interns) {
            internIds.add(ip.getId());
        }

        // 2. Quét từ bảng intern_mentor_assignments theo mentor_id và trạng thái ACTIVE
        List<InternMentorAssignment> assignments = internMentorAssignmentRepository
                .findByMentorIdInAndStatus(candidateMentorIds, MentorAssignmentStatus.ACTIVE);
        for (InternMentorAssignment assignment : assignments) {
            if (assignment.getIntern() != null && assignment.getIntern().getId() != null) {
                internIds.add(assignment.getIntern().getId());
            }
        }

        return internIds;
    }
}

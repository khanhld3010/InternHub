package org.example.internservice.intern.service;

import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests - MentorAccessHelper")
class MentorAccessHelperTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternMentorAssignmentRepository internMentorAssignmentRepository;

    @InjectMocks
    private MentorAccessHelper mentorAccessHelper;

    private MentorProfile mentorProfile;
    private CustomUserDetails mentorUserDetails;
    private InternProfile intern1;
    private InternProfile intern2;

    @BeforeEach
    void setUp() {
        mentorProfile = MentorProfile.builder()
                .userId(5L)
                .fullName("Trần Thị Mentor")
                .email("mentor@internhub.vn")
                .build();
        mentorProfile.setId(2L);

        mentorUserDetails = CustomUserDetails.builder()
                .userId(5L)
                .username("mentor.tran")
                .role("MENTOR")
                .build();

        intern1 = InternProfile.builder()
                .userId(101L)
                .internCode("INT-001")
                .fullName("Nguyễn Văn An")
                .mentorId(2L)
                .build();
        intern1.setId(11L);

        intern2 = InternProfile.builder()
                .userId(102L)
                .internCode("INT-002")
                .fullName("Lê Văn Bình")
                .build();
        intern2.setId(22L);
    }

    @Test
    @DisplayName("Phân giải candidate IDs cho Mentor từ CustomUserDetails trả về cả userId và profileId")
    void resolveMentorCandidateIds_FromUserDetails_ReturnsBothIds() {
        when(mentorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(mentorProfile));

        Set<Long> candidateIds = mentorAccessHelper.resolveMentorCandidateIds(mentorUserDetails);

        assertEquals(2, candidateIds.size());
        assertTrue(candidateIds.contains(5L));
        assertTrue(candidateIds.contains(2L));
    }

    @Test
    @DisplayName("Phân giải candidate IDs từ profileId trả về cả profileId và userId")
    void resolveMentorCandidateIds_FromProfileId_ReturnsBothIds() {
        when(mentorProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(mentorProfileRepository.findById(2L)).thenReturn(Optional.of(mentorProfile));

        Set<Long> candidateIds = mentorAccessHelper.resolveMentorCandidateIds(2L);

        assertEquals(2, candidateIds.size());
        assertTrue(candidateIds.contains(2L));
        assertTrue(candidateIds.contains(5L));
    }

    @Test
    @DisplayName("Lấy danh sách intern phụ trách kết hợp cả từ intern_profiles và intern_mentor_assignments")
    void getManagedInternIds_CombinesDirectMentorIdAndAssignmentTable() {
        when(mentorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(mentorProfile));

        // intern1 được gán trực tiếp qua intern_profiles.mentor_id
        when(internProfileRepository.findByMentorIdIn(any())).thenReturn(List.of(intern1));

        // intern2 được gán qua bảng intern_mentor_assignments
        InternMentorAssignment assignment = InternMentorAssignment.builder()
                .intern(intern2)
                .mentorId(2L)
                .status(MentorAssignmentStatus.ACTIVE)
                .build();
        when(internMentorAssignmentRepository.findByMentorIdInAndStatus(any(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(List.of(assignment));

        Set<Long> managedIds = mentorAccessHelper.getManagedInternIds(mentorUserDetails);

        assertEquals(2, managedIds.size());
        assertTrue(managedIds.contains(11L));
        assertTrue(managedIds.contains(22L));
    }

    @Test
    @DisplayName("Kiểm tra quyền phụ trách TTS trả về true khi TTS thuộc danh sách quản lý")
    void isInternManagedByMentor_ReturnsTrue_WhenInternIsManaged() {
        when(mentorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(mentorProfile));
        when(internProfileRepository.findByMentorIdIn(any())).thenReturn(List.of(intern1));
        when(internMentorAssignmentRepository.findByMentorIdInAndStatus(any(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        boolean result = mentorAccessHelper.isInternManagedByMentor(11L, mentorUserDetails);

        assertTrue(result);
    }

    @Test
    @DisplayName("Kiểm tra quyền phụ trách TTS trả về false khi TTS không do Mentor phụ trách")
    void isInternManagedByMentor_ReturnsFalse_WhenInternIsNotManaged() {
        when(mentorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(mentorProfile));
        when(internProfileRepository.findByMentorIdIn(any())).thenReturn(Collections.emptyList());
        when(internMentorAssignmentRepository.findByMentorIdInAndStatus(any(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        boolean result = mentorAccessHelper.isInternManagedByMentor(999L, mentorUserDetails);

        assertFalse(result);
    }

    @Test
    @DisplayName("Lấy recipientId cho thông báo chuyển đổi chính xác từ mentorProfile.id sang userId")
    void getMentorNotificationRecipientId_ResolvesToUserId() {
        when(mentorProfileRepository.findById(2L)).thenReturn(Optional.of(mentorProfile));

        Long recipientId = mentorAccessHelper.getMentorNotificationRecipientId(intern1);

        assertEquals(5L, recipientId);
    }
}

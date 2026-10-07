package org.example.internservice.intern.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.request.SaveWeeklyReportRequest;
import org.example.internservice.intern.dto.response.SuggestedKanbanTasksResponse;
import org.example.internservice.intern.dto.response.WeeklyReportDetailResponse;
import org.example.internservice.intern.dto.response.WeeklyReportTimelineResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.service.impl.InternWeeklyReportServiceImpl;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternWeeklyReportServiceTest {

    @Mock
    private InternWeeklyReportRepository reportRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternWeeklyAssessmentRepository assessmentRepository;

    @Mock
    private MissionItemRepository missionItemRepository;

    @Mock
    private NotificationEventDispatcher notificationEventDispatcher;

    @InjectMocks
    private InternWeeklyReportServiceImpl weeklyReportService;

    private InternProfile activeIntern;

    @BeforeEach
    void setUp() {
        activeIntern = InternProfile.builder()
                .userId(100L)
                .internCode("TTS-2026-001")
                .fullName("Nguyễn Văn Thực Tập")
                .mentorId(200L)
                .mentorName("Lê Văn Mentor")
                .status(InternStatus.INTERNING)
                .startDate(LocalDate.now().minusWeeks(2))
                .endDate(LocalDate.now().plusWeeks(10))
                .build();
        activeIntern.setId(1L);
    }

    @Test
    @DisplayName("UT-01: Lấy timeline các tuần cho Thực tập sinh thành công")
    void getTimeline_Success() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));

        InternWeeklyReport week1Report = InternWeeklyReport.builder()
                .internCode("TTS-2026-001")
                .weekNumber(1)
                .status(WeeklyReportStatus.SUBMITTED)
                .build();
        when(reportRepository.findByInternCodeOrderByWeekNumberAsc("TTS-2026-001"))
                .thenReturn(List.of(week1Report));

        InternWeeklyAssessment week1Assessment = InternWeeklyAssessment.builder()
                .internCode("TTS-2026-001")
                .weekNumber(1)
                .averageScore(new BigDecimal("4.5"))
                .feedback("Hoàn thành tốt nhiệm vụ")
                .status(InternWeeklyAssessment.AssessmentStatus.PUBLISHED)
                .build();
        when(assessmentRepository.findByInternCodeOrderByWeekNumberDesc("TTS-2026-001"))
                .thenReturn(List.of(week1Assessment));

        // Act
        WeeklyReportTimelineResponse response = weeklyReportService.getTimeline(100L);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getCurrentWeek());
        assertFalse(response.getReports().isEmpty());

        WeeklyReportTimelineResponse.WeeklyReportItem week1Item = response.getReports().get(0);
        assertEquals(1, week1Item.getWeekNumber());
        assertEquals("REVIEWED", week1Item.getStatus());
        assertEquals(new BigDecimal("4.5"), week1Item.getMentorAverageScore());
    }

    @Test
    @DisplayName("UT-02: Lấy gợi ý nhiệm vụ Kanban phân loại đúng 2 nhóm completed và unfinished")
    void getSuggestedKanbanTasks_Success() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));

        MissionItem item1 = MissionItem.builder()
                .title("Task Hoàn thành")
                .status(MissionItemStatus.COMPLETED)
                .submissionUrl("https://github.com/pr/1")
                .build();
        item1.setId(10L);

        MissionItem item2 = MissionItem.builder()
                .title("Task Đang làm")
                .status(MissionItemStatus.IN_PROGRESS)
                .dueDate(LocalDate.now().plusDays(2))
                .build();
        item2.setId(20L);

        when(missionItemRepository.findAssignedItemsByInternIdWithDetails(1L))
                .thenReturn(List.of(item1, item2));

        // Act
        SuggestedKanbanTasksResponse response = weeklyReportService.getSuggestedKanbanTasks(100L, 2);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getWeekNumber());
        assertEquals(1, response.getCompletedTasks().size());
        assertEquals("Task Hoàn thành", response.getCompletedTasks().get(0).getTitle());
        assertEquals(1, response.getUnfinishedTasks().size());
        assertEquals("Task Đang làm", response.getUnfinishedTasks().get(0).getTitle());
    }

    @Test
    @DisplayName("UT-03: Lưu nháp báo cáo tuần thành công, không dispatch notification")
    void saveReport_Success_Draft() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-2026-001", 2))
                .thenReturn(Optional.empty());
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-2026-001", 2))
                .thenReturn(Optional.empty());

        SaveWeeklyReportRequest request = SaveWeeklyReportRequest.builder()
                .weekNumber(2)
                .completedTasksSummary("Đã làm xong task A")
                .unfinishedTasksSummary("Chưa xong task B")
                .difficultiesAndChallenges("Gặp khó khăn với Docker")
                .learningsAndKnowledge("Học được kiến trúc microservices")
                .isSubmit(false)
                .tasks(List.of(SaveWeeklyReportRequest.TaskItem.builder()
                        .missionItemId(10L)
                        .taskTitle("Task A")
                        .taskStatus("COMPLETED")
                        .isCompleted(true)
                        .build()))
                .build();

        InternWeeklyReport savedEntity = InternWeeklyReport.builder()
                .internCode("TTS-2026-001")
                .mentorId(200L)
                .weekNumber(2)
                .status(WeeklyReportStatus.DRAFT)
                .completedTasksSummary("Đã làm xong task A")
                .tasks(new ArrayList<>())
                .build();
        savedEntity.setId(5L);

        when(reportRepository.save(any(InternWeeklyReport.class))).thenReturn(savedEntity);

        // Act
        WeeklyReportDetailResponse response = weeklyReportService.saveOrUpdateReport(100L, request);

        // Assert
        assertNotNull(response);
        assertEquals("DRAFT", response.getStatus());
        assertEquals("Bản nháp", response.getStatusDisplayName());
        verify(reportRepository, times(1)).save(any(InternWeeklyReport.class));
        verify(notificationEventDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("UT-04: Nộp chính thức báo cáo tuần thành công, dispatch notification cho Mentor")
    void submitReport_Success() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-2026-001", 2))
                .thenReturn(Optional.empty());

        InternWeeklyReport existingReport = InternWeeklyReport.builder()
                .internCode("TTS-2026-001")
                .mentorId(200L)
                .weekNumber(2)
                .status(WeeklyReportStatus.DRAFT)
                .completedTasksSummary("Hoàn thành module authentication")
                .tasks(new ArrayList<>())
                .build();
        existingReport.setId(5L);

        when(reportRepository.findByInternCodeAndWeekNumberWithTasks("TTS-2026-001", 2))
                .thenReturn(Optional.of(existingReport));
        when(reportRepository.save(any(InternWeeklyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WeeklyReportDetailResponse response = weeklyReportService.submitReport(100L, 2);

        // Assert
        assertNotNull(response);
        assertEquals("SUBMITTED", response.getStatus());
        assertNotNull(response.getSubmittedAt());
        verify(notificationEventDispatcher, times(1)).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("UT-05: Chặn sửa báo cáo khi Mentor đã công bố đánh giá (PUBLISHED)")
    void saveReport_Fail_WhenMentorPublished() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));

        InternWeeklyAssessment publishedAssessment = InternWeeklyAssessment.builder()
                .internCode("TTS-2026-001")
                .weekNumber(2)
                .status(InternWeeklyAssessment.AssessmentStatus.PUBLISHED)
                .build();
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-2026-001", 2))
                .thenReturn(Optional.of(publishedAssessment));

        SaveWeeklyReportRequest request = SaveWeeklyReportRequest.builder()
                .weekNumber(2)
                .completedTasksSummary("Cố tình sửa báo cáo")
                .build();

        // Act & Assert
        assertThrows(BadRequestException.class, () -> weeklyReportService.saveOrUpdateReport(100L, request));
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-06: Chặn nộp báo cáo cho tuần trong tương lai vượt quá thời gian thực tế")
    void saveReport_Fail_FutureWeek() {
        // Arrange (activeIntern startDate is 2 weeks ago -> currentWeek is 3)
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));

        SaveWeeklyReportRequest request = SaveWeeklyReportRequest.builder()
                .weekNumber(10) // Tuần 10 > currentWeek (3) + 1
                .completedTasksSummary("Báo cáo tuần 10")
                .build();

        // Act & Assert
        assertThrows(BadRequestException.class, () -> weeklyReportService.saveOrUpdateReport(100L, request));
    }

    @Test
    @DisplayName("UT-07: Xem chi tiết báo cáo tuần của TTS thành công")
    void getMyReportDetail_Success() {
        // Arrange
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(activeIntern));

        InternWeeklyReport existingReport = InternWeeklyReport.builder()
                .internCode("TTS-2026-001")
                .mentorId(200L)
                .weekNumber(1)
                .status(WeeklyReportStatus.SUBMITTED)
                .completedTasksSummary("Tổng hợp task tuần 1")
                .tasks(new ArrayList<>())
                .build();
        existingReport.setId(1L);

        when(reportRepository.findByInternCodeAndWeekNumberWithTasks("TTS-2026-001", 1))
                .thenReturn(Optional.of(existingReport));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-2026-001", 1))
                .thenReturn(Optional.empty());

        // Act
        WeeklyReportDetailResponse response = weeklyReportService.getMyReportDetail(100L, 1);

        // Assert
        assertNotNull(response);
        assertEquals("TTS-2026-001", response.getInternCode());
        assertEquals(1, response.getWeekNumber());
        assertEquals("SUBMITTED", response.getStatus());
    }
}

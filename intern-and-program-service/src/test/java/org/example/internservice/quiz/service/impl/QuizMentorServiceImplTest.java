package org.example.internservice.quiz.service.impl;

import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternGroup;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.repository.InternGroupRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.quiz.client.AiServiceClient;
import org.example.internservice.quiz.client.dto.AiQuizGenerateClientRequest;
import org.example.internservice.quiz.client.dto.AiQuizGenerateClientResponse;
import org.example.internservice.quiz.client.dto.AiQuizOptionClientDto;
import org.example.internservice.quiz.client.dto.AiQuizQuestionClientDto;
import org.example.internservice.quiz.dto.request.AssignQuizRequest;
import org.example.internservice.quiz.dto.request.GenerateAiQuizRequest;
import org.example.internservice.quiz.dto.response.QuizDetailResponse;
import org.example.internservice.quiz.dto.response.QuizReportResponse;
import org.example.internservice.quiz.entity.Quiz;
import org.example.internservice.quiz.entity.QuizAssignment;
import org.example.internservice.quiz.entity.QuizQuestion;
import org.example.internservice.quiz.entity.QuizQuestionOption;
import org.example.internservice.quiz.entity.QuizSubmission;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.example.internservice.quiz.entity.enums.AssignmentTargetType;
import org.example.internservice.quiz.entity.enums.QuestionType;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;
import org.example.internservice.quiz.entity.enums.QuizStatus;
import org.example.internservice.quiz.helper.QuizKanbanSyncHelper;
import org.example.internservice.quiz.repository.QuizAssignmentRepository;
import org.example.internservice.quiz.repository.QuizQuestionOptionRepository;
import org.example.internservice.quiz.repository.QuizQuestionRepository;
import org.example.internservice.quiz.repository.QuizRepository;
import org.example.internservice.quiz.repository.QuizSubmissionRepository;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizMentorServiceImplTest {

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizQuestionRepository questionRepository;
    @Mock
    private QuizQuestionOptionRepository optionRepository;
    @Mock
    private QuizAssignmentRepository assignmentRepository;
    @Mock
    private QuizSubmissionRepository submissionRepository;
    @Mock
    private InternshipProgramRepository programRepository;
    @Mock
    private InternGroupRepository groupRepository;
    @Mock
    private InternProfileRepository internProfileRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private ProgramMentorRepository programMentorRepository;
    @Mock
    private InternWeeklyReportRepository weeklyReportRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private QuizKanbanSyncHelper kanbanSyncHelper;

    @InjectMocks
    private QuizMentorServiceImpl quizMentorService;

    private CustomUserDetails mentorUserDetails;
    private MentorProfile mentorProfile;
    private InternshipProgram program;
    private InternGroup group;
    private InternProfile intern1, intern2;

    @BeforeEach
    void setUp() {
        mentorUserDetails = CustomUserDetails.builder()
                .userId(100L)
                .username("mentor_test")
                .role("ROLE_MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();

        mentorProfile = MentorProfile.builder()
                .userId(100L)
                .fullName("Lê Văn Mentor")
                .email("mentor@internhub.vn")
                .build();
        mentorProfile.setId(5L);

        Department dept = Department.builder().code("TECH").name("Phòng Công nghệ").build();
        dept.setId(1L);

        program = InternshipProgram.builder()
                .name("Kỳ Thực Tập K28")
                .department(dept)
                .build();
        program.setId(10L);

        group = InternGroup.builder()
                .name("Nhóm Java Backend")
                .program(program)
                .build();
        group.setId(20L);

        intern1 = InternProfile.builder().userId(201L).fullName("TTS Nguyễn Văn A").internCode("TTS-01").program(program).build();
        intern1.setId(51L);
        intern2 = InternProfile.builder().userId(202L).fullName("TTS Trần Văn B").internCode("TTS-02").program(program).build();
        intern2.setId(52L);
    }

    @Test
    @DisplayName("Mentor yêu cầu AI sinh bài Quiz dựa trên báo cáo tuần - Thành công, tạo bài nháp DRAFT")
    void testGenerateAiQuiz_Success() {
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mentorProfile));
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(true);
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(internProfileRepository.findByGroupId(20L)).thenReturn(Arrays.asList(intern1, intern2));

        // Mock báo cáo tuần có kiến thức đã học
        InternWeeklyReport report1 = InternWeeklyReport.builder()
                .internCode("TTS-01")
                .weekNumber(1)
                .status(WeeklyReportStatus.SUBMITTED)
                .learningsAndKnowledge("Tìm hiểu Spring Security và JWT Filter")
                .completedTasksSummary("Hoàn thành API Auth")
                .build();
        when(weeklyReportRepository.findByInternCodeAndWeekNumber("TTS-01", 1)).thenReturn(Optional.of(report1));
        when(weeklyReportRepository.findByInternCodeAndWeekNumber("TTS-02", 1)).thenReturn(Optional.empty());

        // Mock AI Service Response
        AiQuizOptionClientDto opt1 = AiQuizOptionClientDto.builder().optionLabel("A").optionText("Filter").isCorrect(true).orderIndex(0).build();
        AiQuizOptionClientDto opt2 = AiQuizOptionClientDto.builder().optionLabel("B").optionText("Servlet").isCorrect(false).orderIndex(1).build();
        AiQuizQuestionClientDto q1 = AiQuizQuestionClientDto.builder()
                .questionText("Thành phần nào chặn request trong Spring Security?")
                .questionType(QuestionType.SINGLE_CHOICE)
                .points(10)
                .explanation("Filter can thiệp vào Servlet chain")
                .orderIndex(0)
                .options(Arrays.asList(opt1, opt2))
                .build();

        AiQuizGenerateClientResponse aiResp = AiQuizGenerateClientResponse.builder()
                .title("Bài Quiz Spring Security Tuần 1")
                .topic("Spring Security")
                .difficulty(QuizDifficulty.MEDIUM)
                .totalQuestions(1)
                .estimatedMinutes(15)
                .questions(Collections.singletonList(q1))
                .build();

        when(aiServiceClient.generateQuiz(any(AiQuizGenerateClientRequest.class), eq(null)))
                .thenReturn(ApiResponse.success(200, "Success", aiResp));

        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz q = invocation.getArgument(0);
            q.setId(99L);
            return q;
        });
        when(questionRepository.save(any(QuizQuestion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(optionRepository.save(any(QuizQuestionOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GenerateAiQuizRequest request = GenerateAiQuizRequest.builder()
                .programId(10L)
                .groupId(20L)
                .weekNumber(1)
                .questionCount(5)
                .difficulty(QuizDifficulty.MEDIUM)
                .build();

        QuizDetailResponse response = quizMentorService.generateAiQuiz(request, mentorUserDetails);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(QuizStatus.DRAFT, response.getStatus());
        assertEquals("Bài Quiz Spring Security Tuần 1", response.getTitle());
        assertEquals(1, response.getQuestions().size());

        verify(quizRepository).save(any(Quiz.class));
        verify(questionRepository, atLeastOnce()).save(any(QuizQuestion.class));
    }

    @Test
    @DisplayName("Mentor giao bài Quiz cho 1 nhóm TTS - Chuyển sang PUBLISHED và đồng bộ thẻ Kanban")
    void testAssignQuiz_ToGroup_Success() {
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mentorProfile));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(true);

        QuizQuestion q1 = QuizQuestion.builder()
                .questionText("Test question")
                .points(10)
                .build();

        Quiz draftQuiz = Quiz.builder()
                .title("Bài Quiz Java Core")
                .program(program)
                .mentorId(5L)
                .status(QuizStatus.DRAFT)
                .timeLimitMinutes(20)
                .passingScore(70)
                .maxAttempts(2)
                .questions(new ArrayList<>(Collections.singletonList(q1)))
                .build();
        draftQuiz.setId(88L);

        when(quizRepository.findByIdWithDetails(88L)).thenReturn(Optional.of(draftQuiz));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(internProfileRepository.findByGroupId(20L)).thenReturn(Arrays.asList(intern1, intern2));
        when(assignmentRepository.save(any(QuizAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignQuizRequest request = AssignQuizRequest.builder()
                .targetType(AssignmentTargetType.GROUP)
                .targetId(20L)
                .dueDate(LocalDate.now().plusDays(5))
                .createKanbanTask(true)
                .build();

        quizMentorService.assignQuiz(88L, request, mentorUserDetails);

        assertEquals(QuizStatus.PUBLISHED, draftQuiz.getStatus());

        // Kiểm tra đã tạo assignment cho cả 2 intern trong group
        verify(assignmentRepository, atLeast(2)).save(any(QuizAssignment.class));
        verify(kanbanSyncHelper, times(2)).createKanbanTaskForQuizAssignment(any(), any());
    }

    @Test
    @DisplayName("Lấy báo cáo thống kê bài Quiz - Tính toán chính xác completionRate, averageScore, passRate")
    void testGetQuizReport_Success() {
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mentorProfile));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(true);

        Quiz publishedQuiz = Quiz.builder()
                .title("Bài Quiz Java")
                .program(program)
                .mentorId(5L)
                .status(QuizStatus.PUBLISHED)
                .totalPoints(100)
                .passingScore(70)
                .build();
        publishedQuiz.setId(77L);

        when(quizRepository.findById(77L)).thenReturn(Optional.of(publishedQuiz));

        QuizAssignment assign1 = QuizAssignment.builder()
                .quiz(publishedQuiz)
                .intern(intern1)
                .status(AssignmentStatus.ACTIVE)
                .build();
        assign1.setId(1L);

        QuizAssignment assign2 = QuizAssignment.builder()
                .quiz(publishedQuiz)
                .intern(intern2)
                .status(AssignmentStatus.ACTIVE)
                .build();
        assign2.setId(2L);

        when(assignmentRepository.findByQuizId(77L)).thenReturn(Arrays.asList(assign1, assign2));

        // Intern 1 đã làm bài đạt 90 điểm
        QuizSubmission sub1 = QuizSubmission.builder()
                .assignment(assign1)
                .intern(intern1)
                .score(90)
                .passed(true)
                .submittedAt(LocalDateTime.now().minusHours(2))
                .build();
        when(submissionRepository.findSubmissionsOrderByScoreDesc(1L, 51L)).thenReturn(Collections.singletonList(sub1));

        // Intern 2 đã làm bài không đạt 50 điểm
        QuizSubmission sub2 = QuizSubmission.builder()
                .assignment(assign2)
                .intern(intern2)
                .score(50)
                .passed(false)
                .submittedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(submissionRepository.findSubmissionsOrderByScoreDesc(2L, 52L)).thenReturn(Collections.singletonList(sub2));

        QuizReportResponse report = quizMentorService.getQuizReport(77L, mentorUserDetails);

        assertNotNull(report);
        assertEquals(77L, report.getQuizId());
        assertEquals(2, report.getTotalAssigned());
        assertEquals(2, report.getCompletedCount());
        assertEquals(1, report.getPassedCount());
        assertEquals(70.0, report.getAverageScore()); // (90 + 50) / 2 = 70.0
        assertEquals(2, report.getInternReports().size());
    }
}

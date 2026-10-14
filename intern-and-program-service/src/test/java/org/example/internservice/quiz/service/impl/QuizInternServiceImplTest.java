package org.example.internservice.quiz.service.impl;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.quiz.dto.request.SubmitQuizAnswerItem;
import org.example.internservice.quiz.dto.request.SubmitQuizRequest;
import org.example.internservice.quiz.dto.response.QuizResultResponse;
import org.example.internservice.quiz.dto.response.QuizTakingResponse;
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
import org.example.internservice.quiz.entity.enums.SubmissionStatus;
import org.example.internservice.quiz.helper.QuizKanbanSyncHelper;
import org.example.internservice.quiz.repository.QuizAssignmentRepository;
import org.example.internservice.quiz.repository.QuizQuestionOptionRepository;
import org.example.internservice.quiz.repository.QuizQuestionRepository;
import org.example.internservice.quiz.repository.QuizRepository;
import org.example.internservice.quiz.repository.QuizSubmissionAnswerRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizInternServiceImplTest {

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizAssignmentRepository assignmentRepository;
    @Mock
    private QuizSubmissionRepository submissionRepository;
    @Mock
    private QuizSubmissionAnswerRepository submissionAnswerRepository;
    @Mock
    private QuizQuestionRepository questionRepository;
    @Mock
    private QuizQuestionOptionRepository optionRepository;
    @Mock
    private InternProfileRepository internProfileRepository;
    @Mock
    private QuizKanbanSyncHelper kanbanSyncHelper;

    @InjectMocks
    private QuizInternServiceImpl quizInternService;

    private CustomUserDetails internUserDetails;
    private InternProfile internProfile;
    private Quiz mockQuiz;
    private QuizAssignment mockAssignment;
    private QuizQuestion mockQuestion1; // SINGLE_CHOICE
    private QuizQuestion mockQuestion2; // MULTIPLE_CHOICE
    private QuizQuestionOption opt1A, opt1B;
    private QuizQuestionOption opt2A, opt2B, opt2C;

    @BeforeEach
    void setUp() {
        internUserDetails = CustomUserDetails.builder()
                .userId(200L)
                .username("intern_test")
                .role("ROLE_INTERN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        internProfile = InternProfile.builder()
                .userId(200L)
                .fullName("Trần Thị TTS")
                .internCode("TTS-2026-001")
                .email("tts@internhub.vn")
                .build();
        internProfile.setId(10L);

        mockQuiz = Quiz.builder()
                .title("Bài Quiz Java Core & Spring Boot")
                .description("Kiểm tra kiến thức tuần 1")
                .mentorId(5L)
                .status(QuizStatus.PUBLISHED)
                .difficulty(QuizDifficulty.MEDIUM)
                .timeLimitMinutes(15)
                .passingScore(8)
                .totalPoints(10)
                .maxAttempts(2)
                .questions(new ArrayList<>())
                .build();
        mockQuiz.setId(1L);

        mockAssignment = QuizAssignment.builder()
                .quiz(mockQuiz)
                .intern(internProfile)
                .targetType(AssignmentTargetType.INDIVIDUAL)
                .status(AssignmentStatus.ACTIVE)
                .assignedByMentorId(5L)
                .dueDate(LocalDate.now().plusDays(3))
                .build();
        mockAssignment.setId(100L);

        // Câu 1: Single Choice (5 điểm), Đáp án đúng: opt1A
        mockQuestion1 = QuizQuestion.builder()
                .quiz(mockQuiz)
                .questionText("Từ khóa nào được dùng để khai báo hằng số trong Java?")
                .questionType(QuestionType.SINGLE_CHOICE)
                .points(5)
                .orderIndex(0)
                .explanation("Từ khóa final biến trường thành hằng số.")
                .options(new ArrayList<>())
                .build();
        mockQuestion1.setId(1001L);

        opt1A = QuizQuestionOption.builder().question(mockQuestion1).optionLabel("A").optionText("final").isCorrect(true).orderIndex(0).build();
        opt1A.setId(2001L);
        opt1B = QuizQuestionOption.builder().question(mockQuestion1).optionLabel("B").optionText("const").isCorrect(false).orderIndex(1).build();
        opt1B.setId(2002L);
        mockQuestion1.getOptions().addAll(Arrays.asList(opt1A, opt1B));

        // Câu 2: Multiple Choice (5 điểm), Đáp án đúng: opt2A và opt2C (All-or-Nothing)
        mockQuestion2 = QuizQuestion.builder()
                .quiz(mockQuiz)
                .questionText("Các interface nào thuộc Java Collections Framework?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .points(5)
                .orderIndex(1)
                .explanation("List và Set kế thừa từ Collection.")
                .options(new ArrayList<>())
                .build();
        mockQuestion2.setId(1002L);

        opt2A = QuizQuestionOption.builder().question(mockQuestion2).optionLabel("A").optionText("List").isCorrect(true).orderIndex(0).build();
        opt2A.setId(3001L);
        opt2B = QuizQuestionOption.builder().question(mockQuestion2).optionLabel("B").optionText("Thread").isCorrect(false).orderIndex(1).build();
        opt2B.setId(3002L);
        opt2C = QuizQuestionOption.builder().question(mockQuestion2).optionLabel("C").optionText("Set").isCorrect(true).orderIndex(2).build();
        opt2C.setId(3003L);
        mockQuestion2.getOptions().addAll(Arrays.asList(opt2A, opt2B, opt2C));

        mockQuiz.getQuestions().addAll(Arrays.asList(mockQuestion1, mockQuestion2));
    }

    @Test
    @DisplayName("Bắt đầu làm bài thi - Thành công, ẩn hoàn toàn đáp án đúng")
    void testStartQuizAttempt_Success_HidesCorrectAnswers() {
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(internProfile));
        when(assignmentRepository.findByIdWithQuizAndProgram(100L)).thenReturn(Optional.of(mockAssignment));
        when(quizRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockQuiz));
        when(submissionRepository.countByAssignmentIdAndInternIdAndStatusNot(100L, 10L, SubmissionStatus.TIMED_OUT)).thenReturn(0L);
        when(submissionRepository.save(any(QuizSubmission.class))).thenAnswer(invocation -> {
            QuizSubmission s = invocation.getArgument(0);
            s.setId(500L);
            return s;
        });

        QuizTakingResponse response = quizInternService.startQuizAttempt(100L, internUserDetails);

        assertNotNull(response);
        assertEquals(500L, response.getSubmissionId());
        assertEquals(1, response.getAttemptNumber());
        assertEquals(2, response.getQuestions().size());

        // Kiểm tra tính bảo mật: khi đang làm bài thì DTO QuizTakingOptionResponse chỉ chứa label và text, không lộ isCorrect
        response.getQuestions().forEach(q -> {
            q.getOptions().forEach(opt -> {
                assertNotNull(opt.getOptionLabel());
                assertNotNull(opt.getOptionText());
            });
        });

        verify(submissionRepository).save(any(QuizSubmission.class));
    }

    @Test
    @DisplayName("Bắt đầu làm bài thi - Thất bại do đã vượt quá số lần thi tối đa (maxAttempts)")
    void testStartQuizAttempt_ExceedAttempts_ThrowsBadRequest() {
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(internProfile));
        when(assignmentRepository.findByIdWithQuizAndProgram(100L)).thenReturn(Optional.of(mockAssignment));
        when(quizRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockQuiz));
        // maxAttempts của mockQuiz là 2, nếu đã submit 2 lần thì ném lỗi
        when(submissionRepository.countByAssignmentIdAndInternIdAndStatusNot(100L, 10L, SubmissionStatus.TIMED_OUT)).thenReturn(2L);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                quizInternService.startQuizAttempt(100L, internUserDetails));

        assertTrue(ex.getMessage().contains("vượt quá số lượt làm bài cho phép") || ex.getMessage().contains("hết số lượt làm bài"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nộp bài thi - Chấm điểm chuẩn All-or-Nothing, mở khóa đáp án chi tiết")
    void testSubmitQuizAttempt_Success_AllOrNothingScoring() {
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(internProfile));

        QuizSubmission inProgressSubmission = QuizSubmission.builder()
                .assignment(mockAssignment)
                .quiz(mockQuiz)
                .intern(internProfile)
                .attemptNumber(1)
                .status(SubmissionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        inProgressSubmission.setId(500L);

        when(submissionRepository.findById(500L)).thenReturn(Optional.of(inProgressSubmission));
        when(quizRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockQuiz));

        // TTS trả lời:
        // Câu 1 (Single): Chọn đúng opt1A (2001L) -> +5 điểm
        // Câu 2 (Multiple): Chọn đúng cả opt2A (3001L) và opt2C (3003L) -> +5 điểm
        SubmitQuizRequest request = SubmitQuizRequest.builder()
                .answers(Arrays.asList(
                        SubmitQuizAnswerItem.builder().questionId(1001L).selectedOptionIds(List.of(2001L)).build(),
                        SubmitQuizAnswerItem.builder().questionId(1002L).selectedOptionIds(Arrays.asList(3001L, 3003L)).build()
                ))
                .build();

        when(submissionRepository.save(any(QuizSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuizResultResponse result = quizInternService.submitQuizAttempt(500L, request, internUserDetails);

        assertNotNull(result);
        assertEquals(500L, result.getSubmissionId());
        assertEquals(SubmissionStatus.SUBMITTED, result.getStatus());
        assertTrue(result.getPassed(), "10 / 10 = 100% >= 80% điểm đạt nên phải PASS");
        assertEquals(10, result.getScore());

        // Kiểm tra sau khi nộp, đáp án và giải thích được MỞ KHÓA
        assertEquals(2, result.getAnswerResults().size());
        result.getAnswerResults().forEach(ar -> {
            assertNotNull(ar.getExplanation(), "Sau khi nộp bài phải mở khóa explanation");
            assertTrue(ar.getIsCorrect(), "Cả 2 câu đều trả lời đúng");
            ar.getOptions().forEach(opt -> {
                assertNotNull(opt.getIsCorrect(), "Sau khi nộp bài phải mở khóa isCorrect của từng option");
            });
        });
    }

    @Test
    @DisplayName("Nộp bài thi - Câu nhiều đáp án (Multiple Choice) chỉ chọn thiếu một phần bị 0 điểm (All-or-Nothing)")
    void testSubmitQuizAttempt_PartialMultipleChoice_GetsZeroPoints() {
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(internProfile));

        QuizSubmission inProgressSubmission = QuizSubmission.builder()
                .assignment(mockAssignment)
                .quiz(mockQuiz)
                .intern(internProfile)
                .attemptNumber(1)
                .status(SubmissionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        inProgressSubmission.setId(501L);

        when(submissionRepository.findById(501L)).thenReturn(Optional.of(inProgressSubmission));
        when(quizRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockQuiz));

        // TTS trả lời:
        // Câu 1 (Single): Chọn sai opt1B (2002L) -> 0 điểm
        // Câu 2 (Multiple): Chỉ chọn opt2A (3001L), thiếu opt2C (3003L) -> 0 điểm (All-or-Nothing)
        SubmitQuizRequest request = SubmitQuizRequest.builder()
                .answers(Arrays.asList(
                        SubmitQuizAnswerItem.builder().questionId(1001L).selectedOptionIds(List.of(2002L)).build(),
                        SubmitQuizAnswerItem.builder().questionId(1002L).selectedOptionIds(List.of(3001L)).build()
                ))
                .build();

        when(submissionRepository.save(any(QuizSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuizResultResponse result = quizInternService.submitQuizAttempt(501L, request, internUserDetails);

        assertNotNull(result);
        assertEquals(0, result.getScore(), "Chọn thiếu trong Multiple Choice theo All-or-Nothing phải được 0 điểm");
        assertFalse(result.getPassed());
    }

    @Test
    @DisplayName("Xem kết quả bài thi - Đang làm bài thi (IN_PROGRESS) không được xem đáp án chi tiết")
    void testGetSubmissionResult_WhileInProgress_ThrowsBadRequest() {
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(internProfile));

        QuizSubmission inProgressSubmission = QuizSubmission.builder()
                .assignment(mockAssignment)
                .quiz(mockQuiz)
                .intern(internProfile)
                .attemptNumber(1)
                .status(SubmissionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        inProgressSubmission.setId(502L);

        when(submissionRepository.findByIdWithDetails(502L)).thenReturn(Optional.of(inProgressSubmission));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                quizInternService.getSubmissionResult(502L, internUserDetails));

        assertTrue(ex.getMessage().contains("hoàn thành nộp bài trước"));
    }
}

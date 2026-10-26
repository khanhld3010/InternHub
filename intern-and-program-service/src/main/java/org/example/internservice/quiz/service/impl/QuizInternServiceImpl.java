package org.example.internservice.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.quiz.dto.request.SubmitQuizAnswerItem;
import org.example.internservice.quiz.dto.request.SubmitQuizRequest;
import org.example.internservice.quiz.dto.response.QuizAnswerResultResponse;
import org.example.internservice.quiz.dto.response.QuizAssignmentResponse;
import org.example.internservice.quiz.dto.response.QuizOptionResponse;
import org.example.internservice.quiz.dto.response.QuizResultResponse;
import org.example.internservice.quiz.dto.response.QuizTakingOptionResponse;
import org.example.internservice.quiz.dto.response.QuizTakingQuestionResponse;
import org.example.internservice.quiz.dto.response.QuizTakingResponse;
import org.example.internservice.quiz.entity.Quiz;
import org.example.internservice.quiz.entity.QuizAssignment;
import org.example.internservice.quiz.entity.QuizQuestion;
import org.example.internservice.quiz.entity.QuizQuestionOption;
import org.example.internservice.quiz.entity.QuizSubmission;
import org.example.internservice.quiz.entity.QuizSubmissionAnswer;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.example.internservice.quiz.entity.enums.SubmissionStatus;
import org.example.internservice.quiz.helper.QuizKanbanSyncHelper;
import org.example.internservice.quiz.repository.QuizAssignmentRepository;
import org.example.internservice.quiz.repository.QuizRepository;
import org.example.internservice.quiz.repository.QuizSubmissionAnswerRepository;
import org.example.internservice.quiz.repository.QuizSubmissionRepository;
import org.example.internservice.quiz.service.QuizInternService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizInternServiceImpl implements QuizInternService {

    private final QuizAssignmentRepository quizAssignmentRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final QuizSubmissionAnswerRepository quizSubmissionAnswerRepository;
    private final QuizRepository quizRepository;
    private final InternProfileRepository internProfileRepository;
    private final QuizKanbanSyncHelper kanbanSyncHelper;

    @Override
    public List<QuizAssignmentResponse> getMyAssignedQuizzes(CustomUserDetails userDetails) {
        InternProfile intern = getInternProfileOrThrow(userDetails);
        List<QuizAssignment> assignments = quizAssignmentRepository.findByInternIdOrderByCreatedAtDesc(intern.getId());

        return assignments.stream().map(assign -> {
            Quiz quiz = assign.getQuiz();
            List<QuizSubmission> submissions = quizSubmissionRepository.findSubmissionsOrderByScoreDesc(assign.getId(), intern.getId());
            int attemptsTaken = submissions.size();
            Integer highestScore = null;
            boolean passed = false;
            Long latestSubId = null;

            if (!submissions.isEmpty()) {
                QuizSubmission best = submissions.get(0);
                highestScore = best.getScore();
                passed = best.getPassed();
                latestSubId = best.getId();
            }

            return QuizAssignmentResponse.builder()
                    .assignmentId(assign.getId())
                    .quizId(quiz.getId())
                    .quizTitle(quiz.getTitle())
                    .topic(quiz.getTopic())
                    .difficulty(quiz.getDifficulty())
                    .timeLimitMinutes(quiz.getTimeLimitMinutes())
                    .totalQuestions(quiz.getTotalQuestions())
                    .totalPoints(quiz.getTotalPoints())
                    .passingScore(quiz.getPassingScore())
                    .dueDate(assign.getDueDate())
                    .maxAttempts(quiz.getMaxAttempts())
                    .attemptsTaken(attemptsTaken)
                    .highestScore(highestScore)
                    .passed(passed)
                    .status(assign.getStatus())
                    .latestSubmissionId(latestSubId)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public QuizTakingResponse startQuizAttempt(Long assignmentId, CustomUserDetails userDetails) {
        InternProfile intern = getInternProfileOrThrow(userDetails);
        QuizAssignment assignment = quizAssignmentRepository.findByIdWithQuizAndProgram(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài Quiz được giao với ID: " + assignmentId));

        if (!assignment.getIntern().getId().equals(intern.getId())) {
            throw new BadRequestException("Bạn không được phân công thực hiện bài Quiz này");
        }

        if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
            throw new BadRequestException("Bài Quiz này hiện không ở trạng thái mở làm bài.");
        }

        if (assignment.getDueDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Bài Quiz này đã quá hạn nộp bài (" + assignment.getDueDate() + ").");
        }

        Quiz quiz = quizRepository.findByIdWithDetails(assignment.getQuiz().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin đề thi Quiz"));

        // Kiểm tra số lần thi cho phép
        long attemptsTaken = quizSubmissionRepository.countByAssignmentIdAndInternIdAndStatusNot(
                assignment.getId(), intern.getId(), SubmissionStatus.TIMED_OUT
        );

        if (quiz.getMaxAttempts() > 0 && attemptsTaken >= quiz.getMaxAttempts()) {
            throw new BadRequestException("Bạn đã sử dụng hết số lượt làm bài cho phép (" + quiz.getMaxAttempts() + " lượt).");
        }

        // Tạo bản ghi Submission mới ở trạng thái IN_PROGRESS
        QuizSubmission submission = QuizSubmission.builder()
                .assignment(assignment)
                .quiz(quiz)
                .intern(intern)
                .attemptNumber((int) attemptsTaken + 1)
                .startedAt(LocalDateTime.now())
                .totalPoints(quiz.getTotalPoints())
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        QuizSubmission savedSubmission = quizSubmissionRepository.save(submission);
        log.info("Học viên {} bắt đầu làm bài Quiz ID: {}, Lượt thi: {}", intern.getInternCode(), quiz.getId(), submission.getAttemptNumber());

        // QUY TẮC BẢO MẬT BR-8: ẨN 100% isCorrect và explanation trong DTO trả về khi đang làm bài
        List<QuizTakingQuestionResponse> takingQuestions = new ArrayList<>();
        if (quiz.getQuestions() != null) {
            for (QuizQuestion q : quiz.getQuestions()) {
                List<QuizTakingOptionResponse> takingOptions = new ArrayList<>();
                if (q.getOptions() != null) {
                    for (QuizQuestionOption opt : q.getOptions()) {
                        takingOptions.add(QuizTakingOptionResponse.builder()
                                .id(opt.getId())
                                .optionLabel(opt.getOptionLabel())
                                .optionText(opt.getOptionText())
                                .orderIndex(opt.getOrderIndex())
                                .build());
                    }
                }
                takingQuestions.add(QuizTakingQuestionResponse.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .orderIndex(q.getOrderIndex())
                        .points(q.getPoints())
                        .options(takingOptions)
                        .build());
            }
        }

        long elapsedSeconds = java.time.Duration.between(savedSubmission.getStartedAt(), java.time.LocalDateTime.now()).getSeconds();
        int totalLimitSeconds = quiz.getTimeLimitMinutes() != null ? quiz.getTimeLimitMinutes() * 60 : 0;
        int remainingSeconds = Math.max(0, (int) (totalLimitSeconds - elapsedSeconds));

        return QuizTakingResponse.builder()
                .submissionId(savedSubmission.getId())
                .assignmentId(assignment.getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .topic(quiz.getTopic())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .totalQuestions(quiz.getTotalQuestions())
                .totalPoints(quiz.getTotalPoints())
                .passingScore(quiz.getPassingScore())
                .attemptNumber(savedSubmission.getAttemptNumber())
                .maxAttempts(quiz.getMaxAttempts())
                .startedAt(savedSubmission.getStartedAt())
                .remainingSeconds(remainingSeconds)
                .questions(takingQuestions)
                .build();
    }

    @Override
    @Transactional
    public QuizResultResponse submitQuizAttempt(Long submissionId, SubmitQuizRequest request, CustomUserDetails userDetails) {
        InternProfile intern = getInternProfileOrThrow(userDetails);
        QuizSubmission submission = quizSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt làm bài với ID: " + submissionId));

        if (!submission.getIntern().getId().equals(intern.getId())) {
            throw new BadRequestException("Bạn không phải là chủ sở hữu của lượt làm bài này");
        }

        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new BadRequestException("Lượt làm bài này đã được nộp hoặc đã kết thúc trước đó.");
        }

        Quiz quiz = quizRepository.findByIdWithDetails(submission.getQuiz().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi"));

        // Chấm điểm tự động All-or-Nothing
        Map<Long, List<Long>> userAnswersMap = (request.getAnswers() != null)
                ? request.getAnswers().stream().collect(Collectors.toMap(
                        SubmitQuizAnswerItem::getQuestionId,
                        a -> a.getSelectedOptionIds() != null ? a.getSelectedOptionIds() : Collections.emptyList(),
                        (existing, replacing) -> existing))
                : Collections.emptyMap();

        int totalScore = 0;
        List<QuizAnswerResultResponse> answerResults = new ArrayList<>();

        if (quiz.getQuestions() != null) {
            for (QuizQuestion q : quiz.getQuestions()) {
                List<Long> selectedIds = userAnswersMap.getOrDefault(q.getId(), Collections.emptyList());

                Set<Long> userSelectionSet = new HashSet<>(selectedIds);
                Set<Long> correctSelectionSet = q.getOptions().stream()
                        .filter(QuizQuestionOption::getIsCorrect)
                        .map(QuizQuestionOption::getId)
                        .collect(Collectors.toSet());

                boolean isCorrect = !correctSelectionSet.isEmpty() && userSelectionSet.equals(correctSelectionSet);
                int scoreAwarded = isCorrect ? q.getPoints() : 0;
                totalScore += scoreAwarded;

                String selectedIdsStr = selectedIds.stream().map(String::valueOf).collect(Collectors.joining(","));

                QuizSubmissionAnswer answer = QuizSubmissionAnswer.builder()
                        .submission(submission)
                        .question(q)
                        .selectedOptionIds(selectedIdsStr)
                        .isCorrect(isCorrect)
                        .scoreAwarded(scoreAwarded)
                        .build();

                quizSubmissionAnswerRepository.save(answer);

                // Sau khi nộp bài: chuẩn bị chi tiết đáp án và lời giải
                List<QuizOptionResponse> optionResponses = q.getOptions().stream().map(opt -> QuizOptionResponse.builder()
                        .id(opt.getId())
                        .optionLabel(opt.getOptionLabel())
                        .optionText(opt.getOptionText())
                        .isCorrect(opt.getIsCorrect())
                        .orderIndex(opt.getOrderIndex())
                        .build()).toList();

                answerResults.add(QuizAnswerResultResponse.builder()
                        .questionId(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .orderIndex(q.getOrderIndex())
                        .points(q.getPoints())
                        .isCorrect(isCorrect)
                        .scoreAwarded(scoreAwarded)
                        .explanation(q.getExplanation())
                        .selectedOptionIds(selectedIds)
                        .correctOptionIds(new ArrayList<>(correctSelectionSet))
                        .options(optionResponses)
                        .build());
            }
        }

        // Cập nhật kết quả bài thi
        int totalPoints = (quiz.getTotalPoints() != null && quiz.getTotalPoints() > 0) ? quiz.getTotalPoints() : 100;
        BigDecimal percentage = BigDecimal.valueOf((double) totalScore * 100.0 / totalPoints).setScale(2, RoundingMode.HALF_UP);
        boolean passed = totalScore >= quiz.getPassingScore();

        submission.setScore(totalScore);
        submission.setTotalPoints(totalPoints);
        submission.setPercentageScore(percentage);
        submission.setPassed(passed);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());

        QuizSubmission saved = quizSubmissionRepository.save(submission);
        log.info("Học viên {} đã nộp bài Quiz ID: {}. Điểm: {}/{}, Kết quả: {}",
                intern.getInternCode(), quiz.getId(), totalScore, totalPoints, passed ? "ĐẠT" : "CHƯA ĐẠT");

        // Đồng bộ hoàn thành Kanban Mission nếu đạt điểm
        if (passed && submission.getAssignment() != null && submission.getAssignment().getMissionItem() != null) {
            kanbanSyncHelper.markKanbanTaskCompleted(
                    submission.getAssignment().getMissionItem(),
                    totalScore, totalPoints, percentage
            );
        }

        // QUY TẮC BR-8: CHỈ SAU KHI HOÀN THÀNH MỚI MỞ KHÓA VÀ HIỂN THỊ ĐÁP ÁN CÙNG GIẢI THÍCH
        return QuizResultResponse.builder()
                .submissionId(saved.getId())
                .assignmentId(submission.getAssignment().getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .attemptNumber(saved.getAttemptNumber())
                .maxAttempts(quiz.getMaxAttempts())
                .score(saved.getScore())
                .totalPoints(saved.getTotalPoints())
                .percentageScore(saved.getPercentageScore())
                .passingScore(quiz.getPassingScore())
                .passed(saved.getPassed())
                .status(saved.getStatus())
                .startedAt(saved.getStartedAt())
                .submittedAt(saved.getSubmittedAt())
                .answerResults(answerResults)
                .build();
    }

    @Override
    public QuizResultResponse getSubmissionResult(Long submissionId, CustomUserDetails userDetails) {
        InternProfile intern = getInternProfileOrThrow(userDetails);
        QuizSubmission submission = quizSubmissionRepository.findByIdWithDetails(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt làm bài với ID: " + submissionId));

        if (!submission.getIntern().getId().equals(intern.getId())) {
            throw new BadRequestException("Bạn không phải là chủ sở hữu của lượt làm bài này");
        }

        // QUY TẮC BR-8 CỐT LÕI: YÊU CẦU HOÀN THÀNH BÀI THI TRƯỚC MỚI ĐƯỢC PHÉP XEM ĐÁP ÁN
        if (submission.getStatus() == SubmissionStatus.IN_PROGRESS) {
            throw new BadRequestException("Bài Quiz phải được hoàn thành nộp bài trước thì mới được phép xem đáp án và kết quả.");
        }

        Quiz quiz = submission.getQuiz();
        List<QuizAnswerResultResponse> answerResults = new ArrayList<>();

        if (submission.getAnswers() != null) {
            for (QuizSubmissionAnswer ans : submission.getAnswers()) {
                QuizQuestion q = ans.getQuestion();
                List<Long> selectedIds = parseSelectedOptionIds(ans.getSelectedOptionIds());
                List<Long> correctIds = (q.getOptions() != null)
                        ? q.getOptions().stream().filter(QuizQuestionOption::getIsCorrect).map(QuizQuestionOption::getId).toList()
                        : Collections.emptyList();

                List<QuizOptionResponse> optionResponses = (q.getOptions() != null)
                        ? q.getOptions().stream().map(opt -> QuizOptionResponse.builder()
                        .id(opt.getId())
                        .optionLabel(opt.getOptionLabel())
                        .optionText(opt.getOptionText())
                        .isCorrect(opt.getIsCorrect())
                        .orderIndex(opt.getOrderIndex())
                        .build()).toList()
                        : Collections.emptyList();

                answerResults.add(QuizAnswerResultResponse.builder()
                        .questionId(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .orderIndex(q.getOrderIndex())
                        .points(q.getPoints())
                        .isCorrect(ans.getIsCorrect())
                        .scoreAwarded(ans.getScoreAwarded())
                        .explanation(q.getExplanation())
                        .selectedOptionIds(selectedIds)
                        .correctOptionIds(correctIds)
                        .options(optionResponses)
                        .build());
            }
        }

        return QuizResultResponse.builder()
                .submissionId(submission.getId())
                .assignmentId(submission.getAssignment().getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .attemptNumber(submission.getAttemptNumber())
                .maxAttempts(quiz.getMaxAttempts())
                .score(submission.getScore())
                .totalPoints(submission.getTotalPoints())
                .percentageScore(submission.getPercentageScore())
                .passingScore(quiz.getPassingScore())
                .passed(submission.getPassed())
                .status(submission.getStatus())
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .answerResults(answerResults)
                .build();
    }

    // --- Private Helper Methods ---

    private List<Long> parseSelectedOptionIds(String idsStr) {
        if (idsStr == null || idsStr.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (String part : idsStr.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private InternProfile getInternProfileOrThrow(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new BadRequestException("Không tìm thấy thông tin tài khoản đăng nhập");
        }
        return internProfileRepository.findByUserId(userDetails.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh tương ứng với tài khoản này"));
    }
}

package org.example.internservice.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
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
import org.example.internservice.quiz.dto.request.CreateOptionDto;
import org.example.internservice.quiz.dto.request.CreateQuestionDto;
import org.example.internservice.quiz.dto.request.CreateQuizRequest;
import org.example.internservice.quiz.dto.request.GenerateAiQuizRequest;
import org.example.internservice.quiz.dto.request.UpdateQuizRequest;
import org.example.internservice.quiz.dto.response.QuizDetailResponse;
import org.example.internservice.quiz.dto.response.QuizInternReportDto;
import org.example.internservice.quiz.dto.response.QuizOptionResponse;
import org.example.internservice.quiz.dto.response.QuizQuestionResponse;
import org.example.internservice.quiz.dto.response.QuizReportResponse;
import org.example.internservice.quiz.dto.response.QuizSummaryResponse;
import org.example.internservice.quiz.entity.Quiz;
import org.example.internservice.quiz.entity.QuizAssignment;
import org.example.internservice.quiz.entity.QuizQuestion;
import org.example.internservice.quiz.entity.QuizQuestionOption;
import org.example.internservice.quiz.entity.QuizSubmission;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.example.internservice.quiz.entity.enums.AssignmentTargetType;
import org.example.internservice.quiz.entity.enums.QuestionType;
import org.example.internservice.quiz.entity.enums.QuizStatus;
import org.example.internservice.quiz.helper.QuizKanbanSyncHelper;
import org.example.internservice.quiz.repository.QuizAssignmentRepository;
import org.example.internservice.quiz.repository.QuizQuestionOptionRepository;
import org.example.internservice.quiz.repository.QuizQuestionRepository;
import org.example.internservice.quiz.repository.QuizRepository;
import org.example.internservice.quiz.repository.QuizSubmissionRepository;
import org.example.internservice.quiz.service.QuizMentorService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizMentorServiceImpl implements QuizMentorService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizQuestionOptionRepository quizQuestionOptionRepository;
    private final QuizAssignmentRepository quizAssignmentRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final InternshipProgramRepository programRepository;
    private final InternGroupRepository internGroupRepository;
    private final InternProfileRepository internProfileRepository;
    private final InternWeeklyReportRepository weeklyReportRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final AiServiceClient aiServiceClient;
    private final QuizKanbanSyncHelper kanbanSyncHelper;

    @Override
    @Transactional
    public QuizDetailResponse generateAiQuiz(GenerateAiQuizRequest request, CustomUserDetails userDetails) {
        log.info("Mentor yêu cầu AI sinh bài Quiz cho Program ID: {}, Group ID: {}, Week: {}",
                request.getProgramId(), request.getGroupId(), request.getWeekNumber());

        verifyMentorAccess(request.getProgramId(), userDetails);
        InternshipProgram program = getProgramOrThrow(request.getProgramId());
        Long mentorId = resolveMentorId(userDetails);

        // 1. Thu thập ngữ cảnh từ báo cáo tuần nếu có groupId & weekNumber
        String contextText = "";
        String groupName = null;
        if (request.getGroupId() != null) {
            InternGroup group = internGroupRepository.findById(request.getGroupId()).orElse(null);
            if (group != null) {
                groupName = group.getName();
                if (request.getWeekNumber() != null) {
                    contextText = extractWeeklyReportsContext(group.getId(), request.getWeekNumber());
                }
            }
        }

        // 2. Gọi ServiceAI qua FeignClient
        AiQuizGenerateClientRequest clientRequest = AiQuizGenerateClientRequest.builder()
                .topic(request.getTopic())
                .contextText(contextText)
                .programName(program.getName())
                .groupName(groupName)
                .questionCount(request.getQuestionCount())
                .difficulty(request.getDifficulty())
                .build();

        AiQuizGenerateClientResponse aiResponse = null;
        try {
            ApiResponse<AiQuizGenerateClientResponse> feignResponse = aiServiceClient.generateQuiz(clientRequest, null);
            if (feignResponse != null && feignResponse.getData() != null) {
                aiResponse = feignResponse.getData();
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi ServiceAI qua FeignClient: {}", e.getMessage(), e);
        }

        if (aiResponse == null || aiResponse.getQuestions() == null || aiResponse.getQuestions().isEmpty()) {
            throw new BadRequestException("Không thể sinh câu hỏi bài Quiz từ AI. Vui lòng thử lại hoặc nhập câu hỏi thủ công.");
        }

        // 3. Khởi tạo Quiz và Questions ở trạng thái DRAFT
        String quizTitle = aiResponse.getTitle() != null ? aiResponse.getTitle() : "Bài Quiz Ôn Tập Kiến Thức";
        int timeLimit = aiResponse.getEstimatedMinutes() != null && aiResponse.getEstimatedMinutes() > 0
                ? aiResponse.getEstimatedMinutes() : 15;

        Quiz quiz = Quiz.builder()
                .program(program)
                .mentorId(mentorId)
                .title(quizTitle)
                .description("Bài trắc nghiệm ôn tập do AI sinh ra dựa trên nội dung đào tạo của kỳ thực tập.")
                .topic(aiResponse.getTopic() != null ? aiResponse.getTopic() : request.getTopic())
                .difficulty(request.getDifficulty())
                .timeLimitMinutes(timeLimit)
                .passingScore(70)
                .totalQuestions(aiResponse.getQuestions().size())
                .totalPoints(aiResponse.getQuestions().size() * 10)
                .maxAttempts(1)
                .status(QuizStatus.DRAFT)
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);

        int qIdx = 1;
        for (AiQuizQuestionClientDto qDto : aiResponse.getQuestions()) {
            QuizQuestion question = QuizQuestion.builder()
                    .quiz(savedQuiz)
                    .questionText(qDto.getQuestionText())
                    .questionType(qDto.getQuestionType() != null ? qDto.getQuestionType() : QuestionType.SINGLE_CHOICE)
                    .orderIndex(qIdx++)
                    .points(qDto.getPoints() != null ? qDto.getPoints() : 10)
                    .explanation(qDto.getExplanation())
                    .build();

            QuizQuestion savedQuestion = quizQuestionRepository.save(question);

            if (qDto.getOptions() != null) {
                int optIdx = 1;
                for (AiQuizOptionClientDto optDto : qDto.getOptions()) {
                    QuizQuestionOption option = QuizQuestionOption.builder()
                            .question(savedQuestion)
                            .optionLabel(optDto.getOptionLabel() != null ? optDto.getOptionLabel() : String.valueOf((char) ('A' + optIdx - 1)))
                            .optionText(optDto.getOptionText())
                            .isCorrect(Boolean.TRUE.equals(optDto.getIsCorrect()))
                            .orderIndex(optIdx++)
                            .build();
                    quizQuestionOptionRepository.save(option);
                    savedQuestion.getOptions().add(option);
                }
            }
            savedQuiz.getQuestions().add(savedQuestion);
        }

        log.info("Tạo thành công bản nháp Quiz ID: {} với {} câu hỏi", savedQuiz.getId(), savedQuiz.getQuestions().size());
        return mapToDetailResponse(savedQuiz);
    }

    @Override
    @Transactional
    public QuizDetailResponse createQuiz(CreateQuizRequest request, CustomUserDetails userDetails) {
        verifyMentorAccess(request.getProgramId(), userDetails);
        InternshipProgram program = getProgramOrThrow(request.getProgramId());
        Long mentorId = resolveMentorId(userDetails);

        Quiz quiz = Quiz.builder()
                .program(program)
                .mentorId(mentorId)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .topic(request.getTopic())
                .difficulty(request.getDifficulty())
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .passingScore(request.getPassingScore())
                .maxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : 1)
                .totalQuestions(request.getQuestions() != null ? request.getQuestions().size() : 0)
                .totalPoints((request.getQuestions() != null ? request.getQuestions().size() : 0) * 10)
                .status(QuizStatus.DRAFT)
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);
        saveQuestionsFromDto(savedQuiz, request.getQuestions());

        return mapToDetailResponse(savedQuiz);
    }

    @Override
    @Transactional
    public QuizDetailResponse updateQuiz(Long quizId, UpdateQuizRequest request, CustomUserDetails userDetails) {
        Quiz quiz = quizRepository.findByIdWithDetails(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài Quiz với ID: " + quizId));

        verifyMentorAccess(quiz.getProgram().getId(), userDetails);

        if (quiz.getStatus() == QuizStatus.PUBLISHED) {
            throw new BadRequestException("Không thể chỉnh sửa bài Quiz đã xuất bản và giao cho học viên.");
        }

        quiz.setTitle(request.getTitle().trim());
        quiz.setDescription(request.getDescription());
        quiz.setTopic(request.getTopic());
        if (request.getDifficulty() != null) quiz.setDifficulty(request.getDifficulty());
        if (request.getTimeLimitMinutes() != null) quiz.setTimeLimitMinutes(request.getTimeLimitMinutes());
        if (request.getPassingScore() != null) quiz.setPassingScore(request.getPassingScore());
        if (request.getMaxAttempts() != null) quiz.setMaxAttempts(request.getMaxAttempts());

        // Xóa câu hỏi cũ và lưu lại danh sách câu hỏi mới
        quiz.getQuestions().clear();
        quizRepository.saveAndFlush(quiz);

        if (request.getQuestions() != null) {
            saveQuestionsFromDto(quiz, request.getQuestions());
            quiz.setTotalQuestions(request.getQuestions().size());
            quiz.setTotalPoints(request.getQuestions().size() * 10);
        }

        Quiz updated = quizRepository.save(quiz);
        return mapToDetailResponse(updated);
    }

    @Override
    public QuizDetailResponse getQuizDetail(Long quizId, CustomUserDetails userDetails) {
        Quiz quiz = quizRepository.findByIdWithDetails(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài Quiz với ID: " + quizId));
        verifyMentorAccess(quiz.getProgram().getId(), userDetails);
        return mapToDetailResponse(quiz);
    }

    @Override
    public List<QuizSummaryResponse> getQuizzesByProgram(Long programId, CustomUserDetails userDetails) {
        verifyMentorAccess(programId, userDetails);
        List<Quiz> quizzes = quizRepository.findByProgramIdOrderByCreatedAtDesc(programId);
        return quizzes.stream().map(q -> {
            int assignCount = quizAssignmentRepository.findByQuizId(q.getId()).size();
            return QuizSummaryResponse.builder()
                    .id(q.getId())
                    .programId(q.getProgram().getId())
                    .mentorId(q.getMentorId())
                    .title(q.getTitle())
                    .topic(q.getTopic())
                    .difficulty(q.getDifficulty())
                    .timeLimitMinutes(q.getTimeLimitMinutes())
                    .passingScore(q.getPassingScore())
                    .totalQuestions(q.getTotalQuestions())
                    .totalPoints(q.getTotalPoints())
                    .maxAttempts(q.getMaxAttempts())
                    .status(q.getStatus())
                    .assignmentCount(assignCount)
                    .createdAt(q.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public void assignQuiz(Long quizId, AssignQuizRequest request, CustomUserDetails userDetails) {
        log.info("Phân công bài Quiz ID: {} cho target: {} (ID: {})", quizId, request.getTargetType(), request.getTargetId());

        Quiz quiz = quizRepository.findByIdWithDetails(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài Quiz với ID: " + quizId));

        verifyMentorAccess(quiz.getProgram().getId(), userDetails);

        if (quiz.getQuestions().isEmpty()) {
            throw new BadRequestException("Không thể giao bài Quiz chưa có câu hỏi nào.");
        }

        // Validate DueDate
        if (request.getDueDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Hạn chót nộp bài không được ở trong quá khứ");
        }
        if (quiz.getProgram().getEndDate() != null && request.getDueDate().isAfter(quiz.getProgram().getEndDate())) {
            throw new BadRequestException("Hạn chót không được vượt quá ngày kết thúc kỳ thực tập (" + quiz.getProgram().getEndDate() + ")");
        }

        // Cập nhật cấu hình nếu có
        if (request.getTimeLimitMinutes() != null) quiz.setTimeLimitMinutes(request.getTimeLimitMinutes());
        if (request.getPassingScore() != null) quiz.setPassingScore(request.getPassingScore());
        if (request.getMaxAttempts() != null) quiz.setMaxAttempts(request.getMaxAttempts());
        quiz.setStatus(QuizStatus.PUBLISHED);
        quizRepository.save(quiz);

        Long mentorId = resolveMentorId(userDetails);

        // Phân phối bài thi
        if (request.getTargetType() == AssignmentTargetType.INDIVIDUAL) {
            InternProfile intern = internProfileRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên với ID: " + request.getTargetId()));
            if (!intern.getProgram().getId().equals(quiz.getProgram().getId())) {
                throw new BadRequestException("Học viên không thuộc chương trình thực tập này");
            }

            assignToSingleIntern(quiz, intern, null, request.getDueDate(), mentorId, request.getCreateKanbanTask());
        } else if (request.getTargetType() == AssignmentTargetType.GROUP) {
            InternGroup group = internGroupRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + request.getTargetId()));
            if (!group.getProgram().getId().equals(quiz.getProgram().getId())) {
                throw new BadRequestException("Nhóm không thuộc chương trình thực tập này");
            }

            List<InternProfile> groupMembers = internProfileRepository.findByGroupId(group.getId());
            if (groupMembers.isEmpty()) {
                throw new BadRequestException("Nhóm '" + group.getName() + "' hiện chưa có thành viên nào để giao bài.");
            }

            for (InternProfile member : groupMembers) {
                assignToSingleIntern(quiz, member, group, request.getDueDate(), mentorId, request.getCreateKanbanTask());
            }
        }
    }

    @Override
    public QuizReportResponse getQuizReport(Long quizId, CustomUserDetails userDetails) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài Quiz với ID: " + quizId));
        verifyMentorAccess(quiz.getProgram().getId(), userDetails);

        List<QuizAssignment> assignments = quizAssignmentRepository.findByQuizId(quizId);
        List<QuizInternReportDto> internReports = new ArrayList<>();

        int completedCount = 0;
        int passedCount = 0;
        double totalScores = 0.0;
        int scoredCount = 0;

        for (QuizAssignment assign : assignments) {
            if (assign.getIntern() == null) continue;
            InternProfile intern = assign.getIntern();

            List<QuizSubmission> submissions = quizSubmissionRepository.findSubmissionsOrderByScoreDesc(assign.getId(), intern.getId());
            int attempts = submissions.size();
            Integer highestScore = null;
            boolean passed = false;
            java.time.LocalDateTime latestSubmittedAt = null;

            if (!submissions.isEmpty()) {
                QuizSubmission best = submissions.get(0);
                highestScore = best.getScore();
                passed = best.getPassed();
                latestSubmittedAt = best.getSubmittedAt();
                completedCount++;
                if (passed) passedCount++;
                totalScores += highestScore;
                scoredCount++;
            }

            internReports.add(QuizInternReportDto.builder()
                    .internId(intern.getId())
                    .internCode(intern.getInternCode())
                    .fullName(intern.getFullName())
                    .email(intern.getEmail())
                    .groupName(intern.getGroup() != null ? intern.getGroup().getName() : "Chưa phân nhóm")
                    .attemptsCount(attempts)
                    .highestScore(highestScore)
                    .totalPoints(quiz.getTotalPoints())
                    .passed(passed)
                    .latestSubmittedAt(latestSubmittedAt)
                    .build());
        }

        double avgScore = scoredCount > 0 ? Math.round((totalScores / scoredCount) * 10.0) / 10.0 : 0.0;

        return QuizReportResponse.builder()
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .totalAssigned(assignments.size())
                .completedCount(completedCount)
                .passedCount(passedCount)
                .averageScore(avgScore)
                .internReports(internReports)
                .build();
    }

    // --- Private Helper Methods ---

    private void assignToSingleIntern(Quiz quiz, InternProfile intern, InternGroup group, LocalDate dueDate, Long mentorId, Boolean createKanban) {
        Optional<QuizAssignment> existing = quizAssignmentRepository.findByQuizIdAndInternId(quiz.getId(), intern.getId());
        QuizAssignment assignment;
        if (existing.isPresent()) {
            assignment = existing.get();
            assignment.setDueDate(dueDate);
            assignment.setStatus(AssignmentStatus.ACTIVE);
        } else {
            assignment = QuizAssignment.builder()
                    .quiz(quiz)
                    .targetType(group != null ? AssignmentTargetType.GROUP : AssignmentTargetType.INDIVIDUAL)
                    .intern(intern)
                    .group(group)
                    .assignedByMentorId(mentorId)
                    .dueDate(dueDate)
                    .status(AssignmentStatus.ACTIVE)
                    .build();
        }

        QuizAssignment savedAssignment = quizAssignmentRepository.save(assignment);

        if (Boolean.TRUE.equals(createKanban)) {
            kanbanSyncHelper.createKanbanTaskForQuizAssignment(savedAssignment, quiz);
            quizAssignmentRepository.save(savedAssignment);
        }
    }

    private String extractWeeklyReportsContext(Long groupId, Integer weekNumber) {
        List<InternProfile> members = internProfileRepository.findByGroupId(groupId);
        if (members.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (InternProfile m : members) {
            Optional<InternWeeklyReport> reportOpt = weeklyReportRepository.findByInternCodeAndWeekNumber(m.getInternCode(), weekNumber);
            if (reportOpt.isPresent()) {
                InternWeeklyReport r = reportOpt.get();
                if (r.getStatus() == WeeklyReportStatus.SUBMITTED || r.getStatus() == WeeklyReportStatus.REVIEWED) {
                    sb.append("- Học viên: ").append(m.getFullName()).append(" (").append(m.getInternCode()).append("):\n");
                    if (r.getLearningsAndKnowledge() != null) {
                        sb.append("  + Kiến thức đã học: ").append(r.getLearningsAndKnowledge()).append("\n");
                    }
                    if (r.getCompletedTasksSummary() != null) {
                        sb.append("  + Công việc hoàn thành: ").append(r.getCompletedTasksSummary()).append("\n");
                    }
                    if (r.getDifficultiesAndChallenges() != null) {
                        sb.append("  + Khó khăn gặp phải: ").append(r.getDifficultiesAndChallenges()).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private void saveQuestionsFromDto(Quiz quiz, List<CreateQuestionDto> questionDtos) {
        if (questionDtos == null) return;
        int qIdx = 1;
        for (CreateQuestionDto qDto : questionDtos) {
            QuizQuestion question = QuizQuestion.builder()
                    .quiz(quiz)
                    .questionText(qDto.getQuestionText().trim())
                    .questionType(qDto.getQuestionType() != null ? qDto.getQuestionType() : QuestionType.SINGLE_CHOICE)
                    .orderIndex(qIdx++)
                    .points(qDto.getPoints() != null ? qDto.getPoints() : 10)
                    .explanation(qDto.getExplanation())
                    .build();

            QuizQuestion savedQuestion = quizQuestionRepository.save(question);

            if (qDto.getOptions() != null) {
                int optIdx = 1;
                for (CreateOptionDto optDto : qDto.getOptions()) {
                    QuizQuestionOption option = QuizQuestionOption.builder()
                            .question(savedQuestion)
                            .optionLabel(optDto.getOptionLabel() != null ? optDto.getOptionLabel() : String.valueOf((char) ('A' + optIdx - 1)))
                            .optionText(optDto.getOptionText().trim())
                            .isCorrect(Boolean.TRUE.equals(optDto.getIsCorrect()))
                            .orderIndex(optIdx++)
                            .build();
                    quizQuestionOptionRepository.save(option);
                    savedQuestion.getOptions().add(option);
                }
            }
            quiz.getQuestions().add(savedQuestion);
        }
    }

    private QuizDetailResponse mapToDetailResponse(Quiz quiz) {
        List<QuizQuestionResponse> questionResponses = new ArrayList<>();
        if (quiz.getQuestions() != null) {
            for (QuizQuestion q : quiz.getQuestions()) {
                List<QuizOptionResponse> optionResponses = new ArrayList<>();
                if (q.getOptions() != null) {
                    for (QuizQuestionOption opt : q.getOptions()) {
                        optionResponses.add(QuizOptionResponse.builder()
                                .id(opt.getId())
                                .optionLabel(opt.getOptionLabel())
                                .optionText(opt.getOptionText())
                                .isCorrect(opt.getIsCorrect())
                                .orderIndex(opt.getOrderIndex())
                                .build());
                    }
                }
                questionResponses.add(QuizQuestionResponse.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .orderIndex(q.getOrderIndex())
                        .points(q.getPoints())
                        .explanation(q.getExplanation())
                        .options(optionResponses)
                        .build());
            }
        }

        return QuizDetailResponse.builder()
                .id(quiz.getId())
                .programId(quiz.getProgram() != null ? quiz.getProgram().getId() : null)
                .programName(quiz.getProgram() != null ? quiz.getProgram().getName() : null)
                .mentorId(quiz.getMentorId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .topic(quiz.getTopic())
                .difficulty(quiz.getDifficulty())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .passingScore(quiz.getPassingScore())
                .totalQuestions(quiz.getTotalQuestions())
                .totalPoints(quiz.getTotalPoints())
                .maxAttempts(quiz.getMaxAttempts())
                .status(quiz.getStatus())
                .createdAt(quiz.getCreatedAt())
                .updatedAt(quiz.getUpdatedAt())
                .questions(questionResponses)
                .build();
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
            boolean hasInternInProgram = internProfileRepository.findByProgramId(programId).stream()
                    .anyMatch(i -> (i.getMentorId() != null && (i.getMentorId().equals(mentorIdentifier) || (userId != null && i.getMentorId().equals(userId)))));
            if (hasInternInProgram) {
                return;
            }
            throw new AccessDeniedException("Bạn không được phân công phụ trách chương trình thực tập này");
        }
    }

    private Long resolveMentorId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            return 1L;
        }
        return mentorProfileRepository.findByUserId(userDetails.getUserId())
                .map(MentorProfile::getId)
                .orElse(userDetails.getUserId());
    }

    private InternshipProgram getProgramOrThrow(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình với ID: " + programId));
    }
}

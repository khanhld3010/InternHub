package org.example.aiservice.modules.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.client.InternServiceClient;
import org.example.aiservice.client.dto.DocumentClientDto;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.client.dto.ProgramClientDto;
import org.example.aiservice.core.DocumentTextExtractor;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.entity.AiFeatureProfile;
import org.example.aiservice.entity.AiGlobalConfig;
import org.example.aiservice.entity.AiSystemConfig;
import org.example.aiservice.modules.interview.dto.request.GenerateInterviewQuestionsRequest;
import org.example.aiservice.modules.interview.dto.request.UpdateInterviewQuestionSetRequest;
import org.example.aiservice.modules.interview.dto.response.InterviewQuestionSetResponse;
import org.example.aiservice.modules.interview.entity.InterviewQuestion;
import org.example.aiservice.modules.interview.entity.InterviewQuestionSet;
import org.example.aiservice.modules.interview.enums.QuestionCategory;
import org.example.aiservice.modules.interview.enums.QuestionDifficulty;
import org.example.aiservice.modules.interview.enums.QuestionScope;
import org.example.aiservice.modules.interview.enums.QuestionSetStatus;
import org.example.aiservice.modules.interview.repository.InterviewQuestionRepository;
import org.example.aiservice.modules.interview.repository.InterviewQuestionSetRepository;
import org.example.aiservice.modules.interview.service.AiInterviewPromptBuilder;
import org.example.aiservice.modules.interview.service.AiInterviewQuestionService;
import org.example.aiservice.modules.screening.entity.InternAiEvaluation;
import org.example.aiservice.modules.screening.repository.AiEvaluationRepository;
import org.example.aiservice.repository.AiFeatureProfileRepository;
import org.example.aiservice.repository.AiGlobalConfigRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.example.aiservice.service.AiConfigAdminService;
import org.example.aiservice.modules.interview.dto.response.QuestionGenerationJobResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInterviewQuestionServiceImpl implements AiInterviewQuestionService {

    private final InterviewQuestionSetRepository questionSetRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final AiInterviewPromptBuilder promptBuilder;
    private final GeminiApiClient geminiApiClient;
    private final AiConfigAdminService configAdminService;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final InternServiceClient internServiceClient;
    private final DocumentTextExtractor documentTextExtractor;
    private final ObjectMapper objectMapper;
    private final AiFeatureProfileRepository featureProfileRepository;
    private final AiGlobalConfigRepository globalConfigRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;

    private static final String REDIS_JOB_PREFIX = "ai:interview:job:";
    private static final Duration JOB_TTL = Duration.ofHours(24);
    private final Map<String, QuestionGenerationJobResponse> activeJobs = new ConcurrentHashMap<>();



    @Override
    @Transactional
    public InterviewQuestionSetResponse generateQuestions(
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    ) {
        log.info("Bắt đầu sinh câu hỏi phỏng vấn: programId={}, internId={}, role={}",
                request.getProgramId(), request.getInternId(), request.getTargetRole());

        // 1. Lấy thông tin chương trình thực tập (JD)
        ProgramClientDto program = null;
        try {
            ApiResponse<ProgramClientDto> progRes = internServiceClient.getProgramById(request.getProgramId(), authHeader);
            if (progRes != null && progRes.getData() != null) {
                program = progRes.getData();
            }
        } catch (Exception e) {
            log.warn("Không thể lấy thông tin kỳ thực tập id={}: {}", request.getProgramId(), e.getMessage());
        }

        // 2. Lấy thông tin ứng viên, CV và kết quả AI Screening nếu có internId
        InternProfileClientDto intern = null;
        String cvText = "";
        InternAiEvaluation screening = null;
        QuestionScope scope = QuestionScope.PROGRAM;

        if (request.getInternId() != null) {
            scope = QuestionScope.CANDIDATE;
            try {
                ApiResponse<InternProfileClientDto> internRes = internServiceClient.getInternById(request.getInternId(), authHeader);
                if (internRes != null && internRes.getData() != null) {
                    intern = internRes.getData();
                }
            } catch (Exception e) {
                log.warn("Không thể lấy thông tin ứng viên id={}: {}", request.getInternId(), e.getMessage());
            }

            // Tải CV nếu có mã học viên
            if (intern != null && intern.getInternCode() != null) {
                try {
                    ApiResponse<List<DocumentClientDto>> docsRes = internServiceClient.getDocumentsByInternCode(intern.getInternCode(), authHeader);
                    if (docsRes != null && docsRes.getData() != null) {
                        Optional<DocumentClientDto> cvDocOpt = docsRes.getData().stream()
                                .filter(d -> "CV".equalsIgnoreCase(d.getDocumentType()) || "RESUME".equalsIgnoreCase(d.getDocumentType()))
                                .findFirst();
                        if (cvDocOpt.isPresent()) {
                            DocumentClientDto cvDoc = cvDocOpt.get();
                            ResponseEntity<byte[]> fileRes = internServiceClient.downloadDocument(cvDoc.getId(), authHeader);
                            if (fileRes != null && fileRes.getBody() != null) {
                                cvText = documentTextExtractor.extractText(fileRes.getBody(), cvDoc.getOriginalFileName());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Không thể tải CV cho ứng viên {}: {}", intern.getInternCode(), e.getMessage());
                }
            }

            // Đọc kết quả AI Screening
            if (Boolean.TRUE.equals(request.getIncludeScreeningDossier())) {
                screening = aiEvaluationRepository.findTopByInternIdOrderByCreatedAtDesc(request.getInternId())
                        .orElse(null);
            }
        }

        String targetRole = request.getTargetRole();
        if ((targetRole == null || targetRole.isBlank()) && program != null) {
            targetRole = program.getName();
        }
        if (targetRole == null || targetRole.isBlank()) {
            targetRole = "Thực tập sinh Công nghệ";
        }

        int totalQ = (request.getTotalQuestions() != null && request.getTotalQuestions() > 0)
                ? request.getTotalQuestions() : 8;

        // 3. Chuẩn bị prompt và gọi Gemini LLM
        String systemInstruction = promptBuilder.buildSystemInstruction();
        String userPrompt = promptBuilder.buildUserPrompt(
                program, intern, cvText, screening, targetRole, totalQ, request.getCustomRequirements()
        );

        String generatedJson = callGeminiForQuestions(systemInstruction, userPrompt);

        // 4. Parse JSON và lưu vào Database
        return parseAndPersistQuestionSet(
                generatedJson,
                request.getProgramId(),
                request.getInternId(),
                scope,
                targetRole,
                screening,
                userId,
                userName,
                intern
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewQuestionSetResponse getQuestionSetById(Long id) {
        InterviewQuestionSet set = questionSetRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ câu hỏi id=" + id));
        return mapToResponse(set);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestionSetResponse> getQuestionSetsByProgram(Long programId) {
        return questionSetRepository.findByProgramIdOrderByCreatedAtDesc(programId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestionSetResponse> getQuestionSetsByIntern(Long internId) {
        return questionSetRepository.findByInternIdOrderByCreatedAtDesc(internId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public InterviewQuestionSetResponse updateQuestionSet(Long id, UpdateInterviewQuestionSetRequest request) {
        InterviewQuestionSet set = questionSetRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ câu hỏi id=" + id));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            set.setTitle(request.getTitle().trim());
        }
        if (request.getStatus() != null) {
            set.setStatus(request.getStatus());
        }

        // Cập nhật câu hỏi nếu có
        if (request.getQuestions() != null) {
            Map<Long, UpdateInterviewQuestionSetRequest.QuestionItemDto> updateMap = new HashMap<>();
            request.getQuestions().forEach(q -> {
                if (q.getId() != null) updateMap.put(q.getId(), q);
            });

            for (InterviewQuestion q : set.getQuestions()) {
                if (updateMap.containsKey(q.getId())) {
                    UpdateInterviewQuestionSetRequest.QuestionItemDto dto = updateMap.get(q.getId());
                    if (dto.getQuestionText() != null) q.setQuestionText(dto.getQuestionText());
                    if (dto.getExpectedAnswer() != null) q.setExpectedAnswer(dto.getExpectedAnswer());
                    if (dto.getEvaluationCriteria() != null) q.setEvaluationCriteria(dto.getEvaluationCriteria());
                    if (dto.getSuggestedScoreWeight() != null) q.setSuggestedScoreWeight(dto.getSuggestedScoreWeight());
                    if (dto.getDisplayOrder() != null) q.setDisplayOrder(dto.getDisplayOrder());
                }
            }
        }

        InterviewQuestionSet saved = questionSetRepository.save(set);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteQuestionSet(Long id) {
        if (!questionSetRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ câu hỏi id=" + id);
        }
        questionSetRepository.deleteById(id);
    }

    @Override
    @Transactional
    public InterviewQuestionSetResponse.InterviewQuestionResponse regenerateSingleQuestion(Long setId, Long questionId) {
        InterviewQuestionSet set = questionSetRepository.findByIdWithQuestions(setId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ câu hỏi id=" + setId));

        InterviewQuestion question = set.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi id=" + questionId));

        String prompt = promptBuilder.buildSingleQuestionRegeneratePrompt(
                set.getTitle(),
                set.getTargetRole(),
                question.getCategory().name(),
                question.getDifficulty().name(),
                question.getQuestionText()
        );

        String jsonResponse = callGeminiForQuestions("Bạn là Trợ lý tuyển dụng InternHub. Hãy sinh đúng 1 câu hỏi định dạng JSON nguyên bản.", prompt);

        try {
            JsonNode root = objectMapper.readTree(cleanJsonMarkdown(jsonResponse));
            if (root.has("questionText")) {
                question.setQuestionText(root.path("questionText").asText(question.getQuestionText()));
            }
            if (root.has("expectedAnswer")) {
                question.setExpectedAnswer(root.path("expectedAnswer").asText(question.getExpectedAnswer()));
            }
            if (root.has("evaluationCriteria")) {
                question.setEvaluationCriteria(root.path("evaluationCriteria").asText(question.getEvaluationCriteria()));
            }
            if (root.has("focusArea")) {
                question.setFocusArea(root.path("focusArea").asText(question.getFocusArea()));
            }
            questionRepository.save(question);
            return mapToQuestionResponse(question);
        } catch (Exception e) {
            log.error("Lỗi khi parse câu hỏi mới: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đổi câu hỏi lúc này. Vui lòng thử lại sau.");
        }
    }

    @Override
    public byte[] exportInterviewEvaluationSheetPdf(Long setId) {
        InterviewQuestionSet set = questionSetRepository.findByIdWithQuestions(setId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ câu hỏi id=" + setId));
        // Logic tạo PDF chuẩn in ấn sẽ được gọi tại đây
        return generateSimplePdfSheet(set);
    }

    private String callGeminiForQuestions(String systemInstruction, String userPrompt) {
        String rawApiKey = getResolvedApiKey();
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.info("Dùng fallback mock JSON khi chưa cấu hình Gemini API Key thật");
            return generateMockQuestionsJson();
        }

        String modelName = getResolvedModelName();
        BigDecimal temp = getResolvedTemperature();
        int maxTokens = getResolvedMaxTokens();

        log.info("Gọi Google Gemini API sinh câu hỏi phỏng vấn: model={}, temp={}, maxTokens={}", modelName, temp, maxTokens);

        try {
            String response = geminiApiClient.generateContent(
                    rawApiKey,
                    modelName,
                    systemInstruction,
                    Collections.emptyList(),
                    userPrompt,
                    temp,
                    maxTokens
            );

            if (response == null || response.isBlank() || response.equals(GeminiApiClient.FALLBACK_FRIENDLY_MESSAGE) || !looksLikeJson(response)) {
                log.warn("Gemini không trả về nội dung JSON hợp lệ (nội dung: '{}'). Tự động kích hoạt bộ câu hỏi tiêu chuẩn.", response);
                return generateMockQuestionsJson();
            }

            return response;
        } catch (Exception e) {
            log.warn("Gemini API gặp sự cố, chuyển sang kết quả dự phòng chuẩn: {}", e.getMessage());
            return generateMockQuestionsJson();
        }
    }

    private String getResolvedApiKey() {
        AiSystemConfig config = configAdminService.getOrCreateDefaultConfig();
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank()) {
            try {
                String key = encryptionUtil.decrypt(config.getApiKeyEncrypted(), config.getKeyVersion());
                if (key != null && !key.isBlank() && !key.startsWith("mock-")) {
                    return key.trim();
                }
            } catch (Exception e) {
                log.error("Lỗi giải mã API key từ AiSystemConfig: {}", e.getMessage());
            }
        }

        return globalConfigRepository.findFirstByOrderByIdAsc()
                .map(global -> {
                    if (global.getApiKeyEncrypted() != null && !global.getApiKeyEncrypted().isBlank()) {
                        try {
                            String key = encryptionUtil.decrypt(global.getApiKeyEncrypted(), global.getKeyVersion());
                            return (key != null && !key.isBlank() && !key.startsWith("mock-")) ? key.trim() : null;
                        } catch (Exception e) {
                            log.error("Lỗi giải mã API key từ AiGlobalConfig: {}", e.getMessage());
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    private String getResolvedModelName() {
        Optional<AiFeatureProfile> profileOpt = featureProfileRepository.findByFeatureKey("INTERVIEW_GENERATOR");
        if (profileOpt.isPresent() && Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            String profileModel = profileOpt.get().getModelName();
            if (profileModel != null && !profileModel.isBlank() && !"gemini-1.5-flash".equalsIgnoreCase(profileModel)) {
                return profileModel.trim();
            }
        }
        AiSystemConfig config = configAdminService.getOrCreateDefaultConfig();
        if (config.getModelName() != null && !config.getModelName().isBlank() && !"gemini-1.5-flash".equalsIgnoreCase(config.getModelName())) {
            return config.getModelName().trim();
        }
        return "gemini-3.5-flash-lite";
    }

    private BigDecimal getResolvedTemperature() {
        return featureProfileRepository.findByFeatureKey("INTERVIEW_GENERATOR")
                .map(AiFeatureProfile::getTemperature)
                .orElse(BigDecimal.valueOf(0.65));
    }

    private int getResolvedMaxTokens() {
        return featureProfileRepository.findByFeatureKey("INTERVIEW_GENERATOR")
                .map(AiFeatureProfile::getMaxTokens)
                .orElse(3072);
    }

    private boolean looksLikeJson(String text) {
        if (text == null) return false;
        String cleaned = cleanJsonMarkdown(text).trim();
        return (cleaned.startsWith("{") && cleaned.endsWith("}")) ||
               (cleaned.startsWith("[") && cleaned.endsWith("]"));
    }

    private InterviewQuestionSetResponse parseAndPersistQuestionSet(
            String jsonText,
            Long programId,
            Long internId,
            QuestionScope scope,
            String targetRole,
            InternAiEvaluation screening,
            String userId,
            String userName,
            InternProfileClientDto intern
    ) {
        JsonNode root;
        try {
            root = objectMapper.readTree(cleanJsonMarkdown(jsonText));
            if (!root.has("questions") || !root.path("questions").isArray() || root.path("questions").isEmpty()) {
                log.warn("Cấu trúc JSON từ AI thiếu danh sách câu hỏi: {}. Tự động dùng bộ câu hỏi dự phòng.", jsonText);
                root = objectMapper.readTree(cleanJsonMarkdown(generateMockQuestionsJson()));
            }
        } catch (Exception e) {
            log.warn("Không thể parse JSON từ AI (nội dung: '{}'). Tự động fallback: {}", jsonText, e.getMessage());
            try {
                root = objectMapper.readTree(cleanJsonMarkdown(generateMockQuestionsJson()));
            } catch (Exception ex) {
                log.error("Lỗi khi parse mock JSON: {}", ex.getMessage(), ex);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý nội dung câu hỏi từ AI. Vui lòng thử lại.");
            }
        }

        try {
            String title = root.path("title").asText(
                    scope == QuestionScope.CANDIDATE && intern != null
                            ? "Bộ câu hỏi phỏng vấn " + targetRole + " - Ứng viên " + intern.getFullName()
                            : "Bộ câu hỏi phỏng vấn chuẩn hóa - " + targetRole
            );

            InterviewQuestionSet set = InterviewQuestionSet.builder()
                    .title(title)
                    .programId(programId)
                    .internId(internId)
                    .scope(scope)
                    .targetRole(targetRole)
                    .status(QuestionSetStatus.DRAFT)
                    .screeningResultId(screening != null ? screening.getId() : null)
                    .screeningMatchScore(screening != null && screening.getOverallScore() != null
                            ? BigDecimal.valueOf(screening.getOverallScore()) : null)
                    .createdByUserId(userId != null ? userId : "SYSTEM")
                    .createdByName(userName != null ? userName : "HR/Mentor")
                    .build();

            JsonNode questionsNode = root.path("questions");
            if (questionsNode.isArray()) {
                int order = 1;
                for (JsonNode qNode : questionsNode) {
                    QuestionCategory cat = parseCategory(qNode.path("category").asText());
                    QuestionDifficulty diff = parseDifficulty(qNode.path("difficulty").asText());

                    InterviewQuestion q = InterviewQuestion.builder()
                            .category(cat)
                            .difficulty(diff)
                            .questionText(qNode.path("questionText").asText("Câu hỏi phỏng vấn"))
                            .expectedAnswer(qNode.path("expectedAnswer").asText())
                            .evaluationCriteria(qNode.path("evaluationCriteria").asText())
                            .suggestedScoreWeight(qNode.path("suggestedScoreWeight").asInt(10))
                            .focusArea(qNode.path("focusArea").asText())
                            .displayOrder(order++)
                            .build();

                    set.addQuestion(q);
                }
            }

            InterviewQuestionSet saved = questionSetRepository.save(set);
            return mapToResponse(saved);

        } catch (Exception e) {
            log.error("Lỗi khi lưu bộ câu hỏi vào cơ sở dữ liệu: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu bộ câu hỏi. Vui lòng thử lại.");
        }
    }

    private String cleanJsonMarkdown(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private QuestionCategory parseCategory(String str) {
        if (str == null) return QuestionCategory.TECHNICAL_FOUNDATION;
        try {
            return QuestionCategory.valueOf(str.toUpperCase().trim());
        } catch (Exception e) {
            if (str.toUpperCase().contains("PROJECT")) return QuestionCategory.PROJECT_DEEP_DIVE;
            if (str.toUpperCase().contains("BEHAVIOR") || str.toUpperCase().contains("CULTURE")) return QuestionCategory.BEHAVIORAL_AND_CULTURE_FIT;
            return QuestionCategory.TECHNICAL_FOUNDATION;
        }
    }

    private QuestionDifficulty parseDifficulty(String str) {
        if (str == null) return QuestionDifficulty.MEDIUM;
        try {
            return QuestionDifficulty.valueOf(str.toUpperCase().trim());
        } catch (Exception e) {
            return QuestionDifficulty.MEDIUM;
        }
    }

    private InterviewQuestionSetResponse mapToResponse(InterviewQuestionSet set) {
        List<InterviewQuestionSetResponse.InterviewQuestionResponse> qList = (set.getQuestions() != null)
                ? set.getQuestions().stream().map(this::mapToQuestionResponse).toList()
                : Collections.emptyList();

        return InterviewQuestionSetResponse.builder()
                .id(set.getId())
                .title(set.getTitle())
                .programId(set.getProgramId())
                .internId(set.getInternId())
                .scope(set.getScope())
                .targetRole(set.getTargetRole())
                .status(set.getStatus())
                .totalQuestions(set.getTotalQuestions())
                .screeningResultId(set.getScreeningResultId())
                .screeningMatchScore(set.getScreeningMatchScore())
                .createdByUserId(set.getCreatedByUserId())
                .createdByName(set.getCreatedByName())
                .createdAt(set.getCreatedAt())
                .updatedAt(set.getUpdatedAt())
                .questions(qList)
                .build();
    }

    private InterviewQuestionSetResponse.InterviewQuestionResponse mapToQuestionResponse(InterviewQuestion q) {
        return InterviewQuestionSetResponse.InterviewQuestionResponse.builder()
                .id(q.getId())
                .category(q.getCategory())
                .difficulty(q.getDifficulty())
                .questionText(q.getQuestionText())
                .expectedAnswer(q.getExpectedAnswer())
                .evaluationCriteria(q.getEvaluationCriteria())
                .suggestedScoreWeight(q.getSuggestedScoreWeight())
                .displayOrder(q.getDisplayOrder())
                .focusArea(q.getFocusArea())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    private String generateMockQuestionsJson() {
        return """
                {
                  "title": "Bộ câu hỏi phỏng vấn chuẩn hóa (Mock Fallback)",
                  "questions": [
                    {
                      "category": "TECHNICAL_FOUNDATION",
                      "difficulty": "EASY",
                      "questionText": "Hãy giải thích vòng đời của một Bean trong Spring Boot và sự khác biệt giữa @Component và @Service?",
                      "expectedAnswer": "Nêu được các bước khởi tạo, dependency injection, @PostConstruct, Destroy. @Service là specialization của @Component.",
                      "evaluationCriteria": "Nêu đúng khái niệm: 7đ; phân biệt rõ ngữ nghĩa nghiệp vụ: 10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Spring Framework Core"
                    },
                    {
                      "category": "TECHNICAL_FOUNDATION",
                      "difficulty": "MEDIUM",
                      "questionText": "Khi thực hiện truy vấn N+1 trong JPA/Hibernate, bạn phát hiện bằng cách nào và phương án tối ưu tốt nhất là gì?",
                      "expectedAnswer": "Sử dụng JOIN FETCH, @EntityGraph hoặc DTO projection thay vì eager loading mặc định.",
                      "evaluationCriteria": "Hiểu bản chất N+1: 6đ; giải thích cặn kẽ JOIN FETCH: 10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "JPA & Database Optimization"
                    },
                    {
                      "category": "PROJECT_DEEP_DIVE",
                      "difficulty": "MEDIUM",
                      "questionText": "Trong dự án chính bạn khai trong hồ sơ, phần kiến trúc nào do chính tay bạn thiết kế và gặp sự cố khó khăn nhất là gì?",
                      "expectedAnswer": "Ứng viên mô tả trung thực bài toán, các giải pháp đã thử và bài học rút ra.",
                      "evaluationCriteria": "Trả lời mạch lạc, đúng thực tế dự án, không có biểu hiện gian lận: 10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Kiểm chứng đóng góp thực tế trong dự án"
                    },
                    {
                      "category": "PROJECT_DEEP_DIVE",
                      "difficulty": "HARD",
                      "questionText": "Bạn đã bao giờ triển khai ứng dụng bằng Docker hoặc xây dựng CI/CD pipeline chưa? Hãy mô tả các bước một commit đi vào môi trường staging?",
                      "expectedAnswer": "Trình bày được Dockerfile, các bước test tự động, build image và deploy.",
                      "evaluationCriteria": "Hiểu quy trình DevOps cơ bản: 8-10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Đối soát kỹ năng DevOps / Docker"
                    },
                    {
                      "category": "BEHAVIORAL_AND_CULTURE_FIT",
                      "difficulty": "EASY",
                      "questionText": "Nếu bạn được giao một task dùng công nghệ bạn chưa từng học qua và deadline là 3 ngày, bạn sẽ lên kế hoạch giải quyết như thế nào?",
                      "expectedAnswer": "Chủ động nghiên cứu tài liệu chính thức (Official Docs), hỏi Mentor sau khi đã tự tìm hiểu, cập nhật tiến độ liên tục.",
                      "evaluationCriteria": "Tinh thần tự chủ và chủ động: 10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Khả năng tự học và giải quyết vấn đề"
                    },
                    {
                      "category": "BEHAVIORAL_AND_CULTURE_FIT",
                      "difficulty": "MEDIUM",
                      "questionText": "Kể lại một tình huống bạn và bạn cùng nhóm bất đồng ý kiến về giải pháp kỹ thuật, bạn đã xử lý ra sao?",
                      "expectedAnswer": "Dựa trên số liệu, ưu nhược điểm khách quan thay vì cảm xúc cá nhân để đi đến thống nhất.",
                      "evaluationCriteria": "Kỹ năng làm việc nhóm và giao tiếp chuyên nghiệp: 10đ.",
                      "suggestedScoreWeight": 10,
                      "focusArea": "Kỹ năng giải quyết xung đột nhóm"
                    }
                  ]
                }
                """;
    }

    private byte[] generateSimplePdfSheet(InterviewQuestionSet set) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            // Nạp font Unicode hỗ trợ tiếng Việt có dấu
            org.apache.pdfbox.pdmodel.font.PDFont fontRegular;
            try (java.io.InputStream fontStream = getClass().getResourceAsStream("/arial.ttf")) {
                if (fontStream != null) {
                    fontRegular = org.apache.pdfbox.pdmodel.font.PDType0Font.load(document, fontStream);
                } else {
                    fontRegular = new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
                }
            } catch (Exception fe) {
                log.warn("Không thể tải font arial.ttf, sử dụng Helvetica mặc định: {}", fe.getMessage());
                fontRegular = new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            }

            final boolean isUnicode = fontRegular instanceof org.apache.pdfbox.pdmodel.font.PDType0Font;
            final float marginX = 45;
            final float contentWidth = 510;
            final float pageBottomThreshold = 65;

            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            document.addPage(page);

            org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
            float y = 800;

            // Header Banner
            content.beginText();
            content.setFont(fontRegular, 15);
            content.newLineAtOffset(marginX, y);
            content.showText(cleanText("TẬP ĐOÀN INTERNHUB - PHIẾU ĐÁNH GIÁ PHỎNG VẤN", isUnicode));
            content.endText();
            y -= 22;

            content.beginText();
            content.setFont(fontRegular, 11);
            content.newLineAtOffset(marginX, y);
            content.showText(cleanText("Tiêu đề: " + (set.getTitle() != null ? set.getTitle() : "Bộ câu hỏi phỏng vấn"), isUnicode));
            content.endText();
            y -= 17;

            content.beginText();
            content.setFont(fontRegular, 9.5f);
            content.newLineAtOffset(marginX, y);
            String scopeVn = set.getScope() == QuestionScope.CANDIDATE ? "Cá nhân hóa ứng viên" : "Toàn bộ kỳ thực tập";
            String statusVn = set.getStatus() == QuestionSetStatus.FINALIZED ? "Đã chốt duyệt" : "Bản thảo (Nháp)";
            String subInfo = "Vị trí ứng tuyển: " + (set.getTargetRole() != null ? set.getTargetRole() : "Thực tập sinh") +
                    "  |  Phạm vi: " + scopeVn +
                    "  |  Trạng thái: " + statusVn;
            content.showText(cleanText(subInfo, isUnicode));
            content.endText();
            y -= 16;

            content.beginText();
            content.setFont(fontRegular, 9.5f);
            content.newLineAtOffset(marginX, y);
            String creatorVn = "Hội đồng phỏng vấn: " + (set.getCreatedByName() != null ? set.getCreatedByName() : "HR / Mentor") +
                    (set.getScreeningMatchScore() != null ? ("  |  Điểm hồ sơ AI Screening: " + set.getScreeningMatchScore() + "/100đ") : "");
            content.showText(cleanText(creatorVn, isUnicode));
            content.endText();
            y -= 12;

            // Kẻ đường ngăn cách
            content.setLineWidth(0.8f);
            content.moveTo(marginX, y);
            content.lineTo(marginX + contentWidth, y);
            content.stroke();
            y -= 18;

            if (set.getQuestions() != null) {
                int index = 1;
                for (InterviewQuestion q : set.getQuestions()) {
                    // Kiểm tra trang giấy trước khi render 1 câu hỏi
                    if (y < 120) {
                        content.close();
                        page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                        document.addPage(page);
                        content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                        y = 800;
                    }

                    // Tên phân loại tiếng Việt
                    String catNameVn;
                    if (q.getCategory() == QuestionCategory.TECHNICAL_FOUNDATION) {
                        catNameVn = "Kiến thức nền tảng";
                    } else if (q.getCategory() == QuestionCategory.PROJECT_DEEP_DIVE) {
                        catNameVn = "Đào sâu dự án trong CV";
                    } else {
                        catNameVn = "Văn hóa & Xử lý tình huống";
                    }

                    String diffVn;
                    if (q.getDifficulty() == QuestionDifficulty.HARD) {
                        diffVn = "Nâng cao";
                    } else if (q.getDifficulty() == QuestionDifficulty.MEDIUM) {
                        diffVn = "Trung bình";
                    } else {
                        diffVn = "Cơ bản";
                    }

                    // Dòng tiêu đề câu hỏi
                    content.beginText();
                    content.setFont(fontRegular, 10);
                    content.newLineAtOffset(marginX, y);
                    String qHeader = "Câu " + index + ": [" + catNameVn + " - Mức độ: " + diffVn + "]";
                    if (q.getFocusArea() != null && !q.getFocusArea().isBlank()) {
                        qHeader += " • Mục tiêu: " + q.getFocusArea();
                    }
                    content.showText(cleanText(qHeader, isUnicode));
                    content.endText();
                    y -= 15;

                    // Nội dung câu hỏi (Multi-line word wrap)
                    List<String> qLines = wrapText(q.getQuestionText(), fontRegular, 9.5f, contentWidth);
                    for (String line : qLines) {
                        if (y < pageBottomThreshold) {
                            content.close();
                            page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                            document.addPage(page);
                            content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                            y = 800;
                        }
                        content.beginText();
                        content.setFont(fontRegular, 9.5f);
                        content.newLineAtOffset(marginX, y);
                        content.showText(cleanText(line, isUnicode));
                        content.endText();
                        y -= 13;
                    }

                    // Gợi ý câu trả lời kỳ vọng (nếu có)
                    if (q.getExpectedAnswer() != null && !q.getExpectedAnswer().isBlank()) {
                        y -= 2;
                        List<String> expLines = wrapText("» Gợi ý câu trả lời: " + q.getExpectedAnswer(), fontRegular, 8.5f, contentWidth);
                        for (String line : expLines) {
                            if (y < pageBottomThreshold) {
                                content.close();
                                page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                                document.addPage(page);
                                content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                                y = 800;
                            }
                            content.beginText();
                            content.setFont(fontRegular, 8.5f);
                            content.newLineAtOffset(marginX + 8, y);
                            content.showText(cleanText(line, isUnicode));
                            content.endText();
                            y -= 12;
                        }
                    }

                    // Tiêu chí chấm điểm (nếu có)
                    if (q.getEvaluationCriteria() != null && !q.getEvaluationCriteria().isBlank()) {
                        List<String> critLines = wrapText("» Tiêu chí chấm điểm: " + q.getEvaluationCriteria(), fontRegular, 8.5f, contentWidth);
                        for (String line : critLines) {
                            if (y < pageBottomThreshold) {
                                content.close();
                                page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                                document.addPage(page);
                                content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                                y = 800;
                            }
                            content.beginText();
                            content.setFont(fontRegular, 8.5f);
                            content.newLineAtOffset(marginX + 8, y);
                            content.showText(cleanText(line, isUnicode));
                            content.endText();
                            y -= 12;
                        }
                    }

                    // Ô cho điểm và Ghi chú thực tế của Giám khảo
                    y -= 2;
                    if (y < pageBottomThreshold) {
                        content.close();
                        page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                        document.addPage(page);
                        content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                        y = 800;
                    }
                    content.beginText();
                    content.setFont(fontRegular, 9);
                    content.newLineAtOffset(marginX, y);
                    int maxWeight = q.getSuggestedScoreWeight() != null ? q.getSuggestedScoreWeight() : 10;
                    content.showText(cleanText("Điểm số (Tối đa " + maxWeight + "đ): [      / " + maxWeight + " ]    Nhận xét: ____________________________________________________", isUnicode));
                    content.endText();
                    y -= 20;

                    index++;
                }
            }

            // Phần tổng kết đánh giá cuối trang
            if (y < 95) {
                content.close();
                page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                document.addPage(page);
                content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
                y = 800;
            }

            content.setLineWidth(0.6f);
            content.moveTo(marginX, y);
            content.lineTo(marginX + contentWidth, y);
            content.stroke();
            y -= 16;

            content.beginText();
            content.setFont(fontRegular, 10);
            content.newLineAtOffset(marginX, y);
            content.showText(cleanText("KẾT LUẬN CHUNG:    [  ] ĐẠT YÊU CẦU          [  ] CẦN CÂN NHẮC THÊM          [  ] KHÔNG ĐẠT", isUnicode));
            content.endText();
            y -= 20;

            content.beginText();
            content.setFont(fontRegular, 9.5f);
            content.newLineAtOffset(marginX, y);
            content.showText(cleanText("Chữ ký Người phỏng vấn: _________________________________    Ngày: ......./......./2026", isUnicode));
            content.endText();

            content.close();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi tạo binary PDF bằng PDFBox: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo file PDF lúc này");
        }
    }

    private List<String> wrapText(String text, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            try {
                float width = font.getStringWidth(testLine) / 1000 * fontSize;
                if (width <= maxWidth) {
                    currentLine = new StringBuilder(testLine);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                    }
                    currentLine = new StringBuilder(word);
                }
            } catch (Exception e) {
                // Fallback tính theo số ký tự
                if (testLine.length() <= 85) {
                    currentLine = new StringBuilder(testLine);
                } else {
                    if (currentLine.length() > 0) lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private String cleanText(String input, boolean isUnicode) {
        if (input == null) return "";
        if (isUnicode) {
            // Giữ nguyên tiếng Việt có dấu, chỉ bỏ ký tự điều khiển xuống dòng lỗi PDF
            return input.replace("\r", " ").replace("\n", " ").trim();
        }
        // Fallback khi không tải được TTF Unicode
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replace("Đ", "D").replace("đ", "d")
                .replaceAll("[^\\x20-\\x7E]", " ").trim();
    }

    @Override
    public QuestionGenerationJobResponse startAsyncQuestionGeneration(
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    ) {
        String jobId = "job-iq-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Khởi tạo tác vụ sinh câu hỏi phỏng vấn async: jobId={}, programId={}, internId={}",
                jobId, request.getProgramId(), request.getInternId());

        QuestionGenerationJobResponse initialStatus = QuestionGenerationJobResponse.builder()
                .jobId(jobId)
                .programId(request.getProgramId())
                .internId(request.getInternId())
                .targetRole(request.getTargetRole())
                .status("PROCESSING")
                .percent(0)
                .currentStage("Đang khởi tạo tiến trình sinh câu hỏi AI...")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        saveJobState(jobId, initialStatus);

        // Kích hoạt xử lý đa luồng chạy nền không chặn luồng chính
        AiInterviewQuestionService asyncService = null;
        try {
            asyncService = applicationContext.getBean(AiInterviewQuestionService.class);
        } catch (Exception e) {
            log.warn("Không thể lấy AiInterviewQuestionService từ ApplicationContext: {}", e.getMessage());
        }

        if (asyncService != null) {
            asyncService.executeAsyncQuestionGeneration(jobId, request, userId, userName, authHeader);
        } else {
            executeAsyncQuestionGeneration(jobId, request, userId, userName, authHeader);
        }

        return initialStatus;
    }

    @Override
    @Async("aiInterviewTaskExecutor")
    public void executeAsyncQuestionGeneration(
            String jobId,
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    ) {
        log.info("Bắt đầu thực thi đa luồng tiến trình sinh câu hỏi: jobId={}", jobId);
        QuestionGenerationJobResponse jobStatus = loadJobState(jobId);
        if (jobStatus == null) return;

        try {
            // Stage 1: Đọc JD & CV (percent = 25%)
            updateJobStage(jobId, jobStatus, 25, "Đang trích xuất mô tả kỳ thực tập (JD) và CV ứng viên...");

            ProgramClientDto program = null;
            try {
                ApiResponse<ProgramClientDto> progRes = internServiceClient.getProgramById(request.getProgramId(), authHeader);
                if (progRes != null && progRes.getData() != null) {
                    program = progRes.getData();
                }
            } catch (Exception e) {
                log.warn("Không thể lấy thông tin kỳ thực tập id={}: {}", request.getProgramId(), e.getMessage());
            }

            InternProfileClientDto intern = null;
            String cvText = "";
            InternAiEvaluation screening = null;
            QuestionScope scope = QuestionScope.PROGRAM;

            if (request.getInternId() != null) {
                scope = QuestionScope.CANDIDATE;
                try {
                    ApiResponse<InternProfileClientDto> internRes = internServiceClient.getInternById(request.getInternId(), authHeader);
                    if (internRes != null && internRes.getData() != null) {
                        intern = internRes.getData();
                    }
                } catch (Exception e) {
                    log.warn("Không thể lấy thông tin ứng viên id={}: {}", request.getInternId(), e.getMessage());
                }

                if (intern != null && intern.getInternCode() != null) {
                    try {
                        ApiResponse<List<DocumentClientDto>> docsRes = internServiceClient.getDocumentsByInternCode(intern.getInternCode(), authHeader);
                        if (docsRes != null && docsRes.getData() != null) {
                            Optional<DocumentClientDto> cvDocOpt = docsRes.getData().stream()
                                    .filter(d -> "CV".equalsIgnoreCase(d.getDocumentType()) || "RESUME".equalsIgnoreCase(d.getDocumentType()))
                                    .findFirst();
                            if (cvDocOpt.isPresent()) {
                                DocumentClientDto cvDoc = cvDocOpt.get();
                                ResponseEntity<byte[]> fileRes = internServiceClient.downloadDocument(cvDoc.getId(), authHeader);
                                if (fileRes != null && fileRes.getBody() != null) {
                                    cvText = documentTextExtractor.extractText(fileRes.getBody(), cvDoc.getOriginalFileName());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Không thể tải CV cho ứng viên {}: {}", intern.getInternCode(), e.getMessage());
                    }
                }

                if (Boolean.TRUE.equals(request.getIncludeScreeningDossier())) {
                    screening = aiEvaluationRepository.findTopByInternIdOrderByCreatedAtDesc(request.getInternId())
                            .orElse(null);
                }
            }

            String targetRole = request.getTargetRole();
            if ((targetRole == null || targetRole.isBlank()) && program != null) {
                targetRole = program.getName();
            }
            if (targetRole == null || targetRole.isBlank()) {
                targetRole = "Thực tập sinh Công nghệ";
            }

            int totalQ = (request.getTotalQuestions() != null && request.getTotalQuestions() > 0)
                    ? request.getTotalQuestions() : 8;

            // Stage 2: Kết nối Gemini LLM sinh câu hỏi (percent = 60%)
            updateJobStage(jobId, jobStatus, 60, "Gemini AI đang sinh " + totalQ + " câu hỏi phỏng vấn và tiêu chí chấm điểm...");

            String systemInstruction = promptBuilder.buildSystemInstruction();
            String userPrompt = promptBuilder.buildUserPrompt(
                    program, intern, cvText, screening, targetRole, totalQ, request.getCustomRequirements()
            );

            String generatedJson = callGeminiForQuestions(systemInstruction, userPrompt);

            // Stage 3: Chuẩn hóa & lưu vào Database (percent = 85%)
            updateJobStage(jobId, jobStatus, 85, "Đang chuẩn hóa dữ liệu và lưu trữ bộ câu hỏi...");

            InterviewQuestionSetResponse response = parseAndPersistQuestionSet(
                    generatedJson,
                    request.getProgramId(),
                    request.getInternId(),
                    scope,
                    targetRole,
                    screening,
                    userId,
                    userName,
                    intern
            );

            // Stage 4: Hoàn tất (percent = 100%)
            jobStatus.setStatus("COMPLETED");
            jobStatus.setPercent(100);
            jobStatus.setCurrentStage("Hoàn tất! Bộ câu hỏi đã sẵn sàng.");
            jobStatus.setQuestionSetId(response.getId());
            jobStatus.setResult(response);
            jobStatus.setUpdatedAt(LocalDateTime.now());
            saveJobState(jobId, jobStatus);
            log.info("Hoàn tất tiến trình sinh câu hỏi: jobId={}, setId={}", jobId, response.getId());

        } catch (Exception e) {
            log.error("Lỗi trong quá trình thực thi async sinh câu hỏi: jobId={}, error={}", jobId, e.getMessage(), e);
            jobStatus.setStatus("FAILED");
            jobStatus.setPercent(100);
            jobStatus.setErrorMessage(e.getMessage());
            jobStatus.setCurrentStage("Quá trình sinh câu hỏi thất bại: " + e.getMessage());
            jobStatus.setUpdatedAt(LocalDateTime.now());
            saveJobState(jobId, jobStatus);
        }
    }

    @Override
    public QuestionGenerationJobResponse getGenerationJobStatus(String jobId) {
        QuestionGenerationJobResponse job = loadJobState(jobId);
        if (job == null) {
            return QuestionGenerationJobResponse.builder()
                    .jobId(jobId)
                    .status("NOT_FOUND")
                    .currentStage("Không tìm thấy tiến trình")
                    .build();
        }
        return job;
    }

    private void saveJobState(String jobId, QuestionGenerationJobResponse jobStatus) {
        activeJobs.put(jobId, jobStatus);
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(jobStatus);
                redisTemplate.opsForValue().set(REDIS_JOB_PREFIX + jobId, json, JOB_TTL);
            } catch (Exception e) {
                log.warn("Không thể lưu trạng thái job vào Redis: {}", e.getMessage());
            }
        }
    }

    private QuestionGenerationJobResponse loadJobState(String jobId) {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(REDIS_JOB_PREFIX + jobId);
                if (json != null && !json.isBlank()) {
                    QuestionGenerationJobResponse response = objectMapper.readValue(json, QuestionGenerationJobResponse.class);
                    activeJobs.put(jobId, response);
                    return response;
                }
            } catch (Exception e) {
                log.warn("Không thể đọc trạng thái job từ Redis: {}", e.getMessage());
            }
        }
        return activeJobs.get(jobId);
    }

    private void updateJobStage(String jobId, QuestionGenerationJobResponse jobStatus, int percent, String stage) {
        jobStatus.setPercent(percent);
        jobStatus.setCurrentStage(stage);
        jobStatus.setUpdatedAt(LocalDateTime.now());
        saveJobState(jobId, jobStatus);
    }
}

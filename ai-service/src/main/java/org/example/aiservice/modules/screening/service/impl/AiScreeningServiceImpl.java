package org.example.aiservice.modules.screening.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.InternServiceClient;
import org.example.aiservice.client.dto.DocumentClientDto;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.client.dto.ProgramClientDto;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.core.DocumentTextExtractor;
import org.example.aiservice.core.provider.AiProvider;
import org.example.aiservice.modules.screening.dto.request.BatchScreeningRequest;
import org.example.aiservice.modules.screening.dto.response.BatchJobCreatedResponse;
import org.example.aiservice.modules.screening.dto.response.BatchJobStatusResponse;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.entity.AiCandidateBookmark;
import org.example.aiservice.modules.screening.entity.InternAiEvaluation;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.example.aiservice.modules.screening.enums.EvaluationStatus;
import org.example.aiservice.modules.screening.repository.AiCandidateBookmarkRepository;
import org.example.aiservice.modules.screening.repository.AiEvaluationRepository;
import org.example.aiservice.modules.screening.service.AiScreeningService;
import org.example.aiservice.security.PiiDataSanitizerService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiScreeningServiceImpl implements AiScreeningService {

    private final AiEvaluationRepository aiEvaluationRepository;
    private final AiProvider aiProvider;
    private final DocumentTextExtractor documentTextExtractor;
    private final InternServiceClient internServiceClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final PiiDataSanitizerService piiDataSanitizerService;
    private final AiCandidateBookmarkRepository bookmarkRepository;

    // Bộ nhớ tạm fallback và Redis Key prefix
    private static final String REDIS_JOB_PREFIX = "ai:screening:job:";
    private static final Duration JOB_TTL = Duration.ofHours(24);
    private final Map<String, BatchJobStatusResponse> activeJobs = new ConcurrentHashMap<>();

    private String cachedRulesJson = "{}";

    @PostConstruct
    public void loadRulesJson() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/screening_rules.json");
            try (InputStream is = resource.getInputStream()) {
                cachedRulesJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Đã nạp thành công file quy tắc screening_rules.json vào bộ nhớ");
            }
        } catch (Exception e) {
            log.error("Không thể đọc file rules/screening_rules.json: {}", e.getMessage());
        }
    }

    @Override
    public BatchJobCreatedResponse startBatchScreening(BatchScreeningRequest request, String authHeader) {
        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        int total = request.getInternIds().size();

        BatchJobStatusResponse statusResponse = BatchJobStatusResponse.builder()
                .jobId(jobId)
                .programId(request.getProgramId())
                .status("PROCESSING")
                .totalCount(total)
                .processedCount(0)
                .percent(0)
                .results(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        activeJobs.put(jobId, statusResponse);
        saveJobState(jobId, statusResponse);

        // Kích hoạt xử lý đa luồng chạy nền không chặn luồng chính
        executeAsyncBatchJob(jobId, request, authHeader);

        return BatchJobCreatedResponse.builder()
                .jobId(jobId)
                .totalCount(total)
                .status("PROCESSING")
                .submittedAt(LocalDateTime.now())
                .build();
    }

    @Async("aiScreeningTaskExecutor")
    public void executeAsyncBatchJob(String jobId, BatchScreeningRequest request, String authHeader) {
        log.info("Bắt đầu xử lý luồng nền BatchJob: id={}, total={}", jobId, request.getInternIds().size());
        BatchJobStatusResponse jobStatus = loadJobState(jobId);
        if (jobStatus == null) return;

        ProgramClientDto program = fetchProgram(request.getProgramId(), authHeader);
        String programJson = safeToJson(program);

        for (Long internId : request.getInternIds()) {
            try {
                ScreeningResultDto result = evaluateSingleIntern(
                        internId,
                        request.getProgramId(),
                        programJson,
                        request.isForceReevaluate(),
                        authHeader
                );
                synchronized (jobStatus) {
                    jobStatus.getResults().add(result);
                    jobStatus.setProcessedCount(jobStatus.getProcessedCount() + 1);
                    jobStatus.setPercent((jobStatus.getProcessedCount() * 100) / jobStatus.getTotalCount());
                    jobStatus.setUpdatedAt(LocalDateTime.now());
                    saveJobState(jobId, jobStatus);
                }
            } catch (Exception e) {
                log.error("Lỗi khi đánh giá internId={}: {}", internId, e.getMessage(), e);
                synchronized (jobStatus) {
                    jobStatus.getResults().add(ScreeningResultDto.builder()
                            .internId(internId)
                            .programId(request.getProgramId())
                            .overallScore(0)
                            .recommendation(AiRecommendation.CONSIDER)
                            .matchSummary("Đánh giá thất bại: " + e.getMessage())
                            .errorMessage(e.getMessage())
                            .evaluatedAt(LocalDateTime.now())
                            .build());
                    jobStatus.setProcessedCount(jobStatus.getProcessedCount() + 1);
                    jobStatus.setPercent((jobStatus.getProcessedCount() * 100) / jobStatus.getTotalCount());
                    jobStatus.setUpdatedAt(LocalDateTime.now());
                    saveJobState(jobId, jobStatus);
                }
            }
        }

        jobStatus.setStatus("COMPLETED");
        jobStatus.setUpdatedAt(LocalDateTime.now());
        saveJobState(jobId, jobStatus);
        log.info("Hoàn tất xử lý BatchJob: id={}, status=COMPLETED", jobId);
    }

    private ScreeningResultDto evaluateSingleIntern(
            Long internId,
            Long programId,
            String programJson,
            boolean forceReevaluate,
            String authHeader
    ) {
        // 1. Kiểm tra Cache trong CSDL
        if (!forceReevaluate) {
            Optional<InternAiEvaluation> cached = aiEvaluationRepository
                    .findTopByInternIdAndProgramIdOrderByCreatedAtDesc(internId, programId);
            if (cached.isPresent() && cached.get().getStatus() == EvaluationStatus.SUCCESS) {
                log.info("Cache-Hit cho internId={}: Trả về kết quả từ DB", internId);
                return mapEntityToDto(cached.get(), true);
            }
        }

        // 2. Lấy thông tin ứng viên từ intern-and-program-service
        InternProfileClientDto intern = fetchIntern(internId, authHeader);
        // AI-13: Khử định danh PII trước khi gửi sang Cloud LLM (Gemini)
        var sanitizedIntern = piiDataSanitizerService.sanitizeInternProfile(intern);
        String internJson = safeToJson(sanitizedIntern);

        // 3. Lấy file CV và bóc tách văn bản
        DocumentClientDto cvDoc = fetchCvDocument(intern.getInternCode(), authHeader);
        if (cvDoc == null) {
            log.warn("Thực tập sinh internId={}, internCode={} chưa nộp CV. Chặn thẩm định AI.", internId, intern.getInternCode());
            ScreeningResultDto failResult = ScreeningResultDto.builder()
                    .internId(internId)
                    .programId(programId)
                    .overallScore(0)
                    .recommendation(AiRecommendation.CONSIDER)
                    .matchSummary("Ứng viên chưa tải lên CV. Không thể thực hiện thẩm định AI.")
                    .errorMessage("Ứng viên chưa tải lên CV. Không thể thực hiện thẩm định AI.")
                    .evaluatedAt(LocalDateTime.now())
                    .build();
            saveEvaluationToDb(failResult, internId, programId, null, null);
            return failResult;
        }

        String cvText = "";
        String cvHash = null;
        Long docId = cvDoc.getId();

        if (cvDoc != null) {
            docId = cvDoc.getId();
            byte[] fileBytes = downloadDocumentBytes(cvDoc.getId(), authHeader);
            if (fileBytes != null && fileBytes.length > 0) {
                String rawCvText = documentTextExtractor.extractText(fileBytes, cvDoc.getOriginalFileName());
                // AI-13: Khử định danh CCCD, SĐT, Email, STK, Ngày sinh, Địa chỉ chi tiết trong CV
                cvText = piiDataSanitizerService.sanitize(rawCvText);
                cvHash = calculateSha256(fileBytes);
            }
        }

        if (cvText == null || cvText.trim().length() < 10) {
            log.warn("File CV của internId={} không trích xuất được nội dung văn bản hợp lệ.", internId);
            ScreeningResultDto failResult = ScreeningResultDto.builder()
                    .internId(internId)
                    .programId(programId)
                    .overallScore(0)
                    .recommendation(AiRecommendation.CONSIDER)
                    .matchSummary("Tệp CV không có nội dung văn bản hợp lệ để thẩm định.")
                    .errorMessage("Tệp CV không có nội dung văn bản hợp lệ để thẩm định.")
                    .evaluatedAt(LocalDateTime.now())
                    .build();
            saveEvaluationToDb(failResult, internId, programId, docId, cvHash);
            return failResult;
        }

        // 4. Gọi Gemini AI Provider
        ScreeningResultDto evalResult = aiProvider.evaluateCandidate(
                cvText,
                internJson,
                programJson,
                cachedRulesJson
        );
        evalResult.setInternId(internId);
        evalResult.setProgramId(programId);

        // 5. Lưu kết quả vào CSDL để làm Cache vĩnh viễn
        saveEvaluationToDb(evalResult, internId, programId, docId, cvHash);

        return evalResult;
    }

    @Transactional
    public void saveEvaluationToDb(
            ScreeningResultDto dto,
            Long internId,
            Long programId,
            Long documentId,
            String cvHash
    ) {
        try {
            InternAiEvaluation entity = InternAiEvaluation.builder()
                    .internId(internId)
                    .programId(programId)
                    .documentId(documentId)
                    .cvFileHash(cvHash)
                    .overallScore(dto.getOverallScore() != null ? dto.getOverallScore() : 0)
                    .recommendation(dto.getRecommendation() != null ? dto.getRecommendation() : AiRecommendation.CONSIDER)
                    .matchSummary(dto.getMatchSummary())
                    .strengthsJson(safeToJson(dto.getStrengths()))
                    .weaknessesJson(safeToJson(dto.getWeaknesses()))
                    .skillsMatchedJson(safeToJson(dto.getSkillsMatched()))
                    .draftRejectionReason(dto.getDraftRejectionReason())
                    .suggestedInterviewQuestionsJson(safeToJson(dto.getSuggestedInterviewQuestions()))
                    .status(dto.getErrorMessage() == null ? EvaluationStatus.SUCCESS : EvaluationStatus.FAILED)
                    .errorMessage(dto.getErrorMessage())
                    .build();

            aiEvaluationRepository.save(entity);
            log.info("Đã lưu kết quả đánh giá AI vào DB cho internId={}", internId);
        } catch (Exception e) {
            log.error("Lỗi khi lưu kết quả AI vào DB cho internId={}: {}", internId, e.getMessage());
        }
    }

    private void saveJobState(String jobId, BatchJobStatusResponse jobStatus) {
        activeJobs.put(jobId, jobStatus);
        try {
            String json = objectMapper.writeValueAsString(jobStatus);
            redisTemplate.opsForValue().set(REDIS_JOB_PREFIX + jobId, json, JOB_TTL);
        } catch (Exception e) {
            log.warn("Không thể lưu trạng thái job vào Redis: {}", e.getMessage());
        }
    }

    private BatchJobStatusResponse loadJobState(String jobId) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_JOB_PREFIX + jobId);
            if (json != null && !json.isBlank()) {
                BatchJobStatusResponse response = objectMapper.readValue(json, BatchJobStatusResponse.class);
                activeJobs.put(jobId, response);
                return response;
            }
        } catch (Exception e) {
            log.warn("Không thể đọc trạng thái job từ Redis: {}", e.getMessage());
        }
        return activeJobs.get(jobId);
    }

    @Override
    public BatchJobStatusResponse getBatchJobStatus(String jobId) {
        BatchJobStatusResponse job = loadJobState(jobId);
        if (job == null) {
            return BatchJobStatusResponse.builder()
                    .jobId(jobId)
                    .status("NOT_FOUND")
                    .build();
        }
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public ScreeningResultDto getInternEvaluation(Long internId) {
        return aiEvaluationRepository.findTopByInternIdOrderByCreatedAtDesc(internId)
                .map(entity -> mapEntityToDto(entity, false))
                .orElse(null);
    }

    private ScreeningResultDto mapEntityToDto(InternAiEvaluation entity, boolean fromCache) {
        return ScreeningResultDto.builder()
                .internId(entity.getInternId())
                .programId(entity.getProgramId())
                .overallScore(entity.getOverallScore())
                .recommendation(entity.getRecommendation())
                .matchSummary(entity.getMatchSummary())
                .strengths(safeFromJsonList(entity.getStrengthsJson()))
                .weaknesses(safeFromJsonList(entity.getWeaknessesJson()))
                .skillsMatched(safeFromJsonList(entity.getSkillsMatchedJson()))
                .draftRejectionReason(entity.getDraftRejectionReason())
                .suggestedInterviewQuestions(safeFromJsonList(entity.getSuggestedInterviewQuestionsJson()))
                .evaluatedAt(entity.getCreatedAt())
                .fromCache(fromCache)
                .build();
    }

    private InternProfileClientDto fetchIntern(Long id, String authHeader) {
        try {
            ApiResponse<InternProfileClientDto> res = internServiceClient.getInternById(id, authHeader);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.warn("Lỗi khi gọi InternServiceClient.getInternById({}): {}", id, e.getMessage());
        }
        return InternProfileClientDto.builder().id(id).build();
    }

    private ProgramClientDto fetchProgram(Long id, String authHeader) {
        try {
            ApiResponse<ProgramClientDto> res = internServiceClient.getProgramById(id, authHeader);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.warn("Lỗi khi gọi InternServiceClient.getProgramById({}): {}", id, e.getMessage());
        }
        return ProgramClientDto.builder().id(id).build();
    }

    private DocumentClientDto fetchCvDocument(String internCode, String authHeader) {
        if (internCode == null) return null;
        try {
            ApiResponse<List<DocumentClientDto>> res = internServiceClient.getDocumentsByInternCode(internCode, authHeader);
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(d -> "CV".equalsIgnoreCase(d.getDocumentType()))
                        .findFirst()
                        .orElse(res.getData().isEmpty() ? null : res.getData().get(0));
            }
        } catch (Exception e) {
            log.warn("Lỗi khi lấy tài liệu CV cho internCode={}: {}", internCode, e.getMessage());
        }
        return null;
    }

    private byte[] downloadDocumentBytes(Long documentId, String authHeader) {
        try {
            ResponseEntity<byte[]> response = internServiceClient.downloadDocument(documentId, authHeader);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Không thể tải file CV documentId={}: {}", documentId, e.getMessage());
            return null;
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<String> safeFromJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public boolean toggleCandidateBookmark(Long programId, Long internId, String createdBy, String notes) {
        if (programId == null || internId == null) {
            return false;
        }

        var existing = bookmarkRepository.findByProgramIdAndInternId(programId, internId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            log.info("Đã xóa bookmark cho internId={} trong programId={}", internId, programId);
            return false; // Đã un-bookmark
        } else {
            AiCandidateBookmark bookmark = AiCandidateBookmark.builder()
                    .programId(programId)
                    .internId(internId)
                    .createdBy(createdBy)
                    .notes(notes)
                    .build();
            bookmarkRepository.save(bookmark);
            log.info("Đã tạo mới bookmark cho internId={} trong programId={} bởi user={}", internId, programId, createdBy);
            return true; // Đã bookmark
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getBookmarkedInternIds(Long programId) {
        if (programId == null) {
            return Collections.emptyList();
        }
        return bookmarkRepository.findByProgramId(programId).stream()
                .map(AiCandidateBookmark::getInternId)
                .toList();
    }
}

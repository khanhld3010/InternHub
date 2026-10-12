package org.example.aiservice.modules.screening.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiservice.client.InternServiceClient;
import org.example.aiservice.client.dto.DocumentClientDto;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.client.dto.ProgramClientDto;
import org.example.aiservice.common.dto.ApiResponse;
import org.example.aiservice.core.extractor.DocumentTextExtractor;
import org.example.aiservice.core.provider.AiProvider;
import org.example.aiservice.modules.screening.dto.request.BatchScreeningRequest;
import org.example.aiservice.modules.screening.dto.response.BatchJobCreatedResponse;
import org.example.aiservice.modules.screening.dto.response.BatchJobStatusResponse;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.entity.InternAiEvaluation;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.example.aiservice.modules.screening.repository.AiEvaluationRepository;
import org.example.aiservice.modules.screening.service.impl.AiScreeningServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiScreeningServiceTest {

    @Mock
    private AiEvaluationRepository aiEvaluationRepository;

    @Mock
    private AiProvider aiProvider;

    @Mock
    private DocumentTextExtractor documentTextExtractor;

    @Mock
    private InternServiceClient internServiceClient;

    private ObjectMapper objectMapper;
    private AiScreeningServiceImpl aiScreeningService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        aiScreeningService = new AiScreeningServiceImpl(
                aiEvaluationRepository,
                aiProvider,
                documentTextExtractor,
                internServiceClient,
                objectMapper
        );
    }

    @Test
    @DisplayName("Khởi tạo Batch Job thành công và trả về mã JobId trong vài mili-giây")
    void testStartBatchScreeningReturnsJobIdInstantly() {
        BatchScreeningRequest request = BatchScreeningRequest.builder()
                .programId(1L)
                .internIds(List.of(101L, 102L))
                .build();

        BatchJobCreatedResponse response = aiScreeningService.startBatchScreening(request, "Bearer mock-token");

        assertNotNull(response);
        assertNotNull(response.getJobId());
        assertTrue(response.getJobId().startsWith("job-"));
        assertEquals(2, response.getTotalCount());
        assertEquals("PROCESSING", response.getStatus());

        BatchJobStatusResponse status = aiScreeningService.getBatchJobStatus(response.getJobId());
        assertNotNull(status);
        assertEquals(response.getJobId(), status.getJobId());
    }

    @Test
    @DisplayName("Cache-Hit: Lấy kết quả từ CSDL cho internId đã đánh giá trước đó")
    void testGetInternEvaluationFromCache() {
        InternAiEvaluation cached = InternAiEvaluation.builder()
                .internId(101L)
                .programId(1L)
                .overallScore(85)
                .recommendation(AiRecommendation.RECOMMENDED_APPROVE)
                .matchSummary("Ứng viên phù hợp")
                .strengthsJson("[\"Java\", \"Spring\"]")
                .weaknessesJson("[]")
                .skillsMatchedJson("[\"Java\"]")
                .build();

        when(aiEvaluationRepository.findTopByInternIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.of(cached));

        ScreeningResultDto result = aiScreeningService.getInternEvaluation(101L);

        assertNotNull(result);
        assertEquals(101L, result.getInternId());
        assertEquals(85, result.getOverallScore());
        assertEquals(AiRecommendation.RECOMMENDED_APPROVE, result.getRecommendation());
        assertFalse(result.getStrengths().isEmpty());
    }
}

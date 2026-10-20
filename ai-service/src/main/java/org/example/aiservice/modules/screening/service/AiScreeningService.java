package org.example.aiservice.modules.screening.service;

import org.example.aiservice.modules.screening.dto.request.BatchScreeningRequest;
import org.example.aiservice.modules.screening.dto.response.BatchJobCreatedResponse;
import org.example.aiservice.modules.screening.dto.response.BatchJobStatusResponse;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;

public interface AiScreeningService {

    BatchJobCreatedResponse startBatchScreening(BatchScreeningRequest request, String authHeader);

    BatchJobStatusResponse getBatchJobStatus(String jobId);

    ScreeningResultDto getInternEvaluation(Long internId);

    boolean toggleCandidateBookmark(Long programId, Long internId, String createdBy, String notes);

    java.util.List<Long> getBookmarkedInternIds(Long programId);
}


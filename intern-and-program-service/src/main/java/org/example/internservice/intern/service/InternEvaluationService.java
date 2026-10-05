package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.InternEvaluationRequest;
import org.example.internservice.intern.dto.InternEvaluationResponse;

import java.util.List;

public interface InternEvaluationService {
    InternEvaluationResponse saveOrUpdateEvaluation(String internCode, InternEvaluationRequest request);
    InternEvaluationResponse getEvaluationByType(String internCode, String evaluationType);
    List<InternEvaluationResponse> getAllEvaluationsByIntern(String internCode);

    // HR Phê duyệt đánh giá cuối kỳ của từng TTS
    InternEvaluationResponse hrApproveEvaluation(String internCode, org.example.internservice.intern.dto.HrApproveEvaluationRequest request, String hrUsername);

    // HR Lấy danh sách tổng quan đánh giá của toàn bộ TTS theo bộ lọc
    List<org.example.internservice.intern.dto.HrEvaluationSummaryItem> getHrEvaluationSummary(Long programId, String university, String status, String keyword);
}

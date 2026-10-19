package org.example.aiservice.modules.interview.service;

import org.example.aiservice.modules.interview.entity.InterviewAnswerEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiInterviewGraderServiceMathTest {

    private AiInterviewGraderService graderService;

    @BeforeEach
    void setUp() {
        graderService = new AiInterviewGraderService(null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("P0 - Weighted Score Calculation: Tính điểm trung bình có trọng số chính xác")
    void testCalculateBackendWeightedScore_StandardWeights() {
        List<InterviewAnswerEvaluation> evals = new ArrayList<>();
        // Câu 1: Score 80, Trọng số 2.0 -> 160
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(80.0)).weight(BigDecimal.valueOf(2.0)).build());
        // Câu 2: Score 90, Trọng số 3.0 -> 270
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(90.0)).weight(BigDecimal.valueOf(3.0)).build());
        // Tổng điểm = (160 + 270) / 5.0 = 430 / 5.0 = 86.00

        BigDecimal total = graderService.calculateBackendWeightedScore(evals);
        assertEquals(new BigDecimal("86.00"), total);
    }

    @Test
    @DisplayName("P0 - Edge Case: Khi tổng trọng số bằng 0 thì tự động chia đều (Fallback Average)")
    void testCalculateBackendWeightedScore_ZeroTotalWeight_Fallback() {
        List<InterviewAnswerEvaluation> evals = new ArrayList<>();
        // 2 câu đều có trọng số 0
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(70.0)).weight(BigDecimal.ZERO).build());
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(80.0)).weight(BigDecimal.ZERO).build());
        // Chia đều = (70 + 80) / 2 = 75.00

        BigDecimal total = graderService.calculateBackendWeightedScore(evals);
        assertEquals(new BigDecimal("75.00"), total);
    }

    @Test
    @DisplayName("P0 - Edge Case: Điểm câu hỏi ngoài khoảng 0-100 được clamp an toàn")
    void testCalculateBackendWeightedScore_Clamping() {
        List<InterviewAnswerEvaluation> evals = new ArrayList<>();
        // Điểm âm được clamp về 0, điểm > 100 clamp về 100
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(-15.0)).weight(BigDecimal.ONE).build());
        evals.add(InterviewAnswerEvaluation.builder().score(BigDecimal.valueOf(125.0)).weight(BigDecimal.ONE).build());
        // Sau clamp: 0 và 100 -> (0 + 100) / 2 = 50.00

        BigDecimal total = graderService.calculateBackendWeightedScore(evals);
        assertEquals(new BigDecimal("50.00"), total);
    }

    @Test
    @DisplayName("P0 - Edge Case: Danh sách câu rỗng trả về 0")
    void testCalculateBackendWeightedScore_EmptyList() {
        BigDecimal total = graderService.calculateBackendWeightedScore(new ArrayList<>());
        assertEquals(BigDecimal.ZERO, total);
    }
}

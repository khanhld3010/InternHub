package org.example.aiservice.modules.interview.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.interview.dto.request.CreateInterviewScheduleRequest;
import org.example.aiservice.modules.interview.dto.request.UpdateInterviewScheduleRequest;
import org.example.aiservice.modules.interview.dto.response.InterviewScheduleDetailResponse;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.service.InterviewScheduleManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/interviews/schedules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public class HrInterviewScheduleController {

    private final InterviewScheduleManagementService scheduleManagementService;

    @PostMapping
    public ResponseEntity<InterviewScheduleDetailResponse> createSchedule(
            @Valid @RequestBody CreateInterviewScheduleRequest request,
            Authentication authentication) {
        String hrUserId = authentication != null ? authentication.getName() : "HR_USER";
        InterviewScheduleDetailResponse response = scheduleManagementService.createSchedule(request, hrUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewScheduleDetailResponse> getScheduleDetail(@PathVariable Long id) {
        InterviewScheduleDetailResponse response = scheduleManagementService.getScheduleDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<InterviewSchedule>> getSchedules(
            @RequestParam(required = false) Long programId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InterviewSchedule> response = scheduleManagementService.getSchedulesByProgram(programId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewScheduleDetailResponse> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInterviewScheduleRequest request) {
        InterviewScheduleDetailResponse response = scheduleManagementService.updateSchedule(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> cancelSchedule(@PathVariable Long id) {
        scheduleManagementService.cancelSchedule(id);
        return ResponseEntity.ok(Map.of("message", "Đã hủy đợt phỏng vấn và vô hiệu hóa các liên kết mời thành công."));
    }

    @PostMapping("/{scheduleId}/sessions/{sessionId}/resend-invite")
    public ResponseEntity<Map<String, String>> resendInvite(
            @PathVariable Long scheduleId,
            @PathVariable Long sessionId) {
        String manualLink = scheduleManagementService.resendInvite(scheduleId, sessionId);
        return ResponseEntity.ok(Map.of(
                "message", "Đã gửi lại lời mời phỏng vấn thành công.",
                "manualInviteLink", manualLink
        ));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<Map<String, String>> revokeSession(@PathVariable Long sessionId) {
        scheduleManagementService.revokeSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Đã thu hồi phiên phỏng vấn của ứng viên thành công."));
    }

    /**
     * Tra cứu phiên phỏng vấn gần nhất của ứng viên theo candidateId hoặc email để mở phiếu chấm điểm AI
     */
    @GetMapping("/candidate-session")
    public ResponseEntity<Map<String, Object>> getCandidateSession(
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) String email
    ) {
        return scheduleManagementService.findLatestSessionForCandidate(candidateId, email)
                .map(session -> ResponseEntity.ok(Map.<String, Object>of(
                        "sessionId", session.getId(),
                        "scheduleId", session.getScheduleId(),
                        "status", session.getStatus().name(),
                        "candidateEmail", session.getCandidateEmail(),
                        "candidateName", session.getCandidateName()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "message", "Chưa tìm thấy phiên phỏng vấn của ứng viên này."
                )));
    }
}

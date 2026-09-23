package org.example.internservice.intern.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternDecisionRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.service.InternProfileService;
import org.example.internservice.system.audit.annotation.Auditable;
import org.example.internservice.system.audit.entity.AuditAction;
import org.example.internservice.system.audit.entity.AuditModule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interns")
@RequiredArgsConstructor
@Slf4j
public class InternProfileController {

    private final InternProfileService internProfileService;

    @Auditable(action = AuditAction.CREATE_INTERN, module = AuditModule.INTERN, description = "Tạo mới hồ sơ thực tập sinh")
    @PostMapping
    public ResponseEntity<ApiResponse<InternResponse>> createIntern(@Valid @RequestBody CreateInternRequest request) {
        log.info("Nhan request tao ho so thuc tap sinh: {}", request.getEmail());
        InternResponse response = internProfileService.createIntern(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo hồ sơ thực tập sinh thành công", response));
    }

    @Auditable(action = AuditAction.UPDATE_INTERN, module = AuditModule.INTERN, description = "Cập nhật thông tin hồ sơ thực tập sinh")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<InternResponse>> updateIntern(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateInternRequest request
    ) {
        log.info("Nhan request cap nhat ho so thuc tap sinh voi ID: {}", id);
        InternResponse response = internProfileService.updateIntern(id, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật hồ sơ thực tập sinh thành công", response));
    }

    @Auditable(action = AuditAction.CHANGE_INTERN_STATUS, module = AuditModule.INTERN, description = "Duyệt hoặc từ chối hồ sơ thực tập sinh")
    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<InternResponse>> processDecision(
            @PathVariable("id") Long id,
            @Valid @RequestBody InternDecisionRequest request,
            Authentication authentication
    ) {
        String reviewerUsername = (authentication != null) ? authentication.getName() : "system";
        log.info("Nhan request xu ly quyet dinh ho so ID: {} boi user: {}", id, reviewerUsername);
        InternResponse response = internProfileService.processDecision(id, request, reviewerUsername);
        String actionMessage = request.getDecision() == InternStatus.APPROVED 
                ? "Duyệt hồ sơ thực tập sinh thành công" 
                : "Từ chối hồ sơ thực tập sinh thành công";
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), actionMessage, response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<PageResponse<InternResponse>>> searchInterns(
            @ModelAttribute InternFilterRequest request,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC, size = 10) Pageable pageable
    ) {
        log.info("Nhan request tim kiem ho so thuc tap sinh");
        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách hồ sơ thực tập sinh thành công", response));
    }
}


package org.example.employeeservice.intern.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.intern.dto.request.CreateInternRequest;
import org.example.employeeservice.intern.dto.request.UpdateInternRequest;
import org.example.employeeservice.intern.dto.response.InternResponse;
import org.example.employeeservice.intern.service.InternProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/interns")
@RequiredArgsConstructor
@Slf4j
public class InternProfileController {

    private final InternProfileService internProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<InternResponse>> createIntern(@Valid @RequestBody CreateInternRequest request) {
        log.info("Nhan request tao ho so thuc tap sinh: {}", request.getEmail());
        InternResponse response = internProfileService.createIntern(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo hồ sơ thực tập sinh thành công", response));
    }

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
}

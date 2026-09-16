package org.example.employeeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.request.CreateInternRequest;
import org.example.employeeservice.dto.response.ApiResponse;
import org.example.employeeservice.dto.response.InternResponse;
import org.example.employeeservice.service.InternProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/employees/interns")
@RequiredArgsConstructor
public class InternProfileController {

    private final InternProfileService internProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<InternResponse>> createIntern(@Valid @RequestBody CreateInternRequest request) {
        log.info("Nhận yêu cầu tạo mới hồ sơ thực tập sinh: {}", request.getFullName());
        InternResponse response = internProfileService.createIntern(request);

        ApiResponse<InternResponse> apiResponse = ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Tạo mới hồ sơ thực tập sinh thành công",
                response
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }
}

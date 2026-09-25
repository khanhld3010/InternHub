package org.example.internservice.program.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.program.dto.request.ChangeProgramStatusRequest;
import org.example.internservice.program.dto.request.CreateProgramRequest;
import org.example.internservice.program.dto.request.ProgramFilterRequest;
import org.example.internservice.program.dto.request.UpdateProgramRequest;
import org.example.internservice.program.dto.response.DepartmentResponse;
import org.example.internservice.program.dto.response.ProgramDetailResponse;
import org.example.internservice.program.dto.response.ProgramSummaryResponse;
import org.example.internservice.program.service.InternshipProgramService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Internship Program Management", description = "APIs quản lý chương trình thực tập và danh mục phòng ban")
public class ProgramController {

    private final InternshipProgramService programService;

    @Operation(summary = "Lấy danh mục phòng ban (Dành cho Dropdown)")
    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getDepartments() {
        return ResponseEntity.ok(ApiResponse.success(programService.getAllDepartments()));
    }

    @Operation(summary = "Tạo mới chương trình thực tập (HR/Admin)")
    @PostMapping("/programs")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDetailResponse>> createProgram(
            @Valid @RequestBody CreateProgramRequest request,
            Authentication authentication
    ) {
        String createdBy = authentication != null ? authentication.getName() : "system";
        ProgramDetailResponse response = programService.createProgram(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo chương trình thực tập thành công", response));
    }

    @Operation(summary = "Danh sách chương trình thực tập có phân trang và bộ lọc")
    @GetMapping("/programs")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<PageResponse<ProgramDetailResponse>>> getPrograms(
            @ModelAttribute ProgramFilterRequest filter,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(programService.getPrograms(filter, pageable)));
    }

    @Operation(summary = "Lấy danh sách các chương trình đang mở tuyển (Dành cho ứng viên & Card Frontend)")
    @GetMapping("/programs/open")
    public ResponseEntity<ApiResponse<List<ProgramSummaryResponse>>> getOpenPrograms() {
        return ResponseEntity.ok(ApiResponse.success(programService.getOpenPrograms()));
    }

    @Operation(summary = "Xem chi tiết chương trình thực tập (Chiếu DTO theo Role)")
    @GetMapping("/programs/{id}")
    public ResponseEntity<ApiResponse<?>> getProgramDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        boolean isPrivileged = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MENTOR"));

        if (isPrivileged) {
            ProgramDetailResponse detail = programService.getProgramDetailById(id);
            return ResponseEntity.ok(ApiResponse.success(detail));
        } else {
            ProgramSummaryResponse summary = programService.getProgramSummaryById(id);
            return ResponseEntity.ok(ApiResponse.success(summary));
        }
    }

    @Operation(summary = "Cập nhật thông tin chương trình thực tập (HR/Admin)")
    @PutMapping("/programs/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDetailResponse>> updateProgram(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProgramRequest request
    ) {
        ProgramDetailResponse response = programService.updateProgram(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật thông tin chương trình thành công", response));
    }

    @Operation(summary = "Chuyển đổi trạng thái chương trình thực tập theo State Machine")
    @PatchMapping("/programs/{id}/status")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDetailResponse>> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeProgramStatusRequest request
    ) {
        ProgramDetailResponse response = programService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Chuyển trạng thái chương trình thành công", response));
    }

    @Operation(summary = "Bật / tắt cờ nhận hồ sơ tuyển dụng isRecruitmentOpen")
    @PatchMapping("/programs/{id}/recruitment-toggle")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDetailResponse>> toggleRecruitment(
            @PathVariable Long id
    ) {
        ProgramDetailResponse response = programService.toggleRecruitment(id);
        String msg = Boolean.TRUE.equals(response.getIsRecruitmentOpen()) ? "Đã mở nhận hồ sơ tuyển sinh" : "Đã tạm dừng nhận hồ sơ tuyển sinh";
        return ResponseEntity.ok(ApiResponse.success(200, msg, response));
    }

    @Operation(summary = "Xóa chương trình thực tập (Chỉ Admin, trạng thái PLANNING và count=0)")
    @DeleteMapping("/programs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProgram(
            @PathVariable Long id
    ) {
        programService.deleteProgram(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Xóa chương trình thực tập thành công", null));
    }
}

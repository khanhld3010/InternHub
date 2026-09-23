package org.example.internservice.intern.controller;

import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternDecisionRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.service.InternProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternProfileControllerTest {

    @Mock
    private InternProfileService internProfileService;

    @InjectMocks
    private InternProfileController internProfileController;

    private CreateInternRequest validRequest;
    private InternResponse mockResponse;

    @BeforeEach
    void setUp() {
        validRequest = CreateInternRequest.builder()
                .fullName("Lương Anh Huy")
                .email("luonganhhuy2004@gmail.com")
                .phone("0987654321")
                .university("Đại học Bách Khoa")
                .major("Kỹ thuật Phần mềm")
                .appliedPosition("Backend Java Intern")
                .build();

        mockResponse = InternResponse.builder()
                .id(1L)
                .internCode("INT-202609-0001")
                .fullName("Lương Anh Huy")
                .email("luonganhhuy2004@gmail.com")
                .phone("0987654321")
                .status(InternStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Controller goi Service thanh cong va tra ve 201 Created cung ApiResponse")
    void createIntern_validPayload_shouldReturn201() {
        when(internProfileService.createIntern(any(CreateInternRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<InternResponse>> result = internProfileController.createIntern(validRequest);

        assertNotNull(result);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(201, result.getBody().getCode());
        assertEquals("INT-202609-0001", result.getBody().getData().getInternCode());
        assertEquals("Lương Anh Huy", result.getBody().getData().getFullName());

        verify(internProfileService).createIntern(validRequest);
    }

    @Test
    @DisplayName("Controller cap nhat thanh cong va tra ve 200 OK cung ApiResponse")
    void updateIntern_validPayload_shouldReturn200() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Lương Anh Huy")
                .email("luonganhhuy.updated@gmail.com")
                .phone("0987654321")
                .university("Đại học Bách Khoa")
                .major("Kỹ thuật Phần mềm")
                .appliedPosition("Backend Java Intern")
                .status(InternStatus.APPROVED)
                .build();

        InternResponse updatedResponse = InternResponse.builder()
                .id(1L)
                .internCode("INT-202609-0001")
                .fullName("Lương Anh Huy")
                .email("luonganhhuy.updated@gmail.com")
                .phone("0987654321")
                .status(InternStatus.APPROVED)
                .build();

        when(internProfileService.updateIntern(eq(1L), any(UpdateInternRequest.class))).thenReturn(updatedResponse);

        ResponseEntity<ApiResponse<InternResponse>> result = internProfileController.updateIntern(1L, updateRequest);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        verify(internProfileService).updateIntern(eq(1L), any(UpdateInternRequest.class));
    }

    @Test
    @DisplayName("searchInterns: Controller goi Service va tra ve 200 OK cung PageResponse")
    void searchInterns_validRequest_shouldReturn200AndPageResponse() {
        InternFilterRequest filterRequest = InternFilterRequest.builder()
                .keyword("Lương")
                .university("Bách Khoa")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<InternResponse> mockPageResponse = PageResponse.<InternResponse>builder()
                .items(List.of(mockResponse))
                .currentPage(0)
                .pageSize(10)
                .totalItems(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(internProfileService.searchInterns(eq(filterRequest), eq(pageable))).thenReturn(mockPageResponse);

        ResponseEntity<ApiResponse<PageResponse<InternResponse>>> result = internProfileController.searchInterns(filterRequest, pageable);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(200, result.getBody().getCode());
        assertEquals(1, result.getBody().getData().getTotalItems());
        assertEquals(1, result.getBody().getData().getItems().size());
        assertEquals("INT-202609-0001", result.getBody().getData().getItems().get(0).getInternCode());

        verify(internProfileService).searchInterns(eq(filterRequest), eq(pageable));
    }

    // =========================================================================
    // Task 5: Unit Tests for processDecision Endpoint (TM-11)
    // =========================================================================

    @Test
    @DisplayName("processDecision: Tra ve 200 OK khi phe duyet ho so thanh cong")
    void processDecision_whenApproved_shouldReturn200() {
        InternDecisionRequest request = InternDecisionRequest.builder()
                .decision(InternStatus.APPROVED)
                .build();

        mockResponse.setStatus(InternStatus.APPROVED);
        mockResponse.setReviewedBy("hr_manager");

        when(internProfileService.processDecision(eq(1L), any(), eq("hr_manager"))).thenReturn(mockResponse);

        Authentication authentication = new UsernamePasswordAuthenticationToken("hr_manager", null);

        ResponseEntity<ApiResponse<InternResponse>> result = internProfileController.processDecision(1L, request, authentication);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(200, result.getBody().getCode());
        assertEquals("Duyệt hồ sơ thực tập sinh thành công", result.getBody().getMessage());
        assertEquals(InternStatus.APPROVED, result.getBody().getData().getStatus());

        verify(internProfileService).processDecision(eq(1L), any(), eq("hr_manager"));
    }

    @Test
    @DisplayName("processDecision: Tra ve 200 OK khi tu choi ho so thanh cong")
    void processDecision_whenRejected_shouldReturn200() {
        InternDecisionRequest request = InternDecisionRequest.builder()
                .decision(InternStatus.REJECTED)
                .rejectionReason("Khong dat tieu chuan")
                .build();

        mockResponse.setStatus(InternStatus.REJECTED);
        mockResponse.setRejectionReason("Khong dat tieu chuan");
        mockResponse.setReviewedBy("hr_admin");

        when(internProfileService.processDecision(eq(1L), any(), eq("hr_admin"))).thenReturn(mockResponse);

        Authentication authentication = new UsernamePasswordAuthenticationToken("hr_admin", null);

        ResponseEntity<ApiResponse<InternResponse>> result = internProfileController.processDecision(1L, request, authentication);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(200, result.getBody().getCode());
        assertEquals("Từ chối hồ sơ thực tập sinh thành công", result.getBody().getMessage());
        assertEquals(InternStatus.REJECTED, result.getBody().getData().getStatus());

        verify(internProfileService).processDecision(eq(1L), any(), eq("hr_admin"));
    }
}


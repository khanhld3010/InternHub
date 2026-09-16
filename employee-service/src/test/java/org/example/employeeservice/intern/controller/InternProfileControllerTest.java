package org.example.employeeservice.intern.controller;

import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.intern.dto.request.CreateInternRequest;
import org.example.employeeservice.intern.dto.request.UpdateInternRequest;
import org.example.employeeservice.intern.dto.response.InternResponse;
import org.example.employeeservice.intern.entity.enums.InternStatus;
import org.example.employeeservice.intern.service.InternProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
        assertEquals(200, result.getBody().getCode());
        assertEquals("INT-202609-0001", result.getBody().getData().getInternCode());
        assertEquals("luonganhhuy.updated@gmail.com", result.getBody().getData().getEmail());
        assertEquals(InternStatus.APPROVED, result.getBody().getData().getStatus());

        verify(internProfileService).updateIntern(eq(1L), any(UpdateInternRequest.class));
    }
}

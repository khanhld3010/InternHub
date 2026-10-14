package org.example.internservice.support.service;

import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.support.dto.request.CreateSupportRequestDto;
import org.example.internservice.support.dto.response.SupportRequestResponse;
import org.example.internservice.support.entity.SupportRequest;
import org.example.internservice.support.entity.SupportRequestStatusLog;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;
import org.example.internservice.support.repository.DocumentAccessLogRepository;
import org.example.internservice.support.repository.SupportRequestRepository;
import org.example.internservice.support.repository.SupportRequestStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TC-SUP-SVC: Support Request Business Rules Unit Tests")
class SupportRequestServiceBusinessTest {

    @Mock
    private SupportRequestRepository supportRequestRepository;
    @Mock
    private SupportRequestStatusLogRepository statusLogRepository;
    @Mock
    private DocumentAccessLogRepository documentAccessLogRepository;
    @Mock
    private InternProfileRepository internProfileRepository;
    @Mock
    private DynamicContractRepository contractRepository;

    @InjectMocks
    private SupportRequestServiceImpl supportRequestService;

    private InternProfile sampleProfile;
    private Contract sampleContract;

    @BeforeEach
    void setUp() {
        sampleProfile = InternProfile.builder()
                .userId(10L)
                .fullName("Nguyen Van A")
                .email("intern.a@example.com")
                .status(InternStatus.INTERNING)
                .build();
        sampleProfile.setId(1L);

        sampleContract = Contract.builder()
                .internProfile(sampleProfile)
                .status(DynamicContractStatus.ACTIVE)
                .build();
        sampleContract.setId(5L);
    }

    @Test
    @DisplayName("TC-SUP-01: Chặn gửi trùng - Không cho phép tạo ticket nếu đang có ticket PENDING cùng loại")
    void testCreateSupportRequest_DuplicatePending_ThrowsBadRequest() {
        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));
        when(supportRequestRepository.existsActiveRequestByType(10L, SupportRequestType.INTERNSHIP_CONFIRMATION))
                .thenReturn(true);

        CreateSupportRequestDto dto = CreateSupportRequestDto.builder()
                .requestType(SupportRequestType.INTERNSHIP_CONFIRMATION)
                .title("Xin cấp giấy xác nhận thực tập")
                .content("Em cần nộp về trường gấp")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                supportRequestService.createRequest(dto, null, 10L)
        );

        assertTrue(ex.getMessage().contains("đang trong quá trình xử lý"),
                "Phải chặn tạo trùng ticket cùng loại đang PENDING/PROCESSING");
        verify(supportRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-SUP-02: Ngoại lệ COMPLETED/EXPIRED - Cho phép tạo COMPLETION_CERTIFICATE khi hợp đồng đã hết hạn (EXPIRED)")
    void testCreateSupportRequest_CompletedContract_CertificateAllowed() {
        sampleProfile.setStatus(InternStatus.COMPLETED);
        sampleContract.setStatus(DynamicContractStatus.EXPIRED); // Hợp đồng kết thúc

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));
        when(supportRequestRepository.existsActiveRequestByType(10L, SupportRequestType.COMPLETION_CERTIFICATE))
                .thenReturn(false);
        when(contractRepository.findFirstByInternProfileIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(sampleContract));
        when(supportRequestRepository.save(any(SupportRequest.class))).thenAnswer(i -> {
            SupportRequest saved = i.getArgument(0);
            saved.setId(101L);
            return saved;
        });
        when(statusLogRepository.save(any(SupportRequestStatusLog.class))).thenAnswer(i -> i.getArgument(0));

        CreateSupportRequestDto dto = CreateSupportRequestDto.builder()
                .requestType(SupportRequestType.COMPLETION_CERTIFICATE)
                .title("Xin giấy chứng nhận sau khi kết thúc kỳ thực tập")
                .content("Hợp đồng của em đã hết hạn, em xin chứng nhận nộp trường")
                .build();

        SupportRequestResponse res = supportRequestService.createRequest(dto, null, 10L);

        assertNotNull(res);
        assertEquals(SupportRequestStatus.PENDING, res.getStatus());
        assertEquals(SupportRequestType.COMPLETION_CERTIFICATE, res.getRequestType());
        verify(supportRequestRepository, times(1)).save(any(SupportRequest.class));
    }
}

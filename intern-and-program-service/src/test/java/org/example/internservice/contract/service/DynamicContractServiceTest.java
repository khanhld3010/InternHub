package org.example.internservice.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.internservice.contract.dto.request.CreateContractDraftRequest;
import org.example.internservice.contract.dto.response.DynamicContractResponse;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.ContractAuditLog;
import org.example.internservice.contract.entity.ContractRevision;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.ContractAuditLogRepository;
import org.example.internservice.contract.repository.ContractRevisionRepository;
import org.example.internservice.contract.repository.ContractSignatureRepository;
import org.example.internservice.contract.repository.ContractTemplateRepository;
import org.example.internservice.contract.repository.ContractTemplateVersionRepository;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.contract.service.impl.DynamicContractServiceImpl;
import org.example.internservice.contract.util.TemplateSanitizerAndValidator;
import org.example.internservice.intern.client.FileServiceClient;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicContractServiceTest {

    @Mock
    private DynamicContractRepository contractRepository;

    @Mock
    private ContractRevisionRepository revisionRepository;

    @Mock
    private ContractTemplateRepository templateRepository;

    @Mock
    private ContractTemplateVersionRepository templateVersionRepository;

    @Mock
    private ContractSignatureRepository signatureRepository;

    @Mock
    private ContractAuditLogRepository auditLogRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private TemplateSanitizerAndValidator sanitizerAndValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ContractPdfRendererService pdfRendererService;

    @Mock
    private FileServiceClient fileServiceClient;

    @InjectMocks
    private DynamicContractServiceImpl dynamicContractService;

    private InternProfile mockIntern;
    private InternshipProgram mockProgram;
    private Contract mockContract;
    private ContractRevision mockRevision;

    @BeforeEach
    void setUp() {
        mockIntern = InternProfile.builder()
                .internCode("INT-202610-0001")
                .fullName("Nguyen Van A")
                .email("nguyenvana@gmail.com")
                .phone("0987654321")
                .citizenId("001202012345")
                .university("DH Bach Khoa")
                .appliedPosition("Backend Java Intern")
                .startDate(LocalDate.now())
                .build();
        mockIntern.setId(10L);

        mockProgram = InternshipProgram.builder()
                .name("Chương trình Thực tập Sinh K2026")
                .build();
        mockProgram.setId(20L);

        mockContract = Contract.builder()
                .contractNumber("HDTT-123456789")
                .internProfile(mockIntern)
                .internshipProgram(mockProgram)
                .status(DynamicContractStatus.SENT)
                .currentRevisionId(100L)
                .build();
        mockContract.setId(50L);

        mockRevision = ContractRevision.builder()
                .contract(mockContract)
                .revisionNumber(1)
                .status(DynamicContractStatus.SENT)
                .canonicalSnapshotContent("<html>Hợp đồng thực tập</html>")
                .build();
        mockRevision.setId(100L);
    }

    @Test
    @DisplayName("[Lỗi 16 - RED] cancelContract: HR có thể thu hồi hợp đồng ở trạng thái SENT")
    void cancelContract_whenContractIsSent_shouldUpdateStatusToWithdrawnAndAudit() {
        when(contractRepository.findById(50L)).thenReturn(Optional.of(mockContract));
        when(revisionRepository.findById(100L)).thenReturn(Optional.of(mockRevision));

        DynamicContractResponse response = dynamicContractService.cancelContract(50L, "Sai thông tin phụ cấp", "hr_admin");

        assertThat(response).isNotNull();
        assertThat(mockContract.getStatus()).isEqualTo(DynamicContractStatus.WITHDRAWN);
        assertThat(mockRevision.getStatus()).isEqualTo(DynamicContractStatus.WITHDRAWN);
        verify(contractRepository).save(mockContract);
        verify(revisionRepository).save(mockRevision);
        verify(auditLogRepository).save(any(ContractAuditLog.class));
    }

    @Test
    @DisplayName("[Lỗi 16 - RED] cancelContract: Ném ngoại lệ khi hợp đồng đã ký kết SIGNED")
    void cancelContract_whenContractIsAlreadySigned_shouldThrowIllegalStateException() {
        mockContract.setStatus(DynamicContractStatus.SIGNED);
        mockRevision.setStatus(DynamicContractStatus.SIGNED);

        when(contractRepository.findById(50L)).thenReturn(Optional.of(mockContract));
        when(revisionRepository.findById(100L)).thenReturn(Optional.of(mockRevision));

        assertThatThrownBy(() -> dynamicContractService.cancelContract(50L, "Thu hồi", "hr_admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể thu hồi hợp đồng");
    }

    @Test
    @DisplayName("[Lỗi 17 - RED] buildVariablesMap: Trường intern.cccd phải điền số Căn cước công dân thay vì mã thực tập sinh")
    @SuppressWarnings("unchecked")
    void buildVariablesMap_shouldFillCccdWithCitizenIdInsteadOfInternCode() throws Exception {
        CreateContractDraftRequest req = CreateContractDraftRequest.builder()
                .internId(10L)
                .programId(20L)
                .templateId(1L)
                .allowanceAmount(BigDecimal.valueOf(5000000))
                .build();

        Method method = DynamicContractServiceImpl.class.getDeclaredMethod(
                "buildVariablesMap", InternProfile.class, String.class, CreateContractDraftRequest.class);
        method.setAccessible(true);

        Map<String, String> variables = (Map<String, String>) method.invoke(
                dynamicContractService, mockIntern, "HDTT-123456789", req);

        assertThat(variables).isNotNull();
        assertThat(variables.get("intern.cccd")).isEqualTo("001202012345");
        assertThat(variables.get("intern.cccd")).isNotEqualTo(mockIntern.getInternCode());
    }
}

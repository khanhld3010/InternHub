package org.example.internservice.program.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.dto.request.ChangeProgramStatusRequest;
import org.example.internservice.program.dto.request.CreateProgramRequest;
import org.example.internservice.program.dto.request.UpdateProgramRequest;
import org.example.internservice.program.dto.response.ProgramDetailResponse;
import org.example.internservice.program.dto.response.ProgramSummaryResponse;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramCodeSequence;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.DepartmentRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramCodeSequenceRepository;
import org.example.internservice.program.service.impl.InternshipProgramServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipProgramServiceTest {

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ProgramCodeSequenceRepository sequenceRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @InjectMocks
    private InternshipProgramServiceImpl programService;

    private Department devDepartment;
    private InternshipProgram sampleProgram;

    @BeforeEach
    void setUp() {
        devDepartment = Department.builder()
                .name("Phát triển phần mềm")
                .code("DEV")
                .status("ACTIVE")
                .build();
        devDepartment.setId(1L);

        sampleProgram = InternshipProgram.builder()
                .programCode("PRG-202610-0001")
                .name("Chương trình thực tập Backend")
                .department(devDepartment)
                .maxInterns(10)
                .currentInterns(0)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(45))
                .status(ProgramStatus.PLANNING)
                .isRecruitmentOpen(true)
                .isHistorical(false)
                .build();
        sampleProgram.setId(100L);
    }

    @Test
    @DisplayName("Tạo mới chương trình thành công với thời lượng >= 4 tuần và ngày bắt đầu trong tương lai")
    void createProgram_RegularSuccess() {
        CreateProgramRequest request = CreateProgramRequest.builder()
                .name("Chương trình mới")
                .departmentId(1L)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(35)) // 30 ngày > 27
                .maxInterns(15)
                .isHistorical(false)
                .build();

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(devDepartment));
        when(sequenceRepository.findByYearMonthWithLock(any())).thenReturn(Optional.of(
                ProgramCodeSequence.builder().yearMonth("202610").currentSeq(0L).updatedAt(LocalDateTime.now()).build()
        ));
        when(programRepository.save(any(InternshipProgram.class))).thenAnswer(invocation -> {
            InternshipProgram p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        ProgramDetailResponse response = programService.createProgram(request, "hr_test");

        assertThat(response).isNotNull();
        assertThat(response.getProgramCode()).startsWith("PRG-");
        assertThat(response.getStatus()).isEqualTo(ProgramStatus.PLANNING);
        assertThat(response.getAvailableSlots()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Tạo chương trình thường thất bại nếu thời lượng < 4 tuần")
    void createProgram_ThrowsBadRequest_WhenDurationLessThan4Weeks() {
        CreateProgramRequest request = CreateProgramRequest.builder()
                .name("Chương trình ngắn")
                .departmentId(1L)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(15)) // 10 ngày < 27
                .maxInterns(10)
                .isHistorical(false)
                .build();

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(devDepartment));

        assertThatThrownBy(() -> programService.createProgram(request, "hr_test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tối thiểu phải từ 4 tuần trở lên");
    }

    @Test
    @DisplayName("Tạo chương trình lịch sử thành công (cho phép ngày quá khứ và bỏ qua ràng buộc 4 tuần)")
    void createProgram_HistoricalSuccess() {
        CreateProgramRequest request = CreateProgramRequest.builder()
                .name("Chương trình 2024")
                .departmentId(1L)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 15)) // 14 ngày, trong quá khứ
                .currentInterns(8)
                .isHistorical(true)
                .build();

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(devDepartment));
        when(sequenceRepository.findByYearMonthWithLock(any())).thenReturn(Optional.empty());
        when(programRepository.save(any(InternshipProgram.class))).thenAnswer(invocation -> {
            InternshipProgram p = invocation.getArgument(0);
            p.setId(102L);
            return p;
        });

        ProgramDetailResponse response = programService.createProgram(request, "hr_test");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ProgramStatus.COMPLETED);
        assertThat(response.getIsHistorical()).isTrue();
        assertThat(response.getCurrentInterns()).isEqualTo(8L);
    }

    @Test
    @DisplayName("Cập nhật chương trình ONGOING: Ném BadRequest nếu cố tình sửa startDate")
    void updateProgram_ThrowsBadRequest_WhenUpdatingStartDateOnOngoing() {
        sampleProgram.setStatus(ProgramStatus.ONGOING);
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));

        UpdateProgramRequest request = UpdateProgramRequest.builder()
                .name("Cập nhật tên")
                .departmentId(1L)
                .startDate(sampleProgram.getStartDate().plusDays(2)) // Thay đổi ngày bắt đầu
                .endDate(sampleProgram.getEndDate().plusDays(10))
                .build();

        assertThatThrownBy(() -> programService.updateProgram(100L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể thay đổi ngày bắt đầu khi chương trình thực tập đang diễn ra");
    }

    @Test
    @DisplayName("Chuyển trạng thái: Ném BadRequest nếu chuyển từ COMPLETED sang OPEN")
    void changeStatus_ThrowsBadRequest_WhenInvalidTransition() {
        sampleProgram.setStatus(ProgramStatus.COMPLETED);
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));

        ChangeProgramStatusRequest request = ChangeProgramStatusRequest.builder()
                .targetStatus(ProgramStatus.OPEN)
                .build();

        assertThatThrownBy(() -> programService.changeStatus(100L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể chuyển trạng thái chương trình");
    }

    @Test
    @DisplayName("Chuyển sang CANCELLED: Kích hoạt cờ needsReassignment cho các TTS liên kết và không null programId")
    void changeStatus_ToCancelled_CascadesReassignmentFlag() {
        sampleProgram.setStatus(ProgramStatus.OPEN);
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));

        InternProfile intern = InternProfile.builder()
                .internCode("INT-202610-0001")
                .status(InternStatus.APPROVED)
                .program(sampleProgram)
                .needsReassignment(false)
                .build();

        when(internProfileRepository.findByProgramId(100L)).thenReturn(List.of(intern));
        when(programRepository.save(any(InternshipProgram.class))).thenReturn(sampleProgram);

        ChangeProgramStatusRequest request = ChangeProgramStatusRequest.builder()
                .targetStatus(ProgramStatus.CANCELLED)
                .cancellationReason("Dự án tạm hoãn")
                .build();

        programService.changeStatus(100L, request);

        assertThat(sampleProgram.getStatus()).isEqualTo(ProgramStatus.CANCELLED);
        assertThat(intern.getNeedsReassignment()).isTrue();
        assertThat(intern.getReassignmentReason()).contains("Dự án tạm hoãn");
        assertThat(intern.getProgram()).isEqualTo(sampleProgram); // Vẫn giữ nguyên program_id
        verify(internProfileRepository).save(intern);
    }

    @Test
    @DisplayName("Xóa chương trình: Ném BadRequest nếu đã có hồ sơ TTS gắn kèm")
    void deleteProgram_ThrowsBadRequest_WhenInternProfilesLinked() {
        sampleProgram.setStatus(ProgramStatus.PLANNING);
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramId(100L)).thenReturn(3L);

        assertThatThrownBy(() -> programService.deleteProgram(100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể xóa chương trình đã có 3 hồ sơ ứng viên gắn kèm");
    }

    @Test
    @DisplayName("Xóa chương trình: Thành công khi ở PLANNING và 0 hồ sơ")
    void deleteProgram_Success_WhenPlanningAndZeroInterns() {
        sampleProgram.setStatus(ProgramStatus.PLANNING);
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramId(100L)).thenReturn(0L);

        programService.deleteProgram(100L);

        verify(programRepository).delete(sampleProgram);
    }

    @Test
    @DisplayName("TM-10: Lấy danh sách các chương trình đang mở tuyển (getOpenPrograms)")
    void getOpenPrograms_Success() {
        when(programRepository.findOpenProgramsWithDepartment(anyList()))
                .thenReturn(List.of(sampleProgram));

        List<ProgramSummaryResponse> result = programService.getOpenPrograms();

        assertThat(result).isNotEmpty().hasSize(1);
        ProgramSummaryResponse summary = result.get(0);
        assertThat(summary.getProgramCode()).isEqualTo(sampleProgram.getProgramCode());
        assertThat(summary.getName()).isEqualTo(sampleProgram.getName());
        assertThat(summary.getDepartmentName()).isEqualTo("Phát triển phần mềm");
        assertThat(summary.getIsRecruitmentOpen()).isTrue();
    }
}

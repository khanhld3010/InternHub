package org.example.internservice.program.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.dto.response.ExcelImportPreviewResponse;
import org.example.internservice.program.dto.response.ExcelImportResultResponse;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.service.impl.InternExcelImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternExcelImportServiceTest {

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private org.example.internservice.program.repository.ProgramMentorRepository programMentorRepository;

    @Mock
    private org.example.internservice.intern.repository.InternMentorAssignmentRepository internMentorAssignmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternExcelImportServiceImpl excelImportService;

    private InternshipProgram sampleProgram;

    @BeforeEach
    void setUp() {
        Department department = Department.builder()
                .name("Phòng Phát triển Phần mềm")
                .code("DEV")
                .build();
        department.setId(1L);

        sampleProgram = InternshipProgram.builder()
                .programCode("PRG-202610-0001")
                .name("Chương trình Thực tập Java Backend K26")
                .department(department)
                .maxInterns(20)
                .currentInterns(5)
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(60))
                .status(ProgramStatus.OPEN)
                .isRecruitmentOpen(true)
                .build();
        sampleProgram.setId(10L);
    }

    @Test
    @DisplayName("UT-EXCEL-01: Sinh tệp Excel mẫu trả về mảng byte hợp lệ mở được bằng POI")
    void generateExcelTemplate_shouldReturnValidExcelBytes() throws Exception {
        byte[] bytes = excelImportService.generateExcelTemplate();

        assertThat(bytes).isNotNull().isNotEmpty();

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(in)) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Danh sách thực tập sinh");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).contains("Họ và tên");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue()).contains("Số điện thoại");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue()).isEqualTo("+84912345678");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("UT-EXCEL-02: Preview tệp Excel hợp lệ với định dạng SĐT +84 chuẩn hóa thành công về 0... không có lỗi")
    void previewExcelImport_whenValidFile_shouldReturnAllValidRows() throws Exception {
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramIdAndStatusIn(eq(10L), anyList())).thenReturn(5L);
        when(internProfileRepository.findAllByEmailIn(anyList())).thenReturn(Collections.emptyList());
        when(internProfileRepository.findAllByPhoneIn(anyList())).thenReturn(Collections.emptyList());

        MockMultipartFile file = createExcelFile("test_interns.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "a.nguyen@example.com", "+84912345678", "Nam", "15/05/2003", "Đại học Bách Khoa", "CNTT", "2021-2025", "Hà Nội", "", ""),
                List.of("2", "Trần Thị B", "b.tran@example.com", "+84 987 654 321", "Nữ", "20/10/2003", "Đại học Bách Khoa", "KHMT", "2021-2025", "Đà Nẵng", "", "")
        ));

        ExcelImportPreviewResponse response = excelImportService.previewExcelImport(10L, file);

        assertThat(response).isNotNull();
        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getValidRowsCount()).isEqualTo(2);
        assertThat(response.getInvalidRowsCount()).isEqualTo(0);
        assertThat(response.getAvailableSlots()).isEqualTo(15);
        assertThat(response.isQuotaExceeded()).isFalse();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getPreviewRows()).hasSize(2);
        assertThat(response.getPreviewRows().get(0).getPhone()).isEqualTo("0912345678");
        assertThat(response.getPreviewRows().get(1).getPhone()).isEqualTo("0987654321");
    }

    @Test
    @DisplayName("UT-EXCEL-03: Preview tệp chứa email sai định dạng và SĐT sai trả về danh sách lỗi")
    void previewExcelImport_whenInvalidPhoneAndEmail_shouldReturnDetailedErrors() throws Exception {
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramIdAndStatusIn(eq(10L), anyList())).thenReturn(5L);

        MockMultipartFile file = createExcelFile("invalid_data.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "invalid-email-format", "12345", "Nam", "15/05/2003", "Đại học Bách Khoa", "CNTT", "", "", "", "")
        ));

        ExcelImportPreviewResponse response = excelImportService.previewExcelImport(10L, file);

        assertThat(response).isNotNull();
        assertThat(response.getTotalRows()).isEqualTo(1);
        assertThat(response.getValidRowsCount()).isEqualTo(0);
        assertThat(response.getInvalidRowsCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(2); // Lỗi email và lỗi SĐT
    }

    @Test
    @DisplayName("UT-EXCEL-04: Import khi số lượng TTS vượt quá chỉ tiêu trống của chương trình ném BadRequestException")
    void importInternsFromExcel_whenProgramIsFull_shouldThrowBadRequestException() throws Exception {
        sampleProgram.setMaxInterns(5);
        when(programRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramIdAndStatusIn(eq(10L), anyList())).thenReturn(5L); // Còn 0 slot

        MockMultipartFile file = createExcelFile("one_intern.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "a.nguyen@example.com", "0912345678", "Nam", "15/05/2003", "Đại học Bách Khoa", "CNTT", "", "", "", "")
        ));

        assertThatThrownBy(() -> excelImportService.importInternsFromExcel(10L, file, "APPROVED", "hr_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chương trình chỉ còn 0 chỉ tiêu trống");
    }

    @Test
    @DisplayName("UT-EXCEL-05: Import khi tệp bị trùng email nội bộ ném BadRequestException")
    void importInternsFromExcel_whenInternalDuplicateEmail_shouldThrowBadRequestException() throws Exception {
        when(programRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleProgram));

        MockMultipartFile file = createExcelFile("duplicate.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "duplicate@example.com", "0912345678", "Nam", "15/05/2003", "Bách Khoa", "CNTT", "", "", "", ""),
                List.of("2", "Nguyễn Văn B", "duplicate@example.com", "0987654321", "Nam", "20/05/2003", "Bách Khoa", "CNTT", "", "", "", "")
        ));

        assertThatThrownBy(() -> excelImportService.importInternsFromExcel(10L, file, "APPROVED", "hr_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tệp Excel chứa")
                .hasMessageContaining("bị trùng lặp ngay trong tệp Excel");
    }

    @Test
    @DisplayName("UT-EXCEL-06: Import khi chương trình đóng nhận hồ sơ ném BadRequestException")
    void importInternsFromExcel_whenProgramClosed_shouldThrowBadRequestException() throws Exception {
        sampleProgram.setIsRecruitmentOpen(false);
        when(programRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleProgram));

        MockMultipartFile file = createExcelFile("valid.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "a.nguyen@example.com", "0912345678", "Nam", "15/05/2003", "Bách Khoa", "CNTT", "", "", "", "")
        ));

        assertThatThrownBy(() -> excelImportService.importInternsFromExcel(10L, file, "APPROVED", "hr_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tạm dừng nhận hồ sơ tuyển sinh");
    }

    @Test
    @DisplayName("UT-EXCEL-07: Import tệp hợp lệ lưu thành công, sinh mã TTS và cập nhật chỉ tiêu chương trình")
    void importInternsFromExcel_whenValidFile_shouldSaveAllInternsAndPublishEvents() throws Exception {
        when(programRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleProgram));
        when(internProfileRepository.countByProgramIdAndStatusIn(eq(10L), anyList()))
                .thenReturn(5L)  // Trước khi lưu: 5
                .thenReturn(7L); // Sau khi lưu: 7
        when(internProfileRepository.findAllByEmailIn(anyList())).thenReturn(Collections.emptyList());
        when(internProfileRepository.findAllByPhoneIn(anyList())).thenReturn(Collections.emptyList());
        when(internProfileRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);

        MockMultipartFile file = createExcelFile("valid_interns.xlsx", List.of(
                List.of("1", "Nguyễn Văn A", "a.nguyen@example.com", "0912345678", "Nam", "15/05/2003", "Đại học Bách Khoa", "CNTT", "2021-2025", "Hà Nội", "", ""),
                List.of("2", "Trần Thị B", "b.tran@example.com", "0987654321", "Nữ", "20/10/2003", "Đại học Bách Khoa", "KHMT", "2021-2025", "Đà Nẵng", "", "")
        ));

        ExcelImportResultResponse result = excelImportService.importInternsFromExcel(10L, file, "APPROVED", "hr_manager");

        assertThat(result).isNotNull();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getImportedInternCodes()).hasSize(2);
        assertThat(result.getImportedInternCodes().get(0)).contains("INT-");

        verify(internProfileRepository).saveAll(anyList());
        verify(programRepository).save(sampleProgram);
    }

    private MockMultipartFile createExcelFile(String fileName, List<List<String>> rowsData) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {
                    "STT", "Họ và tên (*)", "Email (*)", "Số điện thoại (*)", "Giới tính",
                    "Ngày sinh (dd/MM/yyyy)", "Trường ĐH/CĐ (*)", "Chuyên ngành (*)",
                    "Niên khóa", "Địa chỉ", "Vị trí thực tập", "Ghi chú"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rowsData.size(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                List<String> rowCells = rowsData.get(r);
                for (int c = 0; c < rowCells.size(); c++) {
                    row.createCell(c).setCellValue(rowCells.get(c));
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}

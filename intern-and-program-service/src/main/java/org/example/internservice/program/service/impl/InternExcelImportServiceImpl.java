package org.example.internservice.program.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.event.InternDecisionProcessedEvent;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.dto.request.ExcelInternRowDto;
import org.example.internservice.program.dto.response.ExcelImportPreviewResponse;
import org.example.internservice.program.dto.response.ExcelImportResultResponse;
import org.example.internservice.program.dto.response.ExcelRowError;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.service.InternExcelImportService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternExcelImportServiceImpl implements InternExcelImportService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_ROW_LIMIT = 200;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0[35789])\\d{8}$");

    private static final List<InternStatus> ACTIVE_STATUSES = List.of(
            InternStatus.APPROVED,
            InternStatus.INTERNING,
            InternStatus.COMPLETED
    );

    private final InternshipProgramRepository programRepository;
    private final InternProfileRepository internProfileRepository;
    private final org.example.internservice.program.repository.ProgramMentorRepository programMentorRepository;
    private final org.example.internservice.intern.repository.InternMentorAssignmentRepository internMentorAssignmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sách thực tập sinh");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            String[] headers = {
                    "STT", "Họ và tên (*)", "Email (*)", "Số điện thoại (*) (+84.../0...)", "Giới tính",
                    "Ngày sinh (dd/MM/yyyy)", "Trường ĐH/CĐ (*)", "Chuyên ngành (*)",
                    "Niên khóa", "Địa chỉ", "Vị trí thực tập", "Ghi chú"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Cell Styles cho dữ liệu mẫu và định dạng cột
            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(dataFormat.getFormat("@"));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy"));
            dateStyle.setAlignment(HorizontalAlignment.CENTER);

            // Dữ liệu mẫu minh họa 1
            Row sampleRow1 = sheet.createRow(1);
            sampleRow1.createCell(0).setCellValue(1);
            sampleRow1.createCell(1).setCellValue("Nguyễn Văn An");
            sampleRow1.createCell(2).setCellValue("an.nguyen@example.com");
            Cell phoneCell1 = sampleRow1.createCell(3);
            phoneCell1.setCellValue("+84912345678");
            phoneCell1.setCellStyle(textStyle);
            sampleRow1.createCell(4).setCellValue("Nam");
            Cell dobCell1 = sampleRow1.createCell(5);
            dobCell1.setCellValue("15/05/2003");
            dobCell1.setCellStyle(dateStyle);
            sampleRow1.createCell(6).setCellValue("Đại học Bách Khoa");
            sampleRow1.createCell(7).setCellValue("Công nghệ thông tin");
            sampleRow1.createCell(8).setCellValue("2021-2025");
            sampleRow1.createCell(9).setCellValue("Hà Nội");
            sampleRow1.createCell(10).setCellValue("Java Backend Intern");
            sampleRow1.createCell(11).setCellValue("Sinh viên xuất sắc");

            // Dữ liệu mẫu minh họa 2
            Row sampleRow2 = sheet.createRow(2);
            sampleRow2.createCell(0).setCellValue(2);
            sampleRow2.createCell(1).setCellValue("Trần Thị Bình");
            sampleRow2.createCell(2).setCellValue("binh.tran@example.com");
            Cell phoneCell2 = sampleRow2.createCell(3);
            phoneCell2.setCellValue("+84987654321");
            phoneCell2.setCellStyle(textStyle);
            sampleRow2.createCell(4).setCellValue("Nữ");
            Cell dobCell2 = sampleRow2.createCell(5);
            dobCell2.setCellValue("20/10/2003");
            dobCell2.setCellStyle(dateStyle);
            sampleRow2.createCell(6).setCellValue("Đại học Quốc Gia");
            sampleRow2.createCell(7).setCellValue("Khoa học máy tính");
            sampleRow2.createCell(8).setCellValue("2021-2025");
            sampleRow2.createCell(9).setCellValue("Đà Nẵng");
            sampleRow2.createCell(10).setCellValue("Frontend React Intern");
            sampleRow2.createCell(11).setCellValue("");

            // Đặt định dạng văn bản mặc định cho cột SĐT
            sheet.setDefaultColumnStyle(3, textStyle);

            // Tạo dropdown danh sách lựa chọn Giới tính (Nam, Nữ, Khác)
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            CellRangeAddressList addressList = new CellRangeAddressList(1, 500, 4, 4);
            DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(new String[]{"Nam", "Nữ", "Khác"});
            DataValidation dataValidation = validationHelper.createValidation(constraint, addressList);
            dataValidation.setSuppressDropDownArrow(true);
            dataValidation.setShowErrorBox(true);
            sheet.addValidationData(dataValidation);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi sinh tệp Excel mẫu: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể tạo tệp Excel mẫu: " + e.getMessage());
        }
    }

    @Override
    public ExcelImportPreviewResponse previewExcelImport(Long programId, MultipartFile file) {
        InternshipProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId));

        validateProgramState(program);
        validateUploadedFile(file);

        List<ExcelInternRowDto> rows = parseExcelFile(file, program.getName());
        List<ExcelRowError> errors = new ArrayList<>();
        validateAndCheckDuplicates(rows, errors);

        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_STATUSES);
        int availableSlots = Math.max(0, program.getMaxInterns() - (int) activeCount);
        int validCount = rows.size() - (int) errors.stream().map(ExcelRowError::getRowNumber).distinct().count();
        boolean isQuotaExceeded = validCount > availableSlots;

        List<ExcelInternRowDto> previewRows = rows.stream().limit(10).collect(Collectors.toList());

        return ExcelImportPreviewResponse.builder()
                .programId(program.getId())
                .programName(program.getName())
                .totalRows(rows.size())
                .validRowsCount(validCount)
                .invalidRowsCount(rows.size() - validCount)
                .availableSlots(availableSlots)
                .isQuotaExceeded(isQuotaExceeded)
                .errors(errors)
                .previewRows(errors.isEmpty() ? previewRows : List.of())
                .build();
    }

    @Override
    @Transactional
    public ExcelImportResultResponse importInternsFromExcel(Long programId, MultipartFile file, String targetStatus, String importedBy) {
        log.info("HR {} thực hiện import Excel vào chương trình ID: {}", importedBy, programId);

        InternshipProgram program = programRepository.findByIdWithLock(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + programId));

        validateProgramState(program);
        validateUploadedFile(file);

        List<ExcelInternRowDto> rows = parseExcelFile(file, program.getName());
        List<ExcelRowError> errors = new ArrayList<>();
        validateAndCheckDuplicates(rows, errors);

        if (!errors.isEmpty()) {
            String firstError = errors.get(0).getErrorMessage();
            throw new BadRequestException(String.format("Tệp Excel chứa %d lỗi (Ví dụ dòng %d: %s). Vui lòng sửa lại toàn bộ tệp.",
                    errors.size(), errors.get(0).getRowNumber(), firstError));
        }

        long activeCount = internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_STATUSES);
        int availableSlots = Math.max(0, program.getMaxInterns() - (int) activeCount);

        if (rows.size() > availableSlots) {
            throw new BadRequestException(String.format("Không thể tiếp nhận %d TTS. Chương trình chỉ còn %d chỉ tiêu trống (%d/%d).",
                    rows.size(), availableSlots, activeCount, program.getMaxInterns()));
        }

        InternStatus status = "PENDING".equalsIgnoreCase(targetStatus) ? InternStatus.PENDING : InternStatus.APPROVED;
        LocalDateTime now = LocalDateTime.now();
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);
        long currentMonthCount = internProfileRepository.countByCreatedAtBetween(startOfMonth, endOfMonth);

        List<InternProfile> profiles = new ArrayList<>();
        List<String> generatedCodes = new ArrayList<>();

        List<org.example.internservice.program.entity.ProgramMentor> programMentors = programMentorRepository != null 
                ? programMentorRepository.findByProgramId(programId) 
                : List.of();
        org.example.internservice.intern.entity.MentorProfile defaultMentor = programMentors.isEmpty() 
                ? null 
                : programMentors.get(programMentors.size() - 1).getMentor();

        for (int i = 0; i < rows.size(); i++) {
            ExcelInternRowDto dto = rows.get(i);
            String internCode = String.format("INT-%s-%04d", yearMonth, currentMonthCount + i + 1);
            generatedCodes.add(internCode);

            Long mentorId = null;
            String mentorName = null;
            String mentorEmail = null;
            InternStatus assignedStatus = status;

            if (status == InternStatus.APPROVED && defaultMentor != null) {
                mentorId = defaultMentor.getId();
                mentorName = defaultMentor.getFullName();
                mentorEmail = defaultMentor.getEmail();
                if (program.getStatus() == ProgramStatus.ONGOING) {
                    assignedStatus = InternStatus.INTERNING;
                }
            }

            InternProfile profile = InternProfile.builder()
                    .internCode(internCode)
                    .fullName(dto.getFullName())
                    .email(dto.getEmail())
                    .phone(dto.getPhone())
                    .gender(dto.getGender())
                    .dateOfBirth(dto.getDateOfBirth())
                    .address(dto.getAddress())
                    .university(dto.getUniversity())
                    .major(dto.getMajor())
                    .academicYear(dto.getAcademicYear())
                    .appliedPosition(dto.getAppliedPosition())
                    .startDate(program.getStartDate())
                    .endDate(program.getEndDate())
                    .status(assignedStatus)
                    .program(program)
                    .reviewedBy(importedBy)
                    .reviewedAt(now)
                    .needsReassignment(false)
                    .mentorId(mentorId)
                    .mentorName(mentorName)
                    .mentorEmail(mentorEmail)
                    .notes(dto.getNotes())
                    .build();

            profiles.add(profile);
        }

        internProfileRepository.saveAll(profiles);

        if (status == InternStatus.APPROVED) {
            if (defaultMentor != null && internMentorAssignmentRepository != null) {
                List<org.example.internservice.intern.entity.InternMentorAssignment> assignments = profiles.stream().map(p ->
                        org.example.internservice.intern.entity.InternMentorAssignment.builder()
                                .intern(p)
                                .mentorId(defaultMentor.getId())
                                .mentorName(defaultMentor.getFullName())
                                .mentorEmail(defaultMentor.getEmail())
                                .assignedBy(importedBy)
                                .assignedAt(now)
                                .status(org.example.internservice.intern.entity.enums.MentorAssignmentStatus.ACTIVE)
                                .notes("Tự động kế thừa Mentor của chương trình khi import Excel")
                                .build()
                ).toList();
                internMentorAssignmentRepository.saveAll(assignments);
            }
            long newActiveCount = internProfileRepository.countByProgramIdAndStatusIn(programId, ACTIVE_STATUSES);
            program.setCurrentInterns((int) newActiveCount);
            programRepository.save(program);

            if (eventPublisher != null) {
                for (InternProfile p : profiles) {
                    try {
                        eventPublisher.publishEvent(new InternDecisionProcessedEvent(this, p));
                    } catch (Exception e) {
                        log.warn("Không thể phát sự kiện duyệt hồ sơ cho TTS ID={}: {}", p.getId(), e.getMessage());
                    }
                }
            }
        }

        return ExcelImportResultResponse.builder()
                .programId(program.getId())
                .programName(program.getName())
                .importedCount(profiles.size())
                .currentInterns(program.getCurrentInterns())
                .maxInterns(program.getMaxInterns())
                .importedInternCodes(generatedCodes)
                .importedAt(now)
                .build();
    }

    private void validateProgramState(InternshipProgram program) {
        if (program.getStatus() != ProgramStatus.PLANNING && program.getStatus() != ProgramStatus.OPEN) {
            throw new BadRequestException("Chương trình thực tập không ở trạng thái nhận hồ sơ (" + program.getStatus().getDisplayName() + ")");
        }
        if (!Boolean.TRUE.equals(program.getIsRecruitmentOpen())) {
            throw new BadRequestException("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");
        }
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp Excel tải lên không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng tệp vượt quá giới hạn cho phép (tối đa 5MB)");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".xlsx") && !fileName.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Chỉ chấp nhận tệp bảng tính định dạng .xlsx hoặc .xls");
        }
    }

    private List<ExcelInternRowDto> parseExcelFile(MultipartFile file, String defaultPosition) {
        List<ExcelInternRowDto> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.getDefault());

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            if (lastRowNum <= 0) {
                throw new BadRequestException("Tệp Excel không chứa dữ liệu thực tập sinh");
            }
            if (lastRowNum > MAX_ROW_LIMIT) {
                throw new BadRequestException("Tệp Excel vượt quá giới hạn số dòng (tối đa " + MAX_ROW_LIMIT + " dòng/lần import)");
            }

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                String fullName = getCellValueAsString(row.getCell(1), formatter);
                String email = getCellValueAsString(row.getCell(2), formatter).toLowerCase();
                String rawPhone = getCellValueAsString(row.getCell(3), formatter);
                String phone = normalizePhone(rawPhone);
                String genderStr = getCellValueAsString(row.getCell(4), formatter);
                LocalDate dob = parseDateOfBirth(row.getCell(5), formatter);
                String university = getCellValueAsString(row.getCell(6), formatter);
                String major = getCellValueAsString(row.getCell(7), formatter);
                String academicYear = getCellValueAsString(row.getCell(8), formatter);
                String address = getCellValueAsString(row.getCell(9), formatter);
                String appliedPosition = getCellValueAsString(row.getCell(10), formatter);
                String notes = getCellValueAsString(row.getCell(11), formatter);

                if (appliedPosition.isEmpty()) {
                    appliedPosition = defaultPosition;
                }

                Gender gender = parseGender(genderStr);

                rows.add(ExcelInternRowDto.builder()
                        .rowNumber(r + 1)
                        .fullName(fullName)
                        .email(email)
                        .phone(phone)
                        .gender(gender)
                        .dateOfBirth(dob)
                        .university(university)
                        .major(major)
                        .academicYear(academicYear)
                        .address(address)
                        .appliedPosition(appliedPosition)
                        .notes(notes)
                        .build());
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi đọc tệp Excel: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể đọc tệp Excel: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new BadRequestException("Tệp Excel không chứa dòng dữ liệu nào hợp lệ");
        }

        return rows;
    }

    private void validateAndCheckDuplicates(List<ExcelInternRowDto> rows, List<ExcelRowError> errors) {
        Set<String> fileEmails = new HashSet<>();
        Set<String> filePhones = new HashSet<>();

        for (ExcelInternRowDto row : rows) {
            int rowNum = row.getRowNumber();

            if (row.getFullName().isEmpty() || row.getFullName().length() < 2 || row.getFullName().length() > 100) {
                errors.add(new ExcelRowError(rowNum, "fullName", row.getFullName(), "Họ và tên phải có độ dài từ 2 đến 100 ký tự"));
            }
            if (row.getEmail().isEmpty() || !EMAIL_PATTERN.matcher(row.getEmail()).matches()) {
                errors.add(new ExcelRowError(rowNum, "email", row.getEmail(), "Email không đúng định dạng hợp lệ"));
            } else if (!fileEmails.add(row.getEmail())) {
                errors.add(new ExcelRowError(rowNum, "email", row.getEmail(), "Email bị trùng lặp ngay trong tệp Excel"));
            }

            if (row.getPhone().isEmpty() || !PHONE_PATTERN.matcher(row.getPhone()).matches()) {
                errors.add(new ExcelRowError(rowNum, "phone", row.getPhone(), "Số điện thoại không đúng định dạng Việt Nam (Chấp nhận +84... hoặc 0..., ví dụ: +84912345678 hoặc 0912345678)"));
            } else if (!filePhones.add(row.getPhone())) {
                errors.add(new ExcelRowError(rowNum, "phone", row.getPhone(), "Số điện thoại bị trùng lặp ngay trong tệp Excel"));
            }

            if (row.getUniversity().isEmpty()) {
                errors.add(new ExcelRowError(rowNum, "university", row.getUniversity(), "Trường ĐH/CĐ không được để trống"));
            }
            if (row.getMajor().isEmpty()) {
                errors.add(new ExcelRowError(rowNum, "major", row.getMajor(), "Chuyên ngành không được để trống"));
            }
        }

        // Kiểm tra đối soát trùng lặp với Database
        List<String> emailsToCheck = rows.stream().map(ExcelInternRowDto::getEmail).filter(EMAIL_PATTERN.asPredicate()).collect(Collectors.toList());
        List<String> phonesToCheck = rows.stream().map(ExcelInternRowDto::getPhone).filter(PHONE_PATTERN.asPredicate()).collect(Collectors.toList());

        if (!emailsToCheck.isEmpty()) {
            Set<String> existingEmails = internProfileRepository.findAllByEmailIn(emailsToCheck).stream()
                    .map(InternProfile::getEmail)
                    .collect(Collectors.toSet());
            for (ExcelInternRowDto row : rows) {
                if (existingEmails.contains(row.getEmail())) {
                    errors.add(new ExcelRowError(row.getRowNumber(), "email", row.getEmail(), "Email đã tồn tại trên hệ thống"));
                }
            }
        }

        if (!phonesToCheck.isEmpty()) {
            Set<String> existingPhones = internProfileRepository.findAllByPhoneIn(phonesToCheck).stream()
                    .map(InternProfile::getPhone)
                    .collect(Collectors.toSet());
            for (ExcelInternRowDto row : rows) {
                if (existingPhones.contains(row.getPhone())) {
                    errors.add(new ExcelRowError(row.getRowNumber(), "phone", row.getPhone(), "Số điện thoại đã tồn tại trên hệ thống"));
                }
            }
        }
    }

    private String getCellValueAsString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null) return "";
        String clean = rawPhone.trim();
        // Loại bỏ ký tự nháy đơn (') ở đầu nếu có (kỹ thuật nhập Text của Excel)
        clean = clean.replaceAll("^'+", "");
        // Loại bỏ các ký tự phân cách thông thường: khoảng trắng, dấu gạch nối, dấu chấm, gạch dưới, ngoặc đơn
        clean = clean.replaceAll("[\\s\\-\\._\\(\\)]", "");

        // Chuẩn hóa tiền tố quốc tế Việt Nam (+84 hoặc 84) về đầu số 0
        if (clean.startsWith("+840")) {
            clean = "0" + clean.substring(4);
        } else if (clean.startsWith("+84")) {
            clean = "0" + clean.substring(3);
        } else if (clean.startsWith("840") && clean.length() == 12) {
            clean = "0" + clean.substring(3);
        } else if (clean.startsWith("84") && clean.length() == 11) {
            clean = "0" + clean.substring(2);
        } else if (clean.startsWith("+")) {
            clean = clean.substring(1);
        }

        // Lọc lại các ký tự số
        clean = clean.replaceAll("[^0-9]", "");

        // Trường hợp Excel tự động cắt bỏ số 0 ở đầu (ví dụ: 912345678 còn 9 chữ số)
        if (clean.length() == 9 && (clean.startsWith("3") || clean.startsWith("5") || clean.startsWith("7") || clean.startsWith("8") || clean.startsWith("9"))) {
            clean = "0" + clean;
        }

        return clean;
    }

    private Gender parseGender(String genderStr) {
        if (genderStr == null || genderStr.trim().isEmpty()) {
            return Gender.MALE;
        }
        String clean = genderStr.trim().toLowerCase();
        if (clean.contains("nữ") || clean.contains("nu") || clean.equals("female")) {
            return Gender.FEMALE;
        }
        if (clean.contains("khác") || clean.contains("khac") || clean.equals("other")) {
            return Gender.OTHER;
        }
        return Gender.MALE;
    }

    private LocalDate parseDateOfBirth(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String val = formatter.formatCellValue(cell).trim();
        if (val.isEmpty()) return null;

        String[] patterns = {"dd/MM/yyyy", "yyyy-MM-dd", "d/M/yyyy", "dd-MM-yyyy"};
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(val, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}

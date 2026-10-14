package org.example.internservice.allowance.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.internservice.allowance.dto.response.AllowanceRecordResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class AllowanceExcelExportHelper {

    private static final String[] HEADERS = {
            "STT", "Mã TTS", "Họ và Tên", "Email", "Kỳ phụ cấp",
            "Mức hợp đồng (VNĐ)", "Ngày chuẩn", "Ngày làm thực tế", "Ngày nghỉ phép/lễ",
            "Thưởng (VNĐ)", "Khấu trừ (VNĐ)", "Truy lĩnh/Truy thu (VNĐ)", "Thực nhận (VNĐ)", "Trạng thái & Ngày chi trả"
    };

    public static byte[] exportToExcel(List<AllowanceRecordResponse> records, String periodMonth) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bang_Phu_Cap_" + periodMonth);

            // Style Header
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Row Header
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowIdx = 1;
            for (AllowanceRecordResponse r : records) {
                Row row = sheet.createRow(rowIdx);
                row.createCell(0).setCellValue(rowIdx);
                row.createCell(1).setCellValue(r.getInternProfileId() != null ? String.valueOf(r.getInternProfileId()) : "");
                row.createCell(2).setCellValue(r.getInternFullName() != null ? r.getInternFullName() : "");
                row.createCell(3).setCellValue(r.getInternEmail() != null ? r.getInternEmail() : "");
                row.createCell(4).setCellValue(r.getPeriodMonth() != null ? r.getPeriodMonth() : "");
                row.createCell(5).setCellValue(r.getBaseAllowance() != null ? r.getBaseAllowance().doubleValue() : 0.0);
                row.createCell(6).setCellValue(r.getStandardWorkingDays() != null ? r.getStandardWorkingDays() : 0);
                row.createCell(7).setCellValue(r.getActualWorkingDays() != null ? r.getActualWorkingDays() : 0);
                row.createCell(8).setCellValue(r.getPaidLeaveDays() != null ? r.getPaidLeaveDays() : 0);
                row.createCell(9).setCellValue(r.getBonusAmount() != null ? r.getBonusAmount().doubleValue() : 0.0);
                row.createCell(10).setCellValue(r.getDeductionAmount() != null ? r.getDeductionAmount().doubleValue() : 0.0);
                row.createCell(11).setCellValue(r.getRetroAdjustmentAmount() != null ? r.getRetroAdjustmentAmount().doubleValue() : 0.0);
                row.createCell(12).setCellValue(r.getActualAmount() != null ? r.getActualAmount().doubleValue() : 0.0);
                String statusStr = r.getStatus() != null ? r.getStatus().name() : "";
                if (r.getPaymentDate() != null) {
                    statusStr += " (" + r.getPaymentDate() + ")";
                }
                row.createCell(13).setCellValue(statusStr);
                rowIdx++;
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}

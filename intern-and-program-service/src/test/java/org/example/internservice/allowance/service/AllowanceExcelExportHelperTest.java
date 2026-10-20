package org.example.internservice.allowance.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.internservice.allowance.dto.response.AllowanceRecordResponse;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowanceExcelExportHelperTest {

    @Test
    @DisplayName("Xuất Excel bảng phụ cấp: Định dạng CellStyle Currency (#,##0) cho các cột số tiền")
    void exportToExcel_shouldFormatCurrencyColumnsCorrectly() throws IOException {
        AllowanceRecordResponse record = AllowanceRecordResponse.builder()
                .id(1L)
                .internProfileId(101L)
                .internFullName("Nguyen Van A")
                .internEmail("a.nguyen@example.com")
                .periodMonth("2026-10")
                .baseAllowance(new BigDecimal("5000000.00"))
                .standardWorkingDays(22)
                .actualWorkingDays(20)
                .paidLeaveDays(2)
                .bonusAmount(new BigDecimal("500000.00"))
                .deductionAmount(new BigDecimal("200000.00"))
                .retroAdjustmentAmount(BigDecimal.ZERO)
                .actualAmount(new BigDecimal("5300000.00"))
                .status(AllowanceStatus.APPROVED)
                .build();

        byte[] excelBytes = AllowanceExcelExportHelper.exportToExcel(List.of(record), "2026-10");
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (ByteArrayInputStream in = new ByteArrayInputStream(excelBytes);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("Bang_Phu_Cap_2026-10");
            assertNotNull(sheet);

            Row dataRow = sheet.getRow(1);
            assertNotNull(dataRow);

            // Kiểm tra cột BaseAllowance (Cột 5)
            Cell baseAllowanceCell = dataRow.getCell(5);
            assertEquals(5000000.0, baseAllowanceCell.getNumericCellValue(), 0.001);
            assertEquals("#,##0", baseAllowanceCell.getCellStyle().getDataFormatString());

            // Kiểm tra cột Bonus (Cột 9)
            Cell bonusCell = dataRow.getCell(9);
            assertEquals(500000.0, bonusCell.getNumericCellValue(), 0.001);
            assertEquals("#,##0", bonusCell.getCellStyle().getDataFormatString());

            // Kiểm tra cột Deduction (Cột 10)
            Cell deductionCell = dataRow.getCell(10);
            assertEquals(200000.0, deductionCell.getNumericCellValue(), 0.001);
            assertEquals("#,##0", deductionCell.getCellStyle().getDataFormatString());

            // Kiểm tra cột ActualAmount (Cột 12)
            Cell actualAmountCell = dataRow.getCell(12);
            assertEquals(5300000.0, actualAmountCell.getNumericCellValue(), 0.001);
            assertEquals("#,##0", actualAmountCell.getCellStyle().getDataFormatString());
        }
    }
}

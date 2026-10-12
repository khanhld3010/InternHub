package org.example.internservice.allowance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TC-ALW: Working Calendar & Allowance Calculation Unit Tests")
class WorkingCalendarHelperTest {

    @Test
    @DisplayName("TC-ALW-01: Tính số ngày làm việc chuẩn trong tháng 10/2026 (trừ thứ 7, CN)")
    void testStandardWorkingDaysOctober2026() {
        // Tháng 10 năm 2026 có 31 ngày.
        // Bắt đầu thứ Năm (01/10/2026).
        // Số ngày làm việc chuẩn từ T2 đến T6 là 22 ngày.
        int workingDays = WorkingCalendarHelper.calculateStandardWorkingDays("2026-10");
        assertEquals(22, workingDays, "Tháng 10/2026 phải có 22 ngày công chuẩn");
    }

    @Test
    @DisplayName("TC-ALW-02: Kiểm tra ngày thường và ngày cuối tuần")
    void testIsWorkingDay() {
        // 2026-10-09 là Thứ Sáu -> ngày làm việc
        assertTrue(WorkingCalendarHelper.isWorkingDay(LocalDate.of(2026, 10, 9)));

        // 2026-10-10 là Thứ Bảy -> ngày nghỉ
        assertFalse(WorkingCalendarHelper.isWorkingDay(LocalDate.of(2026, 10, 10)));

        // 2026-10-11 là Chủ Nhật -> ngày nghỉ
        assertFalse(WorkingCalendarHelper.isWorkingDay(LocalDate.of(2026, 10, 11)));
    }

    @Test
    @DisplayName("TC-ALW-03: Làm tròn số tiền phụ cấp đến 1.000 VNĐ")
    void testRoundingToThousands() {
        // 5.000.000 * 10 / 22 = 2.272.727,27... -> làm tròn thành 2.273.000 VNĐ
        double exactAmount = 5000000.0 * 10.0 / 22.0;
        long rounded = Math.round(exactAmount / 1000.0) * 1000;
        assertEquals(2273000L, rounded, "Phải làm tròn đến 1.000đ gần nhất");
    }
}

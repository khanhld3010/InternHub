package org.example.internservice.leave.util;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkdayCalculatorTest {

    private WorkdayCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WorkdayCalculator();
    }

    @Test
    @DisplayName("Tính ngày làm việc trong tuần (Thứ Tư đến Thứ Sáu = 3 ngày)")
    void calculateWorkdays_WithinSameWeek_ReturnCorrectDays() {
        // 2026-10-14 (Thứ Tư) đến 2026-10-16 (Thứ Sáu)
        LocalDate start = LocalDate.of(2026, 10, 14);
        LocalDate end = LocalDate.of(2026, 10, 16);

        double days = calculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY);

        assertEquals(3.0, days);
    }

    @Test
    @DisplayName("Tính ngày làm việc vắt qua cuối tuần (Thứ Sáu đến Thứ Ba tuần sau = 3 ngày)")
    void calculateWorkdays_SpanningWeekend_ExcludeSaturdayAndSunday() {
        // 2026-10-16 (Thứ Sáu) đến 2026-10-20 (Thứ Ba tuần sau)
        LocalDate start = LocalDate.of(2026, 10, 16);
        LocalDate end = LocalDate.of(2026, 10, 20);

        double days = calculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY);

        // Thứ Sáu (1) + Thứ Hai (1) + Thứ Ba (1) = 3.0
        assertEquals(3.0, days);
    }

    @Test
    @DisplayName("Đăng ký nghỉ chỉ rơi vào Thứ Bảy và Chủ Nhật ném BadRequestException")
    void calculateWorkdays_OnlyWeekend_ThrowBadRequestException() {
        // 2026-10-17 (Thứ Bảy) đến 2026-10-18 (Chủ Nhật)
        LocalDate start = LocalDate.of(2026, 10, 17);
        LocalDate end = LocalDate.of(2026, 10, 18);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                calculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY)
        );

        assertTrue(ex.getMessage().contains("chỉ bao gồm ngày nghỉ cuối tuần"));
    }

    @Test
    @DisplayName("Nghỉ nửa ngày vào ngày trong tuần trả về 0.5 ngày")
    void calculateWorkdays_HalfDay_Weekday_ReturnHalfDay() {
        // 2026-10-15 (Thứ Năm)
        LocalDate date = LocalDate.of(2026, 10, 15);

        double morning = calculator.calculateWorkdays(date, date, LeaveDurationType.MORNING);
        double afternoon = calculator.calculateWorkdays(date, date, LeaveDurationType.AFTERNOON);

        assertEquals(0.5, morning);
        assertEquals(0.5, afternoon);
    }

    @Test
    @DisplayName("Nghỉ nửa ngày nhưng startDate != endDate ném BadRequestException")
    void calculateWorkdays_HalfDay_MultipleDates_ThrowBadRequestException() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 10, 16);

        assertThrows(BadRequestException.class, () ->
                calculator.calculateWorkdays(start, end, LeaveDurationType.MORNING)
        );
    }

    @Test
    @DisplayName("Nghỉ nửa ngày vào cuối tuần ném BadRequestException")
    void calculateWorkdays_HalfDay_Weekend_ThrowBadRequestException() {
        LocalDate saturday = LocalDate.of(2026, 10, 17);

        assertThrows(BadRequestException.class, () ->
                calculator.calculateWorkdays(saturday, saturday, LeaveDurationType.MORNING)
        );
    }

    @Test
    @DisplayName("startDate sau endDate ném BadRequestException")
    void calculateWorkdays_StartDateAfterEndDate_ThrowBadRequestException() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 15);

        assertThrows(BadRequestException.class, () ->
                calculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY)
        );
    }

    @Test
    @DisplayName("Kiểm tra ngày cuối tuần bằng isWeekend")
    void isWeekend_IdentifyCorrectly() {
        assertTrue(calculator.isWeekend(LocalDate.of(2026, 10, 17))); // Thứ 7
        assertTrue(calculator.isWeekend(LocalDate.of(2026, 10, 18))); // Chủ Nhật
        assertFalse(calculator.isWeekend(LocalDate.of(2026, 10, 19))); // Thứ 2
    }
}

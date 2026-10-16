package org.example.internservice.attendance.report.util;

import org.example.internservice.intern.entity.InternProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Unit Tests - WorkDayCalculator")
class WorkDayCalculatorTest {

    private WorkDayCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WorkDayCalculator();
    }

    @Test
    @DisplayName("Đếm đúng số ngày làm việc trong tuần (Thứ 2 đến Thứ 6 = 5 ngày)")
    void countWorkingDaysBetween_normalWeek_returnsFiveDays() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate sunday = LocalDate.of(2026, 10, 11);

        int workingDays = calculator.countWorkingDaysBetween(monday, sunday);

        assertEquals(5, workingDays);
    }

    @Test
    @DisplayName("Đếm đúng số ngày làm việc cả tháng 10/2026 (31 ngày, trừ 9 ngày T7, CN = 22 ngày)")
    void countWorkingDaysBetween_fullMonthOctober2026_returnsTwentyTwoDays() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        int workingDays = calculator.countWorkingDaysBetween(start, end);

        assertEquals(22, workingDays);
    }

    @Test
    @DisplayName("Ngày bắt đầu sau ngày kết thúc thì trả về 0")
    void countWorkingDaysBetween_invalidRange_returnsZero() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 10, 10);

        int workingDays = calculator.countWorkingDaysBetween(start, end);

        assertEquals(0, workingDays);
    }

    @Test
    @DisplayName("TTS onboard giữa tháng (15/10/2026) thì chỉ tính ngày công từ 15/10 đến 31/10")
    void calculateExpectedWorkDays_internOnboardMidMonth_calculatesFromStartDateOnly() {
        InternProfile intern = InternProfile.builder()
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        LocalDate reportStart = LocalDate.of(2026, 10, 1);
        LocalDate reportEnd = LocalDate.of(2026, 10, 31);
        LocalDate referenceDate = LocalDate.of(2026, 10, 31);

        int expected = calculator.calculateExpectedWorkDays(intern, reportStart, reportEnd, referenceDate);

        // Từ 15/10/2026 (Thứ Năm) đến 31/10/2026 (Thứ Bảy): có 12 ngày làm việc (15, 16, 19-23, 26-30)
        assertEquals(12, expected);
    }

    @Test
    @DisplayName("Chuyển đổi đúng tên Thứ trong tuần sang tiếng Việt")
    void getDayOfWeekVietnamese_validDays_returnsCorrectName() {
        assertEquals("Thứ Hai", calculator.getDayOfWeekVietnamese(DayOfWeek.MONDAY));
        assertEquals("Thứ Bảy", calculator.getDayOfWeekVietnamese(DayOfWeek.SATURDAY));
        assertEquals("Chủ Nhật", calculator.getDayOfWeekVietnamese(DayOfWeek.SUNDAY));
    }
}

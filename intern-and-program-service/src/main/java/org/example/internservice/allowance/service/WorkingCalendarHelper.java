package org.example.internservice.allowance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class WorkingCalendarHelper {

    /**
     * Danh sách các ngày lễ cố định trong năm của Việt Nam (MM-DD)
     * Có thể mở rộng thành bảng DB cấu hình nếu cần.
     */
    private static final Set<String> FIXED_HOLIDAYS = Set.of(
            "01-01", // Tết Dương Lịch
            "04-30", // Giải phóng Miền Nam
            "05-01", // Quốc tế Lao động
            "09-02"  // Quốc khánh
    );

    /**
     * Tính toán số ngày công chuẩn theo chuỗi YYYY-MM
     */
    public static int calculateStandardWorkingDays(String periodMonth) {
        String[] parts = periodMonth.split("-");
        return calculateStandardWorkingDays(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    /**
     * Tính toán số ngày công chuẩn trong tháng (loại trừ Thứ 7, Chủ Nhật và ngày lễ).
     */
    public static int calculateStandardWorkingDays(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isWorkingDay(current)) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    /**
     * Kiểm tra một ngày cụ thể có phải là ngày làm việc chuẩn không.
     */
    public static boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        String mmDd = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return !FIXED_HOLIDAYS.contains(mmDd);
    }
}

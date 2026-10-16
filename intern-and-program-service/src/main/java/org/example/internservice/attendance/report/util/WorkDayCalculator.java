package org.example.internservice.attendance.report.util;

import org.example.internservice.intern.entity.InternProfile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Tiện ích tính toán số ngày làm việc tiêu chuẩn và loại trừ ngày nghỉ cuối tuần (TM-26).
 */
@Component
public class WorkDayCalculator {

    /**
     * Đếm số ngày làm việc (Thứ 2 đến Thứ 6) giữa 2 mốc thời gian (bao gồm cả start và end).
     */
    public int countWorkingDaysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return 0;
        }

        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    /**
     * Tính toán số ngày làm việc tiêu chuẩn dự kiến của 1 Intern trong kỳ báo cáo (BR-1).
     * Mặc định sử dụng ngày hiện tại làm mốc chặn ngày tương lai.
     */
    public int calculateExpectedWorkDays(InternProfile intern, LocalDate reportStart, LocalDate reportEnd) {
        return calculateExpectedWorkDays(intern, reportStart, reportEnd, LocalDate.now());
    }

    /**
     * Tính toán số ngày làm việc tiêu chuẩn dự kiến với ngày tham chiếu tuỳ chỉnh (dùng cho kiểm thử hoặc mô phỏng).
     */
    public int calculateExpectedWorkDays(InternProfile intern, LocalDate reportStart, LocalDate reportEnd, LocalDate referenceDate) {
        LocalDate effectiveStart = reportStart;
        if (intern.getStartDate() != null && intern.getStartDate().isAfter(effectiveStart)) {
            effectiveStart = intern.getStartDate();
        }

        LocalDate effectiveEnd = reportEnd;
        if (intern.getEndDate() != null && intern.getEndDate().isBefore(effectiveEnd)) {
            effectiveEnd = intern.getEndDate();
        }

        LocalDate today = referenceDate != null ? referenceDate : LocalDate.now();
        if (effectiveEnd.isAfter(today)) {
            effectiveEnd = today;
        }

        if (effectiveStart.isAfter(effectiveEnd)) {
            return 0;
        }

        return countWorkingDaysBetween(effectiveStart, effectiveEnd);
    }

    /**
     * Chuyển đổi DayOfWeek sang tên tiếng Việt hiển thị trên giao diện.
     */
    public String getDayOfWeekVietnamese(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            return "";
        }
        return switch (dayOfWeek) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
    }
}

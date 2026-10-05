package org.example.internservice.leave.util;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Tiện ích tính toán số ngày làm việc thực tế (loại trừ Thứ Bảy & Chủ Nhật) (TM-28).
 */
@Component
public class WorkdayCalculator {

    /**
     * Tính toán tổng số ngày làm việc được nghỉ.
     *
     * @param startDate    Ngày bắt đầu nghỉ
     * @param endDate      Ngày kết thúc nghỉ
     * @param durationType Khung thời gian (Cả ngày hoặc nửa ngày)
     * @return Số ngày làm việc (ví dụ 0.5, 1.0, 2.0...)
     */
    public double calculateWorkdays(LocalDate startDate, LocalDate endDate, LeaveDurationType durationType) {
        if (startDate == null || endDate == null || durationType == null) {
            throw new BadRequestException("Ngày bắt đầu, ngày kết thúc và hình thức nghỉ không được để trống");
        }

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Ngày bắt đầu nghỉ không được sau ngày kết thúc nghỉ");
        }

        if (durationType == LeaveDurationType.MORNING || durationType == LeaveDurationType.AFTERNOON) {
            if (!startDate.isEqual(endDate)) {
                throw new BadRequestException("Hình thức nghỉ nửa ngày chỉ áp dụng cho một ngày duy nhất");
            }
            if (isWeekend(startDate)) {
                throw new BadRequestException("Không thể đăng ký nghỉ nửa ngày vào ngày cuối tuần (Thứ Bảy / Chủ Nhật)");
            }
            return 0.5;
        }

        // Với hình thức FULL_DAY
        double workdays = 0.0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (!isWeekend(current)) {
                workdays += 1.0;
            }
            current = current.plusDays(1);
        }

        if (workdays <= 0.0) {
            throw new BadRequestException("Khoảng thời gian nghỉ chỉ bao gồm ngày nghỉ cuối tuần, không cần tạo đơn xin nghỉ phép");
        }

        return workdays;
    }

    public boolean isWeekend(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}

package org.example.internservice.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduled job xử lý các nghiệp vụ cuối ngày (End-of-Day) cho module Chấm công.
 * Tự động đóng các ca chỉ check-in nhưng quên check-out thành FORGOTTEN_CHECKOUT vào lúc 23:59 hàng ngày.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceEodScheduler {

    private final AttendanceRepository attendanceRepository;

    /**
     * Chạy vào 23:59:00 mỗi ngày theo múi giờ hệ thống (Asia/Ho_Chi_Minh).
     */
    @Scheduled(cron = "0 59 23 * * ?", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void autoCloseForgottenCheckouts() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        log.info("[EOD Scheduler] Bắt đầu quét các ca chấm công chưa hoàn tất ngày {}", today);

        List<Attendance> unclosedRecords = attendanceRepository
                .findByWorkDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNull(today);

        if (unclosedRecords.isEmpty()) {
            log.info("[EOD Scheduler] Không có ca làm việc nào quên check-out trong ngày {}.", today);
            return;
        }

        int count = 0;
        for (Attendance attendance : unclosedRecords) {
            attendance.setStatus(AttendanceStatus.FORGOTTEN_CHECKOUT);
            count++;
        }

        attendanceRepository.saveAll(unclosedRecords);
        log.warn("[EOD Scheduler] Đã tự động đóng {} ca làm việc quên check-out ngày {} sang trạng thái FORGOTTEN_CHECKOUT.", count, today);
    }
}

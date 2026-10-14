package org.example.internservice.meeting.repository;

import org.example.internservice.meeting.entity.MeetingSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingScheduleRepository extends JpaRepository<MeetingSchedule, Long> {

    List<MeetingSchedule> findByInternUserIdOrderByStartTimeAsc(Long internUserId);

    List<MeetingSchedule> findByCreatorIdOrderByStartTimeAsc(Long creatorId);

    List<MeetingSchedule> findByCreatorIdAndStartTimeAfterOrderByStartTimeAsc(Long creatorId, LocalDateTime fromTime);

    /**
     * Tìm danh sách các cuộc họp sắp bắt đầu trong khoảng thời gian xác định và chưa gửi nhắc nhở
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM MeetingSchedule m WHERE m.status = 'SCHEDULED' " +
            "AND m.reminderSent = false AND m.startTime >= :fromTime AND m.startTime <= :toTime")
    List<MeetingSchedule> findUpcomingMeetingsForReminder(
            @org.springframework.data.repository.query.Param("fromTime") LocalDateTime fromTime,
            @org.springframework.data.repository.query.Param("toTime") LocalDateTime toTime
    );

    /**
     * Tự động hoàn thành các cuộc họp đã qua giờ kết thúc hoặc quá 2 tiếng kể từ giờ bắt đầu
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE MeetingSchedule m SET m.status = 'COMPLETED' " +
            "WHERE (m.status = 'SCHEDULED' OR m.status = 'UPDATED') " +
            "AND ((m.endTime IS NOT NULL AND m.endTime < :cutoffTime) OR (m.endTime IS NULL AND m.startTime < :fallbackCutoffTime))")
    int autoCompleteExpiredMeetings(
            @org.springframework.data.repository.query.Param("cutoffTime") LocalDateTime cutoffTime,
            @org.springframework.data.repository.query.Param("fallbackCutoffTime") LocalDateTime fallbackCutoffTime
    );

    /**
     * Cập nhật nguyên tử cờ reminder_sent để tránh việc nhiều instance chạy trùng cronjob
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE MeetingSchedule m SET m.reminderSent = true " +
            "WHERE m.id = :meetingId AND m.reminderSent = false")
    int markReminderSentAtomically(@org.springframework.data.repository.query.Param("meetingId") Long meetingId);
}


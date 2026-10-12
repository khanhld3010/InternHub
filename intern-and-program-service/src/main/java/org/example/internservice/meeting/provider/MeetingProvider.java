package org.example.internservice.meeting.provider;

import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;

/**
 * Interface trừu tượng hóa nhà cung cấp phòng họp trực tuyến theo Strategy Pattern (ADR 0002)
 */
public interface MeetingProvider {

    /**
     * Tạo hoặc xác thực phòng họp
     */
    MeetingRoomInfo createMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl);

    /**
     * Cập nhật thông tin phòng họp
     */
    MeetingRoomInfo updateMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl);

    /**
     * Hủy phòng họp phía bên thứ ba nếu có
     */
    void cancelMeetingRoom(MeetingSchedule meeting);

    /**
     * Xác định nhà cung cấp hỗ trợ loại nào
     */
    boolean supports(MeetingProviderType type);
}

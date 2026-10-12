package org.example.internservice.attendance.service;

import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.request.QuickCheckOutRequest;
import org.example.internservice.attendance.dto.request.ReviewAttendanceRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;

public interface AttendanceService {

    TodayAttendanceResponse getTodayAttendance(Long userId);

    CheckInQrResponse initiateCheckIn(Long userId, CheckInInitiateRequest request);

    AttendanceResponse confirmCheckIn(Long userId, CheckInConfirmRequest request, String clientIp);

    AttendanceResponse quickCheckOut(Long userId, QuickCheckOutRequest request, String clientIp);

    AttendanceResponse checkOut(Long userId, CheckOutRequest request);

    AttendanceResponse reviewAttendance(Long attendanceId, ReviewAttendanceRequest request, String reviewerUsername);

    MonthlyAttendanceSummaryResponse getMyHistory(Long userId, Integer month, Integer year);
}

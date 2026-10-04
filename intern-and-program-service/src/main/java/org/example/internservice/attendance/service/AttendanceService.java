package org.example.internservice.attendance.service;

import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckInRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;

public interface AttendanceService {

    TodayAttendanceResponse getTodayAttendance(Long userId);

    CheckInQrResponse initiateCheckIn(Long userId, CheckInInitiateRequest request);

    AttendanceResponse confirmCheckIn(Long userId, CheckInConfirmRequest request);

    AttendanceResponse checkIn(Long userId, CheckInRequest request);

    AttendanceResponse checkOut(Long userId, CheckOutRequest request);

    MonthlyAttendanceSummaryResponse getMyHistory(Long userId, Integer month, Integer year);
}

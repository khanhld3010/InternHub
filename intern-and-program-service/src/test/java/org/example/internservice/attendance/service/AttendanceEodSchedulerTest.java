package org.example.internservice.attendance.service;

import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceEodSchedulerTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendanceEodScheduler scheduler;

    @Test
    @DisplayName("autoCloseForgottenCheckouts không làm gì khi không có ca nào quên check-out")
    void shouldDoNothingWhenNoUnclosedRecords() {
        when(attendanceRepository.findByWorkDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNull(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        scheduler.autoCloseForgottenCheckouts();

        verify(attendanceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("autoCloseForgottenCheckouts cập nhật các ca quên check-out sang FORGOTTEN_CHECKOUT")
    void shouldUpdateForgottenCheckouts() {
        Attendance att1 = Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now().minusHours(8))
                .status(AttendanceStatus.ON_TIME)
                .build();

        Attendance att2 = Attendance.builder()
                .internId(101L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now().minusHours(7))
                .status(AttendanceStatus.LATE)
                .build();

        when(attendanceRepository.findByWorkDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNull(any(LocalDate.class)))
                .thenReturn(List.of(att1, att2));

        scheduler.autoCloseForgottenCheckouts();

        ArgumentCaptor<List<Attendance>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRepository).saveAll(captor.capture());

        List<Attendance> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getStatus()).isEqualTo(AttendanceStatus.FORGOTTEN_CHECKOUT);
        assertThat(saved.get(1).getStatus()).isEqualTo(AttendanceStatus.FORGOTTEN_CHECKOUT);
    }
}

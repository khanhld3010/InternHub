package org.example.internservice.attendance.service;

import org.example.internservice.attendance.entity.InternDevice;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.entity.enums.AttendanceReviewStatus;
import org.example.internservice.attendance.entity.enums.DeviceTrustStatus;
import org.example.internservice.attendance.entity.enums.TrustLevel;
import org.example.internservice.attendance.repository.InternDeviceRepository;
import org.example.internservice.attendance.util.HaversineDistanceCalculator;
import org.example.internservice.attendance.util.IpSubnetMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustLevelEvaluatorTest {

    @Mock
    private IpSubnetMatcher ipSubnetMatcher;

    @Mock
    private HaversineDistanceCalculator distanceCalculator;

    @Mock
    private InternDeviceRepository internDeviceRepository;

    @InjectMocks
    private TrustLevelEvaluator trustLevelEvaluator;

    private OfficeLocation office;
    private InternDevice trustedDevice;

    @BeforeEach
    void setUp() {
        office = OfficeLocation.builder()
                .name("Trụ sở chính")
                .latitude(21.035665)
                .longitude(105.768296)
                .allowedRadiusMeters(50.0)
                .allowedIpSubnets("192.168.1.0/24, 127.0.0.1")
                .isActive(true)
                .build();

        trustedDevice = InternDevice.builder()
                .internId(10L)
                .deviceFingerprint("device-abc-123")
                .trustStatus(DeviceTrustStatus.PRIMARY_TRUSTED)
                .validCheckinCount(5)
                .lastUsedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Case 1: Đúng máy + IP công ty + GPS <= 50m -> HIGH TRUST, AUTO_APPROVED")
    void testCase1_HighTrust() {
        when(internDeviceRepository.findByInternIdAndDeviceFingerprint(10L, "device-abc-123"))
                .thenReturn(Optional.of(trustedDevice));
        when(ipSubnetMatcher.matches(eq("192.168.1.50"), anyString())).thenReturn(true);
        when(distanceCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(15.0);

        var result = trustLevelEvaluator.evaluate(
                10L, "device-abc-123", "192.168.1.50", 21.035665, 105.768296, 5.0, office
        );

        assertEquals(TrustLevel.HIGH, result.getTrustLevel());
        assertEquals(AttendanceReviewStatus.AUTO_APPROVED, result.getReviewStatus());
        assertTrue(result.getAuditFlags().contains("LOCATION_VERIFIED"));
    }

    @Test
    @DisplayName("Case 2: Đúng máy + IP công ty nhưng mất sóng GPS -> MEDIUM TRUST, AUTO_APPROVED")
    void testCase2_MediumTrust_NoGps() {
        when(internDeviceRepository.findByInternIdAndDeviceFingerprint(10L, "device-abc-123"))
                .thenReturn(Optional.of(trustedDevice));
        when(ipSubnetMatcher.matches(eq("192.168.1.50"), anyString())).thenReturn(true);

        var result = trustLevelEvaluator.evaluate(
                10L, "device-abc-123", "192.168.1.50", null, null, null, office
        );

        assertEquals(TrustLevel.MEDIUM, result.getTrustLevel());
        assertEquals(AttendanceReviewStatus.AUTO_APPROVED, result.getReviewStatus());
        assertTrue(result.getAuditFlags().contains("NO_GPS_SUPPLIED"));
    }

    @Test
    @DisplayName("Case 3: Đúng máy + GPS <= 50m nhưng dùng 4G ngoài IP công ty -> MEDIUM TRUST, AUTO_APPROVED")
    void testCase3_MediumTrust_Cellular() {
        when(internDeviceRepository.findByInternIdAndDeviceFingerprint(10L, "device-abc-123"))
                .thenReturn(Optional.of(trustedDevice));
        when(ipSubnetMatcher.matches(eq("14.162.20.10"), anyString())).thenReturn(false);
        when(distanceCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(20.0);

        var result = trustLevelEvaluator.evaluate(
                10L, "device-abc-123", "14.162.20.10", 21.035665, 105.768296, 5.0, office
        );

        assertEquals(TrustLevel.MEDIUM, result.getTrustLevel());
        assertEquals(AttendanceReviewStatus.AUTO_APPROVED, result.getReviewStatus());
        assertTrue(result.getAuditFlags().contains("CELLULAR_DATA_OK"));
    }

    @Test
    @DisplayName("Case 4: Thiết bị mới/lạ nhưng ngồi tại văn phòng (khớp IP) -> MEDIUM TRUST, AUTO_APPROVED, cờ NEW_DEVICE")
    void testCase4_NewDevice_OnSite() {
        when(internDeviceRepository.findByInternIdAndDeviceFingerprint(10L, "new-device-999"))
                .thenReturn(Optional.empty());
        when(ipSubnetMatcher.matches(eq("192.168.1.50"), anyString())).thenReturn(true);

        var result = trustLevelEvaluator.evaluate(
                10L, "new-device-999", "192.168.1.50", null, null, null, office
        );

        assertEquals(TrustLevel.MEDIUM, result.getTrustLevel());
        assertEquals(AttendanceReviewStatus.AUTO_APPROVED, result.getReviewStatus());
        assertTrue(result.getAuditFlags().contains("NEW_DEVICE"));
    }

    @Test
    @DisplayName("Case 5: Thiết bị lạ VÀ Cả IP lẫn GPS đều ngoài công ty -> LOW TRUST, PENDING_REVIEW")
    void testCase5_LowTrust_PendingReview() {
        when(internDeviceRepository.findByInternIdAndDeviceFingerprint(10L, "new-device-999"))
                .thenReturn(Optional.empty());
        when(ipSubnetMatcher.matches(eq("14.162.20.10"), anyString())).thenReturn(false);
        when(distanceCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(850.0); // Ngoài xa 850m

        var result = trustLevelEvaluator.evaluate(
                10L, "new-device-999", "14.162.20.10", 21.050000, 105.800000, 10.0, office
        );

        assertEquals(TrustLevel.LOW, result.getTrustLevel());
        assertEquals(AttendanceReviewStatus.PENDING_REVIEW, result.getReviewStatus());
        assertTrue(result.getAuditFlags().contains("SUSPICIOUS_FRAUD_RISK"));
    }
}

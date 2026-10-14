package org.example.internservice.attendance.service;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.entity.enums.AttendanceReviewStatus;
import org.example.internservice.attendance.entity.enums.DeviceTrustStatus;
import org.example.internservice.attendance.entity.enums.TrustLevel;
import org.example.internservice.attendance.repository.InternDeviceRepository;
import org.example.internservice.attendance.util.HaversineDistanceCalculator;
import org.example.internservice.attendance.util.IpSubnetMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Đánh giá độ tin cậy đa tầng (Trust Level Engine) theo chuẩn ADR 0003 & TM-25 v2.0.
 * Triển khai ma trận AND logic chặt chẽ, bù trừ sai số GPS tòa nhà và phát hiện thiết bị mới.
 */
@Component
@Slf4j
public class TrustLevelEvaluator {

    @Getter
    @Builder
    public static class EvaluationResult {
        private TrustLevel trustLevel;
        private AttendanceReviewStatus reviewStatus;
        private List<String> auditFlags;
        private Double calculatedDistance;
        private boolean isCompanyIp;
        private boolean isKnownDevice;
    }

    private final IpSubnetMatcher ipSubnetMatcher;
    private final HaversineDistanceCalculator distanceCalculator;
    private final InternDeviceRepository internDeviceRepository;

    public TrustLevelEvaluator(IpSubnetMatcher ipSubnetMatcher,
                               HaversineDistanceCalculator distanceCalculator,
                               InternDeviceRepository internDeviceRepository) {
        this.ipSubnetMatcher = ipSubnetMatcher;
        this.distanceCalculator = distanceCalculator;
        this.internDeviceRepository = internDeviceRepository;
    }

    public EvaluationResult evaluate(Long internId,
                                     String deviceFingerprint,
                                     String clientIp,
                                     Double latitude,
                                     Double longitude,
                                     Double accuracyMeters,
                                     OfficeLocation office) {
        List<String> flags = new ArrayList<>();

        // 1. Kiểm tra dải IP mạng văn phòng (Dynamic per Office/Company)
        String allowedSubnets = (office != null) ? office.getAllowedIpSubnets() : null;
        boolean isCompanyIp = ipSubnetMatcher.matches(clientIp, allowedSubnets);
        if (isCompanyIp) {
            flags.add("COMPANY_NETWORK_MATCH");
        } else {
            flags.add("IP_OUT_OF_OFFICE");
        }

        // 2. Kiểm tra độ tin cậy của thiết bị di động
        boolean isKnownDevice = false;
        if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
            var existingDevice = internDeviceRepository.findByInternIdAndDeviceFingerprint(internId, deviceFingerprint);
            if (existingDevice.isPresent()) {
                isKnownDevice = existingDevice.get().getTrustStatus() != DeviceTrustStatus.BLOCKED;
            }
        }

        if (!isKnownDevice) {
            flags.add("NEW_DEVICE");
        }

        // 3. Tính toán khoảng cách GPS và bù sai số phần cứng (Indoor Signal Fallback)
        Double distance = null;
        boolean isLocationVerified = false;
        if (latitude != null && longitude != null && office != null) {
            distance = distanceCalculator.calculateDistance(
                    latitude, longitude, office.getLatitude(), office.getLongitude()
            );

            double allowedRadius = (office.getAllowedRadiusMeters() != null) ? office.getAllowedRadiusMeters() : 50.0;
            double effectiveDistance = distance;
            if (accuracyMeters != null && accuracyMeters > 0) {
                // Điểm 12 TM-25: Bù trừ bán kính sai số thiết bị trong nhà cao tầng
                effectiveDistance = Math.max(0.0, distance - accuracyMeters);
            }

            if (effectiveDistance <= allowedRadius) {
                isLocationVerified = true;
                flags.add("LOCATION_VERIFIED");
            } else {
                flags.add("GEO_OUT_OF_BOUNDS");
            }
        } else {
            flags.add("NO_GPS_SUPPLIED");
        }

        // 4. Áp dụng Ma Trận Đánh Giá (ADR 0003 & TM-25 v2.0):
        TrustLevel trustLevel;
        AttendanceReviewStatus reviewStatus;

        if (isKnownDevice && isCompanyIp && isLocationVerified) {
            // Case 1: Đúng máy + IP công ty + GPS khớp
            trustLevel = TrustLevel.HIGH;
            reviewStatus = AttendanceReviewStatus.AUTO_APPROVED;
        } else if (isKnownDevice && isCompanyIp && !isLocationVerified) {
            // Case 2: Đúng máy + IP công ty (mất GPS hoặc trong phòng kín)
            trustLevel = TrustLevel.MEDIUM;
            reviewStatus = AttendanceReviewStatus.AUTO_APPROVED;
        } else if (isKnownDevice && !isCompanyIp && isLocationVerified) {
            // Case 3: Đúng máy + GPS khớp <= 50m (dùng 4G/5G tại văn phòng)
            trustLevel = TrustLevel.MEDIUM;
            reviewStatus = AttendanceReviewStatus.AUTO_APPROVED;
            flags.add("CELLULAR_DATA_OK");
        } else if (!isKnownDevice && (isCompanyIp || isLocationVerified)) {
            // Case 4: Máy lạ nhưng có mặt on-site tại công ty (khớp IP hoặc GPS <= 50m)
            trustLevel = TrustLevel.MEDIUM;
            reviewStatus = AttendanceReviewStatus.AUTO_APPROVED;
        } else {
            // Case 5: (MÁY LẠ) AND (CẢ IP VÀ GPS ĐỀU NGOÀI CÔNG TY) -> LOW TRUST
            trustLevel = TrustLevel.LOW;
            reviewStatus = AttendanceReviewStatus.PENDING_REVIEW;
            flags.add("SUSPICIOUS_FRAUD_RISK");
        }

        log.info("Kết quả đánh giá Trust Level cho Intern ID {}: Level={}, Status={}, Flags={}",
                internId, trustLevel, reviewStatus, flags);

        return EvaluationResult.builder()
                .trustLevel(trustLevel)
                .reviewStatus(reviewStatus)
                .auditFlags(flags)
                .calculatedDistance(distance)
                .isCompanyIp(isCompanyIp)
                .isKnownDevice(isKnownDevice)
                .build();
    }
}

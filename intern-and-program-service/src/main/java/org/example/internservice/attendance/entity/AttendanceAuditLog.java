package org.example.internservice.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.entity.enums.TrustLevel;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * Bản ghi kiểm toán bất biến (Append-Only) cho mọi lượt chấm công (ADR 0003).
 */
@Entity
@Table(
        name = "attendance_audit_logs",
        indexes = {
                @Index(name = "idx_audit_intern_id", columnList = "intern_id"),
                @Index(name = "idx_audit_action_time", columnList = "action_time"),
                @Index(name = "idx_audit_device_fingerprint", columnList = "device_fingerprint"),
                @Index(name = "idx_audit_trust_level", columnList = "trust_level")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAuditLog extends BaseEntity {

    @Column(name = "attendance_id")
    private Long attendanceId;

    @Column(name = "intern_id", nullable = false)
    private Long internId;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType; // CHECK_IN, CHECK_OUT, QUICK_CHECK_OUT

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    @Column(name = "device_fingerprint", nullable = false, length = 128)
    private String deviceFingerprint;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "is_company_ip", nullable = false)
    @Builder.Default
    private Boolean isCompanyIp = false;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false, length = 20)
    private TrustLevel trustLevel;

    @Column(name = "audit_flags", length = 255)
    private String auditFlags; // NEW_DEVICE, CELLULAR_DATA_OK, NO_GPS_SUPPLIED, SUSPICIOUS_FRAUD_RISK
}

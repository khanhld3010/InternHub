package org.example.internservice.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.entity.enums.DeviceTrustStatus;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * Quản lý vòng đời thiết bị di động của thực tập sinh (ADR 0003 & TM-25 v2.0).
 */
@Entity
@Table(
        name = "intern_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_intern_device", columnNames = {"intern_id", "device_fingerprint"})
        },
        indexes = {
                @Index(name = "idx_device_intern_id", columnList = "intern_id"),
                @Index(name = "idx_device_fingerprint", columnList = "device_fingerprint")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternDevice extends BaseEntity {

    @Column(name = "intern_id", nullable = false)
    private Long internId;

    @Column(name = "device_fingerprint", nullable = false, length = 128)
    private String deviceFingerprint;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_status", nullable = false, length = 30)
    @Builder.Default
    private DeviceTrustStatus trustStatus = DeviceTrustStatus.PENDING_PAIRING;

    @Column(name = "valid_checkin_count", nullable = false)
    @Builder.Default
    private Integer validCheckinCount = 0;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;
}

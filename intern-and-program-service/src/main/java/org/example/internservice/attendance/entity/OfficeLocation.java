package org.example.internservice.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;

@Entity
@Table(name = "office_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeLocation extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Builder.Default
    @Column(name = "allowed_radius_meters", nullable = false)
    private Double allowedRadiusMeters = 25.0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

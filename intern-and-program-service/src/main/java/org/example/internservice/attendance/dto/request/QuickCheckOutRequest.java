package org.example.internservice.attendance.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tan ca nhanh 1-chạm trên Web Mobile (ADR 0003 & TM-25 v2.0).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickCheckOutRequest {

    private String deviceFingerprint;

    private String deviceModel;

    private Double latitude;

    private Double longitude;

    private Double accuracyMeters;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String notes;
}

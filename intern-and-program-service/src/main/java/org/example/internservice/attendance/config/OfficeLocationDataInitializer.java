package org.example.internservice.attendance.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.repository.OfficeLocationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OfficeLocationDataInitializer implements ApplicationRunner {

    private final OfficeLocationRepository officeLocationRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (officeLocationRepository.count() == 0) {
            log.info("Chưa có cấu hình văn phòng trong database. Đang khởi tạo vị trí văn phòng mặc định...");
            OfficeLocation defaultOffice = OfficeLocation.builder()
                    .name("Trụ sở chính InternHub")
                    .latitude(21.035665)
                    .longitude(105.768296)
                    .allowedRadiusMeters(25.0)
                    .isActive(true)
                    .build();
            officeLocationRepository.save(defaultOffice);
            log.info("Khởi tạo vị trí văn phòng mặc định thành công: {} (Bán kính: {}m)",
                    defaultOffice.getName(), defaultOffice.getAllowedRadiusMeters());
        } else {
            officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc().ifPresent(office -> {
                if ("Trụ sở chính InternHub".equals(office.getName())) {
                    office.setLatitude(21.035665);
                    office.setLongitude(105.768296);
                    office.setAllowedRadiusMeters(25.0);
                    officeLocationRepository.save(office);
                    log.info("Đã đồng bộ toạ độ văn phòng mặc định: {} ({}, {})",
                            office.getName(), office.getLatitude(), office.getLongitude());
                }
            });
        }
    }
}

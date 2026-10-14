package org.example.internservice.attendance.repository;

import org.example.internservice.attendance.entity.InternDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InternDeviceRepository extends JpaRepository<InternDevice, Long> {

    Optional<InternDevice> findByInternIdAndDeviceFingerprint(Long internId, String deviceFingerprint);

    boolean existsByInternIdAndDeviceFingerprint(Long internId, String deviceFingerprint);
}

package org.example.internservice.attendance.repository;

import org.example.internservice.attendance.entity.OfficeLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {

    Optional<OfficeLocation> findFirstByIsActiveTrueOrderByIdAsc();
}

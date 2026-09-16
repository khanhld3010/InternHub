package org.example.employeeservice.intern.repository;

import org.example.employeeservice.intern.entity.InternProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InternProfileRepository extends JpaRepository<InternProfile, Long>, JpaSpecificationExecutor<InternProfile> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<InternProfile> findByInternCode(String internCode);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}

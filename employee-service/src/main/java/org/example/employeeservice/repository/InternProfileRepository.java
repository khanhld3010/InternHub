package org.example.employeeservice.repository;

import org.example.employeeservice.entity.InternProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InternProfileRepository extends JpaRepository<InternProfile, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByInternCode(String internCode);

    Optional<InternProfile> findByInternCode(String internCode);

    long countByInternCodeStartingWith(String prefix);
}

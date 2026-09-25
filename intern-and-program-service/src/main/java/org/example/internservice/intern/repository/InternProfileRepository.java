package org.example.internservice.intern.repository;

import org.example.internservice.intern.entity.InternProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InternProfileRepository extends JpaRepository<InternProfile, Long>, JpaSpecificationExecutor<InternProfile> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    Optional<InternProfile> findByInternCode(String internCode);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    java.util.List<InternProfile> findByEmailStatusAndLastEmailSentAtBefore(String emailStatus, LocalDateTime threshold);

    long countByProgramId(Long programId);

    long countByProgramIdAndStatusIn(Long programId, java.util.Collection<org.example.internservice.intern.entity.enums.InternStatus> statuses);

    boolean existsByProgramIdAndStatusIn(Long programId, java.util.Collection<org.example.internservice.intern.entity.enums.InternStatus> statuses);

    java.util.List<InternProfile> findByProgramId(Long programId);
}

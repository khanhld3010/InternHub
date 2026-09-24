package org.example.reportingservice.email.repository;

import org.example.reportingservice.email.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Optional<EmailLog> findByIdempotencyKey(String idempotencyKey);

    List<EmailLog> findByReferenceIdOrderByCreatedAtDesc(Long referenceId);

    Optional<EmailLog> findFirstByReferenceIdOrderByCreatedAtDesc(Long referenceId);
}

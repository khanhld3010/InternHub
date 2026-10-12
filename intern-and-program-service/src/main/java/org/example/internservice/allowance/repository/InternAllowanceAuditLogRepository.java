package org.example.internservice.allowance.repository;

import org.example.internservice.allowance.entity.InternAllowanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InternAllowanceAuditLogRepository extends JpaRepository<InternAllowanceAuditLog, Long> {

    List<InternAllowanceAuditLog> findByAllowanceRecordIdOrderByAdjustedAtDesc(Long allowanceRecordId);
}

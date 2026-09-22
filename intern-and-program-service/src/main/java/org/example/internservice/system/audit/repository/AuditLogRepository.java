package org.example.internservice.system.audit.repository;

import org.example.internservice.system.audit.entity.AuditLog;
import org.example.internservice.system.audit.entity.AuditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatusAndCreatedAtBetween(AuditStatus status, LocalDateTime start, LocalDateTime end);

    @Query("SELECT a.module, COUNT(a) FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end GROUP BY a.module")
    List<Object[]> countByModuleBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

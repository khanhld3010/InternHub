package org.example.aiservice.repository;

import org.example.aiservice.entity.AiConfigAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiConfigAuditRepository extends JpaRepository<AiConfigAudit, Long> {
}

package org.example.aiservice.modules.interview.repository;

import org.example.aiservice.modules.interview.entity.InterviewAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewAuditLogRepository extends JpaRepository<InterviewAuditLog, Long> {

    List<InterviewAuditLog> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}

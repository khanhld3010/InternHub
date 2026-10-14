package org.example.internservice.support.repository;

import org.example.internservice.support.entity.SupportRequestStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRequestStatusLogRepository extends JpaRepository<SupportRequestStatusLog, Long> {

    List<SupportRequestStatusLog> findBySupportRequestIdOrderByChangedAtAsc(Long supportRequestId);
}

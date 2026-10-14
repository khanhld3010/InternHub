package org.example.internservice.support.repository;

import org.example.internservice.support.entity.DocumentAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, Long> {

    List<DocumentAccessLog> findByTargetIdAndDocumentTypeOrderByAccessedAtDesc(Long targetId, String documentType);
}

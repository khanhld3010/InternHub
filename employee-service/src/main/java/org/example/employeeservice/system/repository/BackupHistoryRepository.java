package org.example.employeeservice.system.repository;

import org.example.employeeservice.system.entity.BackupHistory;
import org.example.employeeservice.system.entity.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BackupHistoryRepository extends JpaRepository<BackupHistory, Long>, JpaSpecificationExecutor<BackupHistory> {

    boolean existsByStatus(BackupStatus status);

    List<BackupHistory> findByStatus(BackupStatus status);

    List<BackupHistory> findByStatusAndCreatedAtBefore(BackupStatus status, LocalDateTime dateTime);

    List<BackupHistory> findByCreatedAtBefore(LocalDateTime dateTime);
}

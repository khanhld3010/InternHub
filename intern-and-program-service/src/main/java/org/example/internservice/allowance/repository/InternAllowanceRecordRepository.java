package org.example.internservice.allowance.repository;

import org.example.internservice.allowance.entity.InternAllowanceRecord;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternAllowanceRecordRepository extends JpaRepository<InternAllowanceRecord, Long> {

    List<InternAllowanceRecord> findByPeriodMonth(String periodMonth);

    List<InternAllowanceRecord> findByInternUserIdOrderByPeriodMonthDesc(Long internUserId);

    Optional<InternAllowanceRecord> findByInternUserIdAndPeriodMonthAndContractId(
            Long internUserId, String periodMonth, Long contractId);

    List<InternAllowanceRecord> findByInternUserIdAndPeriodMonth(Long internUserId, String periodMonth);

    @Query("SELECT r FROM InternAllowanceRecord r " +
           "WHERE r.periodMonth = :periodMonth " +
           "AND (:status IS NULL OR r.status = :status)")
    List<InternAllowanceRecord> findByPeriodMonthAndOptionalStatus(
            @Param("periodMonth") String periodMonth,
            @Param("status") AllowanceStatus status);

    @Query("SELECT COUNT(r) FROM InternAllowanceRecord r " +
           "WHERE r.periodMonth = :periodMonth AND r.status != 'DRAFT'")
    long countLockedRecordsInPeriod(@Param("periodMonth") String periodMonth);
}

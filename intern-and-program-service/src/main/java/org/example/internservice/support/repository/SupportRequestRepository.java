package org.example.internservice.support.repository;

import org.example.internservice.support.entity.SupportRequest;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByInternUserIdOrderByCreatedAtDesc(Long internUserId);

    @Query("SELECT COUNT(s) > 0 FROM SupportRequest s " +
           "WHERE s.internUserId = :internUserId " +
           "AND s.requestType = :requestType " +
           "AND s.status IN ('PENDING', 'PROCESSING')")
    boolean existsActiveRequestByType(
            @Param("internUserId") Long internUserId,
            @Param("requestType") SupportRequestType requestType);

    @Query("SELECT s FROM SupportRequest s " +
           "WHERE (:status IS NULL OR s.status = :status) " +
           "AND (:requestType IS NULL OR s.requestType = :requestType) " +
           "ORDER BY s.createdAt DESC")
    List<SupportRequest> findWithFilters(
            @Param("status") SupportRequestStatus status,
            @Param("requestType") SupportRequestType requestType);
}

package org.example.internservice.leave.repository;

import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l " +
            "WHERE l.intern.id = :internId " +
            "AND l.status IN (org.example.internservice.leave.entity.enums.LeaveStatus.PENDING, org.example.internservice.leave.entity.enums.LeaveStatus.APPROVED) " +
            "AND l.startDate <= :endDate " +
            "AND l.endDate >= :startDate")
    boolean existsOverlappingActiveRequest(
            @Param("internId") Long internId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(value = "SELECT l FROM LeaveRequest l " +
            "JOIN FETCH l.intern i " +
            "WHERE i.id = :internId " +
            "AND (:status IS NULL OR l.status = :status) " +
            "AND (:year IS NULL OR YEAR(l.startDate) = :year OR YEAR(l.endDate) = :year)",
            countQuery = "SELECT COUNT(l) FROM LeaveRequest l " +
                    "WHERE l.intern.id = :internId " +
                    "AND (:status IS NULL OR l.status = :status) " +
                    "AND (:year IS NULL OR YEAR(l.startDate) = :year OR YEAR(l.endDate) = :year)")
    Page<LeaveRequest> findMyRequests(
            @Param("internId") Long internId,
            @Param("status") LeaveStatus status,
            @Param("year") Integer year,
            Pageable pageable
    );

    @Query("SELECT l FROM LeaveRequest l JOIN FETCH l.intern WHERE l.id = :id")
    Optional<LeaveRequest> findByIdWithIntern(@Param("id") Long id);

    @Query(value = "SELECT l FROM LeaveRequest l " +
            "JOIN FETCH l.intern i " +
            "WHERE l.status = org.example.internservice.leave.entity.enums.LeaveStatus.PENDING " +
            "AND i.id IN :internIds",
            countQuery = "SELECT COUNT(l) FROM LeaveRequest l " +
                    "WHERE l.status = org.example.internservice.leave.entity.enums.LeaveStatus.PENDING " +
                    "AND l.intern.id IN :internIds")
    Page<LeaveRequest> findPendingByInternIds(@Param("internIds") List<Long> internIds, Pageable pageable);

    @Query(value = "SELECT l FROM LeaveRequest l " +
            "JOIN FETCH l.intern i " +
            "WHERE l.status = org.example.internservice.leave.entity.enums.LeaveStatus.PENDING",
            countQuery = "SELECT COUNT(l) FROM LeaveRequest l " +
                    "WHERE l.status = org.example.internservice.leave.entity.enums.LeaveStatus.PENDING")
    Page<LeaveRequest> findAllPending(Pageable pageable);

    @Query("SELECT l FROM LeaveRequest l " +
            "WHERE l.intern.id = :internId " +
            "AND l.status = org.example.internservice.leave.entity.enums.LeaveStatus.APPROVED " +
            "AND :targetDate BETWEEN l.startDate AND l.endDate")
    Optional<LeaveRequest> findApprovedLeaveByInternAndDate(
            @Param("internId") Long internId,
            @Param("targetDate") LocalDate targetDate
    );
}

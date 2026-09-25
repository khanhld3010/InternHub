package org.example.internservice.program.repository;

import jakarta.persistence.LockModeType;
import org.example.internservice.program.entity.InternshipProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InternshipProgramRepository extends JpaRepository<InternshipProgram, Long>, JpaSpecificationExecutor<InternshipProgram> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM InternshipProgram p WHERE p.id = :id")
    Optional<InternshipProgram> findByIdWithLock(@Param("id") Long id);

    Optional<InternshipProgram> findByProgramCode(String programCode);

    boolean existsByProgramCode(String programCode);

    @Query("SELECT p FROM InternshipProgram p WHERE p.status = 'OPEN' AND :today >= p.startDate AND :today <= p.endDate")
    List<InternshipProgram> findOpenProgramsReadyToStart(@Param("today") LocalDate today);

    @Query("SELECT p FROM InternshipProgram p WHERE p.status = 'PLANNING' AND :today >= p.startDate AND :today <= p.endDate")
    List<InternshipProgram> findPlanningProgramsReachedStartDate(@Param("today") LocalDate today);

    @Query("SELECT p FROM InternshipProgram p WHERE p.status = 'PLANNING' AND :today >= p.startDate")
    List<InternshipProgram> findPlanningProgramsPastStartDate(@Param("today") LocalDate today);

    @Query("SELECT p FROM InternshipProgram p WHERE p.status = 'ONGOING' AND :today > p.endDate")
    List<InternshipProgram> findOngoingProgramsPastEndDate(@Param("today") LocalDate today);
}

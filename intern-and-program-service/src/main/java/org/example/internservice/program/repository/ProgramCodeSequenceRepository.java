package org.example.internservice.program.repository;

import jakarta.persistence.LockModeType;
import org.example.internservice.program.entity.ProgramCodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgramCodeSequenceRepository extends JpaRepository<ProgramCodeSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProgramCodeSequence s WHERE s.yearMonth = :yearMonth")
    Optional<ProgramCodeSequence> findByYearMonthWithLock(@Param("yearMonth") String yearMonth);
}

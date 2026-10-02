package org.example.internservice.mission.repository;

import org.example.internservice.mission.entity.MissionBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionBoardRepository extends JpaRepository<MissionBoard, Long> {

    @Query("SELECT mb FROM MissionBoard mb JOIN FETCH mb.program p LEFT JOIN FETCH mb.mentor m WHERE mb.program.id = :programId ORDER BY mb.createdAt DESC")
    List<MissionBoard> findByProgramIdWithDetails(@Param("programId") Long programId);

    @Query("SELECT mb FROM MissionBoard mb JOIN FETCH mb.program p LEFT JOIN FETCH mb.mentor m WHERE mb.id = :id")
    Optional<MissionBoard> findByIdWithDetails(@Param("id") Long id);

    long countByProgramId(Long programId);
}

package org.example.internservice.mission.repository;

import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MissionItemRepository extends JpaRepository<MissionItem, Long>, JpaSpecificationExecutor<MissionItem> {

    @Query("SELECT DISTINCT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.board.id = :boardId ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
    List<MissionItem> findByBoardIdWithAssignees(@Param("boardId") Long boardId);

    @Query("SELECT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.id = :id")
    Optional<MissionItem> findByIdWithAssignees(@Param("id") Long id);

    @Query("SELECT DISTINCT mi FROM MissionItem mi " +
           "JOIN FETCH mi.board b " +
           "JOIN FETCH b.program p " +
           "LEFT JOIN FETCH mi.assignees a " +
           "WHERE :internId IN (SELECT i.id FROM mi.assignees i) " +
           "ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
    List<MissionItem> findAssignedItemsByInternIdWithDetails(@Param("internId") Long internId);

    @Query("SELECT DISTINCT mi FROM MissionItem mi " +
           "JOIN FETCH mi.board b " +
           "JOIN FETCH b.program p " +
           "LEFT JOIN FETCH mi.assignees a " +
           "WHERE mi.id = :id")
    Optional<MissionItem> findByIdWithBoardAndAssignees(@Param("id") Long id);

    long countByBoardIdAndStatus(Long boardId, MissionItemStatus status);

    long countByBoardId(Long boardId);

    @Query("SELECT mi.board.program.id, COUNT(mi), " +
           "SUM(CASE WHEN mi.status = org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1L ELSE 0L END) " +
           "FROM MissionItem mi WHERE mi.board.program.id IN :programIds GROUP BY mi.board.program.id")
    List<Object[]> countTasksByProgramIds(@Param("programIds") Collection<Long> programIds);

    @Query("SELECT a.id, " +
           "SUM(CASE WHEN mi.status != org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN mi.status = org.example.internservice.mission.entity.enums.MissionItemStatus.COMPLETED THEN 1L ELSE 0L END) " +
           "FROM MissionItem mi JOIN mi.assignees a WHERE mi.board.program.id = :programId GROUP BY a.id")
    List<Object[]> countTasksByProgramIdGroupedByIntern(@Param("programId") Long programId);
}

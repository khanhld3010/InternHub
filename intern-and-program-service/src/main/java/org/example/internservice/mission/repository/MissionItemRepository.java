package org.example.internservice.mission.repository;

import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionItemRepository extends JpaRepository<MissionItem, Long> {

    @Query("SELECT DISTINCT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.board.id = :boardId ORDER BY mi.orderIndex ASC, mi.createdAt DESC")
    List<MissionItem> findByBoardIdWithAssignees(@Param("boardId") Long boardId);

    @Query("SELECT mi FROM MissionItem mi LEFT JOIN FETCH mi.assignees WHERE mi.id = :id")
    Optional<MissionItem> findByIdWithAssignees(@Param("id") Long id);

    long countByBoardIdAndStatus(Long boardId, MissionItemStatus status);

    long countByBoardId(Long boardId);
}

package org.example.aiservice.repository;

import org.example.aiservice.entity.AiChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AiChatLogRepository extends JpaRepository<AiChatLog, Long> {

    @Modifying
    @Query("DELETE FROM AiChatLog log WHERE log.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM AiChatLog log WHERE log.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}

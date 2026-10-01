package org.example.notificationservice.repository;

import org.example.notificationservice.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // Initial query
    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    // Keyset pagination (Cursor based)
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId " +
           "AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId)) " +
           "ORDER BY n.createdAt DESC, n.id DESC")
    List<Notification> findByRecipientIdWithCursor(
            @Param("recipientId") Long recipientId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);
}

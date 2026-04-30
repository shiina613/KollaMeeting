package com.example.kolla.repositories;

import com.example.kolla.models.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Notification entities.
 * Requirements: 10.5–10.7
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Paginated list of notifications for a user, newest first. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Count unread notifications for a user. */
    long countByUserIdAndIsReadFalse(Long userId);

    /** Mark all unread notifications for a user as read. */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") Long userId);
}

package com.example.kolla.services;

import com.example.kolla.models.Notification;
import com.example.kolla.models.User;
import com.example.kolla.responses.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Notification service interface.
 * Requirements: 10.5–10.7
 */
public interface NotificationService {

    /**
     * List notifications for the given user, newest first.
     */
    Page<NotificationResponse> listNotifications(User user, Pageable pageable);

    /**
     * Count unread notifications for the given user.
     */
    long countUnread(User user);

    /**
     * Mark a single notification as read.
     * Throws ForbiddenException if the notification does not belong to the user.
     */
    NotificationResponse markAsRead(Long notificationId, User user);

    /**
     * Mark all notifications for the user as read.
     *
     * @return number of notifications updated
     */
    int markAllAsRead(User user);

    /**
     * Create and persist a notification for a target user.
     * Used internally by other services (e.g., MeetingService, DocumentService).
     */
    Notification createNotification(User targetUser, User sender, String type,
                                    String title, String message, Long meetingId);
}

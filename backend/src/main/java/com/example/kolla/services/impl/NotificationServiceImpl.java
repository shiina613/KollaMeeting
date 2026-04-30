package com.example.kolla.services.impl;

import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Notification;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.NotificationRepository;
import com.example.kolla.responses.NotificationResponse;
import com.example.kolla.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationService implementation.
 * Requirements: 10.5–10.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final MeetingRepository meetingRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(User user, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(NotificationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(User user) {
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + notificationId));

        // Ensure the notification belongs to the requesting user
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not allowed to access this notification");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(User user) {
        int updated = notificationRepository.markAllReadByUserId(user.getId());
        log.debug("Marked {} notifications as read for userId={}", updated, user.getId());
        return updated;
    }

    @Override
    @Transactional
    public Notification createNotification(User targetUser, User sender, String type,
                                            String title, String message, Long meetingId) {
        Meeting meeting = null;
        if (meetingId != null) {
            meeting = meetingRepository.findById(meetingId).orElse(null);
        }

        Notification notification = Notification.builder()
                .user(targetUser)
                .sender(sender)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .meeting(meeting)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("Created notification type='{}' for userId={}", type, targetUser.getId());
        return saved;
    }
}

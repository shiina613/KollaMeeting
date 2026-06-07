package com.example.kolla.repositories;

import com.example.kolla.models.Notification;
import com.example.kolla.runtime.RuntimeMeetingStateStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationRepository {
    private final RuntimeMeetingStateStore store;

    public Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable) {
        return store.findNotificationsByUserId(userId, pageable);
    }

    public long countByUserIdAndIsReadFalse(Long userId) {
        return store.countUnreadNotifications(userId);
    }

    public Optional<Notification> findById(Long notificationId) {
        return store.findNotificationById(notificationId);
    }

    public Notification save(Notification notification) {
        return store.saveNotification(notification);
    }

    public int markAllReadByUserId(Long userId) {
        return store.markAllNotificationsRead(userId);
    }
}

package com.example.kolla.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private Long id;
    private User user;
    private User sender;
    private String type;
    private String title;
    private String message;

    @Builder.Default
    private boolean isRead = false;

    private Meeting meeting;
    private LocalDateTime createdAt;
}

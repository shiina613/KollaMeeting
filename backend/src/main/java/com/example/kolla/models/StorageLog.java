package com.example.kolla.models;

import com.example.kolla.enums.StorageOperationType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageLog {
    private Long id;
    private User adminUser;
    private StorageOperationType operation;
    private List<Long> meetingIds;
    private int fileCount;
    private long totalSizeBytes;
    private String description;
    private LocalDateTime createdAt;
}

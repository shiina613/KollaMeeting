package com.example.kolla.models;

import com.example.kolla.enums.RecordingStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recording {
    private Long id;
    private Meeting meeting;
    private String fileName;
    private Long fileSize;
    private String filePath;
    private String url;

    @Builder.Default
    private RecordingStatus status = RecordingStatus.RECORDING;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private User createdBy;
    private LocalDateTime createdAt;
}

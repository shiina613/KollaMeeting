package com.example.kolla.models;

import com.example.kolla.enums.StorageOperationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the `storage_log` table.
 * Records admin bulk/single delete operations for audit purposes.
 * Requirements: 6.7
 */
@Entity
@Table(name = "storage_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private StorageOperationType operation;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

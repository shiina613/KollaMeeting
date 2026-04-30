package com.example.kolla.repositories;

import com.example.kolla.models.StorageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for StorageLog entities.
 * Requirements: 6.7
 */
@Repository
public interface StorageLogRepository extends JpaRepository<StorageLog, Long> {
}

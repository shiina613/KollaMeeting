package com.example.kolla.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the `document` table.
 * Requirements: 9.1–9.7
 */
@Entity
@Table(name = "document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "Name", nullable = false, length = 500)
    private String fileName;

    @Transient
    private Long fileSize;

    @Transient
    private String fileType;

    @Column(name = "Content", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "User_id", nullable = false)
    private User uploadedBy;

    @Transient
    private LocalDateTime uploadedAt;

}

package com.example.kolla.models;

import com.example.kolla.enums.MinutesStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code minutes} table.
 *
 * <p>Lifecycle: DRAFT → HOST_CONFIRMED → SECRETARY_CONFIRMED
 *
 * <p>One Minutes record per meeting (UNIQUE on meeting_id).
 * The draft PDF is auto-generated from TranscriptionSegments when the meeting ends.
 * The Host confirms with a PAdES/CAdES digital signature; the Secretary may then edit and publish.
 *
 * Requirements: 25.1–25.7
 */
@Entity
@Table(name = "minutes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Minutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One-to-one with Meeting (UNIQUE constraint on meeting_id). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MinutesStatus status = MinutesStatus.DRAFT;

    /** Relative path to the auto-generated draft PDF under /app/storage/minutes/{meetingId}/. */
    @Column(name = "draft_pdf_path", length = 500)
    private String draftPdfPath;

    /** Relative path to the auto-generated draft DOCX under /app/storage/minutes/{meetingId}/. */
    @Column(name = "draft_docx_path", length = 500)
    private String draftDocxPath;

    /** Relative path to the Host-confirmed PDF (with embedded digital signature). */
    @Column(name = "confirmed_pdf_path", length = 500)
    private String confirmedPdfPath;

    /** Relative path to the Secretary-edited final PDF. */
    @Column(name = "secretary_pdf_path", length = 500)
    private String secretaryPdfPath;

    /** Relative path to the Secretary-edited final DOCX. */
    @Column(name = "secretary_docx_path", length = 500)
    private String secretaryDocxPath;

    /** Rich-text HTML content provided by the Secretary editor. */
    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    /** Timestamp when the Host confirmed the minutes. */
    @Column(name = "host_confirmed_at")
    private LocalDateTime hostConfirmedAt;

    /**
     * SHA-256 hex digest of the signed PDF bytes (post-signature file fingerprint).
     * Requirements: 25.4
     */
    @Column(name = "host_confirmation_hash", length = 255)
    private String hostConfirmationHash;

    /** Timestamp when the Secretary confirmed/published the minutes. */
    @Column(name = "secretary_confirmed_at")
    private LocalDateTime secretaryConfirmedAt;

    /** Timestamp when the 24-hour reminder notification was sent to the Host. */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

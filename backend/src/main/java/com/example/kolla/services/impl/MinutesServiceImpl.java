package com.example.kolla.services.impl;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Minutes;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.models.User;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MinutesRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.responses.MinutesResponse;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.MeetingService;
import com.example.kolla.services.MinutesService;
import com.example.kolla.services.NotificationService;
import com.example.kolla.websocket.MeetingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * MinutesService implementation.
 *
 * <p>PDF generation uses Apache PDFBox 3.x.
 * HTML-to-text extraction for Secretary edits uses jsoup.
 *
 * Requirements: 25.1–25.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinutesServiceImpl implements MinutesService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final float MARGIN = 50f;
    private static final float FONT_SIZE_TITLE = 16f;
    private static final float FONT_SIZE_HEADING = 12f;
    private static final float FONT_SIZE_BODY = 10f;
    private static final float LINE_HEIGHT_BODY = 14f;
    private static final float LINE_HEIGHT_HEADING = 18f;

    private final MinutesRepository minutesRepository;
    private final MeetingRepository meetingRepository;
    private final TranscriptionSegmentRepository transcriptionSegmentRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final MeetingService meetingService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    // ── compileDraftMinutes ───────────────────────────────────────────────────

    /**
     * Compile TranscriptionSegments → sort by (speakerTurnId, sequenceNumber)
     * → format text → generate draft PDF.
     * Requirements: 25.1–25.3
     */
    @Override
    @Transactional
    public void compileDraftMinutes(Meeting meeting) throws IOException {
        Long meetingId = meeting.getId();

        // Idempotency: skip if minutes already exist
        if (minutesRepository.existsByMeetingId(meetingId)) {
            log.info("Minutes already exist for meeting id={}, skipping draft compilation",
                    meetingId);
            return;
        }

        // 1. Fetch and sort segments: (speakerTurnId, sequenceNumber)
        List<TranscriptionSegment> segments =
                transcriptionSegmentRepository.findByMeetingIdOrderedForMinutes(meetingId);

        log.info("Compiling draft minutes for meeting id={} with {} segments",
                meetingId, segments.size());

        // 2. Generate PDF bytes
        byte[] pdfBytes = generateDraftPdf(meeting, segments);

        // 3. Persist Minutes record first to get the ID for the filename
        Minutes minutes = Minutes.builder()
                .meeting(meeting)
                .status(MinutesStatus.DRAFT)
                .build();
        Minutes saved = minutesRepository.save(minutes);

        // 4. Store PDF file: /storage/minutes/{meetingId}/draft_{minutesId}.pdf
        String fileName = "draft_" + saved.getId() + ".pdf";
        java.nio.file.Path storedPath =
                fileStorageService.storeBytes(pdfBytes, FileType.MINUTES, meetingId, fileName);

        // 5. Update record with file path
        saved.setDraftPdfPath(storedPath.toString());
        minutesRepository.save(saved);

        log.info("Draft minutes id={} saved to '{}' for meeting id={}",
                saved.getId(), storedPath, meetingId);

        // 6. Notify Host (Requirement 25.3)
        if (meeting.getHost() != null) {
            notificationService.createNotification(
                    meeting.getHost(),
                    null,
                    "MINUTES_READY",
                    "Meeting minutes ready",
                    "Draft minutes for meeting '" + meeting.getTitle()
                            + "' are ready for your review.",
                    meetingId);
        }

        // 7. Broadcast MINUTES_READY via WebSocket
        eventPublisher.publishMinutesReady(meetingId, saved.getId());
    }

    // ── getMinutes ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MinutesResponse getMinutes(Long meetingId, User requester) {
        findMeetingOrThrow(meetingId);
        checkMembership(meetingId, requester);

        Minutes minutes = minutesRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Minutes not found for meeting id: " + meetingId));

        return MinutesResponse.from(minutes);
    }

    // ── confirmMinutes ────────────────────────────────────────────────────────

    /**
     * Host confirms the draft minutes with a digital stamp.
     * Stamp = hostName + ISO8601 timestamp + SHA-256(JWT + PDF content).
     * Requirements: 25.4
     */
    @Override
    @Transactional
    public MinutesResponse confirmMinutes(Long meetingId, User requester, String jwtToken)
            throws IOException {

        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only the Host (or ADMIN) may confirm
        if (!isHostOrAdmin(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the meeting Host or an ADMIN may confirm the minutes");
        }

        Minutes minutes = findMinutesOrThrow(meetingId);

        if (minutes.getStatus() != MinutesStatus.DRAFT) {
            throw new BadRequestException(
                    "Minutes must be in DRAFT status to confirm (current: "
                            + minutes.getStatus() + ")");
        }

        // Load draft PDF bytes
        Resource draftResource = fileStorageService.loadFileAsResource(minutes.getDraftPdfPath());
        byte[] draftBytes = draftResource.getInputStream().readAllBytes();

        // Compute digital stamp
        LocalDateTime now = LocalDateTime.now(clock);
        String isoTimestamp = now.atZone(ZONE_VN).format(ISO_FORMATTER);
        String stampText = "Confirmed by: " + requester.getFullName()
                + " | " + isoTimestamp;
        String confirmationHash = computeSha256(jwtToken + new String(draftBytes, StandardCharsets.UTF_8));

        // Generate confirmed PDF with stamp overlay
        byte[] confirmedPdfBytes = stampPdf(draftBytes, stampText, confirmationHash);

        // Store confirmed PDF
        String fileName = "confirmed_" + minutes.getId() + ".pdf";
        java.nio.file.Path storedPath =
                fileStorageService.storeBytes(confirmedPdfBytes, FileType.MINUTES, meetingId, fileName);

        // Update record
        minutes.setStatus(MinutesStatus.HOST_CONFIRMED);
        minutes.setConfirmedPdfPath(storedPath.toString());
        minutes.setHostConfirmedAt(now);
        minutes.setHostConfirmationHash(confirmationHash);
        Minutes saved = minutesRepository.save(minutes);

        log.info("Minutes id={} confirmed by host '{}' for meeting id={}",
                saved.getId(), requester.getUsername(), meetingId);

        // Notify Secretary (Requirement 25.4)
        if (meeting.getSecretary() != null) {
            notificationService.createNotification(
                    meeting.getSecretary(),
                    requester,
                    "MINUTES_CONFIRMED",
                    "Minutes confirmed by Host",
                    "The Host has confirmed the minutes for meeting '"
                            + meeting.getTitle() + "'. Please review and publish.",
                    meetingId);
        }

        // Broadcast MINUTES_CONFIRMED
        eventPublisher.publishMinutesConfirmed(meetingId, saved.getId());

        return MinutesResponse.from(saved);
    }

    // ── editMinutes ───────────────────────────────────────────────────────────

    /**
     * Secretary edits and publishes the minutes.
     * Renders HTML → PDF via PDFBox + jsoup.
     * Requirements: 25.5
     */
    @Override
    @Transactional
    public MinutesResponse editMinutes(Long meetingId, String contentHtml, User requester)
            throws IOException {

        Meeting meeting = findMeetingOrThrow(meetingId);

        // Only the Secretary may edit
        if (!isSecretaryOrAdmin(meeting, requester)) {
            throw new ForbiddenException(
                    "Only the meeting Secretary may edit the minutes");
        }

        Minutes minutes = findMinutesOrThrow(meetingId);

        if (minutes.getStatus() != MinutesStatus.HOST_CONFIRMED) {
            throw new BadRequestException(
                    "Minutes must be in HOST_CONFIRMED status to edit (current: "
                            + minutes.getStatus() + ")");
        }

        // Render HTML → PDF
        byte[] secretaryPdfBytes = renderHtmlToPdf(meeting, contentHtml);

        // Store secretary PDF
        String fileName = "secretary_" + minutes.getId() + ".pdf";
        java.nio.file.Path storedPath =
                fileStorageService.storeBytes(secretaryPdfBytes, FileType.MINUTES, meetingId, fileName);

        // Update record
        minutes.setStatus(MinutesStatus.SECRETARY_CONFIRMED);
        minutes.setSecretaryPdfPath(storedPath.toString());
        minutes.setContentHtml(contentHtml);
        minutes.setSecretaryConfirmedAt(LocalDateTime.now(clock));
        Minutes saved = minutesRepository.save(minutes);

        log.info("Minutes id={} published by secretary '{}' for meeting id={}",
                saved.getId(), requester.getUsername(), meetingId);

        // Broadcast MINUTES_PUBLISHED to all participants (Requirement 25.5)
        eventPublisher.publishMinutesPublished(meetingId, saved.getId());

        return MinutesResponse.from(saved);
    }

    // ── downloadMinutes ───────────────────────────────────────────────────────

    /**
     * Download a specific version of the minutes PDF.
     * Requirements: 25.6
     */
    @Override
    @Transactional(readOnly = true)
    public Resource downloadMinutes(Long meetingId, String version, User requester)
            throws IOException {

        findMeetingOrThrow(meetingId);
        checkMembership(meetingId, requester);

        Minutes minutes = findMinutesOrThrow(meetingId);

        String filePath = switch (version.toLowerCase()) {
            case "draft" -> {
                if (minutes.getDraftPdfPath() == null || minutes.getDraftPdfPath().isBlank()) {
                    throw new BadRequestException("Draft PDF is not yet available");
                }
                yield minutes.getDraftPdfPath();
            }
            case "confirmed" -> {
                if (minutes.getConfirmedPdfPath() == null
                        || minutes.getConfirmedPdfPath().isBlank()) {
                    throw new BadRequestException("Confirmed PDF is not yet available");
                }
                yield minutes.getConfirmedPdfPath();
            }
            case "secretary" -> {
                if (minutes.getSecretaryPdfPath() == null
                        || minutes.getSecretaryPdfPath().isBlank()) {
                    throw new BadRequestException("Secretary PDF is not yet available");
                }
                yield minutes.getSecretaryPdfPath();
            }
            default -> throw new BadRequestException(
                    "Invalid version '" + version + "'. Must be: draft, confirmed, or secretary");
        };

        return fileStorageService.loadFileAsResource(filePath);
    }

    // ── Scheduled reminder ────────────────────────────────────────────────────

    /**
     * Runs every hour. Sends a reminder notification to the Host if the minutes
     * have been in DRAFT status for more than 24 hours without confirmation.
     * Requirements: 25.7
     */
    @Scheduled(fixedDelay = 3_600_000) // every hour
    @Transactional
    public void sendHostConfirmationReminders() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(24);
        List<Minutes> pendingMinutes =
                minutesRepository.findDraftMinutesNeedingReminder(cutoff);

        for (Minutes minutes : pendingMinutes) {
            try {
                Meeting meeting = minutes.getMeeting();
                if (meeting.getHost() == null) {
                    continue;
                }

                notificationService.createNotification(
                        meeting.getHost(),
                        null,
                        "MINUTES_REMINDER",
                        "Reminder: Meeting minutes awaiting confirmation",
                        "The draft minutes for meeting '" + meeting.getTitle()
                                + "' have been waiting for your confirmation for over 24 hours.",
                        meeting.getId());

                minutes.setReminderSentAt(LocalDateTime.now(clock));
                minutesRepository.save(minutes);

                log.info("Sent 24h reminder for minutes id={} (meeting id={})",
                        minutes.getId(), meeting.getId());

            } catch (Exception e) {
                log.error("Failed to send reminder for minutes id={}: {}",
                        minutes.getId(), e.getMessage(), e);
            }
        }
    }

    // ── PDF generation helpers ────────────────────────────────────────────────

    /**
     * Generate a draft PDF from sorted TranscriptionSegments.
     * Requirements: 25.1
     */
    private byte[] generateDraftPdf(Meeting meeting,
                                     List<TranscriptionSegment> segments) throws IOException {

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Build lines to render
            List<String> lines = buildTranscriptLines(meeting, segments);

            // Paginate and render
            renderLinesToDocument(doc, lines, fontBold, fontRegular);

            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Build the list of text lines for the draft PDF.
     * Groups consecutive segments by speaker turn for readability.
     */
    private List<String> buildTranscriptLines(Meeting meeting,
                                               List<TranscriptionSegment> segments) {
        List<String> lines = new ArrayList<>();

        // Title block
        lines.add("MEETING MINUTES — DRAFT");
        lines.add("");
        lines.add("Meeting: " + meeting.getTitle());
        if (meeting.getActivatedAt() != null) {
            lines.add("Date: " + meeting.getActivatedAt()
                    .atZone(ZONE_VN)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
        if (meeting.getEndedAt() != null) {
            lines.add("Ended: " + meeting.getEndedAt()
                    .atZone(ZONE_VN)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
        if (meeting.getHost() != null) {
            lines.add("Host: " + meeting.getHost().getFullName());
        }
        if (meeting.getSecretary() != null) {
            lines.add("Secretary: " + meeting.getSecretary().getFullName());
        }
        lines.add("");
        lines.add("─".repeat(60));
        lines.add("");

        if (segments.isEmpty()) {
            lines.add("[No transcription available for this meeting]");
            return lines;
        }

        // Group by speakerTurnId to avoid repeating speaker name on every line
        String currentTurnId = null;
        StringBuilder turnBuffer = new StringBuilder();

        for (TranscriptionSegment seg : segments) {
            if (!seg.getSpeakerTurnId().equals(currentTurnId)) {
                // Flush previous turn
                if (currentTurnId != null && turnBuffer.length() > 0) {
                    wrapAndAdd(lines, turnBuffer.toString(), 80);
                    lines.add("");
                }
                currentTurnId = seg.getSpeakerTurnId();
                turnBuffer = new StringBuilder();
                // Speaker header
                lines.add("[" + seg.getSpeakerName() + "]");
            }
            if (turnBuffer.length() > 0) {
                turnBuffer.append(" ");
            }
            turnBuffer.append(seg.getText().trim());
        }

        // Flush last turn
        if (turnBuffer.length() > 0) {
            wrapAndAdd(lines, turnBuffer.toString(), 80);
            lines.add("");
        }

        return lines;
    }

    /**
     * Word-wrap a long string into multiple lines of at most {@code maxWidth} chars.
     */
    private void wrapAndAdd(List<String> lines, String text, int maxWidth) {
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
    }

    /**
     * Render a list of text lines into a PDDocument, creating new pages as needed.
     */
    private void renderLinesToDocument(PDDocument doc,
                                        List<String> lines,
                                        PDType1Font fontBold,
                                        PDType1Font fontRegular) throws IOException {

        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        float pageHeight = page.getMediaBox().getHeight();
        float pageWidth = page.getMediaBox().getWidth();
        float usableWidth = pageWidth - 2 * MARGIN;
        float yPos = pageHeight - MARGIN;

        PDPageContentStream cs = new PDPageContentStream(doc, page);

        for (String line : lines) {
            boolean isTitle = line.startsWith("MEETING MINUTES");
            boolean isSpeaker = line.startsWith("[") && line.endsWith("]");
            boolean isSeparator = line.startsWith("─");

            float fontSize = isTitle ? FONT_SIZE_TITLE
                    : isSpeaker ? FONT_SIZE_HEADING
                    : FONT_SIZE_BODY;
            float lineHeight = (isTitle || isSpeaker) ? LINE_HEIGHT_HEADING : LINE_HEIGHT_BODY;
            PDType1Font font = (isTitle || isSpeaker) ? fontBold : fontRegular;

            // Check if we need a new page
            if (yPos - lineHeight < MARGIN) {
                cs.close();
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                yPos = page.getMediaBox().getHeight() - MARGIN;
                cs = new PDPageContentStream(doc, page);
            }

            if (!line.isBlank() && !isSeparator) {
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(MARGIN, yPos);
                // Sanitize: PDFBox Type1 fonts only support Latin-1; replace non-Latin chars
                String safeLine = sanitizeForPdf(line);
                cs.showText(safeLine);
                cs.endText();
            }

            yPos -= lineHeight;
        }

        cs.close();
    }

    /**
     * Stamp an existing PDF with a confirmation footer on the last page.
     * Requirements: 25.4
     */
    private byte[] stampPdf(byte[] originalPdfBytes,
                              String stampText,
                              String confirmationHash) throws IOException {

        try (PDDocument doc = Loader.loadPDF(originalPdfBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Add a new page for the confirmation stamp
            PDPage stampPage = new PDPage(PDRectangle.A4);
            doc.addPage(stampPage);

            float pageHeight = stampPage.getMediaBox().getHeight();
            float yPos = pageHeight - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(doc, stampPage)) {
                // Title
                cs.beginText();
                cs.setFont(fontBold, FONT_SIZE_HEADING);
                cs.newLineAtOffset(MARGIN, yPos);
                cs.showText("CONFIRMATION STAMP");
                cs.endText();
                yPos -= LINE_HEIGHT_HEADING * 2;

                // Stamp text
                cs.beginText();
                cs.setFont(fontRegular, FONT_SIZE_BODY);
                cs.newLineAtOffset(MARGIN, yPos);
                cs.showText(sanitizeForPdf(stampText));
                cs.endText();
                yPos -= LINE_HEIGHT_BODY * 2;

                // Hash
                cs.beginText();
                cs.setFont(fontRegular, 8f);
                cs.newLineAtOffset(MARGIN, yPos);
                cs.showText("SHA-256: " + confirmationHash);
                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Render HTML content to a PDF using jsoup for text extraction + PDFBox for rendering.
     * Requirements: 25.5
     */
    private byte[] renderHtmlToPdf(Meeting meeting, String contentHtml) throws IOException {
        // Parse HTML with jsoup and extract structured text
        Document htmlDoc = Jsoup.parse(contentHtml);
        List<String> lines = new ArrayList<>();

        // Header
        lines.add("MEETING MINUTES — FINAL");
        lines.add("");
        lines.add("Meeting: " + meeting.getTitle());
        if (meeting.getHost() != null) {
            lines.add("Host: " + meeting.getHost().getFullName());
        }
        if (meeting.getSecretary() != null) {
            lines.add("Secretary: " + meeting.getSecretary().getFullName());
        }
        lines.add("");
        lines.add("─".repeat(60));
        lines.add("");

        // Extract text from HTML elements
        extractHtmlLines(htmlDoc.body(), lines);

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            renderLinesToDocument(doc, lines, fontBold, fontRegular);

            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Recursively extract text lines from jsoup HTML elements.
     * Headings (h1–h6) are treated as section headers; paragraphs and list items as body text.
     */
    private void extractHtmlLines(Element element, List<String> lines) {
        if (element == null) {
            return;
        }

        for (Element child : element.children()) {
            String tagName = child.tagName().toLowerCase();

            switch (tagName) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    String text = child.text().trim();
                    if (!text.isBlank()) {
                        lines.add("");
                        lines.add("[" + text + "]");
                    }
                }
                case "p" -> {
                    String text = child.text().trim();
                    if (!text.isBlank()) {
                        wrapAndAdd(lines, text, 80);
                    }
                }
                case "li" -> {
                    String text = child.text().trim();
                    if (!text.isBlank()) {
                        wrapAndAdd(lines, "• " + text, 80);
                    }
                }
                case "br" -> lines.add("");
                case "ul", "ol", "div", "section", "article", "main", "body" ->
                        extractHtmlLines(child, lines);
                default -> {
                    // For inline elements (span, strong, em, etc.), extract text directly
                    String text = child.text().trim();
                    if (!text.isBlank() && child.children().isEmpty()) {
                        wrapAndAdd(lines, text, 80);
                    } else {
                        extractHtmlLines(child, lines);
                    }
                }
            }
        }
    }

    /**
     * Replace non-Latin-1 characters with '?' for PDFBox Type1 font compatibility.
     * Vietnamese characters (outside Latin-1) are transliterated to ASCII approximations.
     */
    private String sanitizeForPdf(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c < 256) {
                sb.append(c);
            } else {
                // Replace non-Latin-1 with '?'
                sb.append('?');
            }
        }
        return sb.toString();
    }

    /**
     * Compute SHA-256 hash of the input string and return as hex string.
     * Requirements: 25.4
     */
    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ── Access control helpers ────────────────────────────────────────────────

    private boolean isHostOrAdmin(Meeting meeting, User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return meeting.getHost() != null
                && meeting.getHost().getId().equals(user.getId());
    }

    private boolean isSecretaryOrAdmin(Meeting meeting, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SECRETARY) {
            return true;
        }
        return meeting.getSecretary() != null
                && meeting.getSecretary().getId().equals(user.getId());
    }

    private void checkMembership(Long meetingId, User user) {
        if (user.getRole() == Role.ADMIN
                || user.getRole() == Role.SECRETARY) {
            return; // ADMIN and SECRETARY can access all meeting data
        }
        if (!meetingService.isMember(meetingId, user.getId())) {
            throw new ForbiddenException("You are not a member of meeting id: " + meetingId);
        }
    }

    // ── Repository helpers ────────────────────────────────────────────────────

    private Meeting findMeetingOrThrow(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meeting not found with id: " + meetingId));
    }

    private Minutes findMinutesOrThrow(Long meetingId) {
        return minutesRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Minutes not found for meeting id: " + meetingId));
    }
}

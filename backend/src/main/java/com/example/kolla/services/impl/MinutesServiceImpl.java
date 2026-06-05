package com.example.kolla.services.impl;

import com.example.kolla.enums.FileType;
import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.exceptions.ForbiddenException;
import com.example.kolla.exceptions.ResourceNotFoundException;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Member;
import com.example.kolla.models.Minutes;
import com.example.kolla.models.TranscriptionSegment;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DocumentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.MinutesRepository;
import com.example.kolla.repositories.MemberRepository;
import com.example.kolla.repositories.TranscriptionSegmentRepository;
import com.example.kolla.responses.MinutesResponse;
import com.example.kolla.services.FileStorageService;
import com.example.kolla.services.MeetingService;
import com.example.kolla.services.MinutesService;
import com.example.kolla.services.NotificationService;
import com.example.kolla.services.PdfDigitalSignatureService;
import com.example.kolla.websocket.MeetingEventPublisher;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MinutesService implementation.
 *
 * <p>PDF/DOCX rendering uses PDFBox and Apache POI; PDF signing uses iText signatures.
 * HTML-to-text extraction for Secretary edits uses jsoup.
 *
 * Requirements: 25.1–25.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinutesServiceImpl implements MinutesService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
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
    private final PdfDigitalSignatureService pdfDigitalSignatureService;
    private final DocumentRepository documentRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    // ── compileDraftMinutes ───────────────────────────────────────────────────

    /**
     * Compile TranscriptionSegments → sort by (speakerTurnId, sequenceNumber)
     * → format text → generate draft PDF.
     * Requirements: 25.1–25.3
     */
    @Override
    // REQUIRES_NEW: run in a separate transaction so that a failure here
    // (e.g. PDF font error, IO error) does NOT contaminate the caller's
    // transaction (endMeeting / processExpiredWaitingTimeouts).
    // Without this, an exception inside compileDraftMinutes marks the outer
    // @Transactional as rollback-only, causing UnexpectedRollbackException
    // even when the caller has a try-catch around this call.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compileDraftMinutes(Meeting meeting) throws IOException {
        Long meetingId = meeting.getId();

        // Idempotency: skip if minutes already exist
        if (minutesRepository.existsByMeetingId(meetingId)) {
            log.info("Minutes already exist for meeting id={}, skipping draft compilation",
                    meetingId);
            return;
        }

        // 1. Fetch and sort segments chronologically for minutes assembly
        List<TranscriptionSegment> segments =
                transcriptionSegmentRepository.findByMeetingIdOrderedForMinutes(meetingId);

        log.info("Compiling draft minutes for meeting id={} with {} segments",
                meetingId, segments.size());

        // 2. Generate PDF and DOCX bytes
        List<String> lines = buildTranscriptLines(meeting, segments);
        byte[] pdfBytes = generateDraftPdf(lines);
        byte[] docxBytes = DocxMinutesRenderer.renderLines(lines);

        // 3. Persist Minutes record first to get the ID for the filename
        Minutes minutes = Minutes.builder()
                .meeting(meeting)
                .status(MinutesStatus.DRAFT)
                .build();
        Minutes saved = minutesRepository.save(minutes);

        // 4. Store generated files: /storage/minutes/{meetingId}/draft_{minutesId}.{pdf,docx}
        String pdfFileName = "draft_" + saved.getId() + ".pdf";
        java.nio.file.Path storedPdfPath =
                fileStorageService.storeBytes(pdfBytes, FileType.MINUTES, meetingId, pdfFileName);
        String docxFileName = "draft_" + saved.getId() + ".docx";
        java.nio.file.Path storedDocxPath =
                fileStorageService.storeBytes(docxBytes, FileType.MINUTES, meetingId, docxFileName);

        // 5. Update record with file path
        saved.setDraftPdfPath(storedPdfPath.toString());
        saved.setDraftDocxPath(storedDocxPath.toString());
        minutesRepository.save(saved);
        createMinutesDocument(
                meeting,
                "bien-ban-nhap-" + meetingId + ".pdf",
                "application/pdf",
                storedPdfPath.toString(),
                (long) pdfBytes.length);
        createMinutesDocument(
                meeting,
                "bien-ban-nhap-" + meetingId + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                storedDocxPath.toString(),
                (long) docxBytes.length);

        log.info("Draft minutes id={} saved to '{}' for meeting id={}",
                saved.getId(), storedPdfPath, meetingId);

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
     * Host confirms the draft minutes with a PAdES/CAdES digital signature embedded in the PDF.
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

        LocalDateTime now = LocalDateTime.now(clock);

        // PAdES/CAdES embedded signature (keystore must be configured)
        byte[] confirmedPdfBytes = pdfDigitalSignatureService.signPdf(
                draftBytes, requester.getFullName());

        // Integrity fingerprint of the signed PDF file (audit / API)
        String confirmationHash = computeSha256(confirmedPdfBytes);

        log.info("Minutes digitally signed for meeting {} by {} (cert={})",
                meetingId, requester.getUsername(), pdfDigitalSignatureService.signerCertificateSubject());

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

        // Render HTML to PDF and DOCX
        List<String> secretaryLines = buildSecretaryLines(meeting, contentHtml);
        byte[] secretaryPdfBytes = renderLinesToPdf(secretaryLines);
        byte[] secretaryDocxBytes = DocxMinutesRenderer.renderLines(secretaryLines);

        // Store secretary PDF
        String fileName = "secretary_" + minutes.getId() + ".pdf";
        java.nio.file.Path storedPath =
                fileStorageService.storeBytes(secretaryPdfBytes, FileType.MINUTES, meetingId, fileName);
        String docxFileName = "secretary_" + minutes.getId() + ".docx";
        java.nio.file.Path storedDocxPath =
                fileStorageService.storeBytes(secretaryDocxBytes, FileType.MINUTES, meetingId, docxFileName);

        // Update record
        minutes.setStatus(MinutesStatus.SECRETARY_CONFIRMED);
        minutes.setSecretaryPdfPath(storedPath.toString());
        minutes.setSecretaryDocxPath(storedDocxPath.toString());
        minutes.setContentHtml(contentHtml);
        minutes.setSecretaryConfirmedAt(LocalDateTime.now(clock));
        Minutes saved = minutesRepository.save(minutes);
        createMinutesDocument(
                meeting,
                "bien-ban-thu-ky-" + meetingId + ".pdf",
                "application/pdf",
                storedPath.toString(),
                (long) secretaryPdfBytes.length);
        createMinutesDocument(
                meeting,
                "bien-ban-thu-ky-" + meetingId + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                storedDocxPath.toString(),
                (long) secretaryDocxBytes.length);

        log.info("Minutes id={} published by secretary '{}' for meeting id={}",
                saved.getId(), requester.getUsername(), meetingId);

        // Broadcast MINUTES_PUBLISHED to all participants (Requirement 25.5)
        eventPublisher.publishMinutesPublished(meetingId, saved.getId());

        return MinutesResponse.from(saved);
    }

    // ── downloadMinutes ───────────────────────────────────────────────────────

    /**
     * Download a specific version of the minutes file.
     * Requirements: 25.6
     */
    @Override
    @Transactional(readOnly = true)
    public Resource downloadMinutes(Long meetingId, String version, String format, User requester)
            throws IOException {

        findMeetingOrThrow(meetingId);
        checkMembership(meetingId, requester);

        Minutes minutes = findMinutesOrThrow(meetingId);

        String normalizedFormat = format == null ? "pdf" : format.toLowerCase();
        String filePath = switch (version.toLowerCase()) {
            case "draft" -> selectGeneratedPath(
                    minutes.getDraftPdfPath(),
                    minutes.getDraftDocxPath(),
                    normalizedFormat,
                    "Draft");
            case "confirmed" -> {
                if ("docx".equals(normalizedFormat)) {
                    yield selectGeneratedPath(
                            minutes.getConfirmedPdfPath(),
                            minutes.getDraftDocxPath(),
                            normalizedFormat,
                            "Confirmed");
                }
                yield selectGeneratedPath(
                        minutes.getConfirmedPdfPath(),
                        minutes.getDraftDocxPath(),
                        normalizedFormat,
                        "Confirmed");
            }
            case "secretary" -> selectGeneratedPath(
                    minutes.getSecretaryPdfPath(),
                    minutes.getSecretaryDocxPath(),
                    normalizedFormat,
                    "Secretary");
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
    private byte[] generateDraftPdf(List<String> lines) throws IOException {
        return renderLinesToPdf(lines);
    }

    private byte[] renderLinesToPdf(List<String> lines) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            com.itextpdf.layout.Document document =
                    new com.itextpdf.layout.Document(pdf, PageSize.A4);
            document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

            PdfFont fontBold = loadITextFont("/fonts/Roboto-Bold.ttf", true);
            PdfFont fontRegular = loadITextFont("/fonts/Roboto-Regular.ttf", false);

            for (String line : lines) {
                boolean isTitle = line.startsWith("MEETING MINUTES");
                boolean isSpeaker = line.startsWith("[") && line.contains("]");
                boolean isSeparator = line.startsWith("─") || line.startsWith("â”€");
                if (isSeparator) {
                    continue;
                }

                float fontSize = isTitle ? FONT_SIZE_TITLE
                        : isSpeaker ? FONT_SIZE_HEADING
                        : FONT_SIZE_BODY;
                PdfFont font = (isTitle || isSpeaker) ? fontBold : fontRegular;

                Paragraph paragraph = new Paragraph(line.isBlank() ? " " : line)
                        .setFont(font)
                        .setFontSize(fontSize)
                        .setMultipliedLeading(1.15f)
                        .setMarginTop(0)
                        .setMarginBottom(2);
                document.add(paragraph);
            }

            document.close();
            return out.toByteArray();
        }
    }

    private PdfFont loadITextFont(String classpathPath, boolean bold) throws IOException {
        try (InputStream fontStream = getClass().getResourceAsStream(classpathPath)) {
            if (fontStream == null) {
                log.warn("Font not found at classpath:{}, falling back to Helvetica", classpathPath);
                return PdfFontFactory.createFont(
                        bold ? StandardFonts.HELVETICA_BOLD : StandardFonts.HELVETICA);
            }
            return PdfFontFactory.createFont(
                    fontStream.readAllBytes(),
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
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

        Map<Long, Member> memberByUserId = memberRepository.findByMeetingId(meeting.getId())
                .stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getId(),
                        member -> member,
                        (left, right) -> left));

        for (TranscriptionSegment seg : segments) {
            Member member = memberByUserId.get(seg.getSpeakerId());
            String meetingRole = member != null && member.getMeetingRole() != null
                    ? member.getMeetingRole().name()
                    : "MEMBER";
            String time = seg.getSegmentStartTime() != null
                    ? seg.getSegmentStartTime()
                            .atZone(ZONE_VN)
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "--:--";
            String department = null;
            if (member != null && member.getUser() != null) {
                department = member.getUser().getDegree();
            }
            String speakerHeader = "[" + time + "] " + seg.getSpeakerName() + " (" + meetingRole + ")";
            if (department != null && !department.isBlank()) {
                speakerHeader += " - " + department;
            }
            lines.add(speakerHeader);
            wrapAndAdd(lines, seg.getText().trim(), 80);
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
                                        PDFont fontBold,
                                        PDFont fontRegular) throws IOException {

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
            PDFont font = (isTitle || isSpeaker) ? fontBold : fontRegular;

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
                cs.showText(line);
                cs.endText();
            }

            yPos -= lineHeight;
        }

        cs.close();
    }

    /**
     * Render HTML content to a PDF using jsoup for text extraction + PDFBox for rendering.
     * Requirements: 25.5
     */
    private byte[] renderHtmlToPdf(Meeting meeting, String contentHtml) throws IOException {
        return renderLinesToPdf(buildSecretaryLines(meeting, contentHtml));
    }

    private List<String> buildSecretaryLines(Meeting meeting, String contentHtml) {
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

        return lines;
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
     * Load a TrueType font from the classpath.
     * Uses PDType0Font which supports full Unicode including Vietnamese characters.
     */
    private PDFont loadFont(PDDocument doc, String classpathPath) throws IOException {
        try (InputStream fontStream = getClass().getResourceAsStream(classpathPath)) {
            if (fontStream == null) {
                log.warn("Font not found at classpath:{}, falling back to Helvetica", classpathPath);
                return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            }
            return PDType0Font.load(doc, fontStream);
        }
    }

    /**
     * Compute SHA-256 hash of the input string and return as hex string.
     * Requirements: 25.4
     */
    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String selectGeneratedPath(
            String pdfPath,
            String docxPath,
            String format,
            String label) {
        return switch (format) {
            case "pdf" -> {
                if (pdfPath == null || pdfPath.isBlank()) {
                    throw new BadRequestException(label + " PDF is not yet available");
                }
                yield pdfPath;
            }
            case "docx" -> {
                if (docxPath == null || docxPath.isBlank()) {
                    throw new BadRequestException(label + " DOCX is not yet available");
                }
                yield docxPath;
            }
            default -> throw new BadRequestException(
                    "Invalid format '" + format + "'. Must be: pdf or docx");
        };
    }

    private void createMinutesDocument(
            Meeting meeting,
            String fileName,
            String fileType,
            String filePath,
            Long fileSize) {
        User uploader = documentUploader(meeting);
        if (uploader == null) {
            log.warn("Cannot create document record for minutes file '{}' in meeting id={}: no uploader",
                    fileName, meeting.getId());
            return;
        }

        com.example.kolla.models.Document document = com.example.kolla.models.Document.builder()
                .meeting(meeting)
                .fileName(fileName)
                .fileType(fileType)
                .filePath(filePath)
                .fileSize(fileSize)
                .uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now(clock))
                .build();
        documentRepository.save(document);
    }

    private User documentUploader(Meeting meeting) {
        if (meeting.getHost() != null) {
            return meeting.getHost();
        }
        if (meeting.getSecretary() != null) {
            return meeting.getSecretary();
        }
        return meeting.getCreator();
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

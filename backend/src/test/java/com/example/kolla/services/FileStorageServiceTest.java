package com.example.kolla.services;

import com.example.kolla.config.FileStorageProperties;
import com.example.kolla.enums.FileType;
import com.example.kolla.exceptions.BadRequestException;
import com.example.kolla.services.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileStorageServiceImpl}.
 * Covers: file validation, store/load/delete lifecycle, path-traversal security,
 * concurrent read/write safety, and storage stats.
 * Requirements: 6.1–6.7, 20.6
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileStorageProperties properties;

    private FileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        // Use lenient() because individual tests only exercise a subset of these stubs
        lenient().when(properties.getBasePath()).thenReturn(tempDir.toString());
        lenient().when(properties.getRecordingsDir()).thenReturn("recordings");
        lenient().when(properties.getDocumentsDir()).thenReturn("documents");
        lenient().when(properties.getAudioChunksDir()).thenReturn("audio_chunks");
        lenient().when(properties.getMinutesDir()).thenReturn("minutes");
        lenient().when(properties.getAllowedDocumentTypes()).thenReturn(List.of(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "image/jpeg",
                "image/png"
        ));
        lenient().when(properties.getMaxDocumentSizeMb()).thenReturn(100L);
        lenient().when(properties.getMaxRecordingSizeMb()).thenReturn(5120L);

        service = new FileStorageServiceImpl(properties);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateFile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateFile()")
    class ValidateFile {

        @Test
        @DisplayName("Throws BadRequestException for empty file")
        void validate_throwsForEmptyFile() {
            MockMultipartFile empty = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> service.validateFile(empty, FileType.DOCUMENT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Throws BadRequestException for null file")
        void validate_throwsForNullFile() {
            assertThatThrownBy(() -> service.validateFile(null, FileType.DOCUMENT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Accepts valid PDF document")
        void validate_acceptsValidPdf() {
            MockMultipartFile pdf = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf", "PDF content".getBytes());

            assertThatCode(() -> service.validateFile(pdf, FileType.DOCUMENT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Accepts valid plain text document")
        void validate_acceptsPlainText() {
            MockMultipartFile txt = new MockMultipartFile(
                    "file", "notes.txt", "text/plain", "some text".getBytes());

            assertThatCode(() -> service.validateFile(txt, FileType.DOCUMENT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Throws BadRequestException for unsupported document MIME type")
        void validate_throwsForUnsupportedMimeType() {
            MockMultipartFile exe = new MockMultipartFile(
                    "file", "virus.exe", "application/octet-stream", "binary".getBytes());

            assertThatThrownBy(() -> service.validateFile(exe, FileType.DOCUMENT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported document type");
        }

        @Test
        @DisplayName("Throws BadRequestException when document exceeds max size")
        void validate_throwsWhenDocumentTooLarge() {
            when(properties.getMaxDocumentSizeMb()).thenReturn(1L); // 1 MB limit
            byte[] oversized = new byte[2 * 1024 * 1024]; // 2 MB
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.pdf", "application/pdf", oversized);

            assertThatThrownBy(() -> service.validateFile(file, FileType.DOCUMENT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds the maximum");
        }

        @Test
        @DisplayName("Accepts document exactly at max size boundary")
        void validate_acceptsDocumentAtMaxSizeBoundary() {
            when(properties.getMaxDocumentSizeMb()).thenReturn(1L);
            byte[] exactSize = new byte[1024 * 1024]; // exactly 1 MB
            MockMultipartFile file = new MockMultipartFile(
                    "file", "exact.pdf", "application/pdf", exactSize);

            assertThatCode(() -> service.validateFile(file, FileType.DOCUMENT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Throws BadRequestException when recording exceeds max size")
        void validate_throwsWhenRecordingTooLarge() {
            when(properties.getMaxRecordingSizeMb()).thenReturn(1L); // 1 MB limit
            byte[] oversized = new byte[2 * 1024 * 1024]; // 2 MB
            MockMultipartFile file = new MockMultipartFile(
                    "file", "meeting.mp4", "video/mp4", oversized);

            assertThatThrownBy(() -> service.validateFile(file, FileType.RECORDING))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds the maximum");
        }

        @Test
        @DisplayName("Recording does not check MIME type — any content type accepted")
        void validate_recordingAcceptsAnyMimeType() {
            MockMultipartFile mp4 = new MockMultipartFile(
                    "file", "meeting.mp4", "video/mp4", "video data".getBytes());

            assertThatCode(() -> service.validateFile(mp4, FileType.RECORDING))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AUDIO_CHUNK and MINUTES types have no size restriction")
        void validate_audioChunkAndMinutesHaveNoSizeRestriction() {
            byte[] largeContent = new byte[10 * 1024 * 1024]; // 10 MB
            MockMultipartFile audioChunk = new MockMultipartFile(
                    "file", "chunk.wav", "audio/wav", largeContent);
            MockMultipartFile minutes = new MockMultipartFile(
                    "file", "minutes.pdf", "application/pdf", largeContent);

            assertThatCode(() -> service.validateFile(audioChunk, FileType.AUDIO_CHUNK))
                    .doesNotThrowAnyException();
            assertThatCode(() -> service.validateFile(minutes, FileType.MINUTES))
                    .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // storeFile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("storeFile()")
    class StoreFile {

        @Test
        @DisplayName("Stores file and returns relative path under type/meetingId/")
        void storeFile_returnsRelativePath() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf", "PDF content".getBytes());

            Path relative = service.storeFile(file, FileType.DOCUMENT, 42L, "doc");

            assertThat(relative.toString()).startsWith("documents" + FileSystems.getDefault().getSeparator() + "42");
            assertThat(tempDir.resolve(relative)).exists();
        }

        @Test
        @DisplayName("Stored file content matches original bytes")
        void storeFile_contentMatchesOriginal() throws IOException {
            byte[] content = "Hello, Kolla!".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "notes.txt", "text/plain", content);

            Path relative = service.storeFile(file, FileType.DOCUMENT, 1L, "prefix");
            byte[] stored = Files.readAllBytes(tempDir.resolve(relative));

            assertThat(stored).isEqualTo(content);
        }

        @Test
        @DisplayName("Each call generates a unique filename (UUID-based)")
        void storeFile_generatesUniqueFilenames() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "same.pdf", "application/pdf", "content".getBytes());

            Path path1 = service.storeFile(file, FileType.DOCUMENT, 1L, "doc");
            Path path2 = service.storeFile(file, FileType.DOCUMENT, 1L, "doc");

            assertThat(path1).isNotEqualTo(path2);
        }

        @Test
        @DisplayName("Creates meeting subdirectory automatically")
        void storeFile_createsSubdirectory() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "rec.mp4", "video/mp4", "video".getBytes());

            Path relative = service.storeFile(file, FileType.RECORDING, 99L, "rec");

            Path dir = tempDir.resolve("recordings").resolve("99");
            assertThat(dir).isDirectory();
        }

        @Test
        @DisplayName("Sanitizes filename — removes unsafe characters")
        void storeFile_sanitizesFilename() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "my file (v2).pdf", "application/pdf", "content".getBytes());

            Path relative = service.storeFile(file, FileType.DOCUMENT, 1L, "doc");

            // Filename should not contain spaces or parentheses
            String filename = relative.getFileName().toString();
            assertThat(filename).doesNotContain(" ", "(", ")");
        }

        @Test
        @DisplayName("Throws BadRequestException for invalid file type")
        void storeFile_throwsForInvalidFile() {
            MockMultipartFile invalid = new MockMultipartFile(
                    "file", "bad.exe", "application/octet-stream", "binary".getBytes());

            assertThatThrownBy(() -> service.storeFile(invalid, FileType.DOCUMENT, 1L, "doc"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // storeBytes
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("storeBytes()")
    class StoreBytes {

        @Test
        @DisplayName("Stores raw bytes and returns relative path")
        void storeBytes_returnsRelativePath() throws IOException {
            byte[] pdfBytes = "PDF binary content".getBytes();

            Path relative = service.storeBytes(pdfBytes, FileType.MINUTES, 10L, "draft_10.pdf");

            assertThat(relative.toString()).contains("minutes");
            assertThat(tempDir.resolve(relative)).exists();
        }

        @Test
        @DisplayName("Stored bytes content matches original")
        void storeBytes_contentMatchesOriginal() throws IOException {
            byte[] original = "Meeting minutes content".getBytes();

            Path relative = service.storeBytes(original, FileType.MINUTES, 5L, "minutes.pdf");
            byte[] stored = Files.readAllBytes(tempDir.resolve(relative));

            assertThat(stored).isEqualTo(original);
        }

        @Test
        @DisplayName("Overwrites existing file with same name")
        void storeBytes_overwritesExistingFile() throws IOException {
            service.storeBytes("first version".getBytes(), FileType.MINUTES, 1L, "draft.pdf");
            service.storeBytes("second version".getBytes(), FileType.MINUTES, 1L, "draft.pdf");

            Path relative = Path.of("minutes", "1", "draft.pdf");
            byte[] stored = Files.readAllBytes(tempDir.resolve(relative));

            assertThat(new String(stored)).isEqualTo("second version");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadFileAsResource
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("loadFileAsResource()")
    class LoadFileAsResource {

        @Test
        @DisplayName("Returns readable resource for existing file")
        void load_returnsReadableResource() throws IOException {
            byte[] content = "downloadable content".getBytes();
            Path relative = service.storeBytes(content, FileType.DOCUMENT, 1L, "doc.pdf");

            Resource resource = service.loadFileAsResource(relative.toString());

            assertThat(resource.isReadable()).isTrue();
            assertThat(resource.contentLength()).isEqualTo(content.length);
        }

        @Test
        @DisplayName("Throws NoSuchFileException for non-existent file")
        void load_throwsForMissingFile() {
            assertThatThrownBy(() -> service.loadFileAsResource("documents/1/nonexistent.pdf"))
                    .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Throws BadRequestException for path traversal attempt")
        void load_throwsForPathTraversal() {
            assertThatThrownBy(() -> service.loadFileAsResource("../../../etc/passwd"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid file path");
        }

        @Test
        @DisplayName("Throws BadRequestException for absolute path outside base")
        void load_throwsForAbsolutePathOutsideBase() {
            // Attempt to escape via absolute path
            String maliciousPath = "/etc/passwd";
            assertThatThrownBy(() -> service.loadFileAsResource(maliciousPath))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid file path");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteFile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteFile()")
    class DeleteFile {

        @Test
        @DisplayName("Deletes existing file from filesystem")
        void delete_removesExistingFile() throws IOException {
            Path relative = service.storeBytes("content".getBytes(), FileType.DOCUMENT, 1L, "file.pdf");
            assertThat(tempDir.resolve(relative)).exists();

            service.deleteFile(relative.toString());

            assertThat(tempDir.resolve(relative)).doesNotExist();
        }

        @Test
        @DisplayName("Silently ignores deletion of non-existent file")
        void delete_silentlyIgnoresMissingFile() {
            assertThatCode(() -> service.deleteFile("documents/1/ghost.pdf"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Silently ignores null or blank path")
        void delete_silentlyIgnoresNullOrBlankPath() {
            assertThatCode(() -> service.deleteFile(null)).doesNotThrowAnyException();
            assertThatCode(() -> service.deleteFile("  ")).doesNotThrowAnyException();
            assertThatCode(() -> service.deleteFile("")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ignores path traversal attempt on delete — does not throw")
        void delete_ignoresPathTraversalAttempt() {
            // Should log a warning and return without deleting or throwing
            assertThatCode(() -> service.deleteFile("../../../etc/passwd"))
                    .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStorageStats
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStorageStats()")
    class GetStorageStats {

        @Test
        @DisplayName("Returns zero for all types when storage is empty")
        void stats_returnsZeroWhenEmpty() {
            Map<FileType, Long> stats = service.getStorageStats();

            assertThat(stats).containsKeys(FileType.values());
            assertThat(stats.values()).allMatch(size -> size == 0L);
        }

        @Test
        @DisplayName("Returns correct byte count after storing files")
        void stats_returnsCorrectBytesAfterStore() throws IOException {
            byte[] content = new byte[1024]; // 1 KB
            service.storeBytes(content, FileType.DOCUMENT, 1L, "file1.pdf");
            service.storeBytes(content, FileType.DOCUMENT, 1L, "file2.pdf");

            Map<FileType, Long> stats = service.getStorageStats();

            assertThat(stats.get(FileType.DOCUMENT)).isEqualTo(2048L);
        }

        @Test
        @DisplayName("Stats decrease after file deletion")
        void stats_decreaseAfterDeletion() throws IOException {
            byte[] content = new byte[512];
            Path relative = service.storeBytes(content, FileType.MINUTES, 1L, "minutes.pdf");

            long before = service.getStorageStats().get(FileType.MINUTES);
            service.deleteFile(relative.toString());
            long after = service.getStorageStats().get(FileType.MINUTES);

            assertThat(before).isEqualTo(512L);
            assertThat(after).isEqualTo(0L);
        }

        @Test
        @DisplayName("Returns stats for all FileType values")
        void stats_coversAllFileTypes() {
            Map<FileType, Long> stats = service.getStorageStats();

            assertThat(stats.keySet()).containsExactlyInAnyOrder(FileType.values());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent read/write safety
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent read/write safety")
    class ConcurrentSafety {

        private static final int THREAD_COUNT = 20;
        private static final int ITERATIONS = 5;

        @Test
        @DisplayName("Concurrent storeBytes calls do not corrupt file content")
        void concurrent_storeBytesDoesNotCorruptContent() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<Future<Path>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    latch.await();
                    byte[] content = ("thread-" + threadId + "-content").getBytes();
                    return service.storeBytes(content, FileType.AUDIO_CHUNK, 1L,
                            "chunk_" + threadId + ".wav");
                }));
            }

            latch.countDown(); // release all threads simultaneously
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Verify all files were written without errors
            for (Future<Path> future : futures) {
                try {
                    Path relative = future.get();
                    assertThat(tempDir.resolve(relative)).exists();
                } catch (ExecutionException e) {
                    errorCount.incrementAndGet();
                }
            }

            assertThat(errorCount.get())
                    .as("No concurrent write errors expected")
                    .isZero();
        }

        @Test
        @DisplayName("Concurrent storeFile calls produce distinct files")
        void concurrent_storeFileProducesDistinctFiles() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            Set<Path> paths = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        MockMultipartFile file = new MockMultipartFile(
                                "file", "doc.pdf", "application/pdf", "content".getBytes());
                        Path relative = service.storeFile(file, FileType.DOCUMENT, 1L, "doc");
                        paths.add(relative);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            assertThat(errorCount.get()).as("No concurrent store errors expected").isZero();
            // Each thread should have produced a unique path (UUID-based naming)
            assertThat(paths).hasSize(THREAD_COUNT);
        }

        @Test
        @DisplayName("Concurrent reads while writing do not throw exceptions")
        void concurrent_readsDuringWriteDoNotThrow() throws Exception {
            // Pre-store a file to read
            byte[] initialContent = "initial content".getBytes();
            Path relative = service.storeBytes(initialContent, FileType.DOCUMENT, 1L, "shared.pdf");
            String relativePath = relative.toString();

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger readErrors = new AtomicInteger(0);
            AtomicInteger writeErrors = new AtomicInteger(0);

            int readers = THREAD_COUNT / 2;
            int writers = THREAD_COUNT - readers;

            // Submit readers
            for (int i = 0; i < readers; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < ITERATIONS; j++) {
                            service.loadFileAsResource(relativePath);
                        }
                    } catch (Exception e) {
                        readErrors.incrementAndGet();
                    }
                });
            }

            // Submit writers (writing to different files to avoid lock contention on same file)
            for (int i = 0; i < writers; i++) {
                final int writerId = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < ITERATIONS; j++) {
                            service.storeBytes(
                                    ("writer-" + writerId + "-iter-" + j).getBytes(),
                                    FileType.DOCUMENT, 1L,
                                    "writer_" + writerId + "_" + j + ".pdf");
                        }
                    } catch (Exception e) {
                        writeErrors.incrementAndGet();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            assertThat(readErrors.get()).as("No read errors during concurrent writes").isZero();
            assertThat(writeErrors.get()).as("No write errors during concurrent reads").isZero();
        }

        @Test
        @DisplayName("Concurrent overwrites to same file: no partial writes (file size is 0 or full 256 bytes)")
        void concurrent_overwritesSameFilePreserveContent() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);

            // All threads write to the same filename.
            // FileLock serialises writes; on Windows, OverlappingFileLockException may occur
            // when multiple threads try to lock the same file simultaneously.
            // The key invariant: the file must never contain a partial write (i.e., size must
            // be either 0 or exactly 256 bytes — never something in between).
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        byte[] content = new byte[256];
                        Arrays.fill(content, (byte) threadId);
                        service.storeBytes(content, FileType.MINUTES, 1L, "overwrite_target.pdf");
                    } catch (Exception ignored) {
                        // OverlappingFileLockException is acceptable on same-file concurrent lock
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // File must exist
            Path target = tempDir.resolve("minutes").resolve("1").resolve("overwrite_target.pdf");
            assertThat(target).exists();

            // File size must be exactly 0 (all writes failed) or exactly 256 (a write succeeded)
            // — never a partial size like 128 bytes
            long fileSize = Files.size(target);
            assertThat(fileSize)
                    .as("File must be 0 (no write succeeded) or 256 bytes (full write) — no partial writes")
                    .isIn(0L, 256L);
        }

        @Test
        @DisplayName("Concurrent getStorageStats calls return consistent non-negative values")
        void concurrent_storageStatsAreConsistent() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        Map<FileType, Long> stats = service.getStorageStats();
                        // All values must be non-negative
                        stats.values().forEach(v -> {
                            if (v < 0) errorCount.incrementAndGet();
                        });
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            assertThat(errorCount.get()).as("All stats values must be non-negative").isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File isolation between meetings
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("File isolation between meetings")
    class FileIsolation {

        @Test
        @DisplayName("Files for different meetings are stored in separate subdirectories")
        void isolation_differentMeetingsUseSeparateDirs() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            Path path1 = service.storeFile(file, FileType.DOCUMENT, 1L, "doc");
            Path path2 = service.storeFile(file, FileType.DOCUMENT, 2L, "doc");

            assertThat(path1.toString()).contains("1");
            assertThat(path2.toString()).contains("2");
            assertThat(path1.getParent()).isNotEqualTo(path2.getParent());
        }

        @Test
        @DisplayName("Deleting a file from one meeting does not affect another meeting's files")
        void isolation_deletionDoesNotAffectOtherMeetings() throws IOException {
            byte[] content = "shared content".getBytes();
            Path path1 = service.storeBytes(content, FileType.DOCUMENT, 1L, "file.pdf");
            Path path2 = service.storeBytes(content, FileType.DOCUMENT, 2L, "file.pdf");

            service.deleteFile(path1.toString());

            assertThat(tempDir.resolve(path1)).doesNotExist();
            assertThat(tempDir.resolve(path2)).exists();
        }
    }
}

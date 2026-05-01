package com.example.kolla.integration;

import com.example.kolla.enums.MeetingMode;
import com.example.kolla.enums.MeetingStatus;
import com.example.kolla.enums.Role;
import com.example.kolla.enums.TranscriptionJobStatus;
import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.models.Department;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Room;
import com.example.kolla.models.TranscriptionJob;
import com.example.kolla.models.User;
import com.example.kolla.repositories.DepartmentRepository;
import com.example.kolla.repositories.MeetingRepository;
import com.example.kolla.repositories.RoomRepository;
import com.example.kolla.repositories.TranscriptionJobRepository;
import com.example.kolla.repositories.UserRepository;
import com.example.kolla.services.TranscriptionQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration tests for Redis Sorted Set queue operations.
 *
 * Uses a real Redis container to verify:
 * - HIGH_PRIORITY job score > NORMAL_PRIORITY job score
 * - Pop returns highest-priority job first
 * - Queue depth increases/decreases correctly
 * - Older HIGH_PRIORITY job has higher score than newer one (FIFO within priority)
 * - requeuePendingJobs pushes all PENDING jobs back to queue
 *
 * Requirements: 8.10, 20.2
 */
@SpringBootTest
@ActiveProfiles("tc-test")
@Testcontainers(disabledWithoutDocker = true)
class RedisQueueTcIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("kolla_test")
            .withUsername("kolla")
            .withPassword("kolla_pass");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // ── Injected beans ────────────────────────────────────────────────────────

    @Autowired
    private TranscriptionQueueService queueService;

    @Autowired
    private TranscriptionJobRepository jobRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${transcription.queue.sorted-set-key:tc-transcription-queue}")
    private String sortedSetKey;

    // ── Test state ────────────────────────────────────────────────────────────

    private Meeting testMeeting;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Clean up Redis queue before each test
        redisTemplate.delete(sortedSetKey);

        long ts = System.nanoTime();

        User admin = userRepository.save(User.builder()
                .username("redis_tc_admin_" + ts)
                .email("redis_tc_admin_" + ts + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .fullName("Redis TC Admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build());

        User secretary = userRepository.save(User.builder()
                .username("redis_tc_sec_" + ts)
                .email("redis_tc_sec_" + ts + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .fullName("Redis TC Secretary")
                .role(Role.SECRETARY)
                .isActive(true)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .name("Redis TC Dept " + ts)
                .description("Redis TC test dept")
                .build());

        Room room = roomRepository.save(Room.builder()
                .name("Redis TC Room " + ts)
                .capacity(10)
                .department(dept)
                .build());

        testMeeting = meetingRepository.save(Meeting.builder()
                .code("REDIS" + ts)
                .title("Redis TC Test Meeting")
                .description("Redis Testcontainers integration test")
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .room(room)
                .creator(admin)
                .host(admin)
                .secretary(secretary)
                .status(MeetingStatus.ACTIVE)
                .mode(MeetingMode.FREE_MODE)
                .transcriptionPriority(TranscriptionPriority.NORMAL_PRIORITY)
                .build());
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Priority ordering")
    class PriorityOrdering {

        @Test
        @DisplayName("HIGH_PRIORITY job has higher score than NORMAL_PRIORITY job")
        void highPriorityScoreIsHigherThanNormal() {
            long now = System.currentTimeMillis();
            double highScore = queueService.computeScore(TranscriptionPriority.HIGH_PRIORITY, now);
            double normalScore = queueService.computeScore(TranscriptionPriority.NORMAL_PRIORITY, now);

            assertThat(highScore).isGreaterThan(normalScore);
        }

        @Test
        @DisplayName("Older HIGH_PRIORITY job has higher score than newer HIGH_PRIORITY job (FIFO)")
        void olderHighPriorityJobHasHigherScore() {
            long earlier = System.currentTimeMillis() - 5000;
            long later = System.currentTimeMillis();

            double olderScore = queueService.computeScore(TranscriptionPriority.HIGH_PRIORITY, earlier);
            double newerScore = queueService.computeScore(TranscriptionPriority.HIGH_PRIORITY, later);

            // Older job should have higher score (processed first within HIGH_PRIORITY)
            assertThat(olderScore).isGreaterThan(newerScore);
        }

        @Test
        @DisplayName("Score is deterministic for same inputs")
        void scoreIsDeterministic() {
            long ts = 1_700_000_000_000L;
            double s1 = queueService.computeScore(TranscriptionPriority.HIGH_PRIORITY, ts);
            double s2 = queueService.computeScore(TranscriptionPriority.HIGH_PRIORITY, ts);

            assertThat(s1).isEqualTo(s2);
        }
    }

    // ── Push job ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pushJob()")
    class PushJob {

        @Test
        @DisplayName("Queue depth increases after pushing a job")
        void queueDepthIncreasesAfterPush() {
            long depthBefore = queueService.getQueueDepth();

            TranscriptionJob job = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());
            queueService.pushJob(job);

            assertThat(queueService.getQueueDepth()).isEqualTo(depthBefore + 1);
        }

        @Test
        @DisplayName("Job status is updated to QUEUED after push")
        void jobStatusIsQueuedAfterPush() {
            TranscriptionJob job = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());
            queueService.pushJob(job);

            TranscriptionJob updated = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TranscriptionJobStatus.QUEUED);
            assertThat(updated.getQueuedAt()).isNotNull();
        }

        @Test
        @DisplayName("HIGH_PRIORITY job is added to the sorted set with higher score than NORMAL")
        void highPriorityJobHasHigherScoreInRedis() {
            TranscriptionJob normalJob = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());
            TranscriptionJob highJob = buildAndSaveJob(TranscriptionPriority.HIGH_PRIORITY,
                    LocalDateTime.now());

            queueService.pushJob(normalJob);
            queueService.pushJob(highJob);

            Double normalScore = redisTemplate.opsForZSet().score(sortedSetKey, normalJob.getId());
            Double highScore = redisTemplate.opsForZSet().score(sortedSetKey, highJob.getId());

            assertThat(normalScore).isNotNull();
            assertThat(highScore).isNotNull();
            assertThat(highScore).isGreaterThan(normalScore);
        }
    }

    // ── Pop job ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("popHighestPriorityJobId()")
    class PopJob {

        @Test
        @DisplayName("Returns empty when queue is empty")
        void returnsEmptyWhenQueueEmpty() {
            Optional<String> result = queueService.popHighestPriorityJobId();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Pop returns the HIGH_PRIORITY job before NORMAL_PRIORITY job")
        void popReturnsHighPriorityFirst() {
            TranscriptionJob normalJob = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now().minusSeconds(10));
            TranscriptionJob highJob = buildAndSaveJob(TranscriptionPriority.HIGH_PRIORITY,
                    LocalDateTime.now());

            queueService.pushJob(normalJob);
            queueService.pushJob(highJob);

            Optional<String> popped = queueService.popHighestPriorityJobId();

            assertThat(popped).isPresent();
            assertThat(popped.get()).isEqualTo(highJob.getId());
        }

        @Test
        @DisplayName("Queue depth decreases after pop")
        void queueDepthDecreasesAfterPop() {
            TranscriptionJob job = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());
            queueService.pushJob(job);

            long depthBefore = queueService.getQueueDepth();
            queueService.popHighestPriorityJobId();

            assertThat(queueService.getQueueDepth()).isEqualTo(depthBefore - 1);
        }

        @Test
        @DisplayName("Among HIGH_PRIORITY jobs, older job is popped first")
        void olderHighPriorityJobPoppedFirst() throws InterruptedException {
            TranscriptionJob olderJob = buildAndSaveJob(TranscriptionPriority.HIGH_PRIORITY,
                    LocalDateTime.now().minusSeconds(5));
            Thread.sleep(10);
            TranscriptionJob newerJob = buildAndSaveJob(TranscriptionPriority.HIGH_PRIORITY,
                    LocalDateTime.now());

            queueService.pushJob(olderJob);
            queueService.pushJob(newerJob);

            Optional<String> popped = queueService.popHighestPriorityJobId();

            assertThat(popped).isPresent();
            assertThat(popped.get()).isEqualTo(olderJob.getId());
        }
    }

    // ── requeuePendingJobs ────────────────────────────────────────────────────

    @Nested
    @DisplayName("requeuePendingJobs()")
    class RequeuePendingJobs {

        @Test
        @DisplayName("All PENDING jobs are pushed back to the queue")
        void allPendingJobsAreRequeued() {
            // Create PENDING jobs (not pushed to Redis)
            TranscriptionJob job1 = buildAndSaveJob(TranscriptionPriority.HIGH_PRIORITY,
                    LocalDateTime.now().minusSeconds(10));
            TranscriptionJob job2 = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());

            // Ensure they are PENDING (not in Redis)
            assertThat(queueService.getQueueDepth()).isEqualTo(0);

            // Requeue
            queueService.requeuePendingJobs(List.of(job1, job2));

            assertThat(queueService.getQueueDepth()).isEqualTo(2);

            // Both jobs should now be QUEUED in DB
            assertThat(jobRepository.findById(job1.getId()).orElseThrow().getStatus())
                    .isEqualTo(TranscriptionJobStatus.QUEUED);
            assertThat(jobRepository.findById(job2.getId()).orElseThrow().getStatus())
                    .isEqualTo(TranscriptionJobStatus.QUEUED);
        }

        @Test
        @DisplayName("requeuePendingJobs with empty list is a no-op")
        void emptyListIsNoOp() {
            long depthBefore = queueService.getQueueDepth();
            queueService.requeuePendingJobs(List.of());
            assertThat(queueService.getQueueDepth()).isEqualTo(depthBefore);
        }
    }

    // ── removeJob ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeJob()")
    class RemoveJob {

        @Test
        @DisplayName("Queue depth decreases after removing a job")
        void queueDepthDecreasesAfterRemove() {
            TranscriptionJob job = buildAndSaveJob(TranscriptionPriority.NORMAL_PRIORITY,
                    LocalDateTime.now());
            queueService.pushJob(job);

            long depthBefore = queueService.getQueueDepth();
            queueService.removeJob(job.getId());

            assertThat(queueService.getQueueDepth()).isEqualTo(depthBefore - 1);
        }

        @Test
        @DisplayName("Removing a non-existent job is a no-op")
        void removingNonExistentJobIsNoOp() {
            long depthBefore = queueService.getQueueDepth();
            queueService.removeJob("non-existent-job-id");
            assertThat(queueService.getQueueDepth()).isEqualTo(depthBefore);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TranscriptionJob buildAndSaveJob(TranscriptionPriority priority,
                                              LocalDateTime createdAt) {
        TranscriptionJob job = TranscriptionJob.builder()
                .id(UUID.randomUUID().toString())
                .meeting(testMeeting)
                .speakerId(1L)
                .speakerName("Test Speaker")
                .speakerTurnId(UUID.randomUUID().toString())
                .sequenceNumber(1)
                .priority(priority)
                .status(TranscriptionJobStatus.PENDING)
                .audioPath("/test/audio.wav")
                .retryCount(0)
                .createdAt(createdAt)
                .build();
        return jobRepository.save(job);
    }
}

package com.example.kolla.integration;

import com.example.kolla.services.RaiseHandQueueEntry;
import com.example.kolla.services.RaiseHandQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration tests for the Redis raise-hand queue.
 */
@SpringBootTest
@ActiveProfiles("tc-test")
@Testcontainers(disabledWithoutDocker = true)
class RaiseHandQueueTcIntegrationTest {

    private static final long MEETING_ID = 42L;

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

    @Autowired
    private RaiseHandQueueService raiseHandQueueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearQueue() {
        raiseHandQueueService.clearAll(MEETING_ID);
    }

    @Test
    @DisplayName("enqueue and listPendingOrdered return FIFO order")
    void enqueueAndList_fifoOrder() {
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 28, 10, 0, 0);
        LocalDateTime t2 = t1.plusMinutes(1);
        LocalDateTime t3 = t1.plusMinutes(2);

        raiseHandQueueService.enqueue(MEETING_ID, 1L, "Alice", t1);
        raiseHandQueueService.enqueue(MEETING_ID, 2L, "Bob", t2);
        raiseHandQueueService.enqueue(MEETING_ID, 3L, "Carol", t3);

        List<RaiseHandQueueEntry> pending = raiseHandQueueService.listPendingOrdered(MEETING_ID);

        assertThat(pending).hasSize(3);
        assertThat(pending.get(0).userId()).isEqualTo(1L);
        assertThat(pending.get(0).userName()).isEqualTo("Alice");
        assertThat(pending.get(1).userId()).isEqualTo(2L);
        assertThat(pending.get(2).userId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("hasPending and remove work idempotently")
    void hasPendingAndRemove() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 11, 0, 0);
        raiseHandQueueService.enqueue(MEETING_ID, 5L, "Eve", now);

        assertThat(raiseHandQueueService.hasPending(MEETING_ID, 5L)).isTrue();

        raiseHandQueueService.remove(MEETING_ID, 5L);
        assertThat(raiseHandQueueService.hasPending(MEETING_ID, 5L)).isFalse();

        raiseHandQueueService.remove(MEETING_ID, 5L);
        assertThat(raiseHandQueueService.listPendingOrdered(MEETING_ID)).isEmpty();
    }

    @Test
    @DisplayName("clearAll deletes Redis keys")
    void clearAllRemovesKeys() {
        raiseHandQueueService.enqueue(
                MEETING_ID, 9L, "Ivan",
                LocalDateTime.of(2026, 5, 28, 12, 0, 0));

        raiseHandQueueService.clearAll(MEETING_ID);

        assertThat(raiseHandQueueService.listPendingOrdered(MEETING_ID)).isEmpty();
        assertThat(Boolean.TRUE.equals(
                redisTemplate.hasKey("meeting:" + MEETING_ID + ":raise_hand"))).isFalse();
        assertThat(Boolean.TRUE.equals(
                redisTemplate.hasKey("meeting:" + MEETING_ID + ":raise_hand:meta"))).isFalse();
    }
}

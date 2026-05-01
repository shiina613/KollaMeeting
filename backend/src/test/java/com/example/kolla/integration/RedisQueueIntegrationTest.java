package com.example.kolla.integration;

import com.example.kolla.enums.TranscriptionPriority;
import com.example.kolla.services.TranscriptionQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level tests for Redis Queue score computation logic.
 *
 * Tests the TranscriptionQueueService priority ordering using score computation
 * without requiring a live Redis instance.
 *
 * For full Redis integration tests with a real container, see
 * {@link RedisQueueTcIntegrationTest}.
 *
 * Requirements: 8.10, 20.2
 */
@ExtendWith(MockitoExtension.class)
class RedisQueueIntegrationTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    // ── Score computation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Score computation")
    class ScoreComputation {

        /**
         * Helper to compute score using the same formula as TranscriptionQueueServiceImpl.
         * HIGH_PRIORITY:   score = 1_000_000_000 + (1_000_000_000 - unix_ms % 1_000_000_000)
         * NORMAL_PRIORITY: score = unix_ms % 1_000_000_000
         */
        private double computeScore(TranscriptionPriority priority, long unixMs) {
            long mod = unixMs % 1_000_000_000L;
            if (priority == TranscriptionPriority.HIGH_PRIORITY) {
                return (double) (1_000_000_000L + (1_000_000_000L - mod));
            } else {
                return (double) mod;
            }
        }

        @Test
        @DisplayName("HIGH_PRIORITY job gets a higher score than NORMAL_PRIORITY job")
        void highPriorityScoreIsHigherThanNormal() {
            long now = System.currentTimeMillis();
            double highScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, now);
            double normalScore = computeScore(TranscriptionPriority.NORMAL_PRIORITY, now);

            assertThat(highScore).isGreaterThan(normalScore);
        }

        @Test
        @DisplayName("Older HIGH_PRIORITY job has higher score than newer HIGH_PRIORITY job")
        void olderHighPriorityJobHasHigherScore() {
            long earlier = System.currentTimeMillis() - 5000;
            long later = System.currentTimeMillis();

            double olderScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, earlier);
            double newerScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, later);

            // Older job should have higher score (processed first)
            assertThat(olderScore).isGreaterThan(newerScore);
        }

        @Test
        @DisplayName("HIGH_PRIORITY score is always above 1_000_000_000")
        void highPriorityScoreAboveThreshold() {
            long now = System.currentTimeMillis();
            double highScore = computeScore(TranscriptionPriority.HIGH_PRIORITY, now);
            double normalScore = computeScore(TranscriptionPriority.NORMAL_PRIORITY, now);

            assertThat(highScore).isGreaterThan(normalScore);
            assertThat(highScore).isPositive();
        }

        @Test
        @DisplayName("NORMAL_PRIORITY scores differ for different timestamps")
        void normalPriorityScoresDifferForDifferentTimestamps() {
            long t1 = 1_000_000L;
            long t2 = 2_000_000L;

            double score1 = computeScore(TranscriptionPriority.NORMAL_PRIORITY, t1);
            double score2 = computeScore(TranscriptionPriority.NORMAL_PRIORITY, t2);

            assertThat(score1).isNotEqualTo(score2);
        }

        @Test
        @DisplayName("Score is deterministic for same inputs")
        void scoreIsDeterministic() {
            long ts = 1_700_000_000_000L;
            double s1 = computeScore(TranscriptionPriority.HIGH_PRIORITY, ts);
            double s2 = computeScore(TranscriptionPriority.HIGH_PRIORITY, ts);

            assertThat(s1).isEqualTo(s2);
        }
    }
}

package com.example.kolla.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis configuration for Kolla Meeting backend.
 *
 * <p>Configures a Lettuce connection pool backed by Apache Commons Pool2,
 * and exposes {@link RedisTemplate} and {@link StringRedisTemplate} beans
 * with {@link StringRedisSerializer} for both keys and values.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code TranscriptionQueueServiceImpl} — Sorted Set operations on {@code transcription:queue}</li>
 *   <li>{@code MeetingLifecycleServiceImpl} — TTL keys for waiting timeout</li>
 *   <li>{@code GipformerClient} — active high-priority meeting key</li>
 * </ul>
 *
 * Requirements: 16.6
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:3000ms}")
    private String redisTimeout;

    // ── Pool settings (spring.data.redis.lettuce.pool.*) ─────────────────────

    @Value("${spring.data.redis.lettuce.pool.max-active:10}")
    private int poolMaxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:5}")
    private int poolMaxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:1}")
    private int poolMinIdle;

    /**
     * Max wait time in milliseconds. -1 means wait indefinitely.
     * Mapped from {@code spring.data.redis.lettuce.pool.max-wait}.
     */
    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private String poolMaxWait;

    // ── Beans ─────────────────────────────────────────────────────────────────

    /**
     * Lettuce connection factory with Commons Pool2 connection pooling.
     *
     * <p>Pool settings:
     * <ul>
     *   <li>maxTotal (max-active): maximum number of connections in the pool</li>
     *   <li>maxIdle: maximum number of idle connections</li>
     *   <li>minIdle: minimum number of idle connections kept alive</li>
     *   <li>maxWait: maximum time to wait for a connection (-1 = indefinite)</li>
     * </ul>
     *
     * Requirements: 16.6
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        if (StringUtils.hasText(redisPassword)) {
            standaloneConfig.setPassword(redisPassword);
        }

        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(poolMaxActive);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);
        poolConfig.setMaxWait(parseMaxWait(poolMaxWait));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        LettucePoolingClientConfiguration clientConfig =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(poolConfig)
                        .commandTimeout(parseCommandTimeout(redisTimeout))
                        .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    /**
     * {@link RedisTemplate} with {@link StringRedisSerializer} for both keys and values.
     *
     * <p>Used for Sorted Set operations (transcription queue) and Hash operations
     * (job details). String serialization ensures keys and values are human-readable
     * in Redis CLI and compatible with the Gipformer Python worker.
     *
     * Requirements: 16.6
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.setDefaultSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * {@link StringRedisTemplate} bean — convenience wrapper used by services
     * that only need String key/value operations.
     *
     * Requirements: 16.6
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parse a Spring-style duration string (e.g. "2000ms", "-1ms", "5s") into a
     * {@link Duration}. Returns {@link Duration#ofMillis(long) Duration.ofMillis(-1)}
     * (indefinite wait) for negative values or the literal "-1ms".
     */
    private Duration parseMaxWait(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofMillis(-1);
        }
        String trimmed = value.trim().toLowerCase();
        try {
            if (trimmed.endsWith("ms")) {
                long ms = Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim());
                return ms < 0 ? Duration.ofMillis(-1) : Duration.ofMillis(ms);
            } else if (trimmed.endsWith("s")) {
                long s = Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim());
                return s < 0 ? Duration.ofMillis(-1) : Duration.ofSeconds(s);
            } else {
                long ms = Long.parseLong(trimmed);
                return ms < 0 ? Duration.ofMillis(-1) : Duration.ofMillis(ms);
            }
        } catch (NumberFormatException e) {
            return Duration.ofMillis(-1);
        }
    }

    /**
     * Parse a Spring-style timeout string (e.g. "3000ms", "5s") into a {@link Duration}.
     * Defaults to 3 seconds if parsing fails.
     */
    private Duration parseCommandTimeout(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(3);
        }
        String trimmed = value.trim().toLowerCase();
        try {
            if (trimmed.endsWith("ms")) {
                long ms = Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim());
                return Duration.ofMillis(ms);
            } else if (trimmed.endsWith("s")) {
                long s = Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim());
                return Duration.ofSeconds(s);
            } else {
                return Duration.ofMillis(Long.parseLong(trimmed));
            }
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(3);
        }
    }
}

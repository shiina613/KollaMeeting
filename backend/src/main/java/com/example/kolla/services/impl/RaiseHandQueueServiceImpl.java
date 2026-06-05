package com.example.kolla.services.impl;

import com.example.kolla.services.RaiseHandQueueEntry;
import com.example.kolla.services.RaiseHandQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis ZSET + HASH implementation of the raise-hand queue.
 *
 * <p>Keys per meeting:
 * <ul>
 *   <li>{@code meeting:{id}:raise_hand} — ZSET (member=userId, score=epoch ms)</li>
 *   <li>{@code meeting:{id}:raise_hand:meta} — HASH (field=userId, JSON metadata)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaiseHandQueueServiceImpl implements RaiseHandQueueService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long TTL_SECONDS = 86_400L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${raise-hand.queue.key-prefix:meeting}")
    private String keyPrefix;

    @Override
    public void enqueue(Long meetingId, Long userId, String userName, LocalDateTime requestedAt) {
        String zsetKey = zsetKey(meetingId);
        String metaKey = metaKey(meetingId);
        String member = userId.toString();
        double score = toEpochMillis(requestedAt);

        redisTemplate.opsForZSet().add(zsetKey, member, score);
        redisTemplate.opsForHash().put(metaKey, member, serializeMeta(userName, requestedAt));

        redisTemplate.expire(zsetKey, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(metaKey, TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("Enqueued raise-hand userId={} for meetingId={}", userId, meetingId);
    }

    @Override
    public boolean hasPending(Long meetingId, Long userId) {
        Double score = redisTemplate.opsForZSet().score(zsetKey(meetingId), userId.toString());
        return score != null;
    }

    @Override
    public void remove(Long meetingId, Long userId) {
        String member = userId.toString();
        redisTemplate.opsForZSet().remove(zsetKey(meetingId), member);
        redisTemplate.opsForHash().delete(metaKey(meetingId), member);
        log.debug("Removed raise-hand userId={} from meetingId={}", userId, meetingId);
    }

    @Override
    public List<RaiseHandQueueEntry> listPendingOrdered(Long meetingId) {
        String zsetKey = zsetKey(meetingId);
        String metaKey = metaKey(meetingId);

        Set<String> members = redisTemplate.opsForZSet().range(zsetKey, 0, -1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        List<RaiseHandQueueEntry> result = new ArrayList<>(members.size());
        for (String member : members) {
            Long userId = Long.parseLong(member);
            Object rawMeta = redisTemplate.opsForHash().get(metaKey, member);
            if (rawMeta == null) {
                Double score = redisTemplate.opsForZSet().score(zsetKey, member);
                LocalDateTime requestedAt = score != null
                        ? LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(score.longValue()), VN_ZONE)
                        : LocalDateTime.now();
                result.add(new RaiseHandQueueEntry(userId, "User " + userId, requestedAt));
                continue;
            }
            result.add(deserializeEntry(userId, rawMeta.toString()));
        }
        return result;
    }

    @Override
    public void clearAll(Long meetingId) {
        redisTemplate.delete(List.of(zsetKey(meetingId), metaKey(meetingId)));
        log.debug("Cleared raise-hand queue for meetingId={}", meetingId);
    }

    private String zsetKey(Long meetingId) {
        return keyPrefix + ":" + meetingId + ":raise_hand";
    }

    private String metaKey(Long meetingId) {
        return keyPrefix + ":" + meetingId + ":raise_hand:meta";
    }

    private static double toEpochMillis(LocalDateTime requestedAt) {
        return requestedAt.atZone(VN_ZONE).toInstant().toEpochMilli();
    }

    private String serializeMeta(String userName, LocalDateTime requestedAt) {
        try {
            return objectMapper.writeValueAsString(
                    new MetaPayload(userName, requestedAt.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize raise-hand meta", e);
        }
    }

    private RaiseHandQueueEntry deserializeEntry(Long userId, String json) {
        try {
            MetaPayload payload = objectMapper.readValue(json, MetaPayload.class);
            LocalDateTime requestedAt = LocalDateTime.parse(payload.requestedAt());
            return new RaiseHandQueueEntry(userId, payload.userName(), requestedAt);
        } catch (Exception e) {
            log.warn("Invalid raise-hand meta for userId={}: {}", userId, json, e);
            return new RaiseHandQueueEntry(userId, "User " + userId, LocalDateTime.now());
        }
    }

    private record MetaPayload(String userName, String requestedAt) {
    }
}

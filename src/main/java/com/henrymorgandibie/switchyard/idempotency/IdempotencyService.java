package com.henrymorgandibie.switchyard.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * The Redis fast-path cache for idempotent responses: given an idempotency key, either the
 * exact response bytes already sent for it (a cache hit - safe to replay without reprocessing)
 * or nothing. Postgres (the {@code transactions} table's unique {@code idempotency_key} index)
 * remains the durable, authoritative source of truth for whether a transaction is a duplicate;
 * this class is purely an optimization to avoid a database round trip on a hot duplicate burst.
 *
 * <p>If Redis is unreachable, every method here degrades to a no-op (logged, not thrown) rather
 * than failing the request - the caller falls through to the Postgres unique-constraint path and
 * still behaves correctly, just slower. This is not a hypothetical: the golden-path milestone's
 * end-to-end test deliberately does not provision Redis, so this degradation path is exercised
 * for real by that test on every run, not just described here.
 */
public final class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idem:";

    private final StringRedisTemplate redisTemplate;
    private final Duration cacheTtl;

    public IdempotencyService(StringRedisTemplate redisTemplate, Duration cacheTtl) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtl;
    }

    public Optional<byte[]> checkCache(String idempotencyKey) {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
            return Optional.ofNullable(cached).map(Base64.getDecoder()::decode);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for idempotency cache lookup, falling through to Postgres: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    public void cacheResponse(String idempotencyKey, byte[] responseBytes) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + idempotencyKey, Base64.getEncoder().encodeToString(responseBytes), cacheTtl);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for idempotency cache write, response was not cached: {}", e.getMessage());
        }
    }
}

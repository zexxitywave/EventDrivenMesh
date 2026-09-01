package com.hacisimsek.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Redis-backed access token blacklist.
 *
 * On logout, the access token's JTI (or the token itself hashed) is stored
 * in Redis with a TTL matching the token's remaining lifetime. The gateway
 * checks this blacklist on every authenticated request.
 *
 * Key pattern: "blacklist:<jti>"  →  "revoked"
 * TTL = token expiry - now  (so Redis auto-cleans expired entries)
 *
 * Why not store the full token? The JTI (JWT ID) claim uniquely identifies
 * the token and is much smaller. We add JTI to every generated token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Blacklist an access token by its JTI until it naturally expires.
     *
     * @param jti       the jti claim from the JWT (unique token ID)
     * @param expiresAt the token's expiry time — used to set Redis TTL
     */
    public void blacklist(String jti, Date expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt.toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            // Token is already expired — no need to blacklist
            return;
        }
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "revoked", ttl);
        log.debug("Blacklisted token jti={} for {}s", jti, ttl.toSeconds());
    }

    /**
     * Returns true if the token identified by this JTI has been blacklisted.
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}

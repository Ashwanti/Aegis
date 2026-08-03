package com.aegisteam.aegis.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Refresh-token revocation via Redis, per playbook 1.3: "store invalidated
 * refresh tokens in Redis with TTL = remaining lifetime."
 *
 * <p>This is a blocklist, not a whitelist — most refresh tokens are never
 * written here at all. A key only appears once its token has been rotated
 * (used to mint a new pair) or explicitly logged out. Setting the Redis TTL
 * to the token's own remaining lifetime means the key evicts itself the
 * moment the token would have expired anyway, so the blocklist never grows
 * unbounded.
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:blocklist:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return; // already expired on its own; nothing to block
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "revoked", ttl);
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}

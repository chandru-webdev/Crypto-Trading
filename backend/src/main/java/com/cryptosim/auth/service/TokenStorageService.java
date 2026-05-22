package com.cryptosim.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Stores short-lived single-use tokens (email verification, password reset) and
 * tracks refresh-token JTIs for rotation/revocation. Backed by Redis TTL.
 *
 * Key shapes:
 *   auth:verify:{token}    -> userId    (TTL: emailVerificationTtl)
 *   auth:reset:{token}     -> userId    (TTL: passwordResetTtl)
 *   auth:refresh:{jti}     -> userId    (TTL: refresh token expiry)
 */
@Service
@RequiredArgsConstructor
public class TokenStorageService {

    private static final String VERIFY_PREFIX  = "auth:verify:";
    private static final String RESET_PREFIX   = "auth:reset:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final RedisTemplate<String, String> redis;
    private final SecureRandom random = new SecureRandom();

    // -------- email verification --------

    public String createVerificationToken(Long userId, long ttlMinutes) {
        String token = randomToken();
        redis.opsForValue().set(VERIFY_PREFIX + token, String.valueOf(userId),
                ttlMinutes, TimeUnit.MINUTES);
        return token;
    }

    public Optional<Long> consumeVerificationToken(String token) {
        String key = VERIFY_PREFIX + token;
        String val = redis.opsForValue().get(key);
        if (val == null) return Optional.empty();
        redis.delete(key); // single-use
        return Optional.of(Long.parseLong(val));
    }

    // -------- password reset --------

    public String createPasswordResetToken(Long userId, long ttlMinutes) {
        String token = randomToken();
        redis.opsForValue().set(RESET_PREFIX + token, String.valueOf(userId),
                ttlMinutes, TimeUnit.MINUTES);
        return token;
    }

    public Optional<Long> consumePasswordResetToken(String token) {
        String key = RESET_PREFIX + token;
        String val = redis.opsForValue().get(key);
        if (val == null) return Optional.empty();
        redis.delete(key); // single-use
        return Optional.of(Long.parseLong(val));
    }

    // -------- refresh tokens (rotation) --------

    public void storeRefreshJti(String jti, Long userId, long ttlSeconds) {
        redis.opsForValue().set(REFRESH_PREFIX + jti, String.valueOf(userId),
                ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isRefreshJtiActive(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_PREFIX + jti));
    }

    public void revokeRefreshJti(String jti) {
        redis.delete(REFRESH_PREFIX + jti);
    }

    // -------- internals --------

    private String randomToken() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}

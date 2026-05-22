package com.cryptosim.auth.service;

import com.cryptosim.common.exception.InvalidTokenException;
import com.cryptosim.config.JwtProperties;
import com.cryptosim.user.entity.Role;
import com.cryptosim.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Issues + parses JWTs. We issue two kinds:
 *   - access  : short-lived, sent in Authorization header
 *   - refresh : longer-lived, used only to rotate a new access token
 *
 * The refresh token is also stored server-side (Redis) so we can revoke it on rotation.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;

    private SecretKey key() {
        // secret is base64-encoded — decode to raw bytes so length checks make sense
        byte[] bytes = java.util.Base64.getDecoder().decode(props.getSecret());
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.getAccessTokenTtlMinutes()));
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        CLAIM_TYPE, TYPE_ACCESS,
                        CLAIM_ROLE, user.getRole().name(),
                        "email", user.getEmail()
                ))
                .id(UUID.randomUUID().toString())
                .signWith(key())
                .compact();
    }

    /**
     * Generates a refresh token. The returned tokenId (jti) should be persisted
     * in Redis so it can be revoked when rotated or on logout.
     */
    public RefreshToken generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofDays(props.getRefreshTokenTtlDays()));
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(CLAIM_TYPE, TYPE_REFRESH))
                .id(jti)
                .signWith(key())
                .compact();
        return new RefreshToken(token, jti, exp);
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parse(token, Claims::getSubject));
    }

    public String extractJti(String token) {
        return parse(token, Claims::getId);
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parse(token, c -> c.get(CLAIM_TYPE, String.class)));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parse(token, c -> c.get(CLAIM_TYPE, String.class)));
    }

    public Role extractRole(String token) {
        String role = parse(token, c -> c.get(CLAIM_ROLE, String.class));
        return role == null ? null : Role.valueOf(role);
    }

    /**
     * Validates signature + expiry, throws {@link InvalidTokenException} on failure.
     */
    public void validate(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    private <T> T parse(String token, Function<Claims, T> resolver) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return resolver.apply(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    public record RefreshToken(String token, String jti, Instant expiresAt) {}
}

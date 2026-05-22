package com.cryptosim.auth.service;

import com.cryptosim.auth.dto.*;
import com.cryptosim.common.exception.ApiException;
import com.cryptosim.common.exception.EmailAlreadyExistsException;
import com.cryptosim.common.exception.InvalidCredentialsException;
import com.cryptosim.common.exception.InvalidTokenException;
import com.cryptosim.common.exception.ResourceNotFoundException;
import com.cryptosim.config.AuthProperties;
import com.cryptosim.config.JwtProperties;
import com.cryptosim.user.entity.Role;
import com.cryptosim.user.entity.User;
import com.cryptosim.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Orchestrates registration, login, refresh-token rotation, email verification,
 * and password reset. Persisted state lives in MySQL (users); short-lived single-use
 * tokens live in Redis with TTLs (see {@link TokenStorageService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Per the project spec, every new user starts with $10,000 of virtual money. */
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("10000.00000000");

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final TokenStorageService tokens;
    private final EmailService email;
    private final AuthProperties authProps;
    private final JwtProperties jwtProps;

    // ============================================================================
    // Registration
    // ============================================================================

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException(req.email());
        }

        User user = User.builder()
                .email(req.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .virtualBalance(STARTING_BALANCE)
                .initialBalance(STARTING_BALANCE)
                .emailVerified(false)
                .enabled(true)  // allow login even before verification; gate sensitive ops if needed
                .build();
        userRepo.save(user);

        // Email-verification token: random opaque string in Redis (15-min TTL).
        String verifyToken = tokens.createVerificationToken(
                user.getId(), authProps.getEmailVerificationTtlMinutes());
        email.sendVerificationEmail(user.getEmail(), verifyToken);

        return issueTokens(user);
    }

    // ============================================================================
    // Login
    // ============================================================================

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Account is disabled");
        }

        return issueTokens(user);
    }

    // ============================================================================
    // Refresh-token rotation
    //
    // Rotation: every time the client uses a refresh token, we revoke that jti
    // and issue a brand-new one. If a stolen token gets used, the legit user's
    // next refresh will fail (jti no longer active) and we can react.
    // ============================================================================

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest req) {
        String token = req.refreshToken();
        jwt.validate(token);
        if (!jwt.isRefreshToken(token)) {
            throw new InvalidTokenException("Not a refresh token");
        }
        String jti = jwt.extractJti(token);
        if (!tokens.isRefreshJtiActive(jti)) {
            throw new InvalidTokenException("Refresh token revoked or expired");
        }
        Long userId = jwt.extractUserId(token);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));

        tokens.revokeRefreshJti(jti);  // single-use; rotate immediately
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        try {
            jwt.validate(refreshToken);
            if (jwt.isRefreshToken(refreshToken)) {
                tokens.revokeRefreshJti(jwt.extractJti(refreshToken));
            }
        } catch (InvalidTokenException ignored) {
            // Already invalid — nothing to revoke.
        }
    }

    // ============================================================================
    // Email verification
    // ============================================================================

    @Transactional
    public void verifyEmail(String token) {
        Long userId = tokens.consumeVerificationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setEmailVerified(true);
        // implicit save via dirty checking inside @Transactional
    }

    @Transactional(readOnly = true)
    public void resendVerification(String emailAddr) {
        User user = userRepo.findByEmail(emailAddr.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified()) {
            return; // already verified — silent no-op
        }
        String token = tokens.createVerificationToken(
                user.getId(), authProps.getEmailVerificationTtlMinutes());
        email.sendVerificationEmail(user.getEmail(), token);
    }

    // ============================================================================
    // Password reset (single-use Redis token)
    // ============================================================================

    public void requestPasswordReset(PasswordResetRequest req) {
        // Always behave the same regardless of whether the email exists,
        // so attackers can't enumerate users.
        userRepo.findByEmail(req.email().toLowerCase()).ifPresent(user -> {
            String token = tokens.createPasswordResetToken(
                    user.getId(), authProps.getPasswordResetTtlMinutes());
            email.sendPasswordResetEmail(user.getEmail(), token);
        });
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest req) {
        Long userId = tokens.consumePasswordResetToken(req.token())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private AuthResponse issueTokens(User user) {
        String access = jwt.generateAccessToken(user);
        JwtService.RefreshToken refresh = jwt.generateRefreshToken(user);

        long refreshTtlSeconds = Duration.between(Instant.now(), refresh.expiresAt()).getSeconds();
        tokens.storeRefreshJti(refresh.jti(), user.getId(), refreshTtlSeconds);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .accessToken(access)
                .refreshToken(refresh.token())
                .accessTokenExpiresInSeconds(jwtProps.getAccessTokenTtlMinutes() * 60)
                .build();
    }
}

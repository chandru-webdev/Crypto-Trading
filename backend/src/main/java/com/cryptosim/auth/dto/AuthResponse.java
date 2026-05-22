package com.cryptosim.auth.dto;

import com.cryptosim.user.entity.Role;
import lombok.Builder;

@Builder
public record AuthResponse(
        Long userId,
        String email,
        Role role,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {}

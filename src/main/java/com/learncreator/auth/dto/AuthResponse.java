package com.learncreator.auth.dto;

import com.learncreator.auth.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        UUID userId,
        String name,
        String email,
        Role role
) {}

package com.learncreator.auth.service;

import com.learncreator.auth.entity.RefreshToken;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-token-expiry-days}")
    private long refreshTokenExpiryDays;

    /**
     * Issues a brand new refresh token for a user (used at login/register).
     * Returns the RAW token — this is the only time it ever exists in plaintext.
     */
    public String issueToken(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiryDate(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Validates an incoming raw refresh token, then ROTATES it:
     * the old one is revoked and a new one is issued in the same call.
     * Rotation means a stolen refresh token is only useful once before
     * the legitimate user's next refresh silently invalidates it.
     */
    @Transactional
    public RotationResult validateAndRotate(String rawToken) {
        String incomingHash = hash(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(incomingHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!existing.isValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String newRawToken = issueToken(existing.getUser());

        return new RotationResult(existing.getUser(), newRawToken);
    }

    public void revokeToken(String rawToken) {
        String incomingHash = hash(rawToken);
        refreshTokenRepository.findByTokenHash(incomingHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(User user, String newRawRefreshToken) {}
}

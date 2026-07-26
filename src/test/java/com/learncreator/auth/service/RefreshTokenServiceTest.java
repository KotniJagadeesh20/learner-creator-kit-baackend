package com.learncreator.auth.service;

import com.learncreator.auth.entity.RefreshToken;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiryDays", 7L);
        user = User.builder().id(UUID.randomUUID()).email("asha@example.com").build();
    }

    @Test
    void issueToken_savesHashedToken_notRawToken() {
        String raw = refreshTokenService.issueToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw); // the hash, never the raw value, is persisted
        assertThat(captor.getValue().isRevoked()).isFalse();
    }

    @Test
    void validateAndRotate_revokesOldToken_andIssuesNewOne() {
        String rawToken = "some-raw-refresh-token";
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .tokenHash(hashFor(rawToken))
                .build();

        when(refreshTokenRepository.findByTokenHash(hashFor(rawToken))).thenReturn(Optional.of(stored));

        var result = refreshTokenService.validateAndRotate(rawToken);

        assertThat(stored.isRevoked()).isTrue(); // old token is dead after rotation
        assertThat(result.newRawRefreshToken()).isNotEqualTo(rawToken); // a genuinely new token was issued
        assertThat(result.user()).isEqualTo(user);
        // save() called at least twice: once to revoke the old, once to persist the new
        verify(refreshTokenRepository, atLeast(2)).save(any());
    }

    @Test
    void validateAndRotate_rejects_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("unknown-token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void validateAndRotate_rejects_whenTokenExpired() {
        String rawToken = "expired-token";
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS)) // already expired
                .revoked(false)
                .tokenHash(hashFor(rawToken))
                .build();

        when(refreshTokenRepository.findByTokenHash(hashFor(rawToken))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired or revoked");
    }

    @Test
    void validateAndRotate_rejects_whenTokenAlreadyRevoked() {
        // Simulates reuse of an already-rotated (stolen) refresh token.
        String rawToken = "reused-token";
        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(true)
                .tokenHash(hashFor(rawToken))
                .build();

        when(refreshTokenRepository.findByTokenHash(hashFor(rawToken))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate(rawToken))
                .isInstanceOf(ResponseStatusException.class);
    }

    // Mirrors RefreshTokenService's private hash() method (SHA-256, URL-safe base64) so tests
    // can construct a RefreshToken row whose hash matches a known raw token.
    private String hashFor(String rawToken) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes());
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

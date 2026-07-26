package com.learncreator.auth.service;

import com.learncreator.auth.dto.LoginRequest;
import com.learncreator.auth.dto.RegisterRequest;
import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(UUID.randomUUID())
                .name("Asha Kumar")
                .email("asha@example.com")
                .passwordHash("hashed-password")
                .role(Role.LEARNER)
                .build();
    }

    @Test
    void register_createsLearnerAndReturnsTokens_whenEmailIsNew() {
        RegisterRequest request = new RegisterRequest("Asha Kumar", "asha@example.com", "supersecret123");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiryMillis()).thenReturn(900_000L);
        when(refreshTokenService.issueToken(any(User.class))).thenReturn("refresh-token");

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.role()).isEqualTo(Role.LEARNER); // every signup starts as LEARNER, never CREATOR/ADMIN
        verify(userRepository).save(argThat(u -> u.getRole() == Role.LEARNER && u.getEmail().equals("asha@example.com")));
    }

    @Test
    void register_rejects_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "supersecret123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_succeeds_withCorrectCredentials() {
        LoginRequest request = new LoginRequest("asha@example.com", "correct-password");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existingUser));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiryMillis()).thenReturn(900_000L);
        when(refreshTokenService.issueToken(existingUser)).thenReturn("refresh-token");

        var response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.userId()).isEqualTo(existingUser.getId());
        // authenticationManager.authenticate() was called — this is what actually checks the password
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void login_rejects_withWrongPassword() {
        LoginRequest request = new LoginRequest("asha@example.com", "wrong-password");
        doThrow(new BadCredentialsException("bad creds")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password");

        verify(userRepository, never()).findByEmail(any());
    }
}

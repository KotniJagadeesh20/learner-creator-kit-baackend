package com.learncreator.auth.service;

import com.learncreator.auth.dto.AuthResponse;
import com.learncreator.auth.dto.LoginRequest;
import com.learncreator.auth.dto.RefreshRequest;
import com.learncreator.auth.dto.RegisterRequest;
import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.LEARNER) // every signup starts as a learner; creator status comes via application + approval
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            // Delegates to Spring Security's provider, which uses PasswordEncoder under the hood
            // to compare the raw password against the stored hash.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult result = refreshTokenService.validateAndRotate(request.refreshToken());
        return buildAuthResponse(result.user(), result.newRawRefreshToken());
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = refreshTokenService.issueToken(user);
        return buildAuthResponse(user, refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiryMillis() / 1000,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}

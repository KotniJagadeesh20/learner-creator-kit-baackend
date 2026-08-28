package com.learncreator.users.dto;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String bio,
        String avatarUrl,
        Role role,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getBio(), user.getAvatarUrl(), user.getRole(), user.getCreatedAt()
        );
    }
}

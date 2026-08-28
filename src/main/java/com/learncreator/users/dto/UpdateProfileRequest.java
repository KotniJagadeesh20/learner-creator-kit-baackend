package com.learncreator.users.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        String name,

        String bio,
        String avatarUrl
) {}

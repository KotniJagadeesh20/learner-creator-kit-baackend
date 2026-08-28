package com.learncreator.enrollments.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollRequest(
        @NotNull(message = "courseId is required")
        UUID courseId
) {}

package com.learncreator.courses.dto;

import jakarta.validation.constraints.NotBlank;

public record LessonRequest(
        @NotBlank(message = "Title is required")
        String title,

        String videoRef,
        Integer durationSeconds,
        int orderIndex
) {}

package com.learncreator.courses.dto;

import com.learncreator.courses.entity.CourseLevel;
import jakarta.validation.constraints.NotBlank;

public record CourseRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description,
        String thumbnailUrl,
        String category,
        CourseLevel level
) {}

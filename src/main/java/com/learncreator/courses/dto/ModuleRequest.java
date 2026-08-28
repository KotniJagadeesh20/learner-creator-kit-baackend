package com.learncreator.courses.dto;

import jakarta.validation.constraints.NotBlank;

public record ModuleRequest(
        @NotBlank(message = "Title is required")
        String title,

        int orderIndex
) {}

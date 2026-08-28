package com.learncreator.creator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatorApplicationRequest(
        @NotBlank(message = "Pitch is required")
        @Size(min = 20, message = "Tell us a bit more — at least 20 characters")
        String pitch
) {}

package com.learncreator.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
        @NotBlank(message = "message is required")
        @Size(max = 4000, message = "message must be at most 4000 characters")
        String message,

        // Lets the frontend offer an explicit "search the whole course" action, rather than
        // relying only on the model detecting an uncovered question.
        boolean forceBroaderSearch
) {}

package com.learncreator.aitutor.dto;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(
        @NotBlank(message = "message is required")
        String message,

        // Lets the frontend offer an explicit "search the whole course" action, rather than
        // relying only on the model detecting an uncovered question.
        boolean forceBroaderSearch
) {}

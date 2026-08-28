package com.learncreator.aitutor.dto;

import com.learncreator.aitutor.entity.AiChatMessage;
import com.learncreator.aitutor.entity.ChatRole;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        ChatRole role,
        String content,
        boolean usedBroaderSearch,
        // Titles of the lesson(s) this answer was actually grounded in (source transparency
        // guardrail). Empty for USER-role messages and whenever nothing relevant was found.
        List<String> sourceLessons,
        Instant createdAt
) {
    public static ChatMessageResponse from(AiChatMessage message) {
        List<String> sourceLessons = message.getSourceLessonTitles() == null || message.getSourceLessonTitles().isBlank()
                ? Collections.emptyList()
                : List.of(message.getSourceLessonTitles().split(",\\s*"));

        return new ChatMessageResponse(
                message.getId(), message.getRole(), message.getContent(),
                message.isUsedBroaderSearch(), sourceLessons, message.getCreatedAt()
        );
    }
}

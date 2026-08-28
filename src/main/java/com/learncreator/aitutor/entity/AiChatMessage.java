package com.learncreator.aitutor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private AiChatThread thread;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    // True when this answer came from the RAG escalation path rather than the default
    // transcript-stuffing path — useful later for analyzing how often escalation actually fires.
    @Column(name = "used_broader_search", nullable = false)
    @Builder.Default
    private boolean usedBroaderSearch = false;

    // Comma-separated lesson titles this answer was grounded in (source transparency guardrail).
    // Persisted rather than recomputed, so reopening a thread later still shows the original
    // citation instead of needing to re-run retrieval just to redisplay history. Null for
    // USER-role messages.
    @Column(name = "source_lesson_titles")
    private String sourceLessonTitles;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

package com.learncreator.aitutor.entity;

import com.learncreator.courses.entity.Lesson;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_transcripts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonTranscript {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    // The whole transcript — this is what gets stuffed into the prompt for the default,
    // non-retrieval path. Chunked/embedded copies of this same text live separately, managed by
    // Spring AI's VectorStore (see EmbeddingIndexService) — not a field on this entity.
    @Column(name = "full_text", columnDefinition = "text")
    private String fullText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TranscriptStatus status = TranscriptStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

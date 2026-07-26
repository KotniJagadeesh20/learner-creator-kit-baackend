# AI Tutor — design reference

**Status: not yet implemented.** This document is the full design decided in planning, meant to be
the starting reference when this module actually gets built. Read this before writing any code for
`ai-tutor`.

---

## 1. What it is

A chat panel inside the lesson player (`CoursePlayerPage` on the frontend already mocks this UI —
see "AI Assistant" panel, currently a hardcoded canned response with no real backend).

**Behavior**: grounded in the current lesson by default, can go broader if asked.
- Default: answer using only the current lesson's transcript.
- Escalation: if the transcript doesn't cover the question (or the learner explicitly asks to look
  further), search across the rest of the module/course and answer citing which lesson it came from.

**Access control**: only learners **enrolled** in the course can use the AI tutor for its lessons —
same enrollment check pattern as the `progress` module.

**Chat history**: persisted per `(learner, lesson)` thread — one thread per pair, append-only, no
editing/deleting, no cross-lesson history view in v1. A learner reopening a lesson later sees their
past Q&A for that lesson.

---

## 2. Why these decisions (recap of the reasoning, so it isn't lost)

| Decision | Choice | Why |
|---|---|---|
| Architecture placement | New module inside the existing Spring Boot monolith, **not** a new microservice | Extract only once real usage proves it's needed — same principle as every other module. This *is* flagged as the most likely future extraction candidate (different profile: external LLM calls, not just DB), but speculative extraction before it's even built isn't justified. |
| Transcription | OpenAI Whisper API | Best-in-class STT; the user explicitly chose "best per piece" over staying single-vendor. |
| Embeddings | OpenAI embeddings | Pairs naturally with Whisper; no requirement that embeddings match the answering LLM's vendor — a vector search doesn't care who generated the vectors. |
| Answering LLM | Anthropic Claude | Long context window (lets the "stuff the whole lesson transcript in" default path stay reliable without falling back to retrieval prematurely) and strong instruction-following for a strict behavioral contract: *answer only from the transcript, say "not covered" rather than guess, only broaden the search when asked.* A wrong confident answer is worse than no AI tutor at all — this is the biggest trust risk for an education product. |
| Vector store | `pgvector` on the existing Postgres RDS | No new infrastructure — reuses the database already running. A dedicated vector DB (Pinecone, OpenSearch, Weaviate) only earns its keep at a scale this project isn't at. |
| Chat message storage | **Postgres**, not DynamoDB (revised from the earlier storage-architecture discussion) | Originally, AI tutor chat history was flagged as the one legitimate DynamoDB candidate. Revised now that `pgvector` is already being added to Postgres for embeddings — introducing DynamoDB *as well* for a second, low-volume data type is more new infrastructure than the actual scale justifies. One database, two purposes (vector search + structured chat rows), beats two databases. If chat volume ever becomes a real problem, that's the point to reconsider — not before. |
| Framework | **Spring AI**, not LangChain4j | First-party `pgvector` integration (`PgVectorStore`), a built-in advisor pattern (`QuestionAnswerAdvisor`) that matches this exact hybrid RAG design, and Spring-native config/DI idioms consistent with every other module in this codebase. LangChain4j's drawbacks for this project specifically: not Spring-native (community starter only), less API stability across versions, heavier abstraction (chains/AI-Service proxies vs. plain callable clients like the rest of this codebase), thinner Java-specific docs, harder to unit-test cleanly with plain Mockito. Multi-provider support (OpenAI + Anthropic) is a config change in Spring AI, not a rewrite. |
| Chunking granularity | Per-lesson, never spanning two lessons | Preserves the ability to cite exactly which lesson an answer came from, and keeps chunk boundaries semantically clean (paragraph/sentence-window chunking within one transcript). |
| Cross-course isolation | Metadata filter on every vector search: `courseId` (+ optionally `moduleId`/`lessonId`) | All courses' chunks live in the same table; the filter is what prevents one creator's course from surfacing in another's search — not separate storage. |

---

## 3. Prerequisite: transcript generation doesn't exist yet

Cloudflare Stream doesn't produce a transcript as part of the upload flow already built in `media`.
This has to be a new async step:

1. Video finishes processing on Cloudflare (`media` module already polls for `readyToStream`).
2. Trigger a background job: pull the video/audio from Cloudflare, send it to OpenAI's Whisper API.
3. Store the resulting transcript text against the `Lesson`.
4. Chunk the transcript, generate embeddings for each chunk, store them in `pgvector`.

**Open technical detail, not yet solved**: Cloudflare Stream serves video, not a directly Whisper-
ready audio file — need to confirm the exact mechanism to pull a downloadable/audio-extractable
version from Cloudflare's API before this can be implemented. Flagging so it isn't assumed away.

This whole pipeline is **async** — transcription of a real lesson video takes real time (minutes, not
milliseconds). Same shape as the `media` module's "upload, then poll status" pattern — don't make
anyone wait on a synchronous HTTP call for this.

---

## 4. Data model

**Important nuance, decided here**: `transcript_chunks` is **not** a hand-written JPA entity like
everything else in this app. Spring AI's `VectorStore` abstraction (backed by `PgVectorStore`) owns
its own underlying table and manages it internally — you interact with it through `Document` objects
(text + a metadata map) via `vectorStore.add(...)` and `vectorStore.similaritySearch(...)`, not through
a repository you write. Defining a custom `TranscriptChunk` entity with a manual `vector` column would
mean re-implementing what the framework already does — the same mistake as writing our own JWT logic
would have been if Spring Security already handled it well. The table still physically exists in
Postgres (Spring AI creates it), the row shape below is just descriptive of what it stores, not a
class to write.

```
transcript_chunks  (owned & managed by Spring AI's PgVectorStore — not a JPA entity)
├── id
├── content          -- the chunk text
├── metadata (jsonb)  -- { "lessonId": "...", "moduleId": "...", "courseId": "...", "chunkIndex": 0 }
└── embedding (vector)
```

Filtering by course/module/lesson at query time (section 2's cross-course isolation) is done via a
`FilterExpression` built from these metadata keys — see the code in section 7.

The rest of the data model **is** plain JPA, same as every other module:

```
lesson_transcripts
├── id (UUID, PK)
├── lesson_id (FK → lessons, unique — one transcript per lesson)
├── full_text (text) — the whole transcript, used for the "default" context-stuffing path
├── status (enum: PENDING | PROCESSING | READY | FAILED)
└── created_at

ai_chat_threads
├── id (UUID, PK)
├── user_id (FK → users)
├── lesson_id (FK → lessons)
├── created_at
└── unique (user_id, lesson_id)   -- one thread per (learner, lesson)

ai_chat_messages
├── id (UUID, PK)
├── thread_id (FK → ai_chat_threads)
├── role (enum: USER | ASSISTANT)
├── content (text)
├── used_broader_search (boolean) -- true if this answer came from the RAG escalation path, for later analysis
└── created_at
```

---

## 5. The request flow

```
Learner opens AI Assistant panel on a lesson
  → must be enrolled in the course (reuse EnrollmentRepository.findByUserIdAndCourseId check)
  → load or create the AiChatThread for (learner, lessonId)
  → return existing AiChatMessage history for that thread

Learner asks a question
  → Step 1 (default): build a prompt = [system prompt] + [full lesson transcript] + [question]
      system prompt enforces: "answer only using the transcript below; if the answer isn't
      covered, say so plainly and offer to search the rest of the course; do not guess."
      → call Claude directly (no retrieval)
  → Step 2 (escalation), triggered when either:
      (a) the learner explicitly asks to search more broadly, or
      (b) Claude's own answer indicates the transcript didn't cover it
      → embed the question (OpenAI embeddings)
      → similarity search in transcript_chunks, filtered by course_id (module_id optional)
      → take top-k chunks, each tagged with its source lesson
      → build a prompt = [system prompt] + [retrieved chunks, each labeled with lesson title] + [question]
      → call Claude again with this augmented context
      → answer includes which lesson(s) the info came from
  → persist both the user's message and the assistant's response as AiChatMessage rows
```

---

## 6. Module structure

```
com.learncreator.aitutor
├── entity/          LessonTranscript, TranscriptChunk, AiChatThread, AiChatMessage
├── repository/       ...matching JPA repositories, TranscriptChunkRepository extends
│                      Spring AI's VectorStore-backed repository pattern where applicable
├── config/           AiTutorConfig — ChatModel/EmbeddingModel/VectorStore beans
├── service/
│   ├── TranscriptionService     — talks to Whisper, populates LessonTranscript (async)
│   ├── EmbeddingIndexService     — chunks + embeds a transcript into transcript_chunks
│   └── AiTutorChatService        — the actual question-answering flow described above
└── controller/       AiTutorController
```

---

## 7. Entities, repositories, and DTOs (actual code, not just schema)

**Enums:**

```java
package com.learncreator.aitutor.entity;

public enum TranscriptStatus {
    PENDING, PROCESSING, READY, FAILED
}
```

```java
package com.learncreator.aitutor.entity;

public enum ChatRole {
    USER, ASSISTANT
}
```

**Entities:**

```java
package com.learncreator.aitutor.entity;

import com.learncreator.courses.entity.Lesson;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_transcripts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LessonTranscript {

    @Id @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    // The whole transcript — this is what gets stuffed into the prompt for the default,
    // non-retrieval path. Chunked/embedded copies of this same text live separately,
    // managed by Spring AI's VectorStore (see section 4).
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
```

```java
package com.learncreator.aitutor.entity;

import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Lesson;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_threads", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "lesson_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiChatThread {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
```

```java
package com.learncreator.aitutor.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiChatMessage {

    @Id @GeneratedValue
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

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
```

**Repositories:**

```java
package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.LessonTranscript;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LessonTranscriptRepository extends JpaRepository<LessonTranscript, UUID> {
    Optional<LessonTranscript> findByLessonId(UUID lessonId);
}
```

```java
package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.AiChatThread;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AiChatThreadRepository extends JpaRepository<AiChatThread, UUID> {
    Optional<AiChatThread> findByUserIdAndLessonId(UUID userId, UUID lessonId);
}
```

```java
package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {
    List<AiChatMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
```

**DTOs:**

```java
package com.learncreator.aitutor.dto;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(
        @NotBlank(message = "message is required")
        String message,

        // Lets the frontend offer an explicit "search the whole course" action,
        // rather than relying only on the model detecting an uncovered question.
        boolean forceBroaderSearch
) {}
```

```java
package com.learncreator.aitutor.dto;

import com.learncreator.aitutor.entity.AiChatMessage;
import com.learncreator.aitutor.entity.ChatRole;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        ChatRole role,
        String content,
        boolean usedBroaderSearch,
        Instant createdAt
) {
    public static ChatMessageResponse from(AiChatMessage message) {
        return new ChatMessageResponse(
                message.getId(), message.getRole(), message.getContent(),
                message.isUsedBroaderSearch(), message.getCreatedAt()
        );
    }
}
```

`AskQuestionRequest` → controller returns `List<ChatMessageResponse>` (the two new messages: the
learner's question and the assistant's reply) so the frontend can append both without a second
round trip, same pattern as `CourseProgressResponse` returning the full fresh picture after an action.

---

## 8. Spring AI wiring (illustrative — the actual shape to build against)

```java
@Configuration
public class AiTutorConfig {

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi);
    }

    @Bean
    public ChatModel chatModel(AnthropicApi anthropicApi) {
        return new AnthropicChatModel(anthropicApi,
                AnthropicChatOptions.builder().withModel("claude-...").build());
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536) // must match the OpenAI embedding model's output size
                .build();
    }
}
```

**Default path — plain ChatClient call, no retrieval:**

```java
public String answerFromLessonTranscript(String transcript, String question) {
    return chatClient.prompt()
            .system("Answer only using the transcript below. If the answer isn't covered, " +
                    "say so plainly and offer to search the rest of the course. Do not guess.\n\n" + transcript)
            .user(question)
            .call()
            .content();
}
```

**Escalation path — Spring AI's QuestionAnswerAdvisor does the retrieve-then-augment step:**

```java
public String answerWithBroaderSearch(UUID courseId, String question) {
    var filter = new FilterExpressionBuilder().eq("course_id", courseId.toString()).build();

    return chatClient.prompt()
            .advisors(new QuestionAnswerAdvisor(vectorStore,
                    SearchRequest.builder().filterExpression(filter).topK(5).build()))
            .user(question)
            .call()
            .content();
}
```

These are illustrative signatures to build from, not final code — real implementation still needs
error handling, the enrollment check, thread/message persistence, and the escalation-trigger logic
(deciding when to move from the default path to the broader-search path).

---

## 9. Endpoints (planned)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/ai-tutor/lessons/{lessonId}/messages` | Load thread history (creates an empty thread if none exists). Requires enrollment. |
| POST | `/api/ai-tutor/lessons/{lessonId}/messages` | Ask a question. Body: `{ "message": "...", "forceBroaderSearch": false }`. Returns the assistant's reply and persists both messages. Requires enrollment. |

---

## 10. Config needed (not yet added to `application.yml`)

```yaml
app:
  openai:
    api-key: ${OPENAI_API_KEY:}
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
```

---

## 11. What's deliberately still open

- Exact mechanism for pulling audio out of Cloudflare Stream for Whisper (see section 3)
- The precise trigger condition for "Claude's answer indicates the transcript didn't cover it" —
  needs a concrete rule (e.g. the model is prompted to prefix such answers with a detectable marker)
- Cost/rate-limit handling across three now-independent vendor APIs (OpenAI x2, Anthropic, Cloudflare)
- Whether `forceBroaderSearch` is a UI toggle the learner controls, or purely automatic

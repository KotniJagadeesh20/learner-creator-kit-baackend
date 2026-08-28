# Schema — learn-creator-backend

Single PostgreSQL database (v1). All tables below currently exist via `ddl-auto: update` — replace
with real Flyway/Liquibase migrations before this touches production data.

## `users` (module: `auth`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| name | text | |
| email | text | unique |
| password_hash | text | BCrypt, work factor 12 |
| role | text (enum) | `LEARNER` \| `CREATOR` \| `ADMIN` |
| bio | text | nullable |
| avatar_url | text | nullable, points to S3 |
| created_at | timestamp | |
| updated_at | timestamp | |

## `refresh_tokens` (module: `auth`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → users) | |
| token_hash | text | unique, SHA-256 of the raw token — raw value never stored |
| expiry_date | timestamp | |
| revoked | boolean | set true on rotation/logout |
| created_at | timestamp | |

## `courses` (module: `courses`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| creator_id | UUID (FK → users) | |
| title | text | |
| description | text | nullable |
| thumbnail_url | text | nullable, S3 |
| category | text | nullable |
| level | text (enum) | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED`, nullable |
| status | text (enum) | `DRAFT` \| `PUBLISHED` |
| price | double | defaults 0.0 — unused until payments are built |
| created_at | timestamp | |
| updated_at | timestamp | |

## `modules` (module: `courses`, entity `CourseModule`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| course_id | UUID (FK → courses) | |
| title | text | |
| order_index | int | |
| created_at | timestamp | |

## `lessons` (module: `courses`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| module_id | UUID (FK → modules) | |
| title | text | |
| video_ref | text | nullable — Cloudflare Stream video ID, set by the future `media` module |
| duration_seconds | int | nullable |
| order_index | int | |
| created_at | timestamp | |

## `creator_applications` (module: `creator`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → users) | the applicant |
| pitch | text | |
| status | text (enum) | `PENDING` \| `APPROVED` \| `REJECTED` |
| reviewed_by | UUID (FK → users) | nullable, the admin who reviewed it |
| reviewed_at | timestamp | nullable |
| rejection_reason | text | nullable |
| created_at | timestamp | |

---

## `enrollments` (module: `enrollments`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → users) | the learner |
| course_id | UUID (FK → courses) | |
| status | text (enum) | `ACTIVE` \| `COMPLETED` |
| enrolled_at | timestamp | |
| completed_at | timestamp | nullable — set once `progress` marks the course fully done |
| | | unique constraint on `(user_id, course_id)` |

---

## `lesson_progress` (module: `progress`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| enrollment_id | UUID (FK → enrollments) | |
| lesson_id | UUID (FK → lessons) | |
| completed | boolean | |
| completed_at | timestamp | nullable, set once, never re-stamped on repeat "mark complete" calls |
| | | unique constraint on `(enrollment_id, lesson_id)` |

---

## `lesson_transcripts` (module: `ai-tutor`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lesson_id | UUID (FK → lessons) | unique — one transcript per lesson |
| full_text | text | nullable until a transcript exists; used for the default context-stuffing path |
| status | text (enum) | `PENDING` \| `PROCESSING` \| `READY` \| `FAILED` |
| created_at | timestamp | |

## `ai_chat_threads` (module: `ai-tutor`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → users) | the learner |
| lesson_id | UUID (FK → lessons) | |
| created_at | timestamp | |
| | | unique constraint on `(user_id, lesson_id)` — one thread per learner per lesson |

## `ai_chat_messages` (module: `ai-tutor`)

| Column | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| thread_id | UUID (FK → ai_chat_threads) | |
| role | text (enum) | `USER` \| `ASSISTANT` |
| content | text | |
| used_broader_search | boolean | true if this answer came from the RAG escalation path |
| source_lesson_titles | text, nullable | comma-separated citation, e.g. "Lesson 2: DI, Lesson 4: Bean Scopes" |
| created_at | timestamp | |

**Not a table above, but worth noting**: embeddings for RAG live in a table Spring AI's `PgVectorStore`
creates and manages itself (`spring.ai.vectorstore.pgvector.initialize-schema: true`) — not a
hand-written entity. Rows there hold chunk text + a `vector` column + a JSON metadata blob
(`lessonId`, `lessonTitle`, `moduleId`, `courseId`, `chunkIndex`), filtered on at query time to scope
retrieval and prevent cross-course contamination. See `AITUTOR.md` section 4 for the full reasoning.

## Planned tables (not yet built)

- **certificates** — deferred entirely (see project decisions)

## Entity-relationship overview

```
users ──< courses ──< modules ──< lessons
  │           │                     │
  │           └──< enrollments      ├──< lesson_progress >── enrollments
  │                                 ├──< lesson_transcripts
  │                                 └──< ai_chat_threads >── users
  │                                        └──< ai_chat_messages
  ├──< refresh_tokens
  └──< creator_applications
```

## Storage split (not all in Postgres)

| Data | Store |
|---|---|
| Everything in the tables above | PostgreSQL (RDS) |
| Video files | Cloudflare Stream (not AWS) — backend stores only the `video_ref` ID. Live as of the `media` module: `MediaService` requests a direct-upload URL, the browser uploads straight to Cloudflare, and the returned video ID is what gets saved on `Lesson.video_ref`. |
| Thumbnails / avatars / any other uploaded file | S3 |
| Auth session/rate-limit data | Redis (ElastiCache) — not yet wired up, planned alongside `media` |
| Lesson transcript embeddings (for AI tutor RAG) | Still PostgreSQL — `pgvector` extension, table managed by Spring AI's `PgVectorStore`, not a hand-written table. See `AITUTOR.md`. |

**Note on the DynamoDB decision, revised**: AI tutor chat history was originally flagged as the one
legitimate DynamoDB candidate in this project (simple key-based access, append-only). That was revised
once `pgvector` was already being added to Postgres for embeddings — introducing a *second* new
datastore for a small, low-volume table (chat messages) stopped making sense once one new capability
was already being added to the database already running. `ai_chat_messages` is a plain Postgres table.
DynamoDB remains unused anywhere in v1 — see `ARCHITECTURE.md` for the original reasoning on why.

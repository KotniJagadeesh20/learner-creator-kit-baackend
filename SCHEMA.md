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

## Planned tables (not yet built)

- **certificates** — deferred entirely (see project decisions)

## Entity-relationship overview

```
users ──< courses ──< modules ──< lessons
  │           │                     │
  │           └──< enrollments      └──< lesson_progress >── enrollments
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

See `ARCHITECTURE.md` for why NoSQL (DynamoDB) is deliberately not used anywhere in v1.

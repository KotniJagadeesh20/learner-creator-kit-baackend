# Architecture — learn-creator-backend

## Overview

Single Spring Boot monolith, organized as a **modular monolith**: one deployable JAR, one database,
but internally split into packages with clear boundaries so any module could be extracted into its
own service later without a rewrite (see "Microservices-readiness" below).

```
com.learncreator
├── auth/         → authentication, JWT, refresh tokens
├── courses/      → course/module/lesson CRUD
├── creator/      → creator application (learner → creator)
├── admin/        → admin review actions (currently just creator-application approval)
├── media/        → (not yet built) video upload, Cloudflare Stream integration
├── enrollments/  → (not yet built) learner enrollment + progress
└── progress/     → (not yet built) lesson completion tracking
```

Stack: Java 17, Spring Boot 3.3, PostgreSQL, Spring Security (JWT), Maven. Deployment target: AWS
(ECS/EKS for the app, RDS for Postgres, S3 for files, planned Cloudflare Stream for video).

---

## Module: `auth`

**Responsibility**: who is this request from, and are they who they say they are.

**Depends on**: nothing else in the app. Every other module depends on it (via the `User` entity and
`Authentication` principal), never the other way around — this is the one hard rule keeping the
module graph acyclic.

**Key design decisions**:
- Access token = stateless JWT (15 min). Refresh token = opaque random string, DB-stored as a SHA-256
  hash only, **rotated on every use** (old one revoked, new one issued in the same call).
- `JwtAuthFilter` sets the actual `User` entity (not just an ID) as the `Authentication` principal —
  every downstream controller gets the full user object with zero extra DB lookups.
- Logout revokes the refresh token but cannot instantly kill an already-issued access token (inherent
  to stateless JWTs). Accepted trade-off for v1; a Redis blocklist would close this gap if ever needed.

**Entities**: `User`, `RefreshToken`.

**Public surface**: `/api/auth/register`, `/login`, `/refresh`, `/logout`. All `permitAll` — this is
the front door.

---

## Module: `courses`

**Responsibility**: course catalog and content structure (Course → Module → Lesson).

**Depends on**: `auth` (needs `User` as the course owner and as the request principal).

**Key design decisions**:
- Ownership is enforced in `CourseService`, not just at the route/security level — so it holds even
  if another module calls into `CourseService` directly later.
- `DRAFT` courses are invisible to everyone except the owner/admin (404, not 403 — existence itself
  is hidden).
- Publishing is blocked if a course has zero modules (guards against an empty "published" course).
- Named `CourseModule`, not `Module`, to avoid colliding with `java.lang.Module`.

**Entities**: `Course`, `CourseModule`, `Lesson`.

**Public surface**: `GET /api/courses` (public catalog), `GET /api/courses/{id}` (public if published),
everything else (`POST`/`PUT`/`PATCH`/`DELETE`, `/mine`) requires `CREATOR` or `ADMIN` + ownership.

---

## Module: `creator` + `admin`

**Responsibility**: the only path from `LEARNER` → `CREATOR`, and admin's review action on it.

**Depends on**: `auth` (User, role).

**Key design decisions**:
- **Reactive moderation, not per-course review**: admin approves the *creator*, not each course.
  Chosen because per-course review doesn't scale past one admin and discourages publishing. Admin
  retains takedown power at the course level (via `courses`' status field) if something needs to come
  down after the fact.
- The role flip (`LEARNER` → `CREATOR`) happens inside `CreatorApplicationService.approve()`, in the
  same transaction as the application status update — these two facts can never drift out of sync.
- `admin` is intentionally thin right now — it's not a "module" with its own domain entities, just a
  controller sitting on top of `creator`'s service. It'll grow its own entities (e.g. moderation
  actions, audit log) once there's more for admin to actually do.

**Entities**: `CreatorApplication` (owned by `creator`; `admin` has none of its own yet).

**Public surface**: `/api/creator/apply`, `/api/creator/applications/mine` (any authenticated user —
deliberately not creator-only, since applicants aren't creators yet). `/api/admin/creator-applications/**`
(`ADMIN` only).

---

## Module: `media`

**Responsibility**: video upload and playback, via Cloudflare Stream — the first module that talks to
an external service instead of just the database.

**Depends on**: `auth` (role check only — `CREATOR`/`ADMIN`).

**Key design decisions**:
- **Direct upload, not proxied through our backend.** `MediaService` asks Cloudflare for a one-time
  upload URL; the frontend uploads the raw video file straight to Cloudflare from the browser. Our
  server never touches video bytes — avoids our backend needing to handle large file streaming,
  memory pressure, or timeouts on big uploads.
- **Cloudflare's raw JSON never leaks past `com.learncreator.media.client`.** `CloudflareStreamClient`
  is the only class that knows Cloudflare's response shape; `MediaService` translates it into our own
  `UploadUrlResponse`/`VideoStatusResponse`. This is deliberate — if we ever switch to Bunny, Mux, or
  self-hosted, only `client/` and `MediaService` change; `MediaController` and the frontend contract
  don't move.
- External-call failures are translated to `502 Bad Gateway`, not a raw exception — a person hitting
  our API shouldn't see Cloudflare's internals, only that the *upload service* is unavailable.
- This is flagged in `ARCHITECTURE.md`'s microservices-readiness note as the first realistic
  extraction candidate, precisely because it already only talks to an external API, not our DB.

**Entities**: none — this module is stateless from our database's point of view. The `video_ref`
(Cloudflare's video ID) it hands back gets stored on `Lesson` by the `courses` module, not here.

**Public surface**: `/api/media/upload-url` (POST, `CREATOR`/`ADMIN`), `/api/media/{videoId}/status`
(GET, `CREATOR`/`ADMIN`).


`SecurityConfig` rules are evaluated **top to bottom, first match wins**. Every time a new module adds
routes under a path prefix another module already used (e.g. `courses` and `creator` both needed
narrower rules than their original blanket ones), the specific rule must be added *before* the general
one. This has already caused two real bugs during development (documented in each module's README
section) — treat this file as the checklist to re-verify whenever a new controller is added.

---

## Module: `enrollments`

**Responsibility**: which learners are enrolled in which courses.

**Depends on**: `auth` (learner identity), `courses` (course existence + publish state).

**Key design decisions**:
- Only `PUBLISHED` courses are enrollable — enrolling in a `DRAFT` course is rejected outright, since a
  draft may be incomplete or intentionally hidden by its creator.
- Duplicate enrollment is blocked both at the DB level (`unique(user_id, course_id)`) and the service
  level (an explicit existence check before insert, so the person gets a clear `409` instead of a raw
  DB constraint violation bubbling up).
- No ownership restriction on *who* can enroll — a `CREATOR` can enroll in another creator's course
  like anyone else. Nothing currently stops a creator from enrolling in their own course either;
  not worth guarding against for v1.

**Entities**: `Enrollment`.

**Public surface**: `POST /api/enrollments` (any authenticated user), `GET /api/enrollments/mine`,
`GET /api/enrollments/course/{courseId}`.

---

## Module: `progress`

**Responsibility**: per-lesson completion tracking, and rolling that up into whole-course completion.

**Depends on**: `courses` (Lesson → Course traversal), `enrollments` (must be enrolled to track progress
at all; owns the `ACTIVE → COMPLETED` transition on `Enrollment`).

**Key design decisions**:
- Marking a lesson complete requires an active enrollment in that lesson's course — enforced by
  looking up the enrollment first and rejecting with `403` if it doesn't exist, not just trusting the
  caller.
- **Auto-completion is one-directional for v1**: the moment every lesson in a course is marked done,
  the enrollment flips to `COMPLETED` automatically. If a creator later adds a *new* lesson to an
  already-completed course, the enrollment is deliberately **not** reopened back to `ACTIVE` — this is
  a judgment call (avoids a learner's "completed" course suddenly reverting), not an oversight, and is
  worth revisiting if it becomes a real product question later.
- Marking an already-completed lesson complete again is idempotent — it does not re-stamp
  `completedAt`, so that timestamp reflects the first completion, not the most recent duplicate call.
- `CourseProgressResponse` (whole-course view: total/completed lesson counts, percent, per-lesson
  breakdown) is returned from *both* "mark complete" and "get progress" — the frontend gets a full,
  fresh picture after every action instead of needing a second round trip.

**Entities**: `LessonProgress`.

**Public surface**: `POST /api/progress/lessons/{lessonId}/complete`, `GET /api/progress/courses/{courseId}`
(both: any authenticated user — enrollment is what actually gates access, not a role check).

---

## Microservices-readiness (not built yet, deliberately)

The module boundaries above are drawn so that a future extraction is plausible without a redesign —
each module's entities are only referenced by FK from other modules, never joined across module
boundaries in application code (e.g. `courses` holds `creator_id` as a foreign key to `users`, but
never does a SQL join across the boundary — it fetches the `User` via `UserRepository` like any other
caller would). The known trade-off: **all modules currently share one database**. A true microservices
split would also require splitting the database per service, which is a bigger step than the code
boundaries alone — see the schema doc for what that would involve per table.

The `media` module (once built) is the first realistic extraction candidate, since it has a
fundamentally different profile (talks to an external service, not just the DB). The **AI tutor**
module (design not yet implemented — see `AITUTOR.md`) is the second: it will also call external
services (OpenAI, Anthropic) rather than just the DB, and was explicitly scoped to be built inside
the monolith first, with extraction only if real usage later justifies it.

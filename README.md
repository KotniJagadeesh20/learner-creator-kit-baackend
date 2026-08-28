# learn-creator-backend — Auth module (v1)

This is the first module of the backend: authentication with an access/refresh token pattern.

## How the token pattern works

- **Access token**: a stateless JWT, signed with `app.jwt.secret`, expires in 15 minutes by default.
  Sent as `Authorization: Bearer <token>` on every protected request. `JwtAuthFilter` validates it
  on every request — no database lookup needed to check the signature/expiry, only to load the user.
- **Refresh token**: an opaque random string (not a JWT), expires in 7 days by default. The server
  never stores the raw value — only a SHA-256 hash of it, in the `refresh_tokens` table. Every time
  a refresh token is used, it is **rotated**: the old one is revoked and a brand new one is issued in
  the same response. This limits the damage if a refresh token is ever stolen — it's single-use.

## Setup

1. Have PostgreSQL running locally (or point `DB_URL` at any Postgres instance, e.g. AWS RDS later).
   ```
   createdb learn_creator
   ```
2. Set environment variables (or rely on the defaults in `application.yml` for local dev):
   ```
   export DB_URL=jdbc:postgresql://localhost:5432/learn_creator
   export DB_USERNAME=postgres
   export DB_PASSWORD=postgres
   export JWT_SECRET=$(openssl rand -base64 48)
   ```
   **Never reuse the default JWT_SECRET outside local development.**
3. Run it:
   ```
   ./mvnw spring-boot:run
   ```
   `ddl-auto: update` will create the `users` and `refresh_tokens` tables automatically on first run.
   (Swap this for Flyway/Liquibase migrations before this touches production data.)

## Endpoints

| Method | Path | Auth required | Body |
|---|---|---|---|
| POST | `/api/auth/register` | No | `{ name, email, password }` |
| POST | `/api/auth/login` | No | `{ email, password }` |
| POST | `/api/auth/refresh` | No (uses refresh token itself) | `{ refreshToken }` |
| POST | `/api/auth/logout` | No | `{ refreshToken }` |

All of these return (except logout, which returns 204):
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "accessTokenExpiresInSeconds": 900,
  "userId": "...",
  "name": "...",
  "email": "...",
  "role": "LEARNER"
}
```

## Manual test flow (curl)

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha Kumar","email":"asha@example.com","password":"supersecret123"}'

# Save the accessToken and refreshToken from the response, then:

# 2. Call a protected endpoint (any future endpoint requiring auth)
curl http://localhost:8080/api/courses/mine \
  -H "Authorization: Bearer <accessToken>"

# 3. When the access token expires (or before), refresh it
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
# Note: this response contains a NEW refreshToken. The old one is now dead — using it again returns 401.

# 4. Logout (revokes the refresh token; access token remains valid until it naturally expires)
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

## Design notes / things worth knowing for later

- **Logout doesn't kill the access token immediately** — that's inherent to stateless JWTs. It only
  revokes the refresh token, so the session can't be *extended* past the current access token's
  expiry. This is why the access token is kept short (15 min). If you ever need instant revocation
  (e.g. "log out everywhere" or banning a user immediately), you'd add a token blocklist (Redis, with
  TTL matching the token's remaining lifetime) — flagging this now as a deliberate v1 trade-off, not
  an oversight.
- Roles are enforced two ways: coarse-grained via `SecurityConfig` path rules (`hasRole("ADMIN")` etc.),
  and available on the `User` principal for fine-grained checks inside controllers/services later.
- This module has no dependency on any other module (courses, enrollments, etc.) — it's a clean base
  to build the rest on top of.

---

# Courses module (v1)

Course → Module → Lesson, with creator-ownership enforcement. `CourseModule` is deliberately not
named `Module` to avoid clashing with `java.lang.Module`.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/courses` | No | Lists only `PUBLISHED` courses (summary — no nested modules) |
| GET | `/api/courses/{id}` | Optional | Full detail incl. modules/lessons. Draft courses 404 unless you're the owner/admin |
| GET | `/api/courses/mine` | CREATOR/ADMIN | Creator's own courses, any status |
| POST | `/api/courses` | CREATOR/ADMIN | Creates a `DRAFT` course |
| PUT | `/api/courses/{id}` | Owner/ADMIN | Updates title/description/etc. |
| PATCH | `/api/courses/{id}/status?status=PUBLISHED` | Owner/ADMIN | Publish/unpublish. Refuses to publish a course with zero modules |
| DELETE | `/api/courses/{id}` | Owner/ADMIN | |
| POST | `/api/courses/{id}/modules` | Owner/ADMIN | Add a module |
| POST | `/api/courses/modules/{moduleId}/lessons` | Owner/ADMIN | Add a lesson. `videoRef` is a Cloudflare Stream ID (from the future media module), not a raw file |

## Ownership model

Every write endpoint checks that the caller is either the course's `creator` or has `ADMIN` role —
enforced in `CourseService`, not just at the route level, so it can't be bypassed by hitting the
service directly (e.g. from another module later). A `CREATOR` who doesn't own a course gets `403`,
not `404` — deliberate, since hiding *existence* isn't the goal here, only preventing edits.

## Manual test flow (curl)

```bash
# Assumes you already have an accessToken for a user with role=CREATOR
# (until the creator-application module exists, promote a user manually in the DB for testing:
#   UPDATE users SET role = 'CREATOR' WHERE email = 'asha@example.com';)

TOKEN="<accessToken>"

# Create a course (starts as DRAFT)
curl -X POST http://localhost:8080/api/courses \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"React for Beginners","description":"Learn React from scratch","category":"tech","level":"BEGINNER"}'

# Add a module (use the courseId from the response above)
curl -X POST http://localhost:8080/api/courses/<courseId>/modules \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Getting Started","orderIndex":0}'

# Add a lesson (use the moduleId from the response above)
curl -X POST http://localhost:8080/api/courses/modules/<moduleId>/lessons \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Installing Node & npm","orderIndex":0,"durationSeconds":300}'

# Publish it
curl -X PATCH "http://localhost:8080/api/courses/<courseId>/status?status=PUBLISHED" \
  -H "Authorization: Bearer $TOKEN"

# Now it's visible publicly, no auth needed
curl http://localhost:8080/api/courses
```


---

# Creator application + admin module (v1)

The only way to become a `CREATOR` in v1: a `LEARNER` applies, an `ADMIN` approves or rejects.
No per-course review — see the design note below for why.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/creator/apply` | Any authenticated user | Only works if you're currently `LEARNER` and have no existing `PENDING` application |
| GET | `/api/creator/applications/mine` | Any authenticated user | Your own application history |
| GET | `/api/admin/creator-applications` | ADMIN | Lists all `PENDING` applications, oldest first |
| POST | `/api/admin/creator-applications/{id}/approve` | ADMIN | Flips the applicant's `role` to `CREATOR` |
| POST | `/api/admin/creator-applications/{id}/reject` | ADMIN | Body: `{ "reason": "..." }` (optional) |

## Design notes

- **Reactive moderation, not per-course review**: once approved, a creator can publish courses
  freely (see the courses module's `DRAFT`/`PUBLISHED` toggle) without further admin gatekeeping.
  This was a deliberate trade-off — pre-publish review doesn't scale past a single admin and
  discourages creators from publishing. Admin still has full unpublish/takedown power at the course
  level if something needs to come down after the fact.
- **The role flip happens inside `approve()`**, not as a separate step — `CreatorApplicationService`
  updates both the `CreatorApplication.status` and the underlying `User.role` in the same
  transaction, so they can never drift out of sync.
- A rejected applicant isn't blocked from ever applying again — there's just no `PENDING`
  application blocking a new one. Nothing currently rate-limits re-applications; add that if it
  becomes a real problem.

## Manual test flow (curl)

```bash
LEARNER_TOKEN="<accessToken for a LEARNER>"
ADMIN_TOKEN="<accessToken for an ADMIN>"
# (there's no signup path to ADMIN yet — for now, manually set one user's role in the DB:
#   UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';)

# Learner applies
curl -X POST http://localhost:8080/api/creator/apply \
  -H "Authorization: Bearer $LEARNER_TOKEN" -H "Content-Type: application/json" \
  -d '{"pitch":"I have 8 years of experience as a data analyst and want to teach SQL fundamentals."}'

# Admin views pending applications
curl http://localhost:8080/api/admin/creator-applications \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Admin approves (use the id from above)
curl -X POST http://localhost:8080/api/admin/creator-applications/<applicationId>/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# The learner's next login/refresh will carry role=CREATOR in the JWT — they can now create courses.
```

---

# Media/upload module (v1)

Video upload via Cloudflare Stream — the browser uploads directly to Cloudflare, never through our
backend. Our server only ever brokers the upload URL and later checks processing status.

## Setup

You need a Cloudflare account with Stream enabled. Set:
```
export CLOUDFLARE_ACCOUNT_ID=<your account id>
export CLOUDFLARE_API_TOKEN=<a token with Stream:Edit permission>
export CLOUDFLARE_CUSTOMER_SUBDOMAIN=<your customer code, e.g. customer-abc123>
```

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/media/upload-url` | CREATOR/ADMIN | Returns `{ uploadUrl, videoId }` — frontend uploads the file directly to `uploadUrl` |
| GET | `/api/media/{videoId}/status` | CREATOR/ADMIN | Poll this until `readyToStream: true` before using the video in a published lesson |

## The full upload flow (frontend's job)

1. `POST /api/media/upload-url` → get `{ uploadUrl, videoId }`
2. Upload the raw video file directly to `uploadUrl` (a `multipart/form-data` `POST` straight to
   Cloudflare — see their direct-upload docs for the exact form field name)
3. Poll `GET /api/media/{videoId}/status` every few seconds until `status: "ready"`
4. Only then call `POST /api/courses/modules/{moduleId}/lessons` with `videoRef: videoId`

## Design notes

- Cloudflare's response shapes (`CloudflareDirectUploadApiResponse`, `CloudflareVideoDetailApiResponse`)
  live only in the `media.client` package and are never returned from our controllers — `MediaService`
  translates everything into our own DTOs. This means swapping providers later only touches two files.
- If Cloudflare is down or the call fails, we return `502 Bad Gateway` with a generic message —
  deliberately not leaking Cloudflare's raw error body to API consumers.

## Manual test flow (curl)

```bash
TOKEN="<accessToken for a CREATOR>"

# 1. Get an upload URL
curl -X POST http://localhost:8080/api/media/upload-url \
  -H "Authorization: Bearer $TOKEN"
# => { "uploadUrl": "https://upload.cloudflarestream.com/...", "videoId": "..." }

# 2. Upload the actual file directly to Cloudflare (not our API) — example using their direct upload URL:
curl -X POST "<uploadUrl from above>" -F file=@/path/to/video.mp4

# 3. Poll status (use the videoId from step 1)
curl http://localhost:8080/api/media/<videoId>/status \
  -H "Authorization: Bearer $TOKEN"
```

---

# Enrollments module (v1)

Learner enrolls in a published course. This is what `courses.status = PUBLISHED` was gating access
towards all along.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/enrollments` | Any authenticated user | Body: `{ "courseId": "..." }`. Rejects `DRAFT` courses and duplicate enrollment |
| GET | `/api/enrollments/mine` | Any authenticated user | The learner's own enrollment list |
| GET | `/api/enrollments/course/{courseId}` | Any authenticated user | Check enrollment status for one specific course (e.g. to decide whether to show "Enroll" or "Continue learning" on the frontend) |

## Manual test flow (curl)

```bash
LEARNER_TOKEN="<accessToken for a LEARNER>"

curl -X POST http://localhost:8080/api/enrollments \
  -H "Authorization: Bearer $LEARNER_TOKEN" -H "Content-Type: application/json" \
  -d '{"courseId":"<a PUBLISHED course id>"}'

curl http://localhost:8080/api/enrollments/mine \
  -H "Authorization: Bearer $LEARNER_TOKEN"
```

---

# Progress module (v1)

Per-lesson completion tracking. This is what finally makes `MyLearningPage`'s "% complete" real, and
what flips an `Enrollment` from `ACTIVE` to `COMPLETED` automatically.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/progress/lessons/{lessonId}/complete` | Any authenticated user | Requires an active enrollment in that lesson's course (403 otherwise). Idempotent — calling it again on an already-completed lesson is a no-op |
| GET | `/api/progress/courses/{courseId}` | Any authenticated user | Requires enrollment (404 otherwise). Returns the same shape as the "mark complete" response |

Both return:
```json
{
  "courseId": "...",
  "totalLessons": 8,
  "completedLessons": 3,
  "percentComplete": 38,
  "enrollmentStatus": "ACTIVE",
  "lessons": [
    { "lessonId": "...", "lessonTitle": "Installing Node & npm", "completed": true },
    { "lessonId": "...", "lessonTitle": "Your First Component", "completed": false }
  ]
}
```

## Design notes

- **This is the last module in v1's core loop.** After this: register → apply to be a creator → get
  approved → create/publish a course → learners enroll → watch lessons → mark them complete →
  enrollment auto-completes when the last one is done. That's the whole v1 scope as originally defined.
- Auto-completion is one-directional — see `ARCHITECTURE.md` for why a newly-added lesson doesn't
  reopen an already-completed enrollment.

## Manual test flow (curl)

```bash
LEARNER_TOKEN="<accessToken for a LEARNER already enrolled in a course>"

curl -X POST http://localhost:8080/api/progress/lessons/<lessonId>/complete \
  -H "Authorization: Bearer $LEARNER_TOKEN"

curl http://localhost:8080/api/progress/courses/<courseId> \
  -H "Authorization: Bearer $LEARNER_TOKEN"
```

---

# Running with Docker

## Backend only

```bash
docker build -t learn-creator-backend .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/learn_creator \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  -e JWT_SECRET=$(openssl rand -base64 48) \
  learn-creator-backend
```

## Full stack (Postgres + backend + frontend) via docker-compose

Assumes this backend repo and the frontend repo (`learn-creator-kit-main`) sit as **sibling
directories** — adjust the `frontend.build.context` path in `docker-compose.yml` if your layout
differs.

```bash
cp .env.example .env   # fill in JWT_SECRET and (optionally) Cloudflare credentials
docker compose up --build
```

- Backend: http://localhost:8080
- Frontend: http://localhost:8081
- Postgres: localhost:5432 (user/pass: `postgres`/`postgres`, db: `learn_creator`)

`ddl-auto: update` will create all tables automatically on first boot — no manual migration step
needed for local dev.

## Notes on the images

- **Backend**: multi-stage build (Maven build stage → slim `eclipse-temurin:17-jre-alpine` runtime).
  Runs as a non-root user inside the container. Final image only contains the built JAR and a JRE —
  no Maven, no source code, no build tools.
- **Frontend**: multi-stage build (Node build stage → `nginx:alpine` runtime). `VITE_*` env vars are
  baked in at **build time**, not runtime (that's how Vite works) — passed as Docker build args. The
  nginx config rewrites unknown paths to `index.html` so React Router routes survive a page refresh.

---

# Seeding mock data

For local testing, `DataSeeder` inserts a realistic set of users, courses, an enrollment mid-progress,
a fully completed enrollment, and one pending creator application — without you having to click
through the whole flow by hand every time you restart the database.

**It never runs by default.** It's only active under the `seed` Spring profile, and it's idempotent
(skips entirely if any users already exist), so it's safe to leave the profile on across restarts.

## Running it

**Maven (local, no Docker):**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

**Docker Compose:**
```bash
# in your .env file:
SPRING_PROFILES_ACTIVE=seed

docker compose up --build
```

## What gets created

| Email | Password | Role |
|---|---|---|
| admin@example.com | password123 | ADMIN |
| asha@example.com | password123 | CREATOR |
| ravi@example.com | password123 | CREATOR |
| priya@example.com | password123 | LEARNER |
| sam@example.com | password123 | LEARNER |

- **"React for Beginners"** (Asha, PUBLISHED) — 2 modules, 5 lessons. Priya is enrolled and 1 lesson
  in, so you can see a partial progress bar.
- **"Advanced Java (Work in Progress)"** (Asha, **DRAFT**) — useful for testing that drafts are hidden
  from the public catalog and from other creators, but visible to Asha herself and to admin.
- **"Digital Marketing 101"** (Ravi, PUBLISHED) — 1 module, 3 lessons. Priya is enrolled and has
  completed every lesson, so this enrollment shows `COMPLETED` — good for testing the auto-completion
  rollup and the "Completed" state on the frontend.
- **One pending creator application** from Sam — log in as `admin@example.com` and visit
  `/admin/creator-applications` on the frontend to approve or reject it.

Note: seeded lessons have no real `videoRef` (no actual video was uploaded to Cloudflare for mock
data), so the play button for those lessons stays disabled on the frontend — that's expected, not a
bug. To test real video playback, upload a video through the creator UI as normal after logging in
as `asha@example.com` or `ravi@example.com`.

---

# AI Tutor module (v1)

Lesson-grounded Q&A chat. Full design reasoning lives in `AITUTOR.md` — read that first if anything
here is unclear. **Status**: core Q&A flow built and tested. Automatic transcript generation (Cloudflare
audio extraction + Whisper) is also built, resolving the blocker flagged earlier — Cloudflare added a
`/downloads/audio` endpoint in Nov 2025 that made it possible. Manual transcript entry still exists
too, unchanged, as a real fallback/alternative, not just a stopgap.

## Setup

```
export OPENAI_API_KEY=<your OpenAI key>       # embeddings + Whisper transcription
export ANTHROPIC_API_KEY=<your Anthropic key>  # the answering LLM
export CLOUDFLARE_API_TOKEN=<needs Stream:Edit permission — same token as the media module>
```

**Important**: this module needs `pgvector` available in Postgres — plain `postgres:16-alpine` does
NOT have it. If running locally without Docker, install the `pgvector` extension on your Postgres
instance yourself; the Docker Compose setup already uses `pgvector/pgvector:pg16` for you.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| PUT | `/api/ai-tutor/lessons/{lessonId}/transcript` | CREATOR/ADMIN (ownership-checked) | Manual transcript entry. Indexes it into the vector store immediately. |
| POST | `/api/ai-tutor/lessons/{lessonId}/transcript/generate` | CREATOR/ADMIN (ownership-checked) | Kicks off automatic generation (Cloudflare audio extraction + Whisper). Returns `202 Accepted` immediately — runs in the background. |
| GET | `/api/ai-tutor/lessons/{lessonId}/transcript/status` | CREATOR/ADMIN | Poll this after calling `/generate` — `PENDING` → `PROCESSING` → `READY`/`FAILED` |
| GET | `/api/ai-tutor/lessons/{lessonId}/messages` | Any authenticated user, enrollment-gated | Load the learner's chat history for this lesson (empty list if no thread yet) |
| POST | `/api/ai-tutor/lessons/{lessonId}/messages` | Any authenticated user, enrollment-gated | Ask a question. Body: `{ "message": "...", "forceBroaderSearch": false }`. Returns both the new user message and the assistant's reply. |

## Manual test flow (curl)

```bash
CREATOR_TOKEN="<accessToken for the course's creator>"
LEARNER_TOKEN="<accessToken for a LEARNER enrolled in that course>"

# 1a. Automatic: kick off generation for a lesson that already has a video uploaded
curl -X POST http://localhost:8080/api/ai-tutor/lessons/<lessonId>/transcript/generate \
  -H "Authorization: Bearer $CREATOR_TOKEN"
# Returns 202 immediately. Poll status:
curl http://localhost:8080/api/ai-tutor/lessons/<lessonId>/transcript/status \
  -H "Authorization: Bearer $CREATOR_TOKEN"
# Expect PENDING -> PROCESSING -> READY (can take several minutes for a real video)

# 1b. OR manual: paste a transcript directly instead
curl -X PUT http://localhost:8080/api/ai-tutor/lessons/<lessonId>/transcript \
  -H "Authorization: Bearer $CREATOR_TOKEN" -H "Content-Type: application/json" \
  -d '{"transcriptText":"In this lesson we cover useState, the React Hook that lets you add local state to a function component..."}'

# 2. Learner asks a question grounded in that lesson
curl -X POST http://localhost:8080/api/ai-tutor/lessons/<lessonId>/messages \
  -H "Authorization: Bearer $LEARNER_TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"What does useState do?","forceBroaderSearch":false}'

# 3. Explicitly search the whole course instead of just this lesson
curl -X POST http://localhost:8080/api/ai-tutor/lessons/<lessonId>/messages \
  -H "Authorization: Bearer $LEARNER_TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"What does useReducer do?","forceBroaderSearch":true}'

# 4. Reload chat history for this lesson
curl http://localhost:8080/api/ai-tutor/lessons/<lessonId>/messages \
  -H "Authorization: Bearer $LEARNER_TOKEN"
```

## Design notes

- Trying to ask a question with no transcript set yet doesn't fail — it falls through directly to
  the broader-search path, since other lessons in the course might still have something relevant.
- Every grounded answer has "Answer based on: ✓ <lesson title>" appended — see `AITUTOR.md` section 11
  ("Guardrails" → source transparency) for why this is done in code, not left to the model to remember.
- **Known gap, not silently skipped**: no rate limiting yet on how many questions a learner can ask.
  Every question is a real, metered call to two paid vendors. Flagged in `AITUTOR.md` as the one
  guardrail with a real dollar cost attached to skipping it — worth prioritizing before any real usage.

---

# Users module (v1)

Own-profile view/edit + public profile view. No new database table — operates entirely on the
`users` table already owned by `auth`.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/users/me` | Authenticated | Own profile, includes email |
| PUT | `/api/users/me` | Authenticated | Update name/bio/avatarUrl |
| GET | `/api/users/{userId}` | Public | Public profile — no email; for creators, lists only their `PUBLISHED` courses |

## Manual test flow (curl)

```bash
TOKEN="<accessToken>"

curl http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"

curl -X PUT http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Asha Kumar","bio":"Data analyst turned educator.","avatarUrl":null}'

# No auth needed for a public profile
curl http://localhost:8080/api/users/<userId>
```

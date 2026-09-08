CREATE TABLE users (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    bio text,
    avatar_url varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash varchar(255) NOT NULL UNIQUE,
    expiry_date timestamptz NOT NULL,
    revoked boolean NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE courses (
    id uuid PRIMARY KEY,
    creator_id uuid NOT NULL REFERENCES users(id),
    title varchar(255) NOT NULL,
    description text,
    thumbnail_url varchar(255),
    category varchar(255),
    level varchar(255),
    status varchar(255) NOT NULL,
    price double precision,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE modules (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title varchar(255) NOT NULL,
    order_index integer NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE lessons (
    id uuid PRIMARY KEY,
    module_id uuid NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title varchar(255) NOT NULL,
    video_ref varchar(255),
    duration_seconds integer,
    order_index integer NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE creator_applications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    pitch text NOT NULL,
    status varchar(255) NOT NULL,
    reviewed_by uuid REFERENCES users(id),
    reviewed_at timestamptz,
    rejection_reason text,
    created_at timestamptz NOT NULL
);

CREATE TABLE enrollments (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    course_id uuid NOT NULL REFERENCES courses(id),
    status varchar(255) NOT NULL,
    enrolled_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uk_enrollment_user_course UNIQUE (user_id, course_id)
);

CREATE TABLE lesson_progress (
    id uuid PRIMARY KEY,
    enrollment_id uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    lesson_id uuid NOT NULL REFERENCES lessons(id),
    completed boolean NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uk_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id)
);

CREATE TABLE lesson_transcripts (
    id uuid PRIMARY KEY,
    lesson_id uuid NOT NULL UNIQUE REFERENCES lessons(id) ON DELETE CASCADE,
    full_text text,
    status varchar(255) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE ai_chat_threads (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    lesson_id uuid NOT NULL REFERENCES lessons(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_chat_thread_user_lesson UNIQUE (user_id, lesson_id)
);

CREATE TABLE ai_chat_messages (
    id uuid PRIMARY KEY,
    thread_id uuid NOT NULL REFERENCES ai_chat_threads(id) ON DELETE CASCADE,
    role varchar(255) NOT NULL,
    content text NOT NULL,
    used_broader_search boolean NOT NULL,
    source_lesson_titles varchar(255),
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_courses_status ON courses(status);
CREATE INDEX idx_courses_creator ON courses(creator_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_enrollments_user ON enrollments(user_id);
CREATE INDEX idx_lesson_progress_enrollment ON lesson_progress(enrollment_id);
CREATE INDEX idx_chat_messages_thread_created ON ai_chat_messages(thread_id, created_at);

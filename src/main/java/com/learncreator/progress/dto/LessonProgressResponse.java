package com.learncreator.progress.dto;

import java.util.UUID;

public record LessonProgressResponse(
        UUID lessonId,
        String lessonTitle,
        boolean completed
) {}

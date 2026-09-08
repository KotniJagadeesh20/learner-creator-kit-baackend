package com.learncreator.courses.dto;

import java.util.UUID;

public record LessonPlaybackResponse(UUID lessonId, String videoRef) {}

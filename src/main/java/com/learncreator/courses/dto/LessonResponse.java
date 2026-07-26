package com.learncreator.courses.dto;

import com.learncreator.courses.entity.Lesson;

import java.util.UUID;

public record LessonResponse(
        UUID id,
        String title,
        String videoRef,
        Integer durationSeconds,
        int orderIndex
) {
    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getVideoRef(),
                lesson.getDurationSeconds(),
                lesson.getOrderIndex()
        );
    }
}
